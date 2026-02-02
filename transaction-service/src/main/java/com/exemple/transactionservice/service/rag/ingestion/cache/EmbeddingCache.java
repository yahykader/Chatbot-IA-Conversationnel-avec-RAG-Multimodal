// ============================================================================
// SERVICE - EmbeddingCache.java (VERSION CORRIGÉE v2)
// Cache embeddings avec vérification Redis robuste
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.cache;

import dev.langchain4j.data.embedding.Embedding;
import com.exemple.transactionservice.service.rag.ingestion.compression.EmbeddingCompressor;
import com.exemple.transactionservice.service.rag.ingestion.compression.EmbeddingCompressor.CompressedEmbedding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cache Redis pour embeddings avec gestion d'erreur robuste
 * 
 * ✅ CORRECTIONS APPLIQUÉES v2 :
 * - Vérification Redis améliorée (test PING au lieu de isRunning())
 * - Désérialisation robuste sans metadata de type
 * - Gestion erreur gracieuse
 */
@Slf4j
@Service
public class EmbeddingCache {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final LettuceConnectionFactory connectionFactory;
    private final EmbeddingCompressor compressor;
    
    @Value("${cache.embeddings.enabled:true}")
    private boolean enabled;
    
    @Value("${cache.embeddings.ttl-days:7}")
    private int ttlDays;
    
    @Value("${cache.embeddings.key-prefix:embedding:cache:}")
    private String keyPrefix;
    
    @Value("${cache.embeddings.compression-enabled:false}")
    private boolean compressionEnabled;
    
    public EmbeddingCache(
            @Qualifier("embeddingCacheRedisTemplate") RedisTemplate<String, Object> redisTemplate,
            LettuceConnectionFactory connectionFactory,
            EmbeddingCompressor compressor) {
        
        this.redisTemplate = redisTemplate;
        this.connectionFactory = connectionFactory;
        this.compressor = compressor;
        
        log.info("✅ EmbeddingCache initialisé");
        log.info("   - Enabled: {}", enabled);
        log.info("   - TTL: {} jours", ttlDays);
        log.info("   - Compression: {}", compressionEnabled);
        log.info("   - Key Prefix: {}", keyPrefix);
    }
    
