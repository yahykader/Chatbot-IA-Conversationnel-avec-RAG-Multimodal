// ============================================================================
// CONFIGURATION - RAGConfig.java
// ============================================================================
package com.exemple.transactionservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag")
public class RAGConfig {
    
    /** Score minimum pour considérer un résultat pertinent */
    private double minScore = 0.6;
    
    /** Nombre maximum de tentatives en cas d'échec */
    private int maxRetries = 3;
    
    /** Délai entre les tentatives (ms) */
    private long retryDelayMs = 1000;
    
    /** Nombre de threads pour les recherches parallèles */
    private int parallelSearchThreads = 4;
    
    /** Activer le cache */
    private boolean cacheEnabled = true;
    
    /** Durée de vie du cache (secondes) */
    private long cacheTtlSeconds = 300;
}