package com.exemple.transactionservice.service.rag.cache.metrics;

import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Service de cache Redis
 * 
 * ✅ ADAPTÉ AVEC RAGMetrics unifié
 * 
 * Gère le cache des embeddings et autres données
 */
@Slf4j
@Service
public class CacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RAGMetrics ragMetrics;  // ✅ Métriques unifiées
    
    // Configuration du cache
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final String EMBEDDING_PREFIX = "embedding:";
    private static final String QUERY_PREFIX = "query:";
    
    public CacheService(
            @Qualifier("redisTemplateJson") RedisTemplate<String, Object> redisTemplate,
            RAGMetrics ragMetrics) {
        
        this.redisTemplate = redisTemplate;
        this.ragMetrics = ragMetrics;
    }
    
    /**
     * Récupère une valeur du cache
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            @SuppressWarnings("unchecked")
            T value = (T) redisTemplate.opsForValue().get(key);
            
            if (value != null) {
                // ✅ MÉTRIQUE: Cache HIT
                ragMetrics.recordCacheHit(getCacheType(key));
                
                log.debug("✅ Cache HIT: {}", key);
                return Optional.of(value);
            } else {
                // ✅ MÉTRIQUE: Cache MISS
                ragMetrics.recordCacheMiss(getCacheType(key));
                
                log.debug("❌ Cache MISS: {}", key);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            log.error("❌ Cache get error: {}", key, e);
            
            // ✅ MÉTRIQUE: Cache MISS (en cas d'erreur)
            ragMetrics.recordCacheMiss(getCacheType(key));
            
            return Optional.empty();
        }
    }
    
    /**
     * Stocke une valeur dans le cache
     */
    public void put(String key, Object value) {
        put(key, value, DEFAULT_TTL);
    }
    
    /**
     * Stocke une valeur dans le cache avec TTL custom
     */
    public void put(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            log.debug("💾 Cache SET: {} (TTL={})", key, ttl);
            
        } catch (Exception e) {
            log.error("❌ Cache set error: {}", key, e);
        }
    }
    
    /**
     * Supprime une valeur du cache
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("🗑️ Cache DELETE: {}", key);
            
        } catch (Exception e) {
            log.error("❌ Cache delete error: {}", key, e);
        }
    }
    
    /**
     * Vérifie si une clé existe
     */
    public boolean exists(String key) {
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
            
        } catch (Exception e) {
            log.error("❌ Cache exists error: {}", key, e);
            return false;
        }
    }
    
    // ========================================================================
    // MÉTHODES SPÉCIALISÉES
    // ========================================================================
    
    /**
     * Cache un embedding
     */
    public void cacheEmbedding(String text, Object embedding) {
        String key = EMBEDDING_PREFIX + hash(text);
        put(key, embedding, Duration.ofDays(7));
    }
    
    /**
     * Récupère un embedding du cache
     */
    public <T> Optional<T> getEmbedding(String text, Class<T> type) {
        String key = EMBEDDING_PREFIX + hash(text);
        return get(key, type);
    }
    
    /**
     * Cache le résultat d'une query
     */
    public void cacheQueryResult(String query, Object result) {
        String key = QUERY_PREFIX + hash(query);
        put(key, result, Duration.ofHours(1));
    }
    
    /**
     * Récupère le résultat d'une query du cache
     */
    public <T> Optional<T> getQueryResult(String query, Class<T> type) {
        String key = QUERY_PREFIX + hash(query);
        return get(key, type);
    }
    
    /**
     * Vide tout le cache
     */
    public void clear() {
        try {
            redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();
            
            log.warn("⚠️ Cache cleared (ALL keys deleted)");
            
        } catch (Exception e) {
            log.error("❌ Cache clear error", e);
        }
    }
    
    // ========================================================================
    // HELPERS PRIVÉS
    // ========================================================================
    
    /**
     * Détermine le type de cache depuis la clé
     */
    private String getCacheType(String key) {
        if (key.startsWith(EMBEDDING_PREFIX)) {
            return "embedding";
        } else if (key.startsWith(QUERY_PREFIX)) {
            return "query";
        } else {
            return "other";
        }
    }
    
    /**
     * Hash une string pour créer une clé de cache
     */
    private String hash(String input) {
        return String.valueOf(input.hashCode());
    }
}