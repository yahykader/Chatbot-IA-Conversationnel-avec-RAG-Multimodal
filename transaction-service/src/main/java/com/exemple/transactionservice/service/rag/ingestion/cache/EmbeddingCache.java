package com.exemple.transactionservice.service.rag.ingestion.cache;

import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
import dev.langchain4j.data.embedding.Embedding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cache Redis pour les embeddings avec métriques RAGMetrics
 * 
 * ✅ FIX: recordCacheHit() et recordCacheMiss() avec paramètre cacheName
 */
@Slf4j
@Component
public class EmbeddingCache {
    
    private static final String CACHE_NAME = "embedding";  // ✅ Nom du cache
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RAGMetrics ragMetrics;
    private final int ttlHours;
    
    public EmbeddingCache(
            RedisTemplate<String, String> redisTemplate,
            RAGMetrics ragMetrics) {
        
        this.redisTemplate = redisTemplate;
        this.ragMetrics = ragMetrics;
        this.ttlHours = 24;
        
        log.info("✅ EmbeddingCache initialized (Redis, TTL={}h)", ttlHours);
    }
    
    /**
     * Récupère ou calcule un embedding avec cache Redis
     */
    public Embedding getOrCompute(String text, Supplier<Embedding> supplier) {
        String key = "emb:" + hashText(text);
        
        String cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            // ✅ FIX: Ajouter CACHE_NAME en paramètre
            ragMetrics.recordCacheHit(CACHE_NAME);
            
            log.debug("✓ Cache HIT: {}", truncate(text, 50));
            return deserializeEmbedding(cached);
        }
        
        // ✅ FIX: Ajouter CACHE_NAME en paramètre
        ragMetrics.recordCacheMiss(CACHE_NAME);
        
        log.debug("✗ Cache MISS: {}", truncate(text, 50));
        
        Embedding embedding = supplier.get();
        
        String serialized = serializeEmbedding(embedding);
        redisTemplate.opsForValue().set(key, serialized, ttlHours, TimeUnit.HOURS);
        
        return embedding;
    }
    
    /**
     * Vérifie si un embedding existe en cache
     */
    public boolean exists(String text) {
        String key = "emb:" + hashText(text);
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
    
    /**
     * Supprime un embedding du cache
     */
    public void evict(String text) {
        String key = "emb:" + hashText(text);
        redisTemplate.delete(key);
        log.debug("🗑️ Cache EVICT: {}", truncate(text, 50));
    }
    
    /**
     * Vide complètement le cache
     */
    public void clear() {
        redisTemplate.keys("emb:*").forEach(redisTemplate::delete);
        log.info("🗑️ Cache CLEAR: all embeddings deleted");
    }
    
    /**
     * Hash du texte pour générer la clé Redis
     */
    private String hashText(String text) {
        return Integer.toHexString(text.hashCode());
    }
    
    /**
     * Sérialise un embedding en String (CSV des floats)
     */
    private String serializeEmbedding(Embedding embedding) {
        float[] vector = embedding.vector();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }
    
    /**
     * Désérialise un embedding depuis String
     */
    private Embedding deserializeEmbedding(String serialized) {
        String[] parts = serialized.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return Embedding.from(vector);
    }
    
    /**
     * Tronque le texte pour les logs
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}