    /**
     * ✅ Vérifie Redis avec PING (plus fiable que isRunning())
     * 
     * isRunning() peut retourner false même si Redis est accessible
     * PING teste réellement la connexion
     */
    private boolean isRedisAvailable() {
        if (!enabled) {
            return false;
        }
        
        try {
            // ✅ Test réel de connexion avec PING
            var connection = connectionFactory.getConnection();
            String pong = connection.ping();
            connection.close();
            
            boolean available = "PONG".equals(pong);
            
            if (!available) {
                log.debug("⚠️ Redis PING failed: {}", pong);
            }
            
            return available;
            
        } catch (Exception e) {
            log.debug("⚠️ Redis non accessible: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Récupère embedding depuis cache
     */
    public Optional<Embedding> get(String text) {
        if (text == null) {
            return Optional.empty();
        }
        
        if (!isRedisAvailable()) {
            log.debug("⚠️ Redis non disponible - cache MISS");
            return Optional.empty();
        }
        
        try {
            String key = keyPrefix + hash(text);
            Object cached = redisTemplate.opsForValue().get(key);
            
            if (cached == null) {
                log.debug("❌ Cache MISS: {}", truncateKey(key));
                return Optional.empty();
            }
            
            Embedding embedding = deserializeEmbedding(cached);
            
            if (embedding != null) {
                log.debug("✅ Cache HIT: {} dimensions", embedding.dimension());
            }
            
            return Optional.ofNullable(embedding);
            
        } catch (Exception e) {
            log.error("❌ Erreur cache get: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * ✅ Désérialisation robuste SANS metadata de type
     * 
     * Jackson stocke maintenant directement les tableaux sans @class
     */
    private Embedding deserializeEmbedding(Object cached) {
        try {
            if (cached instanceof CompressedEmbedding compressed) {
                // Cas 1: CompressedEmbedding
                Embedding embedding = compressor.decompress(compressed);
                log.debug("✅ Décompressé: {} dimensions", embedding.dimension());
                return embedding;
            } 
            else if (cached instanceof float[] vector) {
                // Cas 2: float[] direct
                log.debug("✅ Float array: {} dimensions", vector.length);
                return Embedding.from(vector);
            }
            else if (cached instanceof double[] doubleVector) {
                // Cas 3: double[] (conversion possible par Jackson)
                float[] vector = new float[doubleVector.length];
                for (int i = 0; i < doubleVector.length; i++) {
                    vector[i] = (float) doubleVector[i];
                }
                log.debug("✅ Double array converti: {} dimensions", vector.length);
                return Embedding.from(vector);
            }
            else if (cached instanceof ArrayList<?> list) {
                // Cas 4: ArrayList<Number> (JSON array)
                float[] vector = convertListToFloatArray(list);
                log.debug("✅ ArrayList converti: {} dimensions", vector.length);
                return Embedding.from(vector);
            }
            else if (cached instanceof List<?> list) {
                // Cas 5: List générique
                float[] vector = convertListToFloatArray(list);
                log.debug("✅ List converti: {} dimensions", vector.length);
                return Embedding.from(vector);
            }
            else {
                log.warn("⚠️ Type inconnu ignoré: {}", cached.getClass().getName());
                return null;
            }
            
        } catch (Exception e) {
            log.error("❌ Erreur désérialisation: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Convertit List<Number> en float[]
     */
    private float[] convertListToFloatArray(List<?> list) {
        float[] result = new float[list.size()];
        
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            
            if (item instanceof Number number) {
                result[i] = number.floatValue();
            } else {
                throw new IllegalArgumentException(
                    String.format("Element %d n'est pas un Number: %s", 
                        i, item != null ? item.getClass().getName() : "null")
                );
            }
        }
        
        return result;
    }
    
    /**
     * Sauvegarde embedding dans cache
     */
    public void put(String text, Embedding embedding) {
        if (text == null || embedding == null) {
            return;
        }
        
        if (!isRedisAvailable()) {
            log.debug("⚠️ Redis non disponible - cache PUT skip");
            return;
        }
        
        try {
            String key = keyPrefix + hash(text);
            
            Object valueToStore;
            
            if (compressionEnabled) {
                CompressedEmbedding compressed = compressor.quantizeInt8WithMetadata(embedding);
                valueToStore = compressed;
                
                log.debug("💾 Cache PUT (compressé): {} → {} bytes ({:.1f}% compression)", 
                    compressed.originalSize(), 
                    compressed.size(),
                    compressed.reductionPercentage());
            } else {
                // ✅ Stockage direct du float[] (Jackson le sérialisera en JSON array)
                valueToStore = embedding.vector();
                
                log.debug("💾 Cache PUT (non compressé): {} floats", 
                    embedding.vector().length);
            }
            
            redisTemplate.opsForValue().set(
                key, 
                valueToStore, 
                Duration.ofDays(ttlDays)
            );
            
            log.debug("✅ Cache PUT OK: {}", truncateKey(key));
            
        } catch (Exception e) {
            log.error("❌ Erreur cache put: {}", e.getMessage());
        }
    }
    
    /**
     * Helper : récupère depuis cache ou calcule
     */
    public Embedding getOrCompute(
            String text, 
            java.util.function.Supplier<Embedding> supplier) {
        
        Optional<Embedding> cached = get(text);
        
        if (cached.isPresent()) {
            log.debug("🎯 Cache HIT - pas de calcul nécessaire");
            return cached.get();
        }
        
        log.debug("🔧 Cache MISS - calcul embedding");
        
        try {
            Embedding embedding = supplier.get();
            
            if (embedding != null) {
                put(text, embedding);
            }
            
            return embedding;
            
        } catch (Exception e) {
            log.error("❌ Erreur calcul embedding: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur calcul embedding", e);
        }
    }
    
    /**
     * Hash SHA-256 du texte
     */
    public String hash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("❌ Erreur hash SHA-256, fallback sur hashCode", e);
            return String.valueOf(text.hashCode());
        }
    }
    
    /**
     * Invalide une entrée
     */
    public void invalidate(String text) {
        if (text == null || !isRedisAvailable()) {
            return;
        }
        
        try {
            String key = keyPrefix + hash(text);
            redisTemplate.delete(key);
            log.debug("🗑️ Cache invalidé: {}", truncateKey(key));
            
        } catch (Exception e) {
            log.error("❌ Erreur invalidation: {}", e.getMessage());
        }
    }
    
    /**
     * Nettoie tout le cache
     */
    public void clear() {
        if (!isRedisAvailable()) {
            log.warn("⚠️ Redis non disponible - clear skip");
            return;
        }
        
        try {
            var keys = redisTemplate.keys(keyPrefix + "*");
            
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("🗑️ Cache nettoyé: {} clés", keys.size());
            }
            
        } catch (Exception e) {
            log.error("❌ Erreur clear: {}", e.getMessage());
        }
    }
    
    /**
     * Statistiques
     */
    public CacheStats getStats() {
        try {
            if (!isRedisAvailable()) {
                return new CacheStats(enabled, compressionEnabled, 0, false);
            }
            
            var keys = redisTemplate.keys(keyPrefix + "*");
            long count = keys != null ? keys.size() : 0;
            
            return new CacheStats(enabled, compressionEnabled, count, true);
            
        } catch (Exception e) {
            log.error("❌ Erreur stats: {}", e.getMessage());
            return new CacheStats(enabled, compressionEnabled, 0, false);
        }
    }
    
    private String truncateKey(String key) {
        if (key == null || key.length() <= 30) {
            return key;
        }
        return key.substring(0, 25) + "...";
    }
    
    public record CacheStats(
        boolean enabled, 
        boolean compressionEnabled, 
        long entryCount,
        boolean redisAvailable
    ) {}
}