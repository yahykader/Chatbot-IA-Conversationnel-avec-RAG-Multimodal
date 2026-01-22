package com.exemple.transactionservice.config;

import com.exemple.transactionservice.service.ConversationalAssistant.ConversationContext;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * ✅ BEAN REQUIS: RedisTemplate pour ConversationContext
     */
    @Bean
    public RedisTemplate<String, ConversationContext> contextRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        
        RedisTemplate<String, ConversationContext> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Sérialisation clés (String)
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        
        // Sérialisation valeurs (JSON)
        GenericJackson2JsonRedisSerializer valueSerializer = createJsonSerializer();
        
        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);
        
        template.afterPropertiesSet();
        
        return template;
    }

    /**
     * ✅ OPTIONNEL: RedisTemplate générique
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = createJsonSerializer();
        
        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);
        
        template.afterPropertiesSet();
        
        return template;
    }

    /**
     * ✅ RedisCacheManager pour @Cacheable
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        
        GenericJackson2JsonRedisSerializer valueSerializer = createJsonSerializer();
        
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    valueSerializer
                )
            );
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }

    /**
     * ✅ Créer sérializer JSON avec support Java 8 Time
     */
    private GenericJackson2JsonRedisSerializer createJsonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Support Java 8 Time (Instant, Duration, etc.)
        objectMapper.registerModule(new JavaTimeModule());
        
        // Activer informations de type
        objectMapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}