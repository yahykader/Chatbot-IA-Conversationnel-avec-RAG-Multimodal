// ============================================================================
// CONFIGURATION - RAGConfig.java (v3.0)
// ============================================================================
package com.exemple.transactionservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ✅ Configuration RAG v3.0
 * Toutes les propriétés externalisées pour tuning sans recompilation
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag")
public class RAGConfig {
    
    // ==================== RECHERCHE ====================
    
    /**
     * Score minimum de similarité (0.0 à 1.0)
     * Valeur recommandée: 0.7 pour bonne précision
     */
    private double minScore = 0.7;
    
    /**
     * Nombre de résultats par défaut
     */
    private int defaultMaxResults = 5;
    
    /**
     * Nombre maximum de résultats autorisés (sécurité)
     */
    private int maxAllowedResults = 50;
    
    /**
     * ✅ NOUVEAU v3.0: Timeout recherche en secondes
     */
    private int searchTimeoutSeconds = 30;
    
    // ==================== PARALLÉLISME ====================
    
    /**
     * Nombre de threads pour recherches parallèles
     * Recommandation: Nombre de CPU cores
     */
    private int parallelSearchThreads = Runtime.getRuntime().availableProcessors();
    
    // ==================== RETRY ====================
    
    /**
     * Nombre max de tentatives en cas d'erreur
     */
    private int maxRetries = 3;
    
    /**
     * Délai entre tentatives en ms (backoff exponentiel appliqué)
     */
    private long retryDelayMs = 1000L;
    
    // ==================== CACHE ====================
    
    /**
     * ✅ NOUVEAU v3.0: TTL cache en heures
     */
    private int cacheTtlHours = 24;
    
    /**
     * ✅ NOUVEAU v3.0: Invalidation automatique du cache (en heures)
     */
    private int cacheEvictionIntervalHours = 1;
    
    // ==================== EMBEDDINGS ====================
    
    /**
     * ✅ NOUVEAU v3.0: Taille max query (caractères)
     */
    private int maxQueryLength = 1000;
    
    /**
     * Batch size pour génération d'embeddings
     */
    private int embeddingBatchSize = 100;
    
    // ==================== MONITORING ====================
    
    /**
     * ✅ NOUVEAU v3.0: Activer logs détaillés
     */
    private boolean verboseLogging = false;
    
    /**
     * ✅ NOUVEAU v3.0: Activer métriques Micrometer
     */
    private boolean enableMetrics = true;
}