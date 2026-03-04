// // ============================================================================
// // SERVICE - ConversationalAssistant.java (v2.2.0) - Enhanced with Metrics
// // ============================================================================
// package com.exemple.transactionservice.service;

// import com.exemple.transactionservice.dto.CacheableSearchResult;
// import com.exemple.transactionservice.dto.CacheableSearchResult.SearchResultItem;
// import com.exemple.transactionservice.dto.CacheableSearchResult.SearchMetrics;
// import com.google.common.cache.CacheBuilder;
// import com.google.common.cache.CacheLoader;
// import com.google.common.cache.LoadingCache;
// import com.google.common.util.concurrent.RateLimiter;
// import dev.langchain4j.model.chat.StreamingChatLanguageModel;
// import dev.langchain4j.model.chat.response.ChatResponse;
// import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
// import lombok.Data;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.data.redis.core.RedisTemplate;
// import org.springframework.stereotype.Service;
// import reactor.core.publisher.Flux;

// import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
// import java.io.Serializable;
// import java.time.Duration;
// import java.time.Instant;
// import java.util.Deque;
// import java.util.LinkedList;
// import java.util.List;
// import java.util.concurrent.*;
// import java.util.concurrent.atomic.AtomicInteger;

// /**
//  * ============================================================================
//  * CONVERSATIONAL ASSISTANT v2.2 - Enhanced with Metrics (Approche A)
//  * ============================================================================
//  * 
//  * Assistant conversationnel avec RAG multimodal et métriques enrichies
//  * 
//  * Nouveautés v2.2 (par rapport à v2.1):
//  * - ✅ Logs enrichis avec métriques RAG (scores, durées)
//  * - ✅ Indicateur cache hit (performance monitoring)
//  * - ✅ Warning scores faibles (réponses plus nuancées)
//  * - ✅ Score moyen dans prompt (contexte pour LLM)
//  * - ✅ Détection qualité résultats RAG
//  * - ✅ Métriques détaillées dans logs
//  * 
//  * Architecture:
//  * - Compatible CacheableSearchResult (DTO sérialisable Redis)
//  * - Utilise SearchResultItem (POJO direct)
//  * - Chat streaming avec timeout configurable (120s)
//  * - RAG multimodal (textes + images)
//  * - Contexte conversation Redis persistant (24h TTL)
//  * - Rate limiting (10 req/min par utilisateur)
//  * - Formatage markdown préservé
//  * 
//  * @version 2.2.0
//  * @since 2026-01-22
//  */
// @Slf4j
// @Service
// public class ConversationalAssistant {

//     // ========================================================================
//     // DEPENDENCIES
//     // ========================================================================

//     private final StreamingChatLanguageModel streamingChatModel;
//     private final MultimodalRAGService ragService;
//     private final RedisTemplate<String, ConversationContext> contextRedisTemplate;
//     private final ScheduledExecutorService scheduler;
//     private final LoadingCache<String, RateLimiter> rateLimiters;

//     // ========================================================================
//     // CONFIGURATION
//     // ========================================================================

//     @Value("${assistant.stream.timeout-seconds:120}")
//     private int streamTimeoutSeconds;
    
//     @Value("${assistant.context.max-exchanges:5}")
//     private int maxContextExchanges;
    
//     @Value("${assistant.context.max-tokens:4000}")
//     private int maxContextTokens;
    
//     @Value("${assistant.context.ttl-hours:24}")
//     private int contextTtlHours;
    
//     @Value("${assistant.rate-limit.requests-per-minute:10}")
//     private double rateLimitRequestsPerMinute;
    
//     @Value("${assistant.rag.low-quality-threshold:0.5}")
//     private double lowQualityThreshold;
    
//     @Value("${assistant.metrics.enabled:true}")
//     private boolean metricsEnabled;

//     // ========================================================================
//     // CONSTRUCTOR
//     // ========================================================================

//     public ConversationalAssistant(
//             StreamingChatLanguageModel streamingChatModel,
//             MultimodalRAGService ragService,
//             RedisTemplate<String, ConversationContext> contextRedisTemplate) {
        
//         this.streamingChatModel = streamingChatModel;
//         this.ragService = ragService;
//         this.contextRedisTemplate = contextRedisTemplate;
//         this.scheduler = Executors.newScheduledThreadPool(4);
        
//         // Rate limiters avec cache Guava
//         this.rateLimiters = CacheBuilder.newBuilder()
//             .maximumSize(10000)
//             .expireAfterAccess(5, TimeUnit.MINUTES)
//             .build(new CacheLoader<String, RateLimiter>() {
//                 @Override
//                 public RateLimiter load(String userId) {
//                     return RateLimiter.create(rateLimitRequestsPerMinute / 60.0);
//                 }
//             });

