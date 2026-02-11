package com.exemple.transactionservice.service.rag.retrieval;

import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
import com.exemple.transactionservice.service.rag.retrieval.model.*;
import com.exemple.transactionservice.service.rag.retrieval.aggregator.ContentAggregatorService;
import com.exemple.transactionservice.service.rag.retrieval.injector.ContentInjectorService;
import com.exemple.transactionservice.service.rag.retrieval.query.QueryRouterService;
import com.exemple.transactionservice.service.rag.retrieval.query.QueryTransformerService;
import com.exemple.transactionservice.service.rag.retrieval.retriever.ParallelRetrieverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Orchestrateur du Retrieval Augmentor
 * 
 * ✅ VERSION UNIFIÉE - Compatible avec AVANT + RAGMetrics
 * 
 * Pipeline:
 * 1. Query Transformation
 * 2. Query Routing
 * 3. Parallel Retrieval
 * 4. Content Aggregation (RRF + Reranking)
 * 5. Content Injection
 */
@Slf4j
@Service
public class RetrievalAugmentorOrchestrator {
    
    private final QueryTransformerService queryTransformer;
    private final QueryRouterService queryRouter;
    private final ParallelRetrieverService parallelRetriever;
    private final ContentAggregatorService contentAggregator;
    private final ContentInjectorService contentInjector;
    private final RAGMetrics ragMetrics;
    
    public RetrievalAugmentorOrchestrator(
            QueryTransformerService queryTransformer,
            QueryRouterService queryRouter,
            ParallelRetrieverService parallelRetriever,
            ContentAggregatorService contentAggregator,
            ContentInjectorService contentInjector,
            RAGMetrics ragMetrics) {
        
        this.queryTransformer = queryTransformer;
        this.queryRouter = queryRouter;
        this.parallelRetriever = parallelRetriever;
        this.contentAggregator = contentAggregator;
        this.contentInjector = contentInjector;
        this.ragMetrics = ragMetrics;
        
        log.info("✅ RetrievalAugmentorOrchestrator initialisé avec RAGMetrics");
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
            long transformStart = System.currentTimeMillis();
            
            QueryTransformResult transformResult = queryTransformer.transform(query);
            resultBuilder.transformResult(transformResult);
            
            long transformDuration = System.currentTimeMillis() - transformStart;
            
            // ✅ MÉTRIQUE: Query transformation
            ragMetrics.recordQueryTransformation(
                transformDuration,
                transformResult.getVariants().size()
            );
            
            log.info("✅ [1/5] Transformed: {} → {} variants", 
                query, transformResult.getVariants().size());
            
            // ========== STEP 2: QUERY ROUTER ==========
            log.info("🔀 [2/5] Query Router...");
            long routingStart = System.currentTimeMillis();
            
            RoutingDecision routingDecision = queryRouter.route(query);
            resultBuilder.routingDecision(routingDecision);
            
            long routingDuration = System.currentTimeMillis() - routingStart;
            
            // ✅ MÉTRIQUE: Routing decision
            ragMetrics.recordRoutingDecision(
                routingDecision.getStrategy().name(),
                routingDecision.getConfidence()
            );
            
            log.info("✅ [2/5] Routed: strategy={}, confidence={}", 
                routingDecision.getStrategy(), 
                String.format("%.2f", routingDecision.getConfidence()));
            
            // ========== STEP 3: PARALLEL RETRIEVERS ==========
            log.info("🚀 [3/5] Parallel Retrievers...");
            long retrievalStart = System.currentTimeMillis();
            
            // ✅ CONSERVE LA SIGNATURE ORIGINALE: Map<String, RetrievalResult>
            Map<String, RetrievalResult> retrievalResults = 
                parallelRetriever.retrieveParallel(
                    transformResult.getVariants(), 
                    routingDecision
                );
            resultBuilder.retrievalResults(retrievalResults);
            
            long retrievalDuration = System.currentTimeMillis() - retrievalStart;
            
            int totalChunks = retrievalResults.values().stream()
                .mapToInt(RetrievalResult::getTotalFound)
                .sum();
            
            // ✅ MÉTRIQUE: Retrieval par retriever
            retrievalResults.forEach((retriever, result) -> {
                ragMetrics.recordRetrieval(
                    retriever,
                    result.getDurationMs(),
                    result.getTotalFound()
                );
            });
            
            log.info("✅ [3/5] Retrieved: {} chunks from {} retrievers", 
                totalChunks, retrievalResults.size());
            
            // ========== STEP 4: CONTENT AGGREGATOR ==========
            log.info("🎯 [4/5] Content Aggregator (RRF + Reranking)...");
            long aggregationStart = System.currentTimeMillis();
            
            // ✅ CONSERVE LA SIGNATURE ORIGINALE: aggregate(Map, query)
            AggregatedContext aggregatedContext = 
                contentAggregator.aggregate(retrievalResults, query);
            resultBuilder.aggregatedContext(aggregatedContext);
            
            long aggregationDuration = System.currentTimeMillis() - aggregationStart;
            
            // ✅ MÉTRIQUE: Aggregation
            ragMetrics.recordAggregation(
                aggregationDuration,
                aggregatedContext.getInputChunks(),
                aggregatedContext.getFinalSelected()
            );
            
            log.info("✅ [4/5] Aggregated: {} → {} final chunks (method={})", 
                aggregatedContext.getInputChunks(),
                aggregatedContext.getFinalSelected(),
                aggregatedContext.getFusionMethod());
            
            // ========== STEP 5: CONTENT INJECTOR ==========
            log.info("💉 [5/5] Content Injector...");
            long injectionStart = System.currentTimeMillis();
            
            // ✅ CONSERVE LA SIGNATURE ORIGINALE: injectContext(context, query)
            InjectedPrompt injectedPrompt = 
                contentInjector.injectContext(aggregatedContext, query);
            resultBuilder.injectedPrompt(injectedPrompt);
            
            long injectionDuration = System.currentTimeMillis() - injectionStart;
            
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
            log.info("📊 Total: {}ms | Transform={}ms | Routing={}ms | Retrieval={}ms | Aggregation={}ms | Injection={}ms", 
                totalDuration,
                transformDuration,
                routingDuration,
                retrievalDuration,
                aggregationDuration,
                injectionDuration);
            
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
     * 
     * ✅ CONSERVE LA STRUCTURE ORIGINALE
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
        
        // ✅ CONSERVE: Map<String, RetrievalResult>
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