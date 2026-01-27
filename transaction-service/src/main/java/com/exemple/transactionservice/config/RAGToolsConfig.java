// ============================================================================
// CONFIGURATION - RAGToolsConfig.java (v2.0)
// ============================================================================
package com.exemple.transactionservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ✅ Configuration RAGTools v2.0
 * Toutes les propriétés externalisées pour tuning sans recompilation
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag.tools")
public class RAGToolsConfig {
    
    // ==================== RÉSULTATS ====================
    
    /**
     * Nombre max de résultats documents
     */
    private int maxDocumentResults = 5;
    
    /**
     * Nombre max de résultats images
     */
    private int maxImageResults = 3;
    
    /**
     * Nombre max de résultats multimodaux (total)
     */
    private int maxMultimodalResults = 4;
    
    /**
     * ✅ NOUVEAU v2.0: Limite absolue pour recherche
     */
    private int maxAllowedResults = 50;
    
    // ==================== VALIDATION ====================
    
    /**
     * Longueur minimum requête
     */
    private int minQueryLength = 3;
    
    /**
     * Longueur maximum requête
     */
    private int maxQueryLength = 500;
    
    /**
     * Longueur max texte résultat affiché
     */
    private int maxResultTextLength = 1000;
    
    // ==================== AFFICHAGE ====================
    
    /**
     * Afficher métriques détaillées
     */
    private boolean showMetrics = true;
    
    /**
     * Filtrer par type de document
     */
    private boolean filterByDocumentType = true;
    
    /**
     * Inclure métadonnées dans résultats
     */
    private boolean includeMetadata = true;
}