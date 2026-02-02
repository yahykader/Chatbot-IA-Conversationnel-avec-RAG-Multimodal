// ============================================================================
// INTERCEPTOR - RateLimitInterceptor.java
// Intercepteur pour appliquer automatiquement le Rate Limiting
// ============================================================================
package com.exemple.transactionservice.service.rag.interceptor;

import com.exemple.transactionservice.service.rag.ingestion.ratelimit.RateLimitService;
import com.exemple.transactionservice.service.rag.ingestion.ratelimit.RateLimitService.RateLimitResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Intercepteur HTTP pour appliquer le Rate Limiting automatiquement.
 * 
 * Détecte automatiquement le type d'endpoint et applique la limite appropriée.
 * 
 * Routes protégées :
 * - /api/v1/ingestion/upload* → uploadLimit
 * - /api/v1/ingestion/upload/batch* → batchLimit
 * - /api/v1/crud/file/* (DELETE) → deleteLimit
 * - /api/v1/ingestion/search* → searchLimit
 * - Autres → defaultLimit
 * 
 * @author RAG Team
 * @version 1.0
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    
    public RateLimitInterceptor(
            RateLimitService rateLimitService,
            ObjectMapper objectMapper) {
        
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
        
        log.info("✅ RateLimitInterceptor initialisé");
    }
    
    @Override
    public boolean preHandle(
            HttpServletRequest request, 
            HttpServletResponse response, 
            Object handler) throws Exception {
        
        // Récupérer userId (depuis header, session, JWT, etc.)
        String userId = getUserId(request);
        
        if (userId == null) {
            // Si pas d'userId, utiliser l'IP comme fallback
            userId = getClientIP(request);
        }
        
        // Déterminer le type d'endpoint
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        RateLimitResult result = checkRateLimit(userId, path, method);
        
        if (result.isAllowed()) {
            // Ajouter headers informatifs
            response.addHeader("X-RateLimit-Remaining", 
                String.valueOf(result.getRemainingTokens()));
            
            return true; // Autoriser la requête
            
        } else {
            // Bloquer la requête
            sendRateLimitExceededResponse(response, result);
            return false;
        }
    }
    
    /**
     * Vérifie le rate limit selon le type d'endpoint.
     */
    private RateLimitResult checkRateLimit(String userId, String path, String method) {
        
        // Upload batch
        if (path.contains("/upload/batch")) {
            return rateLimitService.checkBatchLimit(userId);
        }
        
        // Upload simple
        if (path.contains("/upload")) {
            return rateLimitService.checkUploadLimit(userId);
        }
        
        // Delete
        if (path.contains("/file/") && "DELETE".equals(method)) {
            return rateLimitService.checkDeleteLimit(userId);
        }
        
        if (path.contains("/batch/") && "DELETE".equals(method)) {
            return rateLimitService.checkDeleteLimit(userId);
        }
        
        if (path.contains("/files/") && "DELETE".equals(method)) {
            return rateLimitService.checkDeleteLimit(userId);
        }
        
        // Search
        if (path.contains("/search")) {
            return rateLimitService.checkSearchLimit(userId);
        }
        
        // Default pour les autres endpoints
        return rateLimitService.checkDefaultLimit(userId);
    }
    
    /**
     * Récupère l'ID utilisateur depuis la requête.
     * 
     * Priorité :
     * 1. Header X-User-Id
     * 2. JWT token (si authentification activée)
     * 3. Session
     * 4. null (fallback sur IP)
     */
    private String getUserId(HttpServletRequest request) {
        
        // 1. Header X-User-Id
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        
        // 2. JWT (à implémenter si vous utilisez JWT)
        // String jwt = request.getHeader("Authorization");
        // if (jwt != null) {
        //     return extractUserIdFromJWT(jwt);
        // }
        
        // 3. Session
        if (request.getSession(false) != null) {
            Object sessionUserId = request.getSession().getAttribute("userId");
            if (sessionUserId != null) {
                return sessionUserId.toString();
            }
        }
        
        // 4. Fallback sur IP (sera fait par le caller)
        return null;
    }
    
    /**
     * Récupère l'IP du client.
     */
    private String getClientIP(HttpServletRequest request) {
        
        // Vérifier les headers de proxy
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            // Prendre la première IP si plusieurs
            return ip.split(",")[0].trim();
        }
        
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip;
        }
        
        // Fallback sur l'IP directe
        return request.getRemoteAddr();
    }
    
    /**
     * Envoie une réponse 429 Too Many Requests.
     */
    private void sendRateLimitExceededResponse(
            HttpServletResponse response, 
            RateLimitResult result) throws IOException {
        
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        // Ajouter headers standard rate limiting
        response.addHeader("X-RateLimit-Limit", "voir configuration");
        response.addHeader("X-RateLimit-Remaining", "0");
        response.addHeader("X-RateLimit-Reset", 
            String.valueOf(System.currentTimeMillis() / 1000 + result.getRetryAfterSeconds()));
        response.addHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
        
        // Corps de la réponse
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Too Many Requests");
        body.put("message", "Rate limit dépassé. Réessayez dans " + 
            result.getRetryAfterSeconds() + " secondes.");
        body.put("retryAfterSeconds", result.getRetryAfterSeconds());
        body.put("timestamp", System.currentTimeMillis());
        
        String json = objectMapper.writeValueAsString(body);
        response.getWriter().write(json);
        response.getWriter().flush();
    }
}