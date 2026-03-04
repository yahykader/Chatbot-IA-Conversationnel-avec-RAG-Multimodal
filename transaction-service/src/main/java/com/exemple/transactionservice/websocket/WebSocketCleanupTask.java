package com.exemple.transactionservice.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task pour cleanup automatique des sessions WebSocket
 * 
 * Runs every hour to clean up inactive sessions
 */
@Slf4j
@Component
public class WebSocketCleanupTask {
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    // Cleanup threshold: 1 hour
    private static final long INACTIVE_THRESHOLD_MS = 3600000; // 1 hour
    
    /**
     * Cleanup task executed every hour
     */
    @Scheduled(fixedRate = 3600000) // Every 1 hour
    public void cleanupInactiveSessions() {
        log.info("🧹 Starting scheduled cleanup of inactive WebSocket sessions...");
        
        int beforeCount = sessionManager.getActiveSessionCount();
        
        sessionManager.cleanupInactiveSessions(INACTIVE_THRESHOLD_MS);
        
        int afterCount = sessionManager.getActiveSessionCount();
        int cleaned = beforeCount - afterCount;
        
        log.info("🧹 Cleanup completed: {} sessions removed, {} remaining", 
            cleaned, afterCount);
    }
    
    /**
     * Log stats every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void logStats() {
        var stats = sessionManager.getStats();
        
        log.info("📊 WebSocket Stats: {} active, {} total, avg {} msg/session, avg duration {}ms",
            stats.getActiveSessions(),
            stats.getTotalSessions(),
            String.format("%.2f", stats.getAvgMessagesPerSession()),
            stats.getAvgConnectionDuration());
    }
}