//         log.info("╔════════════════════════════════════════════════════════════╗");
//         log.info("║  ConversationalAssistant v2.2 - Enhanced (Approche A)    ║");
//         log.info("╠════════════════════════════════════════════════════════════╣");
//         log.info("║  Timeout:        {}s", String.format("%35s", streamTimeoutSeconds + " ║"));
//         log.info("║  Context:        {} exchanges / {} tokens", String.format("%16s", maxContextExchanges + " / " + maxContextTokens + " ║"));
//         log.info("║  Context TTL:    {}h", String.format("%35s", contextTtlHours + " ║"));
//         log.info("║  Rate Limit:     {}/min", String.format("%32s", (int)rateLimitRequestsPerMinute + " ║"));
//         log.info("║  Quality Limit:  {}", String.format("%34s", lowQualityThreshold + " ║"));
//         log.info("║  Metrics:        {}", String.format("%34s", metricsEnabled + " ║"));
//         log.info("╚════════════════════════════════════════════════════════════╝");
//     }

//     // ========================================================================
//     // PUBLIC API - CHAT STREAMING
//     // ========================================================================

//     /**
//      * Chat streaming avec RAG multimodal et métriques enrichies
//      * 
//      * Workflow:
//      * 1. Rate limiting check
//      * 2. Construction prompt enrichi (contexte + RAG avec métriques)
//      * 3. Streaming LLM avec timeout
//      * 4. Sauvegarde contexte Redis
//      * 
//      * @param userId Identifiant utilisateur
//      * @param userMessage Message utilisateur
//      * @return Flux de tokens streamés
//      */
//     public Flux<String> chatStream(String userId, String userMessage) {
//         Instant start = Instant.now();
//         String sessionId = userId + "_" + System.currentTimeMillis();

//         log.info("💬 [{}] Chat streaming - User: {}, Message: '{}'",
//                 sessionId, userId, truncate(userMessage, 100));

//         // ========================================
//         // 1. RATE LIMITING
//         // ========================================
        
//         if (!checkRateLimit(userId, sessionId)) {
//             return Flux.error(new RateLimitException(
//                 "Trop de requêtes. Veuillez patienter quelques secondes."
//             ));
//         }

//         try {
//             // ========================================
//             // 2. PROMPT BUILDING (avec RAG + métriques)
//             // ========================================
            
//             String enhancedPrompt = buildEnhancedMultimodalPrompt(userId, userMessage, sessionId);

//             if (log.isDebugEnabled()) {
//                 log.debug("📤 [{}] Prompt ({} chars):\n{}",
//                         sessionId, enhancedPrompt.length(), truncate(enhancedPrompt, 500));
//             }

//             // ========================================
//             // 3. STREAMING
//             // ========================================
            
//             return createStreamingFlux(sessionId, userId, userMessage, enhancedPrompt, start);

//         } catch (Exception e) {
//             log.error("❌ [{}] Erreur lors de la préparation du chat", sessionId, e);
//             return Flux.error(new RuntimeException(
//                     "Erreur lors de la préparation de la réponse: " + e.getMessage(),
//                     e
//             ));
//         }
//     }

//     // ========================================================================
//     // PRIVATE - RATE LIMITING
//     // ========================================================================

//     /**
//      * Vérifie le rate limit pour un utilisateur
//      */
//     private boolean checkRateLimit(String userId, String sessionId) {
//         try {
//             RateLimiter limiter = rateLimiters.getUnchecked(userId);
            
//             if (!limiter.tryAcquire(1, TimeUnit.SECONDS)) {
//                 log.warn("⚠️ [{}] Rate limit dépassé pour user: {}", sessionId, userId);
//                 return false;
//             }
            
//             return true;
            
//         } catch (Exception e) {
//             log.error("❌ [{}] Erreur rate limiting", sessionId, e);
//             return true; // Fail-open en cas d'erreur
//         }
//     }

//     // ========================================================================
//     // PRIVATE - STREAMING
//     // ========================================================================

//     /**
//      * Crée le flux de streaming avec timeout et gestion erreurs
//      */
//     private Flux<String> createStreamingFlux(
//             String sessionId, 
//             String userId, 
//             String userMessage,
//             String enhancedPrompt, 
//             Instant start) {
        
//         return Flux.<String>create(sink -> {
//             StringBuilder fullResponse = new StringBuilder();
//             AtomicInteger tokenCounter = new AtomicInteger(0);
            
//             // Timeout avec scheduler
//             ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
//                 if (!sink.isCancelled()) {
//                     log.error("⏱️ [{}] Timeout streaming après {}s", 
//                               sessionId, streamTimeoutSeconds);
//                     sink.error(new TimeoutException(
//                         "Délai de réponse dépassé (" + streamTimeoutSeconds + "s)"
//                     ));
//                 }
//             }, streamTimeoutSeconds, TimeUnit.SECONDS);

//             // Handler de streaming LLM
//             streamingChatModel.chat(enhancedPrompt, new StreamingChatResponseHandler() {

//                 @Override
//                 public void onPartialResponse(String token) {
//                     if (token == null || token.isEmpty()) {
//                         return;
//                     }

//                     // Streaming tokens BRUTS (pas de manipulation)
//                     fullResponse.append(token);
//                     sink.next(token);

//                     // Logs sampling (tous les 100 tokens)
//                     int count = tokenCounter.incrementAndGet();
//                     if (log.isDebugEnabled() && count % 100 == 0) {
//                         log.debug("📊 [{}] {} tokens streamés", sessionId, count);
//                     }
//                 }

//                 @Override
//                 public void onCompleteResponse(ChatResponse response) {
//                     timeoutTask.cancel(false);
                    
