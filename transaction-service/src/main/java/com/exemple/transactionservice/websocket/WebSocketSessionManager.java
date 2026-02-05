package com.exemple.transactionservice.websocket;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Gestionnaire de sessions WebSocket
 * 
 * Features:
 * - Track active sessions
 * - Store session metadata
 * - Session lifecycle management
 * - Statistics and monitoring
 */
@Slf4j
@Component
public class WebSocketSessionManager {
    
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> activeConnections = new ConcurrentHashMap<>();
    
    /**
     * Register a new WebSocket session
     */
    public void registerSession(WebSocketSession session, String userId) {
        String sessionId = session.getId();
        
        SessionInfo info = new SessionInfo();
        info.sessionId = sessionId;
        info.userId = userId;
        info.connectionTime = System.currentTimeMillis();
        info.lastActivity = System.currentTimeMillis();
        info.active = true;
        
        sessions.put(sessionId, info);
        activeConnections.put(sessionId, session);
        
        log.info("📝 Registered WebSocket session: {} (user: {}, total: {})",
            sessionId, userId, sessions.size());
    }
    
    /**
     * Unregister a WebSocket session
     */
    public void unregisterSession(String sessionId) {
        SessionInfo info = sessions.remove(sessionId);
        activeConnections.remove(sessionId);
        
        if (info != null) {
            info.active = false;
            info.disconnectionTime = System.currentTimeMillis();
            
            long duration = info.disconnectionTime - info.connectionTime;
            
            log.info("📝 Unregistered WebSocket session: {} (duration: {}ms, total: {})",
                sessionId, duration, sessions.size());
        }
    }
    
    /**
     * Update session activity
     */
    public void updateActivity(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        if (info != null) {
            info.lastActivity = System.currentTimeMillis();
            info.messageCount++;
        }
    }
    
    /**
     * Get session info
     */
    public SessionInfo getSessionInfo(String sessionId) {
        return sessions.get(sessionId);
    }
    
    /**
     * Get WebSocket session
     */
    public WebSocketSession getSession(String sessionId) {
        return activeConnections.get(sessionId);
    }
    
    /**
     * Get all active session IDs
     */
    public java.util.Set<String> getActiveSessionIds() {
        return new java.util.HashSet<>(activeConnections.keySet());
    }
    
    /**
     * Get total active sessions
     */
    public int getActiveSessionCount() {
        return activeConnections.size();
    }
    
    /**
     * Set conversation ID for session
     */
    public void setConversationId(String sessionId, String conversationId) {
        SessionInfo info = sessions.get(sessionId);
        if (info != null) {
            info.conversationId = conversationId;
        }
    }
    
    /**
     * Get conversation ID for session
     */
    public String getConversationId(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        return info != null ? info.conversationId : null;
    }
    
    /**
     * Check if session is active
     */
    public boolean isActive(String sessionId) {
        return activeConnections.containsKey(sessionId);
    }
    
    /**
     * Get session statistics
     */
    public SessionStats getStats() {
        SessionStats stats = new SessionStats();
        stats.totalSessions = sessions.size();
        stats.activeSessions = activeConnections.size();
        
        // ✅ Utiliser AtomicLong pour être compatible avec lambda
        final long[] totalMessages = {0};
        final long[] totalDuration = {0};
        
        sessions.values().forEach(info -> {
            totalMessages[0] += info.messageCount;
            
            if (info.active) {
                long duration = System.currentTimeMillis() - info.connectionTime;
                totalDuration[0] += duration;
            } else if (info.disconnectionTime > 0) {
                long duration = info.disconnectionTime - info.connectionTime;
                totalDuration[0] += duration;
            }
        });
        
        stats.totalMessages = totalMessages[0];
        stats.avgConnectionDuration = totalDuration[0];
        
        if (stats.totalSessions > 0) {
            stats.avgConnectionDuration /= stats.totalSessions;
            stats.avgMessagesPerSession = (double) stats.totalMessages / stats.totalSessions;
        }
        
        return stats;
    }
    
    /**
     * Clean up inactive sessions (call periodically)
     */
    public void cleanupInactiveSessions(long inactiveThresholdMs) {
        long now = System.currentTimeMillis();
        
        // ✅ CORRECTION: Utiliser AtomicInteger au lieu de int
        final AtomicInteger cleaned = new AtomicInteger(0);
        
        sessions.entrySet().removeIf(entry -> {
            SessionInfo info = entry.getValue();
            if (!info.active && info.disconnectionTime > 0) {
                long inactiveDuration = now - info.disconnectionTime;
                if (inactiveDuration > inactiveThresholdMs) {
                    cleaned.incrementAndGet(); // ✅ Utiliser incrementAndGet()
                    return true;
                }
            }
            return false;
        });
        
        if (cleaned.get() > 0) {
            log.info("🧹 Cleaned up {} inactive sessions", cleaned.get());
        }
    }
    
    // ========================================================================
    // DATA CLASSES
    // ========================================================================
    
    /**
     * Information about a WebSocket session
     */
    @Data
    public static class SessionInfo {
        private String sessionId;
        private String userId;
        private String conversationId;
        private long connectionTime;
        private long lastActivity;
        private long disconnectionTime;
        private boolean active;
        private int messageCount;
    }
    
    /**
     * Session statistics
     */
    @Data
    public static class SessionStats {
        private int totalSessions;
        private int activeSessions;
        private long totalMessages;
        private double avgMessagesPerSession;
        private long avgConnectionDuration;
    }
}