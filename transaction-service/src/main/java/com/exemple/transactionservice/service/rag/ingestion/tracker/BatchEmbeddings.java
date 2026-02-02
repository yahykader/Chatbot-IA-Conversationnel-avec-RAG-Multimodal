// ============================================================================
// MODEL - BatchEmbeddings.java
// Modèle pour stocker les IDs d'embeddings d'un batch
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.tracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modèle pour stocker les IDs d'embeddings créés pendant un batch d'ingestion.
 * 
 * Thread-safe : utilise des listes synchronisées
 */
public class BatchEmbeddings {
    
    private final List<String> textEmbeddingIds;
    private final List<String> imageEmbeddingIds;
    
    public BatchEmbeddings() {
        this.textEmbeddingIds = Collections.synchronizedList(new ArrayList<>());
        this.imageEmbeddingIds = Collections.synchronizedList(new ArrayList<>());
    }
    
    // ========================================================================
    // AJOUT
    // ========================================================================
    
    /**
     * Ajoute un ID d'embedding texte
     */
    public void addTextEmbedding(String embeddingId) {
        if (embeddingId != null && !embeddingId.isBlank()) {
            textEmbeddingIds.add(embeddingId);
        }
    }
    
    /**
     * Ajoute un ID d'embedding image
     */
    public void addImageEmbedding(String embeddingId) {
        if (embeddingId != null && !embeddingId.isBlank()) {
            imageEmbeddingIds.add(embeddingId);
        }
    }
    
    // ========================================================================
    // RÉCUPÉRATION
    // ========================================================================
    
    /**
     * Retourne une copie de la liste des IDs d'embeddings texte
     */
    public List<String> getTextEmbeddingIds() {
        synchronized (textEmbeddingIds) {
            return new ArrayList<>(textEmbeddingIds);
        }
    }
    
    /**
     * Retourne une copie de la liste des IDs d'embeddings image
     */
    public List<String> getImageEmbeddingIds() {
        synchronized (imageEmbeddingIds) {
            return new ArrayList<>(imageEmbeddingIds);
        }
    }
    
    /**
     * Retourne le nombre d'embeddings texte
     */
    public int getTextEmbeddingCount() {
        return textEmbeddingIds.size();
    }
    
    /**
     * Retourne le nombre d'embeddings image
     */
    public int getImageEmbeddingCount() {
        return imageEmbeddingIds.size();
    }
    
    /**
     * Retourne le nombre total d'embeddings
     */
    public int getTotalCount() {
        return textEmbeddingIds.size() + imageEmbeddingIds.size();
    }
    
    // ========================================================================
    // NETTOYAGE
    // ========================================================================
    
    /**
     * Vide toutes les listes
     */
    public void clear() {
        textEmbeddingIds.clear();
        imageEmbeddingIds.clear();
    }
    
    @Override
    public String toString() {
        return String.format("BatchEmbeddings[text=%d, images=%d, total=%d]",
            getTextEmbeddingCount(), getImageEmbeddingCount(), getTotalCount());
    }
}