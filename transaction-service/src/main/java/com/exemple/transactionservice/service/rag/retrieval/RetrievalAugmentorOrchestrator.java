package com.exemple.transactionservice.service.rag.retrieval;

import com.exemple.transactionservice.service.rag.retrieval.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Orchestrateur principal du Retrieval Augmentor
 * 
 * Coordonne les 5 composants:
 * 1. Query Transformer
 * 2. Query Router
 * 3. Parallel Retrievers
 * 4. Content Aggregator (RRF + Reranking)
 * 5. Content Injector
 * 
 * Pipeline complet:
 * Query → Transform → Route → Retrieve (parallel) → Aggregate → Inject → Prompt
 */
@Slf4j
@Service
public class RetrievalAugmentorOrchestrator {
    
    private final QueryTransformerService queryTransformer;
    private final QueryRouterService queryRouter;
    private final ParallelRetrieverService parallelRetriever;
    private final ContentAggregatorService contentAggregator;
    private final ContentInjectorService contentInjector;
    
    public RetrievalAugmentorOrchestrator(
            QueryTransformerService queryTransformer,
            QueryRouterService queryRouter,
            ParallelRetrieverService parallelRetriever,
            ContentAggregatorService contentAggregator,
            ContentInjectorService contentInjector) {
        
        this.queryTransformer = queryTransformer;
        this.queryRouter = queryRouter;
        this.parallelRetriever = parallelRetriever;
        this.contentAggregator = contentAggregator;
        this.contentInjector = contentInjector;
        
        log.info("✅ RetrievalAugmentorOrchestrator initialisé");
    }
    
    /**
     * Execute le pipeline complet
     * 
     * @param query Query utilisateur
     * @return Résultat complet avec prompt injecté + métadonnées
     */
    public RetrievalAugmentorResult execute(String query) {
        long startTime = System.currentTimeMillis();
        
        log.info("🚀 ========== RETRIEVAL AUGMENTOR START ==========");
        log.info("📝 Query: {}", query);
        
        RetrievalAugmentorResult.RetrievalAugmentorResultBuilder resultBuilder = 
            RetrievalAugmentorResult.builder()
                .originalQuery(query);
        
        try {
            // ========== STEP 1: QUERY TRANSFORMER ==========
            log.info("⭐ [1/5] Query Transformer...");
            QueryTransformResult transformResult = queryTransformer.transform(query);
            resultBuilder.transformResult(transformResult);
            
            log.info("✅ [1/5] Transformed: {} → {} variants", 
                query, transformResult.getVariants().size());
            
            // ========== STEP 2: QUERY ROUTER ==========
            log.info("🔀 [2/5] Query Router...");
            RoutingDecision routingDecision = queryRouter.route(query);
            resultBuilder.routingDecision(routingDecision);
            
            log.info("✅ [2/5] Routed: strategy={}, confidence={}", 
                routingDecision.getStrategy(), 
                String.format("%.2f", routingDecision.getConfidence()));
            
            // ========== STEP 3: PARALLEL RETRIEVERS ==========
            log.info("🚀 [3/5] Parallel Retrievers...");
            Map<String, RetrievalResult> retrievalResults = 
                parallelRetriever.retrieveParallel(
                    transformResult.getVariants(), 
                    routingDecision
                );
            resultBuilder.retrievalResults(retrievalResults);
            
            int totalChunks = retrievalResults.values().stream()
                .mapToInt(RetrievalResult::getTotalFound)
                .sum();
            
            log.info("✅ [3/5] Retrieved: {} chunks from {} retrievers", 
                totalChunks, retrievalResults.size());
            
            // ========== STEP 4: CONTENT AGGREGATOR ==========
            log.info("🎯 [4/5] Content Aggregator (RRF + Reranking)...");
            AggregatedContext aggregatedContext = 
                contentAggregator.aggregate(retrievalResults, query);
            resultBuilder.aggregatedContext(aggregatedContext);
            
            log.info("✅ [4/5] Aggregated: {} → {} final chunks (method={})", 
                aggregatedContext.getInputChunks(),
                aggregatedContext.getFinalSelected(),
                aggregatedContext.getFusionMethod());
            
            // ========== STEP 5: CONTENT INJECTOR ==========
            log.info("💉 [5/5] Content Injector...");
            InjectedPrompt injectedPrompt = 
                contentInjector.injectContext(aggregatedContext, query);
            resultBuilder.injectedPrompt(injectedPrompt);
            
            log.info("✅ [5/5] Injected: {} tokens ({:.1f}% context), {} sources", 
                injectedPrompt.getStructure().getTotalTokens(),
                injectedPrompt.getContextUsagePercent(),
                injectedPrompt.getSources().size());
            
            // ========== FINALIZE ==========
            long totalDuration = System.currentTimeMillis() - startTime;
            resultBuilder
                .success(true)
                .totalDurationMs(totalDuration);
            
            RetrievalAugmentorResult result = resultBuilder.build();
            
            log.info("✅ ========== RETRIEVAL AUGMENTOR COMPLETE ==========");
            log.info("📊 Total: {}ms | Transform={}ms | Retrieval={}ms | Aggregation={}ms | Injection={}ms", 
                totalDuration,
                transformResult.getDurationMs(),
                retrievalResults.values().stream()
                    .mapToLong(RetrievalResult::getDurationMs)
                    .max().orElse(0),
                aggregatedContext.getDurationMs(),
                injectedPrompt.getDurationMs());
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Retrieval Augmentor failed", e);
            
            long duration = System.currentTimeMillis() - startTime;
            
            return resultBuilder
                .success(false)
                .errorMessage(e.getMessage())
                .totalDurationMs(duration)
                .build();
        }
    }
    
    /**
     * Résultat complet du Retrieval Augmentor
     */
    @lombok.Data
    @lombok.Builder
    public static class RetrievalAugmentorResult {
        private String originalQuery;
        private boolean success;
        private String errorMessage;
        
        // Results par étape
        private QueryTransformResult transformResult;
        private RoutingDecision routingDecision;
        private Map<String, RetrievalResult> retrievalResults;
        private AggregatedContext aggregatedContext;
        private InjectedPrompt injectedPrompt;
        
        // Métriques
        private long totalDurationMs;
        
        /**
         * Shortcut: récupère le prompt final
         */
        public String getFinalPrompt() {
            return injectedPrompt != null ? injectedPrompt.getFullPrompt() : null;
        }
        
        /**
         * Shortcut: récupère les sources
         */
        public List<InjectedPrompt.SourceReference> getSources() {
            return injectedPrompt != null ? injectedPrompt.getSources() : List.of();
        }
    }
}