package com.exemple.transactionservice.controller;

import com.exemple.transactionservice.service.rag.streaming.*;
import com.exemple.transactionservice.service.rag.streaming.model.StreamingRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Controller pour Server-Sent Events (SSE)
 * 
 * Features:
 * - Streaming temps réel token-par-token
 * - Support GET (EventSource) et POST
 * - Événements de progression détaillés
 * - Heartbeat automatique
 * - Timeout configuré
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Streaming Search", description = "API Streaming Search")
public class StreamingAssistantController {
    
    private final StreamingOrchestrator orchestrator;
    private final EventEmitter eventEmitter;
    
    private static final long SSE_TIMEOUT = 300000; // 5 minutes
    
    public StreamingAssistantController(
            StreamingOrchestrator orchestrator,
            EventEmitter eventEmitter) {
        this.orchestrator = orchestrator;
        this.eventEmitter = eventEmitter;
    }
    
    // ========================================================================
    // ✅ NOUVEAU: Endpoint GET pour EventSource (Browser)
    // ========================================================================
    
    /**
     * Endpoint SSE avec GET
     * 
     * GET /api/v1/assistant/stream?query=...&conversationId=...
     * Accept: text/event-stream
     * 
     * Utilisé par EventSource dans les navigateurs (Angular, React, etc.)
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGet(
            @RequestParam String query,
            @RequestParam(required = false) String conversationId) {
        
        log.info("📡 SSE GET: query={}, conversationId={}", 
            truncate(query, 50), conversationId);
        
        // Construire StreamingRequest à partir des query params
        StreamingRequest request = StreamingRequest.builder()
            .query(query)
            .conversationId(conversationId)
            .build();
        
        // Utiliser la même logique que POST
        return executeStream(request);
    }
    
    // ========================================================================
    // Endpoint POST existant (conservé tel quel)
    // ========================================================================
    
    /**
     * Endpoint SSE avec POST
     * 
     * POST /api/v1/assistant/stream
     * Content-Type: application/json
     * Accept: text/event-stream
     * 
     * Body:
     * {
     *   "query": "Analyse les ventes Q3",
     *   "conversationId": "conv_abc123" (optional),
     *   "options": { ... }
     * }
     * 
     * Response: text/event-stream
     * event: connected
     * data: {"sessionId":"...","conversationId":"..."}
     * 
     * event: token
     * data: {"text":"Selon","index":0}
     * 
     * event: complete
     * data: {"response":{...},"metadata":{...}}
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPost(@RequestBody StreamingRequest request) {
        
        log.info("📡 SSE POST: query={}, conversationId={}", 
            truncate(request.getQuery(), 50), request.getConversationId());
        
        return executeStream(request);
    }
    
    // ========================================================================
    // ✅ NOUVEAU: Méthode commune pour GET et POST
    // ========================================================================
    
    /**
     * Logique commune d'exécution du streaming
     * (évite la duplication de code entre GET et POST)
     */
    private SseEmitter executeStream(StreamingRequest request) {
        
        // Générer session ID unique
        String sessionId = generateSessionId();
        
        log.info("🚀 Starting SSE stream: sessionId={}", sessionId);
        
        // Créer SSE emitter
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // Enregistrer l'emitter
        eventEmitter.registerSSE(sessionId, emitter);
        
        // Execute pipeline asynchrone
        CompletableFuture.runAsync(() -> {
            try {
                // Émettre événement de connexion
                eventEmitter.emitConnected(sessionId, request.getConversationId());
                
                // Execute streaming orchestrator
                orchestrator.executeStreaming(sessionId, request).join();
                
            } catch (Exception e) {
                log.error("❌ SSE streaming error: sessionId={}", sessionId, e);
                eventEmitter.emitError(sessionId, e.getMessage(), "STREAMING_ERROR");
                eventEmitter.completeWithError(sessionId, e);
            }
        });
        
        return emitter;
    }
    
    // ========================================================================
    // Endpoints auxiliaires (inchangés)
    // ========================================================================
    
    /**
     * Endpoint de santé pour SSE
     */
    @GetMapping("/stream/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("SSE streaming endpoint is healthy");
    }
    
    /**
     * Endpoint pour annuler un stream
     * 
     * POST /api/v1/assistant/stream/{sessionId}/cancel
     */
    @PostMapping("/stream/{sessionId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String sessionId) {
        log.info("🛑 Cancelling stream: sessionId={}", sessionId);
        
        eventEmitter.emitError(sessionId, "Stream cancelled by user", "CANCELLED");
        eventEmitter.complete(sessionId);
        
        return ResponseEntity.ok().build();
    }
    
    // ========================================================================
    // HELPER METHODS (inchangés)
    // ========================================================================
    
    private String generateSessionId() {
        return "session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}