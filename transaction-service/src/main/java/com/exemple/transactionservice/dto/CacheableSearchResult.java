// ============================================================================
// DTO - CacheableSearchResult.java (v2.0 - Enhanced for RAGTools)
// ============================================================================
package com.exemple.transactionservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.Metadata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DTO sérialisable pour le cache Redis des résultats de recherche RAG.
 * Version enrichie compatible avec RAGTools v2.0+
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CacheableSearchResult implements Serializable {
    
    private static final long serialVersionUID = 2L;
    
    // ========================================================================
    // DONNÉES PRINCIPALES
    // ========================================================================
    
    private List<SearchResultItem> textResults;
    private List<SearchResultItem> imageResults;
    
    // ========================================================================
    // MÉTRIQUES (NOUVEAUX CHAMPS v2.0)
    // ========================================================================
    
    private SearchMetrics textMetrics;
    private SearchMetrics imageMetrics;
    private long totalDurationMs;
    
    // ========================================================================
    // ÉTAT & CACHE
    // ========================================================================
    
    private boolean wasCached = false;
    private boolean hasError = false;
    private String errorMessage;
    private String cacheKey;
    private long timestamp;
    
    // ========================================================================
    // INNER CLASSES
    // ========================================================================
    
    /**
     * Item de résultat de recherche cacheable
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResultItem implements Serializable {
        
        private static final long serialVersionUID = 1L;
        
        private String content;          // Le texte ou la description de l'image
        private Double score;            // Score de similarité
        private String source;           // Source du document
        private String type;             // Type: "text", "image", etc.
        private Integer page;            // Numéro de page (si applicable)
        private Integer totalPages;      // Total pages du document
        private String imagePath;        // Chemin de l'image (si applicable)
        private String imageName;        // Nom de l'image
        private String filename;         // Nom du fichier source
        private Integer imageNumber;     // Numéro d'image dans le document
        private Integer width;           // Largeur image
        private Integer height;          // Hauteur image
        private String imageId;          // ID unique de l'image
        private String savedPath;        // Chemin sauvegarde
        private String metadata;         // Autres métadonnées en JSON string
    }
    
    /**
     * Métriques de recherche
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchMetrics implements Serializable {
        
        private static final long serialVersionUID = 1L;
        
        private int resultCount;
        private long durationMs;
        private double averageScore;
        private double maxScore;
        private double minScore;
        private String searchType;  // "text" ou "image"
    }
    
    // ========================================================================
    // MÉTHODES DE CONVERSION (TextSegment ↔ SearchResultItem)
    // ========================================================================
    
    /**
     * Convertit un TextSegment en SearchResultItem (enrichi)
     */
    public static SearchResultItem fromTextSegment(TextSegment segment, Double score) {
        SearchResultItem item = new SearchResultItem();
        item.setContent(segment.text());
        item.setScore(score);
        
        if (segment.metadata() != null) {
            Metadata metadata = segment.metadata();
            
            // Métadonnées communes
            item.setSource(metadata.getString("source"));
            item.setType(metadata.getString("type"));
            item.setFilename(metadata.getString("filename"));
            
            // Pages
            item.setPage(metadata.getInteger("page"));
            item.setTotalPages(metadata.getInteger("totalPages"));
            
            // Images
            item.setImagePath(metadata.getString("savedPath"));
            item.setImageName(metadata.getString("imageName"));
            item.setImageNumber(metadata.getInteger("imageNumber"));
            item.setWidth(metadata.getInteger("width"));
            item.setHeight(metadata.getInteger("height"));
            item.setImageId(metadata.getString("imageId"));
            item.setSavedPath(metadata.getString("savedPath"));
        }
        
        return item;
    }
    
    /**
     * Convertit un SearchResultItem en TextSegment (pour compatibilité)
     */
    public static TextSegment toTextSegment(SearchResultItem item) {
        // Créer métadonnées avec Map
        Map<String, Object> metadataMap = new HashMap<>();
        
        // Ajouter métadonnées non-null
        if (item.getSource() != null) {
            metadataMap.put("source", item.getSource());
        }
        if (item.getType() != null) {
            metadataMap.put("type", item.getType());
        }
        if (item.getPage() != null) {
            metadataMap.put("page", item.getPage());
        }
        if (item.getTotalPages() != null) {
            metadataMap.put("totalPages", item.getTotalPages());
        }
        if (item.getFilename() != null) {
            metadataMap.put("filename", item.getFilename());
        }
        if (item.getImageName() != null) {
            metadataMap.put("imageName", item.getImageName());
        }
        if (item.getImagePath() != null) {
            metadataMap.put("savedPath", item.getImagePath());
        }
        if (item.getImageNumber() != null) {
            metadataMap.put("imageNumber", item.getImageNumber());
        }
        if (item.getWidth() != null) {
            metadataMap.put("width", item.getWidth());
        }
        if (item.getHeight() != null) {
            metadataMap.put("height", item.getHeight());
        }
        if (item.getImageId() != null) {
            metadataMap.put("imageId", item.getImageId());
        }
        if (item.getSavedPath() != null) {
            metadataMap.put("savedPath", item.getSavedPath());
        }
        
        // Créer Metadata depuis Map
        Metadata metadata = Metadata.from(metadataMap);
        
        // ✅ UTILISER TextSegment.from()
        return TextSegment.from(item.getContent(), metadata);
    }
    
    // ========================================================================
    // MÉTHODES DE CONVERSION EN LISTE (pour RAGTools)
    // ========================================================================
    
    /**
     * Convertit textResults en List<TextSegment>
     * Utilisé par RAGTools pour compatibilité
     */
    @JsonIgnore
    public List<TextSegment> getTextResultsAsSegments() {
        if (textResults == null) {
            return new ArrayList<>();
        }
        return textResults.stream()
            .map(CacheableSearchResult::toTextSegment)
            .collect(Collectors.toList());
    }
    
    /**
     * Convertit imageResults en List<TextSegment>
     * Utilisé par RAGTools pour compatibilité
     */
    @JsonIgnore
    public List<TextSegment> getImageResultsAsSegments() {
        if (imageResults == null) {
            return new ArrayList<>();
        }
        return imageResults.stream()
            .map(CacheableSearchResult::toTextSegment)
            .collect(Collectors.toList());
    }
    
    // ========================================================================
    // MÉTHODES UTILITAIRES
    // ========================================================================
    
    /**
     * Vérifie si le résultat est vide
     */
    @JsonIgnore
    public boolean isEmpty() {
        return (textResults == null || textResults.isEmpty()) &&
               (imageResults == null || imageResults.isEmpty());
    }
    
    /**
     * Retourne le nombre total de résultats
     */
    @JsonIgnore
    public int getTotalResults() {
        int total = 0;
        if (textResults != null) total += textResults.size();
        if (imageResults != null) total += imageResults.size();
        return total;
    }
    
    /**
     * Calcule les métriques automatiquement depuis les résultats
     */
    public void calculateMetrics(long textDurationMs, long imageDurationMs) {
        // Métriques texte
        if (textResults != null && !textResults.isEmpty()) {
            double avgScore = textResults.stream()
                .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0.0)
                .average()
                .orElse(0.0);
            
            double maxScore = textResults.stream()
                .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0.0)
                .max()
                .orElse(0.0);
            
            double minScore = textResults.stream()
                .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0.0)
                .min()
                .orElse(0.0);
            
            this.textMetrics = new SearchMetrics(
                textResults.size(),
                textDurationMs,
                avgScore,
                maxScore,
                minScore,
                "text"
            );
        }
        
        // Métriques images
        if (imageResults != null && !imageResults.isEmpty()) {
            double avgScore = imageResults.stream()
                .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0.0)
                .average()
                .orElse(0.0);
            
            double maxScore = imageResults.stream()
                .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0.0)
                .max()
                .orElse(0.0);
            
            double minScore = imageResults.stream()
                .mapToDouble(r -> r.getScore() != null ? r.getScore() : 0.0)
                .min()
                .orElse(0.0);
            
            this.imageMetrics = new SearchMetrics(
                imageResults.size(),
                imageDurationMs,
                avgScore,
                maxScore,
                minScore,
                "image"
            );
        }
        
        this.totalDurationMs = textDurationMs + imageDurationMs;
    }
    
    // ========================================================================
    // BUILDERS STATIQUES
    // ========================================================================
    
    /**
     * Crée un résultat d'erreur
     */
    public static CacheableSearchResult error(String errorMessage) {
        CacheableSearchResult result = new CacheableSearchResult();
        result.setHasError(true);
        result.setErrorMessage(errorMessage);
        result.setTextResults(new ArrayList<>());
        result.setImageResults(new ArrayList<>());
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }
    
    /**
     * Crée un résultat vide
     */
    public static CacheableSearchResult empty() {
        CacheableSearchResult result = new CacheableSearchResult();
        result.setTextResults(new ArrayList<>());
        result.setImageResults(new ArrayList<>());
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }
}

