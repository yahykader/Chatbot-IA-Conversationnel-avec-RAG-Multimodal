package com.exemple.transactionservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler WebSocket générique avec gestion de sessions
 * 
 * Features:
 * - Session management
 * - Message routing par type
 * - Heartbeat/ping-pong
 * - Error handling
 */
@Slf4j
public abstract class WebSocketHandler extends TextWebSocketHandler {
    
    protected final ObjectMapper objectMapper;
    protected final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    protected final Map<String, Object> sessionData = new ConcurrentHashMap<>();
    
    public WebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        
        log.info("🔌 WebSocket connected: {} (total: {})", 
            sessionId, sessions.size());
        
        // Send welcome message
        sendMessage(session, Map.of(
            "type", "connected",
            "sessionId", sessionId,
            "timestamp", System.currentTimeMillis()
        ));
        
        // Callback
        onConnectionEstablished(session);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();
        
        log.debug("📨 WebSocket message: session={}, payload={}", 
            sessionId, truncate(payload, 100));
        
        try {
            // Parse JSON message
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");
            
            if (type == null) {
                sendError(session, "Message type is required", "MISSING_TYPE");
                return;
            }
            
            // Route message by type
            switch (type) {
                case "ping" -> handlePing(session);
                case "pong" -> handlePong(session);
                default -> handleMessage(session, type, data);
            }
            
        } catch (Exception e) {
            log.error("❌ Error handling WebSocket message", e);
            sendError(session, "Failed to process message: " + e.getMessage(), 
                "MESSAGE_PROCESSING_ERROR");
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        sessionData.remove(sessionId);
        
        log.info("🔌 WebSocket disconnected: {} (status: {}, remaining: {})", 
            sessionId, status, sessions.size());
        
        // Callback
        onConnectionClosed(session, status);
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        log.error("❌ WebSocket transport error: {}", sessionId, exception);
        
        sessions.remove(sessionId);
        sessionData.remove(sessionId);
        
        // Callback
        onTransportError(session, exception);
    }
    
    // ========================================================================
    // ABSTRACT METHODS (to be implemented by subclasses)
    // ========================================================================
    
    /**
     * Appelé lors de l'établissement de la connexion
     */
    protected abstract void onConnectionEstablished(WebSocketSession session);
    
    /**
     * Appelé lors de la réception d'un message
     */
    protected abstract void handleMessage(
        WebSocketSession session, 
        String type, 
        Map<String, Object> data
    );
    
    /**
     * Appelé lors de la fermeture de la connexion
     */
    protected abstract void onConnectionClosed(
        WebSocketSession session, 
        CloseStatus status
    );
    
    /**
     * Appelé en cas d'erreur de transport
     */
    protected abstract void onTransportError(
        WebSocketSession session, 
        Throwable exception
    );
    
    // ========================================================================
    // UTILITY METHODS
    // ========================================================================
    
    /**
     * Envoie un message JSON au client
     */
    protected void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
            
            log.trace("📤 Sent message to {}: type={}", 
                session.getId(), message.get("type"));
            
        } catch (IOException e) {
            log.error("❌ Failed to send WebSocket message", e);
        }
    }
    
    /**
     * Broadcast un message à toutes les sessions
     */
    protected void broadcast(Map<String, Object> message) {
        sessions.values().forEach(session -> {
            try {
                sendMessage(session, message);
            } catch (Exception e) {
                log.error("❌ Failed to broadcast to {}", session.getId(), e);
            }
        });
    }
    
    /**
     * Envoie une erreur au client
     */
    protected void sendError(WebSocketSession session, String message, String code) {
        sendMessage(session, Map.of(
            "type", "error",
            "error", Map.of(
                "message", message,
                "code", code,
                "timestamp", System.currentTimeMillis()
            )
        ));
    }
    
    /**
     * Handle ping message
     */
    private void handlePing(WebSocketSession session) {
        log.trace("💓 Ping from {}", session.getId());
        
        sendMessage(session, Map.of(
            "type", "pong",
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * Handle pong message
     */
    private void handlePong(WebSocketSession session) {
        log.trace("💓 Pong from {}", session.getId());
    }
    
    /**
     * Récupère une session par ID
     */
    protected WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    /**
     * Stocke des données associées à une session
     */
    protected void setSessionData(String sessionId, Object data) {
        sessionData.put(sessionId, data);
    }
    
    /**
     * Récupère des données associées à une session
     */
    protected <T> T getSessionData(String sessionId, Class<T> type) {
        Object data = sessionData.get(sessionId);
        if (data != null && type.isInstance(data)) {
            return type.cast(data);
        }
        return null;
    }
    
    /**
     * Ferme une session
     */
    protected void closeSession(String sessionId, CloseStatus status) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.close(status);
            } catch (IOException e) {
                log.error("❌ Error closing session {}", sessionId, e);
            }
        }
    }
    
    /**
     * Nombre de sessions actives
     */
    protected int getActiveSessionCount() {
        return sessions.size();
    }
    
    /**
     * Tronque un texte
     */
    protected String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}