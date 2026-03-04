// ============================================================================
// CONFIG - RateLimitConfig.java
// Configuration du Rate Limiting avec Bucket4j + Redis
// ============================================================================
package com.exemple.transactionservice.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Configuration du Rate Limiting pour l'API d'ingestion.
 * 
 * Utilise Bucket4j (Token Bucket Algorithm) avec Redis pour le stockage distribué.
 * 
 * Limites configurables par endpoint :
 * - Upload fichier : 10 req/min par utilisateur
 * - Batch upload : 5 req/min par utilisateur
 * - Delete : 20 req/min par utilisateur
 * - Recherche : 50 req/min par utilisateur
 * 
 */
@Slf4j
@Configuration
public class RateLimitConfig {
    
    @Value("${spring.redis.host:localhost}")
    private String redisHost;
    
    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}") 
    String redisPassword;
    
    // Limites par endpoint (requêtes par minute)
    @Value("${rate-limit.upload.requests-per-minute:10}")
    private int uploadLimit;
    
    @Value("${rate-limit.batch.requests-per-minute:5}")
    private int batchLimit;
    
    @Value("${rate-limit.delete.requests-per-minute:20}")
    private int deleteLimit;
    
    @Value("${rate-limit.search.requests-per-minute:50}")
    private int searchLimit;
    
    @Value("${rate-limit.default.requests-per-minute:30}")
    private int defaultLimit;
    
    /**
     * Configuration du ProxyManager Redis pour Bucket4j.
     * Permet le partage des buckets entre plusieurs instances de l'application.
     */
    @Bean
    public ProxyManager<String> proxyManager() {
        try {
            // Connexion Redis avec authentification
            String redisUri;
            if (redisPassword != null && !redisPassword.isEmpty()) {
                redisUri = String.format("redis://:%s@%s:%d", redisPassword, redisHost, redisPort);
            } else {
                redisUri = String.format("redis://%s:%d", redisHost, redisPort);
            }


            RedisClient redisClient = RedisClient.create(redisUri);
            
            // ✅ CORRECTION : Utiliser RedisCodec.of() pour combiner StringCodec + ByteArrayCodec
            io.lettuce.core.codec.RedisCodec<String, byte[]> codec = 
                io.lettuce.core.codec.RedisCodec.of(
                    io.lettuce.core.codec.StringCodec.UTF8,
                    io.lettuce.core.codec.ByteArrayCodec.INSTANCE
                );
            
            StatefulRedisConnection<String, byte[]> connection = redisClient.connect(codec);
            
            // ProxyManager Bucket4j
            ProxyManager<String> proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                    // Les buckets expirent après 1 heure d'inactivité
                    io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(1))
                )
                .build();
            
            log.info("✅ Rate Limiting activé (Redis: {}:{})", redisHost, redisPort);
            log.info("📊 Limites configurées:");
            log.info("   • Upload: {} req/min", uploadLimit);
            log.info("   • Batch: {} req/min", batchLimit);
            log.info("   • Delete: {} req/min", deleteLimit);
            log.info("   • Search: {} req/min", searchLimit);
            log.info("   • Default: {} req/min", defaultLimit);
            
            return proxyManager;
            
        } catch (Exception e) {
            log.error("❌ Erreur initialisation Rate Limiting", e);
            throw new RuntimeException("Erreur Rate Limiting", e);
        }
    }
    
    /**
     * Configuration Bucket pour les uploads.
     */
    @Bean
    public Supplier<BucketConfiguration> uploadBucketConfig() {
        return () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.simple(uploadLimit, Duration.ofMinutes(1)))
            .build();
    }
    
    /**
     * Configuration Bucket pour les batch uploads.
     */
    @Bean
    public Supplier<BucketConfiguration> batchBucketConfig() {
        return () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.simple(batchLimit, Duration.ofMinutes(1)))
            .build();
    }
    
    /**
     * Configuration Bucket pour les suppressions.
     */
    @Bean
    public Supplier<BucketConfiguration> deleteBucketConfig() {
        return () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.simple(deleteLimit, Duration.ofMinutes(1)))
            .build();
    }
    
    /**
     * Configuration Bucket pour les recherches.
     */
    @Bean
    public Supplier<BucketConfiguration> searchBucketConfig() {
        return () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.simple(searchLimit, Duration.ofMinutes(1)))
            .build();
    }
    
    /**
     * Configuration Bucket par défaut.
     */
    @Bean
    public Supplier<BucketConfiguration> defaultBucketConfig() {
        return () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.simple(defaultLimit, Duration.ofMinutes(1)))
            .build();
    }
}