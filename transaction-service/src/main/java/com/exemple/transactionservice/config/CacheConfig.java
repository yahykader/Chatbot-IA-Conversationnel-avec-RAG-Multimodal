package com.exemple.transactionservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Cache Manager avec Caffeine (activé par défaut)
     */
    @Bean
    @Primary
    @ConditionalOnProperty(
        name = "rag.cache-enabled", 
        havingValue = "true", 
        matchIfMissing = true
    )
    public CacheManager caffeineCacheManager(RAGConfig ragConfig) {
        log.info("🗄️ Configuration du Cache Manager (Caffeine)");
        log.info("   - TTL: {}s", ragConfig.getCacheTtlSeconds());
        log.info("   - Max Size: 1000 entrées");
        
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "multimodalSearch",
            "textSearch",
            "imageSearch"
        );
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(ragConfig.getCacheTtlSeconds(), TimeUnit.SECONDS)
            .recordStats());
        
        log.info("✅ Cache Caffeine activé");
        return cacheManager;
    }
    
    /**
     * Cache Manager désactivé (si cache-enabled = false)
     */
    @Bean
    @ConditionalOnProperty(
        name = "rag.cache-enabled", 
        havingValue = "false"
    )
    public CacheManager noOpCacheManager() {
        log.info("🚫 Cache désactivé (NoOpCacheManager)");
        return new NoOpCacheManager();
    }
}