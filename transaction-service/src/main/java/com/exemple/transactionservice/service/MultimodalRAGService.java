// // ============================================================================
// // SERVICE - MultimodalRAGService.java (v3.3 - Approche A Compatible)
// // ============================================================================
// package com.exemple.transactionservice.service;

// import com.exemple.transactionservice.config.RAGConfig;
// import com.exemple.transactionservice.dto.CacheableSearchResult;
// import com.exemple.transactionservice.dto.CacheableSearchResult.SearchResultItem;
// import com.exemple.transactionservice.dto.CacheableSearchResult.SearchMetrics;
// import dev.langchain4j.data.embedding.Embedding;
// import dev.langchain4j.data.segment.TextSegment;
// import dev.langchain4j.model.embedding.EmbeddingModel;
// import dev.langchain4j.store.embedding.EmbeddingMatch;
// import dev.langchain4j.store.embedding.EmbeddingStore;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Qualifier;
// import org.springframework.cache.annotation.CacheEvict;
// import org.springframework.cache.annotation.Cacheable;
// import org.springframework.stereotype.Service;

// import jakarta.annotation.PreDestroy;
// import java.nio.charset.StandardCharsets;
// import java.security.MessageDigest;
// import java.time.Duration;
// import java.time.Instant;
// import java.util.ArrayList;
// import java.util.HexFormat;
// import java.util.List;
// import java.util.Objects;
// import java.util.concurrent.*;
// import java.util.concurrent.atomic.AtomicInteger;

// /**
//  * ============================================================================
//  * SERVICE RAG MULTIMODAL v3.3 - APPROCHE A COMPATIBLE
//  * ============================================================================
//  *
//  * Modifications v3.3 (Approche A):
//  * - ✅ Retourne CacheableSearchResult enrichi avec SearchMetrics
//  * - ✅ Conversion TextSegment → SearchResultItem via fromTextSegment()
//  * - ✅ Calcul automatique des métriques (scores, durées)
//  * - ✅ Compatible avec RAGTools qui utilise getTextResultsAsSegments()
//  * - ✅ Cache key stable avec hash + normalisation (v3.2)
//  * - ✅ Timeout/Retry robustes (v3.2)
//  *
//  * @author Transaction Service Team
//  * @version 3.3
//  * @since 2026-01-22
//  */
// @Slf4j
// @Service
// public class MultimodalRAGService {

//     // ========================================================================
//     // DEPENDENCIES
//     // ========================================================================

//     private final EmbeddingStore<TextSegment> textStore;
//     private final EmbeddingStore<TextSegment> imageStore;
//     private final EmbeddingModel embeddingModel;
//     private final RAGConfig ragConfig;
//     private final ExecutorService searchExecutor;

//     // ========================================================================
//     // CONFIGURATION (from RAGConfig)
//     // ========================================================================

//     private final int searchTimeoutSeconds;
//     private final int threadPoolSize;
//     private final int defaultMaxResults;
//     private final int maxAllowedResults;
//     private final double minScore;
//     private final int maxRetries;
//     private final long retryDelayMs;
//     private final int maxQueryLength;
//     private final boolean verboseLogging;
//     private final boolean enableMetrics;
//     private final String cacheVersion;

//     // ========================================================================
//     // CONSTRUCTOR
//     // ========================================================================

//     public MultimodalRAGService(
//             @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
//             @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
//             EmbeddingModel embeddingModel,
//             RAGConfig ragConfig) {

//         this.textStore = Objects.requireNonNull(textStore, "textStore");
//         this.imageStore = Objects.requireNonNull(imageStore, "imageStore");
//         this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
//         this.ragConfig = Objects.requireNonNull(ragConfig, "ragConfig");

//         this.searchTimeoutSeconds = Math.max(1, ragConfig.getSearchTimeoutSeconds());
//         this.threadPoolSize = Math.max(1, ragConfig.getParallelSearchThreads());
//         this.defaultMaxResults = Math.max(1, ragConfig.getDefaultMaxResults());
//         this.maxAllowedResults = Math.max(this.defaultMaxResults, ragConfig.getMaxAllowedResults());
//         this.minScore = ragConfig.getMinScore();
//         this.maxRetries = Math.max(1, ragConfig.getMaxRetries());
//         this.retryDelayMs = Math.max(0L, ragConfig.getRetryDelayMs());
//         this.maxQueryLength = Math.max(16, ragConfig.getMaxQueryLength());
//         this.verboseLogging = ragConfig.isVerboseLogging();
//         this.enableMetrics = ragConfig.isEnableMetrics();

//         this.cacheVersion = buildCacheVersion(ragConfig);

//         this.searchExecutor = createSearchExecutor();

//         logInitialization();
//     }

//     private ExecutorService createSearchExecutor() {
//         AtomicInteger idx = new AtomicInteger(0);

//         ThreadFactory tf = r -> {
//             Thread t = new Thread(r);
//             t.setName("rag-search-" + idx.incrementAndGet());
//             t.setDaemon(false);
//             t.setUncaughtExceptionHandler((thread, ex) ->
//                     log.error("❌ [RAG] Uncaught exception in {}", thread.getName(), ex)
//             );
//             return t;
//         };

//         return Executors.newFixedThreadPool(threadPoolSize, tf);
//     }

//     private void logInitialization() {
//         log.info("╔════════════════════════════════════════════════════════════╗");
//         log.info("║  MultimodalRAGService {} - Initialized (Approche A)      ║", String.format("%-4s", cacheVersion));
//         log.info("╠════════════════════════════════════════════════════════════╣");
//         log.info("║  Timeout:         {} seconds", String.format("%27s", searchTimeoutSeconds + " ║"));
//         log.info("║  Thread Pool:     {} threads", String.format("%27s", threadPoolSize + " ║"));
//         log.info("║  Max Results:     {} (limit: {})", String.format("%18s", defaultMaxResults + ", " + maxAllowedResults + ") ║"));
//         log.info("║  Min Score:       {}", String.format("%34s", String.format("%.2f", minScore) + " ║"));
//         log.info("║  Max Retries:     {} (delay: {}ms)", String.format("%18s", maxRetries + ", " + retryDelayMs + ") ║"));
//         log.info("║  Max Query:       {} chars", String.format("%25s", maxQueryLength + " ║"));
//         log.info("║  Verbose Logs:    {}", String.format("%34s", verboseLogging + " ║"));
//         log.info("║  Metrics:         {}", String.format("%34s", enableMetrics + " ║"));
//         log.info("╚════════════════════════════════════════════════════════════╝");
//     }

//     private static String buildCacheVersion(RAGConfig cfg) {
//         String raw = String.join("|",
//                 String.valueOf(cfg.getMinScore()),
//                 String.valueOf(cfg.getDefaultMaxResults()),
//                 String.valueOf(cfg.getMaxAllowedResults()),
//                 String.valueOf(cfg.getSearchTimeoutSeconds()),
//                 String.valueOf(cfg.getParallelSearchThreads()),
//                 String.valueOf(cfg.getMaxQueryLength()),
//                 String.valueOf(cfg.isVerboseLogging())
//         );
//         return sha256Hex(raw).substring(0, 8);
//     }

//     // ========================================================================
//     // PUBLIC API - SEARCH (✅ APPROCHE A)
//     // ========================================================================

//     /**
//      * ✅ APPROCHE A: Recherche multimodale avec métriques enrichies
//      * 
//      * Retourne CacheableSearchResult avec:
//      * - textResults: List<SearchResultItem>
//      * - imageResults: List<SearchResultItem>
//      * - textMetrics: SearchMetrics (scores, durée)
//      * - imageMetrics: SearchMetrics (scores, durée)
//      * - totalDurationMs: long
//      * - wasCached: boolean
//      * 
//      * RAGTools utilisera getTextResultsAsSegments() pour conversion vers TextSegment.
//      */
//     @Cacheable(
//             value = "multimodal-rag-search",
//             key = "T(com.exemple.transactionservice.service.MultimodalRAGService)"
//                 + ".buildCacheKey(#query, #maxResults, #userId, #root.target.minScore, #root.target.cacheVersion)",
//             sync = true
//     )
//     public CacheableSearchResult search(String query, int maxResults, String userId) {
//         Instant startTime = Instant.now();

//         // ========================================
//         // 1. VALIDATION
//         // ========================================

//         ValidationResult validation = validateSearchParams(query, maxResults, userId);
//         if (!validation.isValid()) {
//             return validation.getEmptyResult();
//         }

//         query = validation.getSanitizedQuery();
//         maxResults = validation.getSanitizedMaxResults();

//         // ========================================
//         // 2. EMBEDDING GENERATION
//         // ========================================

//         Embedding queryEmbedding = generateQueryEmbedding(query);

//         // ========================================
//         // 3. PARALLEL SEARCH (timeout-safe)
//         // ========================================

//         SearchResults searchResults = executeParallelSearch(queryEmbedding, maxResults);

//         // ========================================
//         // 4. ✅ APPROCHE A: CONVERSION & METRICS
//         // ========================================

//         CacheableSearchResult result = convertAndBuildResult(
//             searchResults, 
//             startTime
//         );

//         // ========================================
//         // 5. LOGGING
//         // ========================================

//         logSearchCompletion(startTime, result, query, userId);

//         return result;
//     }

//     public CacheableSearchResult search(String query, String userId) {
//         return search(query, defaultMaxResults, userId);
//     }

//     // ========================================================================
//     // CACHE KEY (hash + normalisation)
//     // ========================================================================

//     public static String buildCacheKey(String query, int maxResults, String userId, double minScore, String cacheVersion) {
//         String q = normalizeQueryForCache(query);
//         String qHash = sha256Hex(q);

//         int k = maxResults;
//         String uid = (userId == null || userId.isBlank()) ? "anon" : userId;

//         return "v=" + (cacheVersion == null ? "na" : cacheVersion)
//                 + "|q=" + qHash
//                 + "|k=" + k
//                 + "|u=" + uid
//                 + "|ms=" + String.format("%.4f", minScore);
//     }

//     private static String normalizeQueryForCache(String query) {
//         if (query == null) return "";
//         return query.trim().replaceAll("\\s+", " ");
//     }

//     private static String sha256Hex(String input) {
//         try {
//             MessageDigest md = MessageDigest.getInstance("SHA-256");
//             byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
//             return HexFormat.of().formatHex(digest);
//         } catch (Exception e) {
//             return Integer.toHexString(Objects.hashCode(input));
//         }
//     }

//     public double getMinScore() {
//         return minScore;
//     }

//     public String getCacheVersion() {
//         return cacheVersion;
//     }

//     // ========================================================================
//     // PRIVATE - VALIDATION
//     // ========================================================================

//     private ValidationResult validateSearchParams(String query, int maxResults, String userId) {
//         if (query == null || query.isBlank()) {
//             log.warn("⚠️ [RAG] Query vide - User: {}", userId);
//             return ValidationResult.invalid();
//         }

//         String sanitizedQuery = query.trim().replaceAll("\\s+", " ");

//         if (sanitizedQuery.length() > maxQueryLength) {
//             log.warn("⚠️ [RAG] Query trop longue: {} chars (max: {}) - Troncature",
//                     sanitizedQuery.length(), maxQueryLength);
//             sanitizedQuery = sanitizedQuery.substring(0, maxQueryLength);
//         }

//         int sanitizedMaxResults = maxResults;
//         if (sanitizedMaxResults <= 0) {
//             sanitizedMaxResults = defaultMaxResults;
//         }
//         if (sanitizedMaxResults > maxAllowedResults) {
//             log.warn("⚠️ [RAG] maxResults trop élevé: {} (max: {}) - Limitation",
//                     sanitizedMaxResults, maxAllowedResults);
//             sanitizedMaxResults = maxAllowedResults;
//         }

//         if (verboseLogging) {
//             log.info("🔍 [RAG] Recherche - Query: '{}' | Max: {} | User: {}",
//                     truncate(sanitizedQuery, 100), sanitizedMaxResults, userId);
//         } else {
//             log.debug("🔍 [RAG] Recherche - Length: {} | Max: {} | User: {}",
//                     sanitizedQuery.length(), sanitizedMaxResults, userId);
//         }

//         return ValidationResult.valid(sanitizedQuery, sanitizedMaxResults);
//     }

//     // ========================================================================
//     // PRIVATE - EMBEDDING
//     // ========================================================================

//     private Embedding generateQueryEmbedding(String query) {
//         try {
//             Embedding embedding = embeddingModel.embed(query).content();

//             if (verboseLogging) {
//                 log.debug("🔢 [RAG] Embedding généré: {} dimensions", embedding.vector().length);
//             }

//             return embedding;

//         } catch (Exception e) {
//             log.error("❌ [RAG] Erreur génération embedding", e);
//             throw new RuntimeException("Échec génération embedding: " + e.getMessage(), e);
//         }
//     }

//     // ========================================================================
//     // PRIVATE - PARALLEL SEARCH (timeout-safe futures)
//     // ========================================================================

//     private SearchResults executeParallelSearch(Embedding queryEmbedding, int maxResults) {

//         Instant textStart = Instant.now();
//         CompletableFuture<TimedSearchResult> textFuture =
//                 CompletableFuture.supplyAsync(
//                                 () -> {
//                                     Instant start = Instant.now();
//                                     List<EmbeddingMatch<TextSegment>> matches = 
//                                         searchWithRetry(queryEmbedding, maxResults, textStore, "text");
//                                     long duration = Duration.between(start, Instant.now()).toMillis();
//                                     return new TimedSearchResult(matches, duration);
//                                 },
//                                 searchExecutor
//                         )
//                         .completeOnTimeout(
//                             new TimedSearchResult(List.of(), 0L), 
//                             searchTimeoutSeconds, 
//                             TimeUnit.SECONDS
//                         )
//                         .exceptionally(ex -> {
//                             log.error("❌ [RAG] Erreur recherche text", ex);
//                             return new TimedSearchResult(List.of(), 0L);
//                         });

//         Instant imageStart = Instant.now();
//         CompletableFuture<TimedSearchResult> imageFuture =
//                 CompletableFuture.supplyAsync(
//                                 () -> {
//                                     Instant start = Instant.now();
//                                     List<EmbeddingMatch<TextSegment>> matches = 
//                                         searchWithRetry(queryEmbedding, maxResults, imageStore, "image");
//                                     long duration = Duration.between(start, Instant.now()).toMillis();
//                                     return new TimedSearchResult(matches, duration);
//                                 },
//                                 searchExecutor
//                         )
//                         .completeOnTimeout(
//                             new TimedSearchResult(List.of(), 0L), 
//                             searchTimeoutSeconds, 
//                             TimeUnit.SECONDS
//                         )
//                         .exceptionally(ex -> {
//                             log.error("❌ [RAG] Erreur recherche image", ex);
//                             return new TimedSearchResult(List.of(), 0L);
//                         });

//         TimedSearchResult textResult = textFuture.join();
//         TimedSearchResult imageResult = imageFuture.join();

//         return new SearchResults(
//             textResult.matches(), 
//             imageResult.matches(),
//             textResult.durationMs(),
//             imageResult.durationMs()
//         );
//     }

//     // ========================================================================
//     // PRIVATE - RETRY MECHANISM
//     // ========================================================================

//     private List<EmbeddingMatch<TextSegment>> searchWithRetry(
//             Embedding queryEmbedding,
//             int maxResults,
//             EmbeddingStore<TextSegment> store,
//             String storeName) {

//         Exception lastException = null;

//         for (int attempt = 1; attempt <= maxRetries; attempt++) {
//             try {
//                 if (verboseLogging) {
//                     log.debug("📄 [RAG] Recherche {} démarrée... (attempt {}/{})", 
//                         storeName, attempt, maxRetries);
//                 }

//                 Instant start = Instant.now();
//                 List<EmbeddingMatch<TextSegment>> matches = store.findRelevant(queryEmbedding, maxResults);

//                 if (verboseLogging) {
//                     log.debug("📄 [RAG] Recherche {} terminée en {}ms: {} résultats",
//                             storeName, Duration.between(start, Instant.now()).toMillis(), matches.size());
//                 }

//                 return matches;

//             } catch (Exception e) {
//                 lastException = e;

//                 if (attempt < maxRetries) {
//                     long base = retryDelayMs * (1L << (attempt - 1));
//                     long jitter = ThreadLocalRandom.current().nextLong(0, 200);
//                     long delay = base + jitter;

//                     if (verboseLogging) {
//                         log.warn("⚠️ [RAG] Erreur {} (tentative {}/{}) - Retry dans {}ms: {}",
//                                 storeName, attempt, maxRetries, delay, e.getMessage());
//                     }

//                     try {
//                         Thread.sleep(delay);
//                     } catch (InterruptedException ie) {
//                         Thread.currentThread().interrupt();
//                         throw new RuntimeException("Retry interrompu", ie);
//                     }
//                 }
//             }
//         }

//         log.error("❌ [RAG] Échec définitif {} après {} tentatives", storeName, maxRetries, lastException);
//         return List.of();
//     }

//     // ========================================================================
//     // PRIVATE - ✅ APPROCHE A: CONVERSION & BUILD RESULT
//     // ========================================================================

//     /**
//      * ✅ APPROCHE A: Convertit EmbeddingMatch → SearchResultItem et calcule métriques
//      */
//     private CacheableSearchResult convertAndBuildResult(
//             SearchResults searchResults, 
//             Instant startTime) {

//         // 1. Filtrage par score + Conversion TextSegment → SearchResultItem
//         List<SearchResultItem> textItems = searchResults.textMatches().stream()
//                 .filter(match -> match.score() >= minScore)
//                 .map(match -> CacheableSearchResult.fromTextSegment(match.embedded(), match.score()))
//                 .toList();

//         List<SearchResultItem> imageItems = searchResults.imageMatches().stream()
//                 .filter(match -> match.score() >= minScore)
//                 .map(match -> CacheableSearchResult.fromTextSegment(match.embedded(), match.score()))
//                 .toList();

//         // 2. Construction du résultat
//         CacheableSearchResult result = new CacheableSearchResult();
//         result.setTextResults(textItems);
//         result.setImageResults(imageItems);

//         // 3. ✅ APPROCHE A: Calcul des métriques enrichies
//         result.calculateMetrics(
//             searchResults.textDurationMs(), 
//             searchResults.imageDurationMs()
//         );

//         // 4. Métadonnées cache
//         result.setWasCached(false); // Sera true si servi depuis cache
//         result.setTimestamp(System.currentTimeMillis());
//         result.setTotalDurationMs(Duration.between(startTime, Instant.now()).toMillis());

//         // 5. Vérification cohérence
//         if (result.getTextMetrics() == null && !textItems.isEmpty()) {
//             log.warn("⚠️ [RAG] Métriques texte nulles malgré {} résultats", textItems.size());
//         }
//         if (result.getImageMetrics() == null && !imageItems.isEmpty()) {
//             log.warn("⚠️ [RAG] Métriques images nulles malgré {} résultats", imageItems.size());
//         }

//         return result;
//     }

//     // ========================================================================
//     // PRIVATE - LOGGING & METRICS
//     // ========================================================================

//     private void logSearchCompletion(
//             Instant startTime,
//             CacheableSearchResult result,
//             String query,
//             String userId) {

//         Duration duration = Duration.between(startTime, Instant.now());

//         if (verboseLogging) {
//             double avgTextScore = result.getTextResults().stream()
//                     .mapToDouble(SearchResultItem::getScore)
//                     .average()
//                     .orElse(0.0);

//             double avgImageScore = result.getImageResults().stream()
//                     .mapToDouble(SearchResultItem::getScore)
//                     .average()
//                     .orElse(0.0);

//             log.info("✅ [RAG] Recherche terminée en {}ms - Textes: {} (avg: {:.3f}) | Images: {} (avg: {:.3f})",
//                     duration.toMillis(),
//                     result.getTextResults().size(), avgTextScore,
//                     result.getImageResults().size(), avgImageScore);
//         } else {
//             log.debug("✅ [RAG] Recherche terminée en {}ms - {} résultats totaux",
//                     duration.toMillis(), result.getTotalResults());
//         }

//         // TODO: Micrometer si enableMetrics = true
//     }

//     // ========================================================================
//     // PUBLIC API - CACHE MANAGEMENT
//     // ========================================================================

//     @CacheEvict(value = "multimodal-rag-search", allEntries = true)
//     public void invalidateCacheAfterIngestion() {
//         log.info("🗑️ [RAG] Cache invalidé après ingestion");
//     }

//     @CacheEvict(value = "multimodal-rag-search", allEntries = true)
//     public void invalidateCacheForUser(String userId) {
//         log.info("🗑️ [RAG] Cache invalidé (global) - Demande purge user: {}", userId);
//     }

//     @CacheEvict(value = "multimodal-rag-search", allEntries = true)
//     public void clearCache() {
//         log.info("🗑️ [RAG] Cache entièrement vidé");
//     }

//     // ========================================================================
//     // PUBLIC API - STATISTICS
//     // ========================================================================

//     public RagStatistics getStatistics() {
//         return new RagStatistics(
//                 0, // textStore.count() si dispo
//                 0, // imageStore.count() si dispo
//                 searchTimeoutSeconds,
//                 threadPoolSize,
//                 minScore,
//                 maxRetries
//         );
//     }

//     public record RagStatistics(
//             int textEmbeddingsCount,
//             int imageEmbeddingsCount,
//             int searchTimeoutSeconds,
//             int threadPoolSize,
//             double minScore,
//             int maxRetries
//     ) {}

//     // ========================================================================
//     // LIFECYCLE - CLEANUP
//     // ========================================================================

//     @PreDestroy
//     public void shutdown() {
//         log.info("🛑 [RAG] Arrêt du service...");

//         searchExecutor.shutdown();

//         try {
//             if (!searchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
//                 log.warn("⚠️ [RAG] Timeout arrêt thread pool - Force shutdown");
//                 List<Runnable> dropped = searchExecutor.shutdownNow();
//                 log.warn("⚠️ [RAG] Tâches annulées: {}", dropped.size());

//                 if (!searchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
//                     log.error("❌ [RAG] Thread pool ne s'arrête pas");
//                 }
//             }

//             log.info("✅ [RAG] Service arrêté proprement");

//         } catch (InterruptedException e) {
//             log.error("❌ [RAG] Interruption lors de l'arrêt", e);
//             searchExecutor.shutdownNow();
//             Thread.currentThread().interrupt();
//         }
//     }

//     // ========================================================================
//     // UTILITY METHODS
//     // ========================================================================

//     private String truncate(String text, int maxLength) {
//         if (text == null) return "null";
//         if (text.length() <= maxLength) return text;
//         return text.substring(0, maxLength - 3) + "...";
//     }

//     // ========================================================================
//     // INNER CLASSES - DTOs
//     // ========================================================================

//     private static class ValidationResult {
//         private final boolean valid;
//         private final String sanitizedQuery;
//         private final int sanitizedMaxResults;

//         private ValidationResult(boolean valid, String query, int maxResults) {
//             this.valid = valid;
//             this.sanitizedQuery = query;
//             this.sanitizedMaxResults = maxResults;
//         }

//         static ValidationResult valid(String query, int maxResults) {
//             return new ValidationResult(true, query, maxResults);
//         }

//         static ValidationResult invalid() {
//             return new ValidationResult(false, null, 0);
//         }

//         boolean isValid() { return valid; }
//         String getSanitizedQuery() { return sanitizedQuery; }
//         int getSanitizedMaxResults() { return sanitizedMaxResults; }

//         CacheableSearchResult getEmptyResult() {
//             CacheableSearchResult result = new CacheableSearchResult();
//             result.setTextResults(new ArrayList<>());
//             result.setImageResults(new ArrayList<>());
//             result.setHasError(true);
//             result.setErrorMessage("Query invalide");
//             result.setTimestamp(System.currentTimeMillis());
//             return result;
//         }
//     }

//     /**
//      * ✅ APPROCHE A: SearchResults enrichi avec durées
//      */
//     private record SearchResults(
//             List<EmbeddingMatch<TextSegment>> textMatches,
//             List<EmbeddingMatch<TextSegment>> imageMatches,
//             long textDurationMs,
//             long imageDurationMs
//     ) {}

//     /**
//      * ✅ APPROCHE A: Résultat de recherche avec durée
//      */
//     private record TimedSearchResult(
//             List<EmbeddingMatch<TextSegment>> matches,
//             long durationMs
//     ) {}
// }

// /*
//  * ============================================================================
//  * APPROCHE A - ADAPTATIONS v3.3
//  * ============================================================================
//  * 
//  * CHANGEMENTS PAR RAPPORT À v3.2:
//  * 
//  * 1. ✅ CONVERSION TEXTSEGMENT → SEARCHRESULTITEM
//  *    - Utilise CacheableSearchResult.fromTextSegment(segment, score)
//  *    - Préserve toutes les métadonnées (source, type, page, etc.)
//  *    - Score extrait de EmbeddingMatch.score()
//  * 
//  * 2. ✅ MÉTRIQUES ENRICHIES
//  *    - SearchResults inclut maintenant textDurationMs et imageDurationMs
//  *    - TimedSearchResult capture durée de chaque recherche
//  *    - calculateMetrics() calcule automatiquement avg/min/max scores
//  * 
//  * 3. ✅ CACHEABLESEARCHRESULT COMPLET
//  *    - textResults: List<SearchResultItem>
//  *    - imageResults: List<SearchResultItem>
//  *    - textMetrics: SearchMetrics (count, duration, scores)
//  *    - imageMetrics: SearchMetrics (count, duration, scores)
//  *    - totalDurationMs: durée totale end-to-end
//  *    - wasCached: false (sera true si cache hit)
//  *    - timestamp: pour debug/expiration
//  * 
//  * 4. ✅ VALIDATION RESULT
//  *    - getEmptyResult() retourne maintenant un CacheableSearchResult enrichi
//  *    - Avec hasError=true et errorMessage explicite
//  * 
//  * ============================================================================
//  * UTILISATION DANS RAGTOOLS (APPROCHE A):
//  * ============================================================================
//  * 
//  * ```java
//  * // RAGTools appelle le service
//  * CacheableSearchResult cacheResult = ragService.search(query, limit, userId);
//  * 
//  * // Conversion transparente via méthodes helper
//  * List<TextSegment> textSegments = cacheResult.getTextResultsAsSegments();
//  * List<TextSegment> imageSegments = cacheResult.getImageResultsAsSegments();
//  * 
//  * // Filtrage + Pagination standard (TextSegment)
//  * List<TextSegment> filtered = filterByFileType(textSegments, fileType);
//  * PaginationResult<TextSegment> paginated = paginate(filtered, page, size);
//  * 
//  * // Formatage standard (TextSegment.metadata())
//  * String result = formatDocumentResults(paginated, query, fileType, duration);
//  * ```
//  * 
//  * ============================================================================
//  * FLOW COMPLET:
//  * ============================================================================
//  * 
//  * 1. search(query, maxResults, userId)
//  *    ↓
//  * 2. Validation + Génération embedding
//  *    ↓
//  * 3. Recherche parallèle (timeout-safe)
//  *    → textStore.findRelevant() → List<EmbeddingMatch<TextSegment>> + durée
//  *    → imageStore.findRelevant() → List<EmbeddingMatch<TextSegment>> + durée
//  *    ↓
//  * 4. convertAndBuildResult()
//  *    → Filtrage par minScore
//  *    → Conversion: EmbeddingMatch → SearchResultItem via fromTextSegment()
//  *    → Calcul métriques: avg/min/max scores
//  *    → Construction CacheableSearchResult complet
//  *    ↓
//  * 5. Mise en cache Redis (via @Cacheable)
//  *    → Sérialisation JSON de CacheableSearchResult
//  *    → Key = hash(query) + user + maxResults + minScore + version
//  *    ↓
//  * 6. Retour à RAGTools
//  *    → RAGTools utilise getTextResultsAsSegments()
//  *    → Conversion transparente SearchResultItem → TextSegment
//  *    → Formatage standard avec TextSegment
//  * 
//  * ============================================================================
//  * CACHE HIT FLOW:
//  * ============================================================================
//  * 
//  * 1. search(query, maxResults, userId) - @Cacheable
//  *    ↓
//  * 2. Redis: GET cache-key
//  *    ↓
//  * 3. Désérialisation CacheableSearchResult depuis JSON
//  *    ↓
//  * 4. Retour direct à RAGTools (0 recherche vector store)
//  *    → wasCached = true
//  *    → Durées = 0ms (ou durées cachées)
//  * 
//  * ============================================================================
//  * TESTS SUGGÉRÉS:
//  * ============================================================================
//  * 
//  * @Test
//  * public void testSearchWithMetrics() {
//  *     CacheableSearchResult result = service.search("test query", 10, "user123");
//  *     
//  *     assertNotNull(result);
//  *     assertNotNull(result.getTextResults());
//  *     assertNotNull(result.getImageResults());
//  *     assertNotNull(result.getTextMetrics());
//  *     assertTrue(result.getTotalDurationMs() > 0);
//  *     assertFalse(result.isWasCached()); // Premier appel
//  *     
//  *     // Vérifier conversion TextSegment
//  *     List<TextSegment> segments = result.getTextResultsAsSegments();
//  *     assertNotNull(segments);
//  *     
//  *     // Scores cohérents
//  *     if (!result.getTextResults().isEmpty()) {
//  *         assertEquals(
//  *             result.getTextMetrics().getResultCount(),
//  *             result.getTextResults().size()
//  *         );
//  *     }
//  * }
//  */