//                     long durationMs = System.currentTimeMillis() - start.toEpochMilli();
//                     int totalTokens = tokenCounter.get();
//                     double tokensPerSecond = totalTokens / Math.max(durationMs / 1000.0, 0.1);

//                     log.info("✅ [{}] Streaming terminé - {} chars, {} tokens en {}ms ({:.1f} tokens/s)",
//                             sessionId, fullResponse.length(), totalTokens, 
//                             durationMs, tokensPerSecond);

//                     // Sauvegarder contexte dans Redis
//                     updateConversationContext(userId, userMessage, fullResponse.toString());

//                     if (log.isDebugEnabled()) {
//                         log.debug("📝 [{}] Réponse:\n{}",
//                                 sessionId, truncate(fullResponse.toString(), 200));
//                     }

//                     sink.complete();
//                 }

//                 @Override
//                 public void onError(Throwable error) {
//                     timeoutTask.cancel(false);
                    
//                     log.error("❌ [{}] Erreur streaming", sessionId, error);
//                     sink.error(new RuntimeException(
//                             "Erreur lors de la génération de la réponse: " + error.getMessage(),
//                             error
//                     ));
//                 }
//             });
//         })
//         .timeout(Duration.ofSeconds(streamTimeoutSeconds + 5))
//         .doOnCancel(() -> log.warn("🚫 [{}] Streaming annulé par utilisateur", sessionId))
//         .doOnError(TimeoutException.class, e -> 
//             log.error("⏱️ [{}] Timeout Reactor backup", sessionId));
//     }

//     // ========================================================================
//     // PRIVATE - PROMPT BUILDING (✅ ENRICHI v2.2)
//     // ========================================================================

//     /**
//      * ✅ ENRICHI v2.2: Construction prompt avec métriques enrichies
//      * 
//      * Construit un prompt enrichi contenant:
//      * 1. Instructions système
//      * 2. Contexte conversation (Redis)
//      * 3. Documents pertinents (RAG texte) avec métriques
//      * 4. Images pertinentes (RAG image) avec métriques
//      * 5. Warning si qualité faible
//      * 6. Question utilisateur
//      * 
//      * @param userId ID utilisateur pour contexte Redis
//      * @param userMessage Question utilisateur
//      * @param sessionId ID session pour logs
//      * @return Prompt enrichi complet
//      */
//     private String buildEnhancedMultimodalPrompt(String userId, String userMessage, String sessionId) {
//         log.debug("🔨 [{}] Construction prompt multimodal pour: {}", 
//                   sessionId, truncate(userMessage, 50));

//         // ========================================
//         // 1. RÉCUPÉRATION DONNÉES
//         // ========================================
        
//         // Contexte conversation depuis Redis
//         ConversationContext context = getConversationContext(userId);
        
//         // Recherche RAG avec métriques
//         CacheableSearchResult searchResult = ragService.search(userMessage, 5, userId);

//         int totalDocs = searchResult.getTextResults() != null ? 
//                         searchResult.getTextResults().size() : 0;
//         int totalImages = searchResult.getImageResults() != null ? 
//                           searchResult.getImageResults().size() : 0;

//         // ✅ NOUVEAU v2.2: Logs enrichis avec métriques
//         logSearchMetrics(sessionId, searchResult, totalDocs, totalImages);

//         // ========================================
//         // 2. ANALYSE QUALITÉ
//         // ========================================
        
//         // ✅ NOUVEAU v2.2: Détection qualité résultats
//         QualityAnalysis quality = analyzeResultsQuality(searchResult);

//         // ========================================
//         // 3. CONSTRUCTION PROMPT
//         // ========================================
        
//         StringBuilder prompt = new StringBuilder();

//         // Instructions système
//         appendSystemInstructions(prompt);
        
//         // Contexte conversation
//         if (context != null && !context.isEmpty()) {
//             appendConversationContext(prompt, context);
//         }
        
//         // Documents RAG avec métriques
//         appendTextDocuments(prompt, searchResult.getTextResults(), quality.textAvgScore);
//         appendImageDocuments(prompt, searchResult.getImageResults(), quality.imageAvgScore);
        
//         // ✅ NOUVEAU v2.2: Warning si aucun résultat ou qualité faible
//         if (totalDocs == 0 && totalImages == 0) {
//             appendNoResultsMessage(prompt);
//         } else if (quality.isLowQuality) {
//             appendLowQualityWarning(prompt, quality);
//         }
        
//         // Question utilisateur
//         appendUserQuestion(prompt, userMessage);

//         return prompt.toString();
//     }

//     /**
//      * ✅ NOUVEAU v2.2: Logs enrichis avec métriques RAG
//      */
//     private void logSearchMetrics(String sessionId, CacheableSearchResult searchResult, 
//                                    int totalDocs, int totalImages) {
        
//         if (!metricsEnabled) {
//             log.info("📚 [{}] RAG: {} documents, {} images", sessionId, totalDocs, totalImages);
//             return;
//         }

