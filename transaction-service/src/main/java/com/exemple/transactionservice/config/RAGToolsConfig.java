// ============================================================================
// CONFIGURATION - RAGToolsConfig.java (MISE À JOUR)
// ============================================================================
package com.exemple.transactionservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.tools")
public class RAGToolsConfig {
    
    /** Nombre maximum de résultats pour recherche de documents */
    private int maxDocumentResults = 10;
    
    /** Nombre maximum de résultats pour recherche d'images */
    private int maxImageResults = 10;
    
    /** Nombre maximum de résultats pour recherche multimodale */
    private int maxMultimodalResults = 4;
    
    /** Longueur minimale d'une requête */
    private int minQueryLength = 3;
    
    /** Longueur maximale d'une requête */
    private int maxQueryLength = 500;
    
    /** Longueur maximale du texte d'un résultat (pour éviter la surcharge) */
    private int maxResultTextLength = 1000;
    
    /** Afficher les métriques de qualité dans les résultats */
    private boolean showMetrics = true;
    
    // NOUVEAU : Filtrer les résultats par type de document
    private boolean filterByDocumentType = true;
    
    // NOUVEAU : Inclure les métadonnées dans les résultats
    private boolean includeMetadata = true;
}