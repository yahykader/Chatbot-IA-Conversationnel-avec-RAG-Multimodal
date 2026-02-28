package com.exemple.transactionservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration CORS globale
 */
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Allowed origins
        config.setAllowedOriginPatterns(List.of(
            "http://localhost",
            "http://localhost:4200",
            "http://localhost:3000",
            "http://34.53.187.62",   // ✅ VM dev
            "http://34.53.187.62:8080",   // ✅ VM dev
            "https://*.run.app",        // ✅ Cloud Run prod
            "https://*"                 // ✅ tous les HTTPS
        ));
        
        // Allowed methods
        config.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        // Allowed headers
        config.setAllowedHeaders(Arrays.asList(
            "Origin",
            "Content-Type",
            "Accept",
            "Authorization",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
        ));
        
        // Exposed headers
        config.setExposedHeaders(Arrays.asList(
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials"
        ));
        
        // Credentials
        config.setAllowCredentials(true);
        
        // Max age
        config.setMaxAge(3600L);
        
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}