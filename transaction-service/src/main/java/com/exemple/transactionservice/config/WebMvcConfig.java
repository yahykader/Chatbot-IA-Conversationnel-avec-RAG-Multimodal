// ============================================================================
// CONFIG - WebMvcConfig.java
// Configuration Spring MVC pour enregistrer l'intercepteur Rate Limiting
// ============================================================================
package com.exemple.transactionservice.config;

import com.exemple.transactionservice.service.rag.interceptor.RateLimitInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration Spring MVC.
 * 
 * Enregistre les intercepteurs HTTP, dont le RateLimitInterceptor.
 * 
 * @author RAG Team
 * @version 1.0
 */
@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    private final RateLimitInterceptor rateLimitInterceptor;
    
    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        log.info("✅ WebMvcConfig initialisé");
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/api/v1/ingestion/**","/api/v1/crud/**")  // Appliquer sur tous les endpoints ingestion
            .excludePathPatterns(
                "/api/v1/ingestion/health",           // Exclure health check
                "/api/v1/ingestion/health/detailed",  // Exclure health détaillé
                "/api/v1/ingestion/strategies"        // Exclure liste strategies
            );
        
        log.info("✅ RateLimitInterceptor enregistré sur /api/v1/ingestion/** & /api/v1/crud/**");
        log.info("   • Exclusions: /health, /health/detailed, /strategies");
    }
}