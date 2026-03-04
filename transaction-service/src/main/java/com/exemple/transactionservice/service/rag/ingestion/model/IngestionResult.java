// ============================================================================
// MODEL - IngestionResult.java
// Modèle pour le résultat d'une ingestion
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Résultat d'une ingestion de document.
 * 
 * Contient les statistiques et metadata de l'ingestion :
 * - Nombre d'embeddings texte créés
 * - Nombre d'embeddings image créés
 * - Metadata additionnelles (strategy, filename, durée, etc.)
 * 
 * Usage :
 * <pre>
 * IngestionResult result = new IngestionResult(
 *     45,  // 45 embeddings texte
 *     8,   // 8 embeddings image
 *     Map.of("strategy", "PDF", "pages", 15)
 * );
 * </pre>
 */
public record IngestionResult(
    int textEmbeddings,
    int imageEmbeddings,
    Map<String, Object> metadata
) {
    
    /**
     * Constructeur avec validation
     */
    public IngestionResult {
        if (textEmbeddings < 0) {
            throw new IllegalArgumentException("textEmbeddings ne peut pas être négatif");
        }
        if (imageEmbeddings < 0) {
            throw new IllegalArgumentException("imageEmbeddings ne peut pas être négatif");
        }
        if (metadata == null) {
            metadata = new HashMap<>();
        }
    }
    
    /**
     * Constructeur sans metadata
     */
    public IngestionResult(int textEmbeddings, int imageEmbeddings) {
        this(textEmbeddings, imageEmbeddings, new HashMap<>());
    }
    
    /**
     * Retourne le nombre total d'embeddings créés
     */
    public int getTotalEmbeddings() {
        return textEmbeddings + imageEmbeddings;
    }
    
    /**
     * Vérifie si l'ingestion a créé au moins un embedding
     */
    public boolean hasEmbeddings() {
        return getTotalEmbeddings() > 0;
    }
    
    /**
     * Vérifie si l'ingestion contient des embeddings texte
     */
    public boolean hasTextEmbeddings() {
        return textEmbeddings > 0;
    }
    
    /**
     * Vérifie si l'ingestion contient des embeddings image
     */
    public boolean hasImageEmbeddings() {
        return imageEmbeddings > 0;
    }
    
    /**
     * Ajoute une metadata
     */
    public IngestionResult withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(metadata);
        newMetadata.put(key, value);
        return new IngestionResult(textEmbeddings, imageEmbeddings, newMetadata);
    }
    
    /**
     * Crée un résultat d'erreur
     */
    public static IngestionResult error(String filename, String errorMessage) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("filename", filename);
        meta.put("error", errorMessage);
        meta.put("status", "ERROR");
        return new IngestionResult(0, 0, meta);
    }
    
    /**
     * Crée un résultat de succès
     */
    public static IngestionResult success(
            int textEmbeddings, 
            int imageEmbeddings,
            String strategy,
            String filename) {
        
        Map<String, Object> meta = new HashMap<>();
        meta.put("strategy", strategy);
        meta.put("filename", filename);
        meta.put("status", "SUCCESS");
        return new IngestionResult(textEmbeddings, imageEmbeddings, meta);
    }
    
    @Override
    public String toString() {
        return String.format(
            "IngestionResult[text=%d, images=%d, total=%d, metadata=%s]",
            textEmbeddings, imageEmbeddings, getTotalEmbeddings(), metadata
        );
    }
}