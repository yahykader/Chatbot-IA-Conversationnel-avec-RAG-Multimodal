// ============================================================================
// SERVICE - RateLimitService.java
// Service pour appliquer le Rate Limiting
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Service de Rate Limiting.
 * 
 * Gère la création et la vérification des buckets par utilisateur et endpoint.
 * 
 */
@Slf4j
@Service
public class RateLimitService {
    
    private final ProxyManager<String> proxyManager;
    private final Supplier<BucketConfiguration> uploadBucketConfig;
    private final Supplier<BucketConfiguration> batchBucketConfig;
    private final Supplier<BucketConfiguration> deleteBucketConfig;
    private final Supplier<BucketConfiguration> searchBucketConfig;
    private final Supplier<BucketConfiguration> defaultBucketConfig;
    
    public RateLimitService(
            ProxyManager<String> proxyManager,
            Supplier<BucketConfiguration> uploadBucketConfig,
            Supplier<BucketConfiguration> batchBucketConfig,
            Supplier<BucketConfiguration> deleteBucketConfig,
            Supplier<BucketConfiguration> searchBucketConfig,
            Supplier<BucketConfiguration> defaultBucketConfig) {
        
        this.proxyManager = proxyManager;
        this.uploadBucketConfig = uploadBucketConfig;
        this.batchBucketConfig = batchBucketConfig;
        this.deleteBucketConfig = deleteBucketConfig;
        this.searchBucketConfig = searchBucketConfig;
        this.defaultBucketConfig = defaultBucketConfig;
        
        log.info("✅ RateLimitService initialisé");
    }
    
    /**
     * Vérifie si la requête est autorisée pour un endpoint upload.
     * 
     * @param userId ID de l'utilisateur
     * @return Résultat de la vérification
     */
    public RateLimitResult checkUploadLimit(String userId) {
        return checkLimit(userId, "upload", uploadBucketConfig);
    }
    
    /**
     * Vérifie si la requête est autorisée pour un endpoint batch.
     */
    public RateLimitResult checkBatchLimit(String userId) {
        return checkLimit(userId, "batch", batchBucketConfig);
    }
    
    /**
     * Vérifie si la requête est autorisée pour un endpoint delete.
     */
    public RateLimitResult checkDeleteLimit(String userId) {
        return checkLimit(userId, "delete", deleteBucketConfig);
    }
    
    /**
     * Vérifie si la requête est autorisée pour un endpoint search.
     */
    public RateLimitResult checkSearchLimit(String userId) {
        return checkLimit(userId, "search", searchBucketConfig);
    }
    
    /**
     * Vérifie avec limite par défaut.
     */
    public RateLimitResult checkDefaultLimit(String userId) {
        return checkLimit(userId, "default", defaultBucketConfig);
    }
    
    /**
     * Logique commune de vérification rate limit.
     */
    private RateLimitResult checkLimit(
            String userId, 
            String endpoint, 
            Supplier<BucketConfiguration> config) {
        
        try {
            // Clé unique : userId:endpoint
            String bucketKey = String.format("rate-limit:%s:%s", userId, endpoint);
            
            // Récupérer ou créer le bucket
            Bucket bucket = proxyManager.builder().build(bucketKey, config);
            
            // Tenter de consommer 1 token
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            
            if (probe.isConsumed()) {
                // Autorisé
                log.debug("✅ [RateLimit] OK - user={}, endpoint={}, remaining={}", 
                    userId, endpoint, probe.getRemainingTokens());
                
                return RateLimitResult.allowed(probe.getRemainingTokens());
                
            } else {
                // Bloqué
                long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
                
                log.warn("⚠️ [RateLimit] BLOQUÉ - user={}, endpoint={}, retry_in={}s", 
                    userId, endpoint, waitSeconds);
                
                return RateLimitResult.blocked(waitSeconds);
            }
            
        } catch (Exception e) {
            log.error("❌ [RateLimit] Erreur vérification - user={}, endpoint={}", 
                userId, endpoint, e);
            
            // En cas d'erreur, autoriser la requête (fail-open)
            return RateLimitResult.allowed(0);
        }
    }
    
    /**
     * Résultat d'une vérification de rate limit.
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final long remainingTokens;
        private final long retryAfterSeconds;
        
        private RateLimitResult(boolean allowed, long remainingTokens, long retryAfterSeconds) {
            this.allowed = allowed;
            this.remainingTokens = remainingTokens;
            this.retryAfterSeconds = retryAfterSeconds;
        }
        
        public static RateLimitResult allowed(long remainingTokens) {
            return new RateLimitResult(true, remainingTokens, 0);
        }
        
        public static RateLimitResult blocked(long retryAfterSeconds) {
            return new RateLimitResult(false, 0, retryAfterSeconds);
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public long getRemainingTokens() {
            return remainingTokens;
        }
        
        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}