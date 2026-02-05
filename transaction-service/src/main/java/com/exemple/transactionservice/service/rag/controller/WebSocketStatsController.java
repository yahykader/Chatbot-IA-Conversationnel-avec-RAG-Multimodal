package com.exemple.transactionservice.service.rag.controller;

import com.exemple.transactionservice.websocket.WebSocketSessionManager;
import com.exemple.transactionservice.websocket.WebSocketSessionManager.SessionInfo;
import com.exemple.transactionservice.websocket.WebSocketSessionManager.SessionStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * REST API pour WebSocket statistics et monitoring
 * 
 * Endpoints:
 * - GET /api/v1/websocket/stats       : Statistiques globales
 * - GET /api/v1/websocket/active      : Nombre de sessions actives
 * - GET /api/v1/websocket/sessions    : Liste des sessions actives
 * - GET /api/v1/websocket/session/{id}: Info d'une session
 * - POST /api/v1/websocket/cleanup    : Forcer cleanup
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/websocket")
public class WebSocketStatsController {
    
    @Autowired
    private WebSocketSessionManager sessionManager;
    
    /**
     * GET /api/v1/websocket/stats
     * 
     * Retourne statistiques globales
     */
    @GetMapping("/stats")
    public ResponseEntity<SessionStats> getStats() {
        SessionStats stats = sessionManager.getStats();
        
        log.info("📊 Stats requested: {} active sessions", stats.getActiveSessions());
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * GET /api/v1/websocket/active
     * 
     * Retourne nombre de sessions actives
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveCount() {
        int count = sessionManager.getActiveSessionCount();
        
        return ResponseEntity.ok(Map.of(
            "activeSessions", count,
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * GET /api/v1/websocket/sessions
     * 
     * Liste toutes les sessions actives
     */
    @GetMapping("/sessions")
    public ResponseEntity<Set<String>> getActiveSessions() {
        Set<String> sessionIds = sessionManager.getActiveSessionIds();
        
        return ResponseEntity.ok(sessionIds);
    }
    
    /**
     * GET /api/v1/websocket/session/{sessionId}
     * 
     * Info détaillée d'une session
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<SessionInfo> getSessionInfo(@PathVariable String sessionId) {
        SessionInfo info = sessionManager.getSessionInfo(sessionId);
        
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(info);
    }
    
    /**
     * POST /api/v1/websocket/cleanup
     * 
     * Force cleanup des sessions inactives
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> forceCleanup(
            @RequestParam(defaultValue = "3600000") long inactiveThresholdMs) {
        
        int beforeCount = sessionManager.getActiveSessionCount();
        
        sessionManager.cleanupInactiveSessions(inactiveThresholdMs);
        
        int afterCount = sessionManager.getActiveSessionCount();
        int cleaned = beforeCount - afterCount;
        
        log.info("🧹 Cleanup executed: {} sessions removed", cleaned);
        
        return ResponseEntity.ok(Map.of(
            "cleaned", cleaned,
            "remaining", afterCount,
            "threshold_ms", inactiveThresholdMs
        ));
    }
    
    /**
     * GET /api/v1/websocket/health
     * 
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        SessionStats stats = sessionManager.getStats();
        
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "activeSessions", stats.getActiveSessions(),
            "totalSessions", stats.getTotalSessions()
        ));
    }
}