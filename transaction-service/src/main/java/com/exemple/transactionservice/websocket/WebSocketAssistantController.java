package com.exemple.transactionservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;

/**
 * WebSocket controller for RAG Assistant
 * 
 * AVEC INTÉGRATION WebSocketSessionManager
 */
@Slf4j
@Component
public class WebSocketAssistantController extends WebSocketHandler {
    
    // ========================================================================
    // INJECTION WebSocketSessionManager
    // ========================================================================
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    public WebSocketAssistantController(ObjectMapper objectMapper) {
        super(objectMapper);
    }
    
    // ========================================================================
    // LIFECYCLE HOOKS (avec SessionManager)
    // ========================================================================
    
    @Override
    protected void onConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        
        log.info("✅ RAG Assistant connected: {}", sessionId);
        
        // 🔥 UTILISATION 1: Register session
        sessionManager.registerSession(session, "anonymous"); // TODO: Get real userId from auth
        
        // Send welcome message
        sendMessage(session, Map.of(
            "type", "connected",
            "sessionId", sessionId,
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    @Override
    protected void handleMessage(WebSocketSession session, String type, Map<String, Object> data) {
        String sessionId = session.getId();
        
        // 🔥 UTILISATION 2: Update activity on every message
        sessionManager.updateActivity(sessionId);
        
        log.info("📨 Message type: {} from session: {}", type, sessionId);
        
        switch (type) {
            case "init" -> handleInit(session, data);
            case "query" -> handleQuery(session, data);
            case "cancel" -> handleCancel(session);
            default -> sendError(session, "Unknown type: " + type, "UNKNOWN_TYPE");
        }
    }
    
    @Override
    protected void onConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        
        log.info("✅ RAG Assistant disconnected: {} (status: {})", sessionId, status);
        
        // 🔥 UTILISATION 3: Unregister session
        sessionManager.unregisterSession(sessionId);
    }

    @Override
    protected void onTransportError(WebSocketSession session, Throwable exception) {
        String sessionId = session.getId();
        log.error("❌ WebSocket transport error: {}", sessionId, exception);
        
        // Optional: cleanup session
        if (sessionManager != null) {
            sessionManager.unregisterSession(sessionId);
        }
    }
    
    // ========================================================================
    // MESSAGE HANDLERS
    // ========================================================================
    
    private void handleInit(WebSocketSession session, Map<String, Object> data) {
        String sessionId = session.getId();
        String userId = (String) data.get("userId");
        
        // Generate conversation ID
        String conversationId = "conv_" + UUID.randomUUID().toString().substring(0, 8);
        
        // 🔥 UTILISATION 4: Store conversationId in session
        sessionManager.setConversationId(sessionId, conversationId);
        
        log.info("🔧 Init: user={}, conv={}", userId, conversationId);
        
        sendMessage(session, Map.of(
            "type", "conversation_created",
            "conversationId", conversationId,
            "userId", userId != null ? userId : "anonymous"
        ));
    }
    
    private void handleQuery(WebSocketSession session, Map<String, Object> data) {
        String sessionId = session.getId();
        String query = (String) data.get("text");
        
        if (query == null || query.isEmpty()) {
            sendError(session, "Query text is required", "MISSING_QUERY");
            return;
        }
        
        // 🔥 UTILISATION 5: Get conversationId from session
        String conversationId = sessionManager.getConversationId(sessionId);
        
        log.info("🚀 Query: {} (conv: {})", truncate(query, 50), conversationId);
        
        sendMessage(session, Map.of(
            "type", "query_received",
            "query", query,
            "conversationId", conversationId
        ));
        
        // TODO: Integrate with StreamingOrchestrator
        // Mock response for now
        simulateResponse(session, query);
    }
    
    private void handleCancel(WebSocketSession session) {
        String sessionId = session.getId();
        
        log.info("🛑 Cancel: {}", sessionId);
        
        sendMessage(session, Map.of(
            "type", "cancelled",
            "message", "Stream cancelled"
        ));
    }
    
    // ========================================================================
    // HELPER METHODS
    // ========================================================================
    
    /**
     * 🔥 UTILISATION 6: Broadcast to all active sessions
     */
    public void broadcastToAll(Map<String, Object> message) {
        int sent = 0;
        
        for (String sessionId : sessionManager.getActiveSessionIds()) {
            WebSocketSession session = sessionManager.getSession(sessionId);
            if (session != null && session.isOpen()) {
                sendMessage(session, message);
                sent++;
            }
        }
        
        log.info("📢 Broadcast sent to {} sessions", sent);
    }
    
    /**
     * Mock response (remove when real integration done)
     */
    private void simulateResponse(WebSocketSession session, String query) {
        // Simulate tokens
        String[] tokens = {"Selon", " le", " rapport", ", les", " ventes..."};
        
        new Thread(() -> {
            try {
                for (int i = 0; i < tokens.length; i++) {
                    Thread.sleep(100);
                    
                    sendMessage(session, Map.of(
                        "type", "token",
                        "data", Map.of(
                            "text", tokens[i],
                            "index", i
                        )
                    ));
                }
                
                sendMessage(session, Map.of(
                    "type", "complete",
                    "response", Map.of(
                        "text", String.join("", tokens),
                        "sources", java.util.List.of()
                    )
                ));
                
            } catch (InterruptedException e) {
                log.error("Error simulating response", e);
            }
        }).start();
    }
}