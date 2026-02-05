package com.exemple.transactionservice.service.rag.streaming;

import com.exemple.transactionservice.service.rag.streaming.model.StreamingEvent;
import com.exemple.transactionservice.service.rag.streaming.model.StreamingEvent.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Émetteur d'événements pour SSE et WebSocket
 * 
 * Features:
 * - Buffering tokens pour éviter flooding
 * - Heartbeat automatique
 * - Error handling
 * - Multi-emitter support
 */
@Slf4j
@Component
public class EventEmitter {
    
    private final Map<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();
    private final Map<String, TokenBuffer> tokenBuffers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    private static final int TOKEN_BUFFER_SIZE = 5;
    private static final long TOKEN_FLUSH_INTERVAL_MS = 50;
    private static final long HEARTBEAT_INTERVAL_MS = 15000;
    
    public EventEmitter() {
        // Schedule heartbeat
        scheduler.scheduleAtFixedRate(
            this::sendHeartbeats,
            HEARTBEAT_INTERVAL_MS,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        // Schedule token buffer flush
        scheduler.scheduleAtFixedRate(
            this::flushTokenBuffers,
            TOKEN_FLUSH_INTERVAL_MS,
            TOKEN_FLUSH_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Enregistre un SSE emitter
     */
    public void registerSSE(String sessionId, SseEmitter emitter) {
        sseEmitters.put(sessionId, emitter);
        tokenBuffers.put(sessionId, new TokenBuffer());
        
        // Cleanup on completion/timeout
        emitter.onCompletion(() -> {
            sseEmitters.remove(sessionId);
            tokenBuffers.remove(sessionId);
            log.debug("✅ SSE emitter completed: {}", sessionId);
        });
        
        emitter.onTimeout(() -> {
            sseEmitters.remove(sessionId);
            tokenBuffers.remove(sessionId);
            log.warn("⏱️ SSE emitter timeout: {}", sessionId);
        });
        
        emitter.onError(e -> {
            sseEmitters.remove(sessionId);
            tokenBuffers.remove(sessionId);
            log.error("❌ SSE emitter error: {}", sessionId, e);
        });
        
        log.debug("📡 Registered SSE emitter: {}", sessionId);
    }
    
    /**
     * Émet un événement générique
     */
    public void emit(String sessionId, StreamingEvent event) {
        SseEmitter emitter = sseEmitters.get(sessionId);
        
        if (emitter == null) {
            log.warn("⚠️ No emitter found for session: {}", sessionId);
            return;
        }
        
        try {
            // Format SSE
            String sseData = event.toSSE();
            emitter.send(SseEmitter.event()
                .name(event.getType().name().toLowerCase())
                .data(sseData));
            
            log.trace("📤 Emitted event: {} to session: {}", event.getType(), sessionId);
            
        } catch (IOException e) {
            log.error("❌ Failed to emit event to session: {}", sessionId, e);
            sseEmitters.remove(sessionId);
            tokenBuffers.remove(sessionId);
        }
    }
    
    /**
     * Émet un token (avec buffering)
     */
    public void emitToken(String sessionId, String text, int index) {
        TokenBuffer buffer = tokenBuffers.get(sessionId);
        
        if (buffer == null) {
            return;
        }
        
        buffer.add(text, index);
        
        // Flush si buffer plein
        if (buffer.size() >= TOKEN_BUFFER_SIZE) {
            flushTokenBuffer(sessionId);
        }
    }
    
    /**
     * Émet un événement de connection
     */
    public void emitConnected(String sessionId, String conversationId) {
        StreamingEvent event = StreamingEvent.builder()
            .type(Type.CONNECTED)
            .sessionId(sessionId)
            .conversationId(conversationId)
            .data(Map.of(
                "message", "Connected to streaming assistant",
                "timestamp", Instant.now().toString()
            ))
            .timestamp(Instant.now())
            .build();
        
        emit(sessionId, event);
    }
    
    /**
     * Émet un événement d'erreur
     */
    public void emitError(String sessionId, String message, String code) {
        StreamingEvent event = StreamingEvent.builder()
            .type(Type.ERROR)
            .sessionId(sessionId)
            .data(Map.of(
                "message", message,
                "code", code != null ? code : "UNKNOWN_ERROR",
                "timestamp", Instant.now().toString()
            ))
            .timestamp(Instant.now())
            .build();
        
        emit(sessionId, event);
    }
    
    /**
     * Émet un événement de completion
     */
    public void emitComplete(String sessionId, Map<String, Object> responseData) {
        StreamingEvent event = StreamingEvent.builder()
            .type(Type.COMPLETE)
            .sessionId(sessionId)
            .data(responseData)
            .timestamp(Instant.now())
            .build();
        
        emit(sessionId, event);
    }
    
    /**
     * Complète et ferme l'emitter
     */
    public void complete(String sessionId) {
        SseEmitter emitter = sseEmitters.remove(sessionId);
        tokenBuffers.remove(sessionId);
        
        if (emitter != null) {
            try {
                emitter.complete();
                log.debug("✅ Completed SSE stream: {}", sessionId);
            } catch (Exception e) {
                log.error("❌ Error completing SSE stream: {}", sessionId, e);
            }
        }
    }
    
    /**
     * Complète avec erreur
     */
    public void completeWithError(String sessionId, Throwable error) {
        SseEmitter emitter = sseEmitters.remove(sessionId);
        tokenBuffers.remove(sessionId);
        
        if (emitter != null) {
            try {
                emitter.completeWithError(error);
                log.error("❌ Completed SSE stream with error: {}", sessionId, error);
            } catch (Exception e) {
                log.error("❌ Error completing SSE stream with error: {}", sessionId, e);
            }
        }
    }
    
    // ========================================================================
    // PRIVATE METHODS
    // ========================================================================
    
    /**
     * Flush tous les token buffers
     */
    private void flushTokenBuffers() {
        tokenBuffers.keySet().forEach(this::flushTokenBuffer);
    }
    
    /**
     * Flush un token buffer spécifique
     */
    private void flushTokenBuffer(String sessionId) {
        TokenBuffer buffer = tokenBuffers.get(sessionId);
        
        if (buffer == null || buffer.isEmpty()) {
            return;
        }
        
        List<TokenBuffer.Token> tokens = buffer.flush();
        
        // Combiner les tokens en un seul événement
        StringBuilder combined = new StringBuilder();
        for (TokenBuffer.Token token : tokens) {
            combined.append(token.text);
        }
        
        if (combined.length() > 0) {
            StreamingEvent event = StreamingEvent.builder()
                .type(Type.TOKEN)
                .sessionId(sessionId)
                .data(Map.of(
                    "text", combined.toString(),
                    "count", tokens.size()
                ))
                .timestamp(Instant.now())
                .build();
            
            emit(sessionId, event);
        }
    }
    
    /**
     * Envoie heartbeats à tous les emitters actifs
     */
    private void sendHeartbeats() {
        sseEmitters.keySet().forEach(sessionId -> {
            StreamingEvent heartbeat = StreamingEvent.builder()
                .type(Type.HEARTBEAT)
                .sessionId(sessionId)
                .data(Map.of("timestamp", Instant.now().toString()))
                .timestamp(Instant.now())
                .build();
            
            emit(sessionId, heartbeat);
        });
    }
    
    /**
     * Buffer pour tokens
     */
    private static class TokenBuffer {
        private final List<Token> tokens = new ArrayList<>();
        
        static class Token {
            String text;
            int index;
            
            Token(String text, int index) {
                this.text = text;
                this.index = index;
            }
        }
        
        void add(String text, int index) {
            tokens.add(new Token(text, index));
        }
        
        int size() {
            return tokens.size();
        }
        
        boolean isEmpty() {
            return tokens.isEmpty();
        }
        
        List<Token> flush() {
            List<Token> flushed = new ArrayList<>(tokens);
            tokens.clear();
            return flushed;
        }
    }
}