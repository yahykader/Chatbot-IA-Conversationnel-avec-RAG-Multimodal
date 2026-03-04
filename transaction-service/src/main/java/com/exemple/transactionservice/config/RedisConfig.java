// ============================================================================
// CONFIG - RedisConfig.java (VERSION CORRIGÉE FINALE)
// Configuration Redis avec factory.start() pour éviter "STOPPED" error
// ============================================================================
package com.exemple.transactionservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Configuration Redis pour :
 * - Déduplication de fichiers (hash storage)
 * - Cache embeddings
 * - Session storage
 * 
 * ✅ CORRECTION APPLIQUÉE :
 * - factory.start() ajouté pour éviter "LettuceConnectionFactory has been STOPPED"
 * - Configuration Lettuce optimisée
 * - Health check amélioré
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {
    
    @Value("${spring.redis.host:localhost}")
    private String redisHost;
    
    @Value("${spring.redis.port:6379}")
    private int redisPort;
    
    @Value("${spring.redis.password:}")
    private String redisPassword;
    
    @Value("${spring.redis.database:0}")
    private int redisDatabase;
    
    @Value("${ingestion.cache.ttl-hours:24}")
    private int cacheTtlHours;
    
    /**
     * ✅ Factory de connexion Redis avec démarrage automatique
     * 
     * CORRECTION CRITIQUE : Ajout de factory.start() pour éviter l'erreur
     * "LettuceConnectionFactory has been STOPPED" lors de l'utilisation
     * asynchrone dans EmbeddingCache
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // Configuration serveur Redis
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(redisDatabase);
        
        if (redisPassword != null && !redisPassword.isBlank()) {
            config.setPassword(redisPassword);
        }
        
        // Configuration client Lettuce
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(10))
            .shutdownTimeout(Duration.ZERO)  // ✅ Pas de shutdown automatique
            .clientOptions(
                ClientOptions.builder()
                    .socketOptions(
                        SocketOptions.builder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .keepAlive(true)
                            .build()
                    )
                    .build()
            )
            .build();
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        
        // ✅ CRITIQUE : Configuration pour démarrage immédiat
        factory.setEagerInitialization(true);
        factory.setShareNativeConnection(true);
        factory.setValidateConnection(true);
        
        // ✅ CRITIQUE : Initialisation et démarrage explicites
        factory.afterPropertiesSet();
        factory.start();
        
        log.info("✅ Redis ConnectionFactory démarrée: {}:{} (db: {})", 
            redisHost, redisPort, redisDatabase);
        
        return factory;
    }
    
    /**
     * RedisTemplate pour opérations String → String
     * Utilisé par DeduplicationService
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(
            LettuceConnectionFactory connectionFactory) {
        
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Serializers
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        
        template.afterPropertiesSet();
        
        log.info("✅ RedisTemplate<String, String> configuré");
        
        return template;
    }
    
    /**
     * RedisTemplate pour objets JSON (cache générique)
     */
    @Bean(name = "redisTemplateJson")
    public RedisTemplate<String, Object> redisTemplateJson(
            LettuceConnectionFactory connectionFactory) {
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Serializer JSON
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer();
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        
        log.info("✅ RedisTemplate<String, Object> (JSON) configuré");
        
        return template;
    }
    
    /**
     * CacheManager pour @Cacheable
     */
    @Bean
    public CacheManager cacheManager(LettuceConnectionFactory connectionFactory) {
        
        // Configuration cache par défaut
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(cacheTtlHours))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    createJsonSerializer()
                )
            )
            .disableCachingNullValues();
        
        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .transactionAware() // Support transactions
            .build();
        
        log.info("✅ CacheManager Redis configuré (TTL: {}h)", cacheTtlHours);
        
        return cacheManager;
    }
    
    /**
     * Crée un serializer JSON avec support polymorphisme
     */
    private GenericJackson2JsonRedisSerializer createJsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Support polymorphisme (nécessaire pour objets complexes)
        mapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
    
    /**
     * ✅ Health check Redis amélioré
     */
    @Bean
    public HealthIndicator redisHealthIndicator(
            LettuceConnectionFactory connectionFactory) {
        
        return () -> {
            try {
                // Vérifier si la factory est démarrée
                if (!connectionFactory.isRunning()) {
                    return Health.down()
                        .withDetail("status", "STOPPED")
                        .withDetail("message", "ConnectionFactory not running")
                        .withDetail("redis.host", redisHost)
                        .withDetail("redis.port", redisPort)
                        .build();
                }
                
                // Tester connexion
                var connection = connectionFactory.getConnection();
                connection.ping();
                connection.close();
                
                return Health.up()
                    .withDetail("redis.host", redisHost)
                    .withDetail("redis.port", redisPort)
                    .withDetail("redis.database", redisDatabase)
                    .withDetail("status", "connected")
                    .build();
                    
            } catch (Exception e) {
                return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("redis.host", redisHost)
                    .withDetail("redis.port", redisPort)
                    .build();
            }
        };
    }
}