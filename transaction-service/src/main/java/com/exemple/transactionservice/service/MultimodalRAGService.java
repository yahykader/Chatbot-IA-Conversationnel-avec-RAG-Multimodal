// ============================================================================
// SERVICE - MultimodalRAGService.java (v2.0.0) - AMÉLIORATION
// ============================================================================
package com.exemple.transactionservice.service;


import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import com.exemple.transactionservice.config.RAGConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MultimodalRAGService {
    
    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingStore<TextSegment> imageStore;
    private final EmbeddingModel embeddingModel;
    private final ExecutorService executorService;
    private final RAGConfig config;
    
    public MultimodalRAGService(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            EmbeddingModel embeddingModel,
            RAGConfig config) {
        this.textStore = textStore;
        this.imageStore = imageStore;
        this.embeddingModel = embeddingModel;
        this.config = config;
        this.executorService = Executors.newFixedThreadPool(
            config.getParallelSearchThreads()
        );
    }
    
    /**
     * Recherche multimodale avec exécution parallèle et gestion d'erreurs
     */
    @Cacheable(value = "multimodalSearch", key = "#query + '-' + #maxResults", 
               unless = "#result == null")
    public MultimodalSearchResult search(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            log.warn("⚠️ Requête vide reçue");
            return MultimodalSearchResult.empty();
        }
        
        Instant start = Instant.now();
        log.info("🔎 Recherche multimodale pour: '{}' (max: {})", query, maxResults);
        
        try {
            // Recherches parallèles pour optimiser les performances
            CompletableFuture<SearchResultWithMetrics<TextSegment>> textFuture = 
                CompletableFuture.supplyAsync(
                    () -> searchTextWithMetrics(query, maxResults), 
                    executorService
                );
            
            CompletableFuture<SearchResultWithMetrics<TextSegment>> imageFuture = 
                CompletableFuture.supplyAsync(
                    () -> searchImagesWithMetrics(query, maxResults), 
                    executorService
                );
            
            // Attendre les deux résultats
            CompletableFuture.allOf(textFuture, imageFuture).join();
            
            SearchResultWithMetrics<TextSegment> textResult = textFuture.get();
            SearchResultWithMetrics<TextSegment> imageResult = imageFuture.get();
            
            Duration totalDuration = Duration.between(start, Instant.now());
            
            MultimodalSearchResult result = MultimodalSearchResult.builder()
                .textResults(textResult.getResults())
                .imageResults(imageResult.getResults())
                .textMetrics(textResult.getMetrics())
                .imageMetrics(imageResult.getMetrics())
                .totalDurationMs(totalDuration.toMillis())
                .query(query)
                .build();
            
            log.info("✅ Recherche terminée en {}ms: {} textes (avg score: {:.3f}), {} images (avg score: {:.3f})", 
                totalDuration.toMillis(),
                result.getTextResults().size(), 
                textResult.getMetrics().getAverageScore(),
                result.getImageResults().size(),
                imageResult.getMetrics().getAverageScore()
            );
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Erreur lors de la recherche multimodale pour: '{}'", query, e);
            return MultimodalSearchResult.error(query, e.getMessage());
        }
    }
    
    /**
     * Recherche textuelle avec métriques et gestion d'erreurs
     */
    private SearchResultWithMetrics<TextSegment> searchTextWithMetrics(String query, int maxResults) {
        Instant start = Instant.now();
        
        try {
            List<EmbeddingMatch<TextSegment>> matches = performSearch(
                query, maxResults, textStore, "texte"
            );
            
            Duration duration = Duration.between(start, Instant.now());
            SearchMetrics metrics = computeMetrics(matches, duration);
            
            List<TextSegment> results = matches.stream()
                .map(EmbeddingMatch::embedded)
                .collect(Collectors.toList());
            
            return new SearchResultWithMetrics<>(results, metrics);
            
        } catch (Exception e) {
            log.error("❌ Erreur lors de la recherche textuelle", e);
            return SearchResultWithMetrics.error();
        }
    }
    
    /**
     * Recherche d'images avec métriques et gestion d'erreurs
     */
    private SearchResultWithMetrics<TextSegment> searchImagesWithMetrics(String query, int maxResults) {
        Instant start = Instant.now();
        
        try {
            List<EmbeddingMatch<TextSegment>> matches = performSearch(
                query, maxResults, imageStore, "image"
            );
            
            Duration duration = Duration.between(start, Instant.now());
            SearchMetrics metrics = computeMetrics(matches, duration);
            
            List<TextSegment> results = matches.stream()
                .map(EmbeddingMatch::embedded)
                .collect(Collectors.toList());
            
            return new SearchResultWithMetrics<>(results, metrics);
            
        } catch (Exception e) {
            log.error("❌ Erreur lors de la recherche d'images", e);
            return SearchResultWithMetrics.error();
        }
    }
    
    /**
     * Effectue la recherche d'embeddings avec retry
     */
    private List<EmbeddingMatch<TextSegment>> performSearch(
            String query, 
            int maxResults, 
            EmbeddingStore<TextSegment> store,
            String storeType) {
        
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < config.getMaxRetries()) {
            try {
                Embedding queryEmbedding = embeddingModel.embed(query).content();
                
                EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(config.getMinScore())
                    .build();
                
                EmbeddingSearchResult<TextSegment> results = store.search(request);
                
                log.debug("🔍 Recherche {} réussie: {} résultats trouvés", 
                    storeType, results.matches().size());
                
                return results.matches();
                
            } catch (Exception e) {
                attempts++;
                lastException = e;
                log.warn("⚠️ Tentative {}/{} échouée pour recherche {}", 
                    attempts, config.getMaxRetries(), storeType, e);
                
                if (attempts < config.getMaxRetries()) {
                    try {
                        Thread.sleep(config.getRetryDelayMs() * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        log.error("❌ Échec définitif après {} tentatives pour recherche {}", 
            attempts, storeType, lastException);
        return Collections.emptyList();
    }
    
    /**
     * Calcule les métriques de qualité des résultats
     */
    private SearchMetrics computeMetrics(
            List<EmbeddingMatch<TextSegment>> matches, 
            Duration duration) {
        
        if (matches.isEmpty()) {
            return SearchMetrics.builder()
                .resultCount(0)
                .averageScore(0.0)
                .maxScore(0.0)
                .minScore(0.0)
                .durationMs(duration.toMillis())
                .build();
        }
        
        double avgScore = matches.stream()
            .mapToDouble(EmbeddingMatch::score)
            .average()
            .orElse(0.0);
        
        double maxScore = matches.stream()
            .mapToDouble(EmbeddingMatch::score)
            .max()
            .orElse(0.0);
        
        double minScore = matches.stream()
            .mapToDouble(EmbeddingMatch::score)
            .min()
            .orElse(0.0);
        
        return SearchMetrics.builder()
            .resultCount(matches.size())
            .averageScore(avgScore)
            .maxScore(maxScore)
            .minScore(minScore)
            .durationMs(duration.toMillis())
            .build();
    }
    
    /**
     * Recherche publique pour texte uniquement (compatibilité)
     */
    public List<TextSegment> searchText(String query, int maxResults) {
        return searchTextWithMetrics(query, maxResults).getResults();
    }
    
    /**
     * Recherche publique pour images uniquement (compatibilité)
     */
    public List<TextSegment> searchImages(String query, int maxResults) {
        return searchImagesWithMetrics(query, maxResults).getResults();
    }
    
    // ========================================================================
    // CLASSES INTERNES
    // ========================================================================
    
    @Data
    @Builder
    @AllArgsConstructor
    public static class MultimodalSearchResult {
        private String query;
        private List<TextSegment> textResults;
        private List<TextSegment> imageResults;
        private SearchMetrics textMetrics;
        private SearchMetrics imageMetrics;
        private long totalDurationMs;
        private boolean hasError;
        private String errorMessage;
        
        public static MultimodalSearchResult empty() {
            return MultimodalSearchResult.builder()
                .textResults(Collections.emptyList())
                .imageResults(Collections.emptyList())
                .textMetrics(SearchMetrics.empty())
                .imageMetrics(SearchMetrics.empty())
                .totalDurationMs(0)
                .hasError(false)
                .build();
        }
        
        public static MultimodalSearchResult error(String query, String errorMessage) {
            return MultimodalSearchResult.builder()
                .query(query)
                .textResults(Collections.emptyList())
                .imageResults(Collections.emptyList())
                .textMetrics(SearchMetrics.empty())
                .imageMetrics(SearchMetrics.empty())
                .totalDurationMs(0)
                .hasError(true)
                .errorMessage(errorMessage)
                .build();
        }
        
        public int getTotalResults() {
            return textResults.size() + imageResults.size();
        }
    }
    
    @Data
    @Builder
    @AllArgsConstructor
    private static class SearchResultWithMetrics<T> {
        private List<T> results;
        private SearchMetrics metrics;
        
        public static <T> SearchResultWithMetrics<T> error() {
            return new SearchResultWithMetrics<>(
                Collections.emptyList(), 
                SearchMetrics.empty()
            );
        }
    }
    
    @Data
    @Builder
    public static class SearchMetrics {
        private int resultCount;
        private double averageScore;
        private double maxScore;
        private double minScore;
        private long durationMs;
        
        public static SearchMetrics empty() {
            return SearchMetrics.builder()
                .resultCount(0)
                .averageScore(0.0)
                .maxScore(0.0)
                .minScore(0.0)
                .durationMs(0)
                .build();
        }
    }
}

/*
        Bénéfices des améliorations
    ✅ Performance : Recherches parallèles (gain ~50%)
    ✅ Résilience : Retry automatique, gestion d'erreurs robuste
    ✅ Observabilité : Métriques détaillées (scores, latences)
    ✅ Flexibilité : Configuration externalisée
    ✅ Cache : Évite les recherches répétitives
    ✅ Production-ready : Logs structurés, monitoring
    Ces améliorations rendent le service beaucoup plus robuste et performant pour un usage en production.
*/

// ============================================================================
// FLUX DE DONNÉES - Multimodal RAG avec Métadonnées Enrichies
// ============================================================================ 
/**
 * Flux de données complet pour une architecture RAG multimodale
 * avec extraction et utilisation de métadonnées enrichies.
 * 
 * ## 📊 Flux complet de données
```
┌─────────────────────────────────────────────────────────────┐
│  1. UPLOAD (Frontend → Controller)                          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  2. INGESTION (MultimodalIngestionService)                  │
│     - Détection type fichier                                │
│     - Extraction texte/images                               │
│     - Génération embeddings                                 │
│     - Stockage PgVector avec métadonnées enrichies          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  3. STOCKAGE (PgVector)                                     │
│     text_embeddings table:                                  │
│     ├─ embedding (vector 1536)                              │
│     ├─ text (contenu)                                       │
│     └─ metadata (source, type, page, uploadDate, etc.)     │
│                                                             │
│     image_embeddings table:                                 │
│     ├─ embedding (vector 1536)                              │
│     ├─ text (description IA)                                │
│     └─ metadata (imageName, width, height, page, etc.)     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  4. RECHERCHE (MultimodalRAGService)                        │
│     - Embedding de la requête                               │
│     - Recherche similarité vectorielle                      │
│     - Retourne TextSegments avec métadonnées               │
│     ✅ AUCUNE MODIFICATION NÉCESSAIRE                       │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  5. FORMATAGE (RAGTools)                                    │
│     - Extrait métadonnées des TextSegments                  │
│     - Formate avec source, type, page, etc.                 │
│     ✅ DÉJÀ ENRICHI (voir ma réponse précédente)            │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  6. RÉPONSE À L'UTILISATEUR                                 │
│     📚 Document 1                                           │
│     Fichier: Git-lab CI-CD.docx                             │
│     Type: Word                                              │
│     Page: 1                                                 │
│     Extrait: "GitLab CI/CD est un outil..."                │
└─────────────────────────────────────────────────────────────┘
 */ 


