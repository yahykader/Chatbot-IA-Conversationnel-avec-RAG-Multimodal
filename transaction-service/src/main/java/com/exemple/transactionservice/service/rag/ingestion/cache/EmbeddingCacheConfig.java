// ============================================================================
// Configuration cache embeddings avec serializer JSON simple
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration cache Redis pour embeddings
 * 
 * ✅ CORRECTION APPLIQUÉE :
 * - ObjectMapper SANS activateDefaultTyping (pas de metadata @class)
 * - Stockage direct des float[] sans information de type polymorphe
 * - Résout l'erreur "Could not resolve type id '-0.030514618'"
 */
@Slf4j
@Configuration
@EnableCaching
public class EmbeddingCacheConfig {
    
    /**
     * RedisTemplate spécifique pour cache embeddings
     * 
     * ✅ CORRECTION : ObjectMapper simple sans type metadata
     * 
     * Ce serializer stocke les données JSON SANS les métadonnées de type.
     * Pour un float[], il stockera directement : [-0.03, 0.15, ...]
     * au lieu de : ["java.util.ArrayList", [-0.03, 0.15, ...]]
     * 
     * Cela évite l'erreur de désérialisation où Jackson interprète
     * la première valeur du tableau comme un identifiant de classe.
     */
    @Bean(name = "embeddingCacheRedisTemplate")
    public RedisTemplate<String, Object> embeddingCacheRedisTemplate(
            LettuceConnectionFactory connectionFactory) {
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // ✅ ObjectMapper simple SANS polymorphisme
        ObjectMapper objectMapper = new ObjectMapper();
        
        // NE PAS activer le typage par défaut
        // objectMapper.activateDefaultTyping(...) --> SUPPRIMÉ
        
        // ✅ Serializer JSON sans metadata de type
        GenericJackson2JsonRedisSerializer serializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        
        log.info("✅ embeddingCacheRedisTemplate configuré (JSON sans type metadata)");
        
        return template;
    }
}