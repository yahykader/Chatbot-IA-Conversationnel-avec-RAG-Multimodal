package com.exemple.transactionservice.service;

import com.exemple.transactionservice.dto.CachedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * ✅ Service de gestion du cache LLM avec Redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * ✅ Génère une clé de cache unique pour une question
     * Utilise SHA-256 pour garantir unicité et taille fixe
     */
    public String generateCacheKey(String question, String userId) {
        try {
            String input = question.trim().toLowerCase() + "|" + (userId != null ? userId : "anonymous");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return "llm:q:" + hexString.toString();
            
        } catch (Exception e) {
            log.error("❌ [Cache] Erreur génération clé", e);
            return "llm:q:" + question.hashCode();
        }
    }
    
    /**
     * ✅ Récupère une réponse du cache
     */
    @Cacheable(value = "llm-responses", key = "#cacheKey", unless = "#result == null")
    public CachedResponse getCachedResponse(String cacheKey) {
        log.debug("🔍 [Cache] Recherche: {}", cacheKey);
        return null; // Spring gère automatiquement via @Cacheable
    }
    
    /**
     * ✅ Stocke une réponse dans le cache
     */
    @CachePut(value = "llm-responses", key = "#cacheKey")
    public CachedResponse cacheResponse(
        String cacheKey,
        String question,
        String response,
        String model,
        Integer tokensUsed,
        Long responseTimeMs,
        String userId
    ) {
        log.info("💾 [Cache] Stockage: {}", cacheKey);
        
        CachedResponse cached = CachedResponse.builder()
            .question(question)
            .response(response)
            .model(model)
            .timestamp(LocalDateTime.now())
            .tokensUsed(tokensUsed)
            .responseTimeMs(responseTimeMs)
            .userId(userId)
            .build();
        
        log.debug("✅ [Cache] Réponse mise en cache (TTL: 24h)");
        return cached;
    }
    
    /**
     * ✅ Invalide une entrée du cache
     */
    @CacheEvict(value = "llm-responses", key = "#cacheKey")
    public void evictCache(String cacheKey) {
        log.info("🗑️ [Cache] Invalidation: {}", cacheKey);
    }
    
    /**
     * ✅ Invalide tout le cache LLM
     */
    @CacheEvict(value = "llm-responses", allEntries = true)
    public void evictAllCache() {
        log.warn("🗑️ [Cache] Invalidation complète du cache LLM");
    }
    
    /**
     * ✅ Vérifie si une clé existe dans le cache
     */
    public boolean existsInCache(String cacheKey) {
        Boolean exists = redisTemplate.hasKey("llm-cache:llm-responses::" + cacheKey);
        log.debug("🔍 [Cache] Existe: {} → {}", cacheKey, exists);
        return Boolean.TRUE.equals(exists);
    }
    
    /**
     * ✅ Obtient le TTL d'une clé
     */
    public Long getTtl(String cacheKey) {
        Long ttl = redisTemplate.getExpire("llm-cache:llm-responses::" + cacheKey, TimeUnit.SECONDS);
        log.debug("⏱️ [Cache] TTL: {} → {} secondes", cacheKey, ttl);
        return ttl;
    }
}