//         // Métriques texte
//         if (searchResult.getTextMetrics() != null && totalDocs > 0) {
//             SearchMetrics textMetrics = searchResult.getTextMetrics();
//                 log.info("📚 [{}] RAG Texte: {} docs | Avg: {}% | Min: {}% | Max: {}% | {}ms",
//                     sessionId,
//                     totalDocs,
//                     textMetrics.getAverageScore() * 100,
//                     textMetrics.getMinScore() * 100,
//                     textMetrics.getMaxScore() * 100,
//                     textMetrics.getDurationMs());
//         } else if (totalDocs == 0) {
//             log.info("📚 [{}] RAG Texte: Aucun document trouvé", sessionId);
//         }
        
//         // Métriques images
//         if (searchResult.getImageMetrics() != null && totalImages > 0) {
//             SearchMetrics imageMetrics = searchResult.getImageMetrics();
//             log.info("🖼️ [{}] RAG Image: {} images  | Avg: {}% | Min: {}% | Max: {}% | {}ms",
//                     sessionId,
//                     totalImages,
//                     imageMetrics.getAverageScore() * 100,
//                     imageMetrics.getMinScore() * 100,
//                     imageMetrics.getMaxScore() * 100,
//                     imageMetrics.getDurationMs());
//         } else if (totalImages == 0) {
//             log.info("🖼️ [{}] RAG Image: Aucune image trouvée", sessionId);
//         }
        
//         // Cache hit indicator
//         if (searchResult.isWasCached()) {
//             log.debug("⚡ [{}] Résultats RAG servis depuis cache (instantané)", sessionId);
//         }
        
//         // Temps total
//         if (searchResult.getTotalDurationMs() > 0) {
//             log.debug("⏱️ [{}] RAG durée totale: {}ms", sessionId, searchResult.getTotalDurationMs());
//         }
//     }

//     /**
//      * ✅ NOUVEAU v2.2: Analyse de la qualité des résultats
//      */
//     private QualityAnalysis analyzeResultsQuality(CacheableSearchResult searchResult) {
//         QualityAnalysis analysis = new QualityAnalysis();
        
//         // Analyse texte
//         if (searchResult.getTextResults() != null && !searchResult.getTextResults().isEmpty()) {
//             List<SearchResultItem> textResults = searchResult.getTextResults();
            
//             analysis.textAvgScore = textResults.stream()
//                 .filter(item -> item.getScore() != null)
//                 .mapToDouble(SearchResultItem::getScore)
//                 .average()
//                 .orElse(0.0);
            
//             analysis.textMaxScore = textResults.stream()
//                 .filter(item -> item.getScore() != null)
//                 .mapToDouble(SearchResultItem::getScore)
//                 .max()
//                 .orElse(0.0);
            
//             analysis.hasTextResults = true;
//         }
        
//         // Analyse images
//         if (searchResult.getImageResults() != null && !searchResult.getImageResults().isEmpty()) {
//             List<SearchResultItem> imageResults = searchResult.getImageResults();
            
//             analysis.imageAvgScore = imageResults.stream()
//                 .filter(item -> item.getScore() != null)
//                 .mapToDouble(SearchResultItem::getScore)
//                 .average()
//                 .orElse(0.0);
            
//             analysis.imageMaxScore = imageResults.stream()
//                 .filter(item -> item.getScore() != null)
//                 .mapToDouble(SearchResultItem::getScore)
//                 .max()
//                 .orElse(0.0);
            
//             analysis.hasImageResults = true;
//         }
        
//         // Déterminer si qualité faible
//         double overallMaxScore = Math.max(analysis.textMaxScore, analysis.imageMaxScore);
//         analysis.isLowQuality = overallMaxScore > 0 && overallMaxScore < lowQualityThreshold;
        
//         return analysis;
//     }

//     /**
//      * Instructions système pour l'assistant
//      */
//     private void appendSystemInstructions(StringBuilder prompt) {
//         prompt.append("Tu es un assistant IA avancé avec accès aux documents uploadés.\n\n");

//         prompt.append("📋 TES CAPACITÉS:\n");
//         prompt.append("- Accès aux documents texte (PDF, Word, Excel, PowerPoint, TXT, etc.)\n");
//         prompt.append("- Accès aux images (avec descriptions IA)\n");
//         prompt.append("- Accès aux images extraites de PDF et documents Word\n");
//         prompt.append("- Recherche sémantique avancée avec scoring de pertinence\n\n");

//         prompt.append("🎯 RÈGLES IMPÉRATIVES:\n");
//         prompt.append("1. Réponds UNIQUEMENT avec les informations des documents fournis\n");
//         prompt.append("2. Si l'information n'est pas dans les documents, dis-le clairement\n");
//         prompt.append("3. Cite TOUJOURS tes sources: (Source: nom_fichier.ext)\n");
//         prompt.append("4. Pour les PDFs, ajoute le numéro de page: (Source: fichier.pdf, page 3)\n");
//         prompt.append("5. Tiens compte des scores de pertinence affichés\n");
//         prompt.append("6. Structure ta réponse avec des paragraphes et sauts de ligne\n");
//         prompt.append("7. Utilise le markdown:\n");
//         prompt.append("   - **Texte en gras** pour les titres importants\n");
//         prompt.append("   - Sauts de ligne entre les sections\n");
//         prompt.append("   - Listes à puces si pertinent\n");
//         prompt.append("   - Code blocks avec ``` si code présent\n\n");
//     }

