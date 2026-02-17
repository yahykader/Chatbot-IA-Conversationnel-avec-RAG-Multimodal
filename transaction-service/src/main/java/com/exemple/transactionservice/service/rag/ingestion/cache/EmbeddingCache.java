package com.exemple.transactionservice.service.rag.ingestion.cache;

import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
import dev.langchain4j.data.embedding.Embedding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cache Redis pour les embeddings avec métriques RAGMetrics
 * 
 * ✅ VERSION AVEC NETTOYAGE SÉLECTIF PAR BATCH
 */
@Slf4j
@Component
public class EmbeddingCache {
    
    private static final String CACHE_NAME = "embedding";
    private static final String EMB_CACHE_PREFIX = "emb:";
    private static final String BATCH_EMB_PREFIX = "batch:emb:";  // ✅ NOUVEAU
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RAGMetrics ragMetrics;
    private final int ttlHours;
    
    public EmbeddingCache(
            RedisTemplate<String, String> redisTemplate,
            RAGMetrics ragMetrics) {
        
        this.redisTemplate = redisTemplate;
        this.ragMetrics = ragMetrics;
        this.ttlHours = 24;
        
        log.info("✅ EmbeddingCache initialized (Redis, TTL={}h, Batch tracking)", ttlHours);
    }
    
    // ========================================================================
    // MÉTHODES EXISTANTES (INCHANGÉES)
    // ========================================================================
    
    /**
     * Récupère ou calcule un embedding avec cache Redis
     */
    public Embedding getOrCompute(String text, Supplier<Embedding> supplier) {
        String key = EMB_CACHE_PREFIX + hashText(text);
        
        String cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            ragMetrics.recordCacheHit(CACHE_NAME);
            log.debug("✓ Cache HIT: {}", truncate(text, 50));
            return deserializeEmbedding(cached);
        }
        
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
        String key = EMB_CACHE_PREFIX + hashText(text);
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
    
    /**
     * Supprime un embedding du cache
     */
    public void evict(String text) {
        String key = EMB_CACHE_PREFIX + hashText(text);
        redisTemplate.delete(key);
        log.debug("🗑️ Cache EVICT: {}", truncate(text, 50));
    }
    
    /**
     * Vide complètement le cache
     */
    public void clear() {
        try {
            Set<String> keys = redisTemplate.keys(EMB_CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("🗑️ Cache CLEAR: {} embeddings deleted", keys.size());
            }
        } catch (Exception e) {
            log.error("❌ Cache CLEAR error", e);
        }
    }
    
    // ========================================================================
    // ✅ NOUVELLES MÉTHODES - TRACKING PAR BATCH
    // ========================================================================
    
    /**
     * ✅ NOUVEAU: Met en cache un embedding ET l'associe à un batch
     */
    public void put(String text, Embedding embedding, String batchId) {
        try {
            String textHash = hashText(text);
            String key = EMB_CACHE_PREFIX + textHash;
            
            // Stocker l'embedding
            String serialized = serializeEmbedding(embedding);
            redisTemplate.opsForValue().set(key, serialized, ttlHours, TimeUnit.HOURS);
            
            // ✅ Associer ce hash au batch
            if (batchId != null && !batchId.isBlank()) {
                String batchKey = BATCH_EMB_PREFIX + batchId;
                redisTemplate.opsForSet().add(batchKey, textHash);
                redisTemplate.expire(batchKey, ttlHours, TimeUnit.HOURS);
                
                log.debug("✅ [Cache] Embedding cached et associé au batch: {}", batchId);
            }
            
        } catch (Exception e) {
            log.error("❌ [Cache] Erreur stockage embedding", e);
        }
    }
    
    /**
     * ✅ NOUVEAU: Récupère un embedding et enregistre son utilisation par un batch
     */
    public Embedding getAndTrack(String text, String batchId) {
        String textHash = hashText(text);
        String key = EMB_CACHE_PREFIX + textHash;
        
        String cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            ragMetrics.recordCacheHit(CACHE_NAME);
            log.debug("✓ Cache HIT: {}", truncate(text, 50));
            
            // ✅ Associer ce hash au batch (même si déjà en cache)
            if (batchId != null && !batchId.isBlank()) {
                String batchKey = BATCH_EMB_PREFIX + batchId;
                redisTemplate.opsForSet().add(batchKey, textHash);
                redisTemplate.expire(batchKey, ttlHours, TimeUnit.HOURS);
            }
            
            return deserializeEmbedding(cached);
        }
        
        ragMetrics.recordCacheMiss(CACHE_NAME);
        log.debug("✗ Cache MISS: {}", truncate(text, 50));
        
        return null;
    }
    
    /**
     * ✅ NOUVEAU: Supprime uniquement les embeddings d'un batch
     */
    public void removeBatch(String batchId) {
        try {
            String batchKey = BATCH_EMB_PREFIX + batchId;
            
            Set<String> hashes = redisTemplate.opsForSet().members(batchKey);
            
            if (hashes == null || hashes.isEmpty()) {
                log.debug("ℹ️ [Cache] Aucun embedding trouvé pour batch: {}", batchId);
                return;
            }
            
            int deleted = 0;
            
            for (String hash : hashes) {
                String key = EMB_CACHE_PREFIX + hash;
                Boolean success = redisTemplate.delete(key);
                if (Boolean.TRUE.equals(success)) {
                    deleted++;
                }
            }
            
            redisTemplate.delete(batchKey);
            
            log.info("✅ [Cache] Batch embeddings supprimé: {} ({} clés)", batchId, deleted);
            
        } catch (Exception e) {
            log.error("❌ [Cache] Erreur suppression batch: {}", batchId, e);
        }
    }
    
    /**
     * ✅ NOUVEAU: Compte le nombre d'embeddings d'un batch
     */
    public long countBatchEmbeddings(String batchId) {
        try {
            String batchKey = BATCH_EMB_PREFIX + batchId;
            Long size = redisTemplate.opsForSet().size(batchKey);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.error("❌ [Cache] Erreur comptage batch: {}", batchId, e);
            return 0;
        }
    }


    /**
     * Supprime TOUS les embeddings + batch tracking
     */
    public void clearAll() {
        // Supprimer tous les emb:*
        Set<String> embKeys = redisTemplate.keys(EMB_CACHE_PREFIX + "*");
        if (embKeys != null && !embKeys.isEmpty()) {
            redisTemplate.delete(embKeys);
            log.info("✅ [Cache] {} embeddings supprimés", embKeys.size());
        }
        
        // Supprimer tous les batch:emb:*
        Set<String> batchKeys = redisTemplate.keys(BATCH_EMB_PREFIX + "*");
        if (batchKeys != null && !batchKeys.isEmpty()) {
            redisTemplate.delete(batchKeys);
            log.info("✅ [Cache] {} batch tracking supprimés", batchKeys.size());
        }
        
        log.info("✅ [Cache] Tous les caches embeddings supprimés");
    }
    
    // ========================================================================
    // MÉTHODES PRIVÉES (INCHANGÉES)
    // ========================================================================
    
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