/*
 * ============================================================================
 * AMÉLIORATIONS VERSION 2.0
 * ============================================================================
 * 
 * ✅ COMPATIBILITÉ RAGTOOLS:
 * - Méthodes getTextResultsAsSegments() / getImageResultsAsSegments()
 * - Conversion bidirectionnelle TextSegment ↔ SearchResultItem
 * - Support complet des métadonnées (images, pages, etc.)
 * 
 * ✅ MÉTRIQUES ENRICHIES:
 * - SearchMetrics inner class (scores, durée, count)
 * - Calcul automatique via calculateMetrics()
 * - Métriques séparées texte/images
 * 
 * ✅ GESTION CACHE:
 * - Flag wasCached pour traçabilité
 * - CacheKey pour debugging
 * - Timestamp pour expiration
 * 
 * ✅ GESTION ERREURS:
 * - Flags hasError + errorMessage
 * - Builders statiques error() et empty()
 * 
 * USAGE DANS MULTIMODALRAGSERVICE:
 * ```java
 * CacheableSearchResult result = new CacheableSearchResult();
 * result.setTextResults(textItems);      // List<SearchResultItem>
 * result.setImageResults(imageItems);    // List<SearchResultItem>
 * result.calculateMetrics(textMs, imageMs);
 * result.setWasCached(false);
 * return result;
 * ```
 * 
 * USAGE DANS RAGTOOLS:
 * ```java
 * CacheableSearchResult result = ragService.search(...);
 * List<TextSegment> textSegments = result.getTextResultsAsSegments();
 * List<TextSegment> imageSegments = result.getImageResultsAsSegments();
 * // Puis formatage normal
 * ```
 */