//     /**
//      * Ajoute le contexte de conversation
//      */
//     private void appendConversationContext(StringBuilder prompt, ConversationContext context) {
//         prompt.append("💬 CONTEXTE CONVERSATION:\n");
//         prompt.append(context.getSummary());
//         prompt.append("\n\n");
//     }

//     /**
//      * ✅ ENRICHI v2.2: Ajoute documents texte avec score moyen
//      */
//     private void appendTextDocuments(StringBuilder prompt, List<SearchResultItem> textResults, 
//                                      double avgScore) {
//         if (textResults == null || textResults.isEmpty()) {
//             return;
//         }

//         prompt.append("═══════════════════════════════════════════════════════════\n");
//         prompt.append("📄 DOCUMENTS TEXTE DISPONIBLES\n");
        
//         // ✅ NOUVEAU v2.2: Score moyen affiché
//         if (metricsEnabled && avgScore > 0) {
//             prompt.append(String.format("(Score moyen de pertinence: %.1f%%)\n", avgScore * 100));
//         }
        
//         prompt.append("═══════════════════════════════════════════════════════════\n\n");

//         int docNum = 1;
//         for (SearchResultItem item : textResults) {
//             String source = item.getSource();
//             String type = item.getType();
//             Integer page = item.getPage();
//             String content = item.getContent();
//             Double score = item.getScore();

//             String formattedContent = formatTextPreservingMarkdown(content);

//             prompt.append(String.format("📄 DOCUMENT #%d", docNum));
//             if (score != null) {
//                 prompt.append(String.format(" (Pertinence: %.1f%%)", score * 100));
//             }
//             prompt.append("\n");
            
//             prompt.append(String.format("Fichier: %s\n", source != null ? source : "Inconnu"));

//             if (type != null) {
//                 prompt.append(String.format("Type: %s\n", formatDocumentType(type)));
//             }
//             if (page != null) {
//                 prompt.append(String.format("Page: %d\n", page));
//             }

//             prompt.append("───────────────────────────────────────────────────────────\n");
//             prompt.append("CONTENU:\n");
//             prompt.append(formattedContent);
//             prompt.append("\n═══════════════════════════════════════════════════════════\n\n");

//             docNum++;
//         }
//     }

//     /**
//      * ✅ ENRICHI v2.2: Ajoute images avec score moyen
//      */
//     private void appendImageDocuments(StringBuilder prompt, List<SearchResultItem> imageResults,
//                                       double avgScore) {
//         if (imageResults == null || imageResults.isEmpty()) {
//             return;
//         }

//         prompt.append("═══════════════════════════════════════════════════════════\n");
//         prompt.append("🖼️ IMAGES DISPONIBLES\n");
        
//         // ✅ NOUVEAU v2.2: Score moyen affiché
//         if (metricsEnabled && avgScore > 0) {
//             prompt.append(String.format("(Score moyen de pertinence: %.1f%%)\n", avgScore * 100));
//         }
        
//         prompt.append("═══════════════════════════════════════════════════════════\n\n");

//         int imgNum = 1;
//         for (SearchResultItem item : imageResults) {
//             String source = item.getSource();
//             String type = item.getType();
//             Integer page = item.getPage();
//             String imagePath = item.getImagePath();
//             String description = item.getContent();
//             Double score = item.getScore();

//             prompt.append(String.format("🖼️ IMAGE #%d", imgNum));
//             if (score != null) {
//                 prompt.append(String.format(" (Pertinence: %.1f%%)", score * 100));
//             }
//             prompt.append("\n");

//             if (source != null) {
//                 prompt.append(String.format("Fichier source: %s\n", source));
//             }
//             if (type != null) {
//                 prompt.append(String.format("Type: %s\n", formatImageSource(type)));
//             }
//             if (page != null) {
//                 prompt.append(String.format("Page: %d\n", page));
//             }
//             if (imagePath != null) {
//                 prompt.append(String.format("Chemin: %s\n", imagePath));
//             }

//             prompt.append("───────────────────────────────────────────────────────────\n");
//             prompt.append("DESCRIPTION:\n");
//             prompt.append(formatTextPreservingMarkdown(description));
//             prompt.append("\n═══════════════════════════════════════════════════════════\n\n");

//             imgNum++;
//         }
//     }

//     /**
//      * Message si aucun résultat RAG
//      */
//     private void appendNoResultsMessage(StringBuilder prompt) {
//         prompt.append("⚠️ AUCUN DOCUMENT PERTINENT TROUVÉ\n\n");
//         prompt.append("Aucun document ne correspond à la recherche.\n");
//         prompt.append("Informe l'utilisateur qu'il doit uploader des fichiers pertinents.\n\n");
//     }

//     /**
//      * ✅ NOUVEAU v2.2: Warning si qualité faible
//      */
//     private void appendLowQualityWarning(StringBuilder prompt, QualityAnalysis quality) {
//         prompt.append("═══════════════════════════════════════════════════════════\n");
//         prompt.append("⚠️ ATTENTION: PERTINENCE FAIBLE\n");
//         prompt.append("═══════════════════════════════════════════════════════════\n\n");
        
//         prompt.append("Les documents trouvés ont un score de pertinence relativement faible:\n");
        
