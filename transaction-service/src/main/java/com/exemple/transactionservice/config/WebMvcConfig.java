// ============================================================================
// CONFIG - WebMvcConfig.java
// Configuration Spring MVC pour enregistrer l'intercepteur Rate Limiting
// ============================================================================
package com.exemple.transactionservice.config;

import com.exemple.transactionservice.service.rag.interceptor.RateLimitInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
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

    // @Override
    // public void addCorsMappings(CorsRegistry registry) {
    //     registry.addMapping("/api/**")
    //         .allowedOrigins("*")
    //         .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
    //         .allowedHeaders("*")
    //         .allowCredentials(false)
    //         .maxAge(3600);
    // }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/v1/ingestion/**","/v1/crud/**")  // Appliquer sur tous les endpoints ingestion
            .excludePathPatterns(
                "/v1/ingestion/health",           // Exclure health check
                "/v1/ingestion/health/detailed",  // Exclure health détaillé
                "/v1/ingestion/strategies"        // Exclure liste strategies
            );
        
        log.info("✅ RateLimitInterceptor enregistré sur /v1/ingestion/** & /v1/crud/**");
        log.info("   • Exclusions: /health, /health/detailed, /strategies");
    }
}