//         if (quality.hasTextResults) {
//             prompt.append(String.format("- Documents texte: score max %.1f%%\n", 
//                 quality.textMaxScore * 100));
//         }
        
//         if (quality.hasImageResults) {
//             prompt.append(String.format("- Images: score max %.1f%%\n", 
//                 quality.imageMaxScore * 100));
//         }
        
//         prompt.append("\n");
//         prompt.append("INSTRUCTIONS SPÉCIALES:\n");
//         prompt.append("1. Mentionne cette limitation dans ta réponse\n");
//         prompt.append("2. Sois prudent dans tes conclusions\n");
//         prompt.append("3. Suggère à l'utilisateur d'affiner sa question ou d'uploader des documents plus pertinents\n");
//         prompt.append("4. Ne force pas de connexion artificielle entre les documents et la question\n\n");
//     }

//     /**
//      * Ajoute la question utilisateur
//      */
//     private void appendUserQuestion(StringBuilder prompt, String userMessage) {
//         prompt.append("═══════════════════════════════════════════════════════════\n");
//         prompt.append("❓ QUESTION DE L'UTILISATEUR\n");
//         prompt.append("═══════════════════════════════════════════════════════════\n\n");
//         prompt.append(userMessage);
//         prompt.append("\n\n");

//         prompt.append("═══════════════════════════════════════════════════════════\n");
//         prompt.append("✍️ TA RÉPONSE (en français, bien formatée avec markdown)\n");
//         prompt.append("═══════════════════════════════════════════════════════════\n\n");

//         prompt.append("Réponds maintenant en utilisant UNIQUEMENT les informations ");
//         prompt.append("des documents ci-dessus. Structure bien ta réponse avec markdown et cite tes sources.\n\n");
//     }

//     // ========================================================================
//     // PRIVATE - TEXT FORMATTING
//     // ========================================================================

//     /**
//      * Formatage préserve markdown et structure
//      */
//     private String formatTextPreservingMarkdown(String text) {
//         if (text == null || text.isEmpty()) {
//             return "";
//         }

//         String result = text;

//         // Corrections ponctuation
//         result = result.replaceAll("\\.([A-ZÀ-Ú])", ". $1");
//         result = result.replaceAll(",([A-Za-zÀ-ú])", ", $1");
//         result = result.replaceAll(":([A-Za-zÀ-ú])", ": $1");
//         result = result.replaceAll(";([A-Za-zÀ-ú])", "; $1");
//         result = result.replaceAll("\\?([A-Za-zÀ-ú])", "? $1");
//         result = result.replaceAll("!([A-Za-zÀ-ú])", "! $1");
//         result = result.replaceAll("\\)([A-Za-zÀ-ú])", ") $1");
//         result = result.replaceAll("([A-Za-zÀ-ú])\\(", "$1 (");
//         result = result.replaceAll("([a-zà-ú])([A-ZÀ-Ú])", "$1 $2");
//         result = result.replaceAll("(\\d)([A-Za-zÀ-ú])", "$1 $2");

//         // Préserver sauts de ligne pour markdown
//         result = result.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
//         result = result.replaceAll("\\n{3,}", "\n\n");
//         result = result.replaceAll(" +([.,;:!?])", "$1");
//         result = result.trim();

//         // Limiter longueur
//         if (result.length() > 2000) {
//             result = result.substring(0, 1997) + "...";
//         }

//         return result;
//     }

//     /**
//      * Formater le type de document
//      */
//     private String formatDocumentType(String type) {
//         if (type == null) return "Inconnu";

//         String typeLower = type.toLowerCase();
        
//         if (typeLower.contains("pdf")) {
//             if (typeLower.contains("rendered")) return "PDF (rendu page)";
//             if (typeLower.contains("embedded")) return "PDF (image extraite)";
//             if (typeLower.contains("page")) return "PDF (texte page)";
//             return "PDF";
//         }
        
//         if (typeLower.contains("docx") || typeLower.contains("word")) return "Microsoft Word";
//         if (typeLower.contains("xlsx") || typeLower.contains("excel")) return "Microsoft Excel";
//         if (typeLower.contains("pptx") || typeLower.contains("powerpoint")) return "Microsoft PowerPoint";
        
//         if (typeLower.contains("text") || typeLower.equals("txt")) return "Fichier texte";
//         if (typeLower.equals("md")) return "Markdown";
//         if (typeLower.contains("image")) return "Image";
        
//         return type;
//     }
    
//     /**
//      * Formater source image
//      */
//     private String formatImageSource(String source) {
//         if (source == null) return "Inconnu";
        
//         return switch (source.toLowerCase()) {
//             case "pdf_embedded" -> "PDF (image intégrée)";
//             case "pdf_rendered" -> "PDF (page rendue)";
//             case "docx" -> "Word";
//             case "docx_header" -> "Word (en-tête)";
//             case "docx_footer" -> "Word (pied de page)";
//             case "standalone" -> "Image uploadée";
//             default -> source;
//         };
//     }

//     // ========================================================================
//     // PRIVATE - CONVERSATION CONTEXT (Redis)
//     // ========================================================================

//     /**
//      * Met à jour le contexte de conversation dans Redis
//      */
//     private void updateConversationContext(String userId, String question, String response) {
//         try {
//             String cacheKey = "conversation:" + userId;
            
//             ConversationContext context = contextRedisTemplate.opsForValue().get(cacheKey);
//             if (context == null) {
//                 context = new ConversationContext();
//             }
            
//             context.addExchange(question, response);
//             context.smartTrim(maxContextExchanges, maxContextTokens);
            
//             contextRedisTemplate.opsForValue().set(
//                 cacheKey, 
//                 context, 
//                 Duration.ofHours(contextTtlHours)
//             );
            
//             log.debug("💾 [Assistant] Contexte sauvegardé Redis: {} ({} échanges, ~{} tokens)", 
//                       cacheKey, context.getExchangeCount(), context.estimateTokens());
                      
//         } catch (Exception e) {
//             log.error("❌ [Assistant] Erreur sauvegarde contexte Redis", e);
//         }
//     }
    
//     /**
//      * Récupère le contexte de conversation depuis Redis
//      */
//     private ConversationContext getConversationContext(String userId) {
//         try {
//             String cacheKey = "conversation:" + userId;
//             ConversationContext context = contextRedisTemplate.opsForValue().get(cacheKey);
            
//             if (context != null) {
//                 log.debug("✅ [Assistant] Contexte récupéré Redis: {} ({} échanges)", 
//                           cacheKey, context.getExchangeCount());
//             }
            
//             return context != null ? context : new ConversationContext();
            
//         } catch (Exception e) {
//             log.error("❌ [Assistant] Erreur récupération contexte Redis", e);
//             return new ConversationContext();
//         }
//     }

//     // ========================================================================
//     // UTILITY METHODS
//     // ========================================================================

//     /**
//      * Tronque le texte pour les logs
//      */
//     private String truncate(String text, int maxLength) {
//         if (text == null) return "null";
//         if (text.length() <= maxLength) return text;
//         return text.substring(0, maxLength - 3) + "...";
//     }

//     // ========================================================================
//     // INNER CLASSES
//     // ========================================================================
    
//     /**
//      * ✅ NOUVEAU v2.2: Analyse qualité résultats
//      */
//     private static class QualityAnalysis {
//         boolean hasTextResults = false;
//         boolean hasImageResults = false;
//         double textAvgScore = 0.0;
//         double textMaxScore = 0.0;
//         double imageAvgScore = 0.0;
//         double imageMaxScore = 0.0;
//         boolean isLowQuality = false;
//     }
    
//     /**
//      * Contexte de conversation sérialisable pour Redis
//      */
//     @Data
//     @JsonIgnoreProperties(ignoreUnknown = true)
//     public static class ConversationContext implements Serializable {
//         private static final long serialVersionUID = 1L;
        
//         private Deque<Exchange> exchanges = new LinkedList<>();

//         public void addExchange(String question, String response) {
//             exchanges.addLast(new Exchange(question, response, Instant.now()));
//         }

//         /**
//          * Trim intelligent basé sur échanges ET tokens
//          */
//         public void smartTrim(int maxExchanges, int maxTokens) {
//             // Trim par nombre d'échanges
//             while (exchanges.size() > maxExchanges) {
//                 exchanges.removeFirst();
//             }
            
//             // Trim par tokens estimés
//             while (estimateTokens() > maxTokens && !exchanges.isEmpty()) {
//                 exchanges.removeFirst();
//             }
//         }
        
//         /**
//          * Estimation tokens (1 token ≈ 4 chars)
//          */
//         public int estimateTokens() {
//             int totalChars = exchanges.stream()
//                 .mapToInt(e -> e.question.length() + e.response.length())
//                 .sum();
//             return totalChars / 4;
//         }

//         public String getSummary() {
//             if (exchanges.isEmpty()) return "";

//             StringBuilder summary = new StringBuilder();
//             int num = 1;

//             for (Exchange exchange : exchanges) {
//                 summary.append(String.format("Échange %d:\n", num++));
//                 summary.append(String.format("Q: %s\n", truncateText(exchange.question, 100)));
//                 summary.append(String.format("R: %s\n\n", truncateText(exchange.response, 150)));
//             }

//             return summary.toString();
//         }

//         public int getExchangeCount() {
//             return exchanges.size();
//         }

//         public boolean isEmpty() {
//             return exchanges.isEmpty();
//         }

//         private static String truncateText(String text, int maxLength) {
//             if (text == null || text.length() <= maxLength) return text;
//             return text.substring(0, maxLength - 3) + "...";
//         }

//         /**
//          * Exchange sérialisable
//          */
//         @Data
//         public static class Exchange implements Serializable {
//             private static final long serialVersionUID = 1L;
            
//             private String question;
//             private String response;
//             private Instant timestamp;
            
//             public Exchange() {}
            
//             public Exchange(String question, String response, Instant timestamp) {
//                 this.question = question;
//                 this.response = response;
//                 this.timestamp = timestamp;
//             }
//         }
//     }
    
//     /**
//      * Exception rate limiting
//      */
//     public static class RateLimitException extends RuntimeException {
//         public RateLimitException(String message) {
//             super(message);
//         }
//     }
// }

// /*
//  * ============================================================================
//  * CHANGELOG v2.2 - Enhanced with Metrics (Approche A)
//  * ============================================================================
//  * 
//  * ✅ NOUVELLES FONCTIONNALITÉS
//  * 
//  * 1. LOGS ENRICHIS
//  *    - Scores moyens, min, max affichés
//  *    - Durées RAG séparées (texte/image)
//  *    - Indicateur cache hit (⚡)
//  *    - Temps total end-to-end
//  * 
//  * 2. MÉTRIQUES DANS PROMPT
//  *    - Score moyen de pertinence affiché dans prompt
//  *    - LLM conscient de la qualité des résultats
//  *    - Peut influencer le ton de sa réponse
//  * 
//  * 3. DÉTECTION QUALITÉ
//  *    - QualityAnalysis: analyse automatique des scores
//  *    - Warning si tous résultats < threshold (0.5 par défaut)
//  *    - LLM informé de la limitation
//  * 
//  * 4. WARNING SCORES FAIBLES
//  *    - Message explicite dans prompt
//  *    - Instructions spéciales pour LLM
//  *    - Suggestion d'affiner la recherche
//  * 
//  * 5. CONFIGURATION ENRICHIE
//  *    - assistant.rag.low-quality-threshold=0.5
//  *    - assistant.metrics.enabled=true
//  * 
//  * ✅ COMPATIBILITÉ
//  *    - 100% compatible v2.1 (backward compatible)
//  *    - Fonctionne avec MultimodalRAGService v3.3
//  *    - Compatible CacheableSearchResult enrichi
//  * 
//  * ✅ EXEMPLES LOGS v2.2
//  * 
//  * ```
//  * 📚 [session_123] RAG Texte: 5 docs | Avg: 87.3% | Min: 65.2% | Max: 95.1% | 45ms
//  * 🖼️ [session_123] RAG Image: 3 images | Avg: 72.5% | Min: 60.8% | Max: 85.3% | 32ms
//  * ⚡ [session_123] Résultats RAG servis depuis cache (instantané)
//  * ⏱️ [session_123] RAG durée totale: 77ms
//  * ```
//  * 
//  * ✅ EXEMPLE PROMPT v2.2
//  * 
//  * ```
//  * ═══════════════════════════════════════════════════════════
//  * 📄 DOCUMENTS TEXTE DISPONIBLES
//  * (Score moyen de pertinence: 87.3%)
//  * ═══════════════════════════════════════════════════════════
//  * 
//  * 📄 DOCUMENT #1 (Pertinence: 95.1%)
//  * Fichier: rapport_Q4.pdf
//  * Type: PDF
//  * Page: 3
//  * ...
//  * ```
//  * 
//  * ✅ EXEMPLE WARNING QUALITÉ FAIBLE
//  * 
//  * ```
//  * ═══════════════════════════════════════════════════════════
//  * ⚠️ ATTENTION: PERTINENCE FAIBLE
//  * ═══════════════════════════════════════════════════════════
//  * 
//  * Les documents trouvés ont un score de pertinence relativement faible:
//  * - Documents texte: score max 45.2%
//  * - Images: score max 38.7%
//  * 
//  * INSTRUCTIONS SPÉCIALES:
//  * 1. Mentionne cette limitation dans ta réponse
//  * 2. Sois prudent dans tes conclusions
//  * 3. Suggère à l'utilisateur d'affiner sa question
//  * ```
//  * 
//  * ============================================================================
//  * MIGRATION v2.1 → v2.2
//  * ============================================================================
//  * 
//  * Étape 1: Backup
//  * ```bash
//  * cp ConversationalAssistant.java ConversationalAssistant_v2.1.backup
//  * ```
//  * 
//  * Étape 2: Remplacer
//  * ```bash
//  * cp ConversationalAssistant_v2.2.java ConversationalAssistant.java
//  * ```
//  * 
//  * Étape 3: Configuration (optionnel)
//  * ```yaml
//  * # application.yml
//  * assistant:
//  *   rag:
//  *     low-quality-threshold: 0.5  # Seuil scores faibles (0-1)
//  *   metrics:
//  *     enabled: true  # Activer métriques enrichies
//  * ```
//  * 
//  * Étape 4: Tester
//  * ```bash
//  * mvn test -Dtest=ConversationalAssistantTest
//  * ```
//  * 
//  * ============================================================================
//  * IMPACT PERFORMANCE v2.2
//  * ============================================================================
//  * 
//  * - Calcul métriques: +1ms (calcul avg/min/max scores)
//  * - Logs enrichis: 0ms (asynchrone)
//  * - Warning qualité: +1ms (analyse scores)
//  * 
//  * TOTAL: +2ms sur ~200-500ms (négligeable <1%)
//  * 
//  * ============================================================================
//  * BENEFITS v2.2
//  * ============================================================================
//  * 
//  * ✅ Meilleure observabilité (logs détaillés)
//  * ✅ Réponses LLM plus nuancées (conscient qualité)
//  * ✅ Détection problèmes recherche (scores faibles)
//  * ✅ Monitoring cache hit/miss
//  * ✅ Debug facilité (métriques complètes)
//  * ✅ UX améliorée (suggestions pertinentes)
//  */