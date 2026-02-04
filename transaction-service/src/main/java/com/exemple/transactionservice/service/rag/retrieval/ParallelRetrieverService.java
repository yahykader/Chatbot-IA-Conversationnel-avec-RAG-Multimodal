package com.exemple.transactionservice.service.rag.retrieval;

import com.exemple.transactionservice.config.RetrievalConfig;
import com.exemple.transactionservice.service.rag.retrieval.model.RetrievalResult;
import com.exemple.transactionservice.service.rag.retrieval.model.RoutingDecision;
import com.exemple.transactionservice.service.rag.retrieval.retriever.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service d'orchestration des retrievers en parallèle
 * 
 * Exécute plusieurs retrievers simultanément pour maximiser coverage et minimiser latence
 */
@Slf4j
@Service
public class ParallelRetrieverService {
    
    private final Map<String, Retriever> retrievers;
    private final RetrievalConfig config;
    
    public ParallelRetrieverService(
            TextVectorRetriever textRetriever,
            ImageVectorRetriever imageRetriever,
            BM25Retriever bm25Retriever,
            RetrievalConfig config) {
        
        this.config = config;
        this.retrievers = new HashMap<>();
        this.retrievers.put("text", textRetriever);
        this.retrievers.put("image", imageRetriever);
        this.retrievers.put("bm25", bm25Retriever);
        
        log.info("✅ ParallelRetrieverService initialisé avec {} retrievers", 
            retrievers.size());
    }
    
    /**
     * Execute retrievers en parallèle selon la routing decision
     */
    public Map<String, RetrievalResult> retrieveParallel(
            List<String> queries,
            RoutingDecision routingDecision) {
        
        long startTime = System.currentTimeMillis();
        
        log.info("🚀 Launching parallel retrieval: {} retrievers, {} queries", 
            countEnabledRetrievers(routingDecision), queries.size());
        
        // Créer les futures pour chaque retriever activé
        Map<String, CompletableFuture<RetrievalResult>> futures = new HashMap<>();
        
        routingDecision.getRetrievers().forEach((retrieverName, retrieverConfig) -> {
            if (retrieverConfig.isEnabled()) {
                Retriever retriever = retrievers.get(retrieverName);
                
                if (retriever != null) {
                    CompletableFuture<RetrievalResult> future = 
                        retriever.retrieveAsync(queries, retrieverConfig.getTopK());
                    
                    futures.put(retrieverName, future);
                    
                    log.debug("📤 Launched: {} (topK={})", 
                        retrieverName, retrieverConfig.getTopK());
                } else {
                    log.warn("⚠️ Retriever not found: {}", retrieverName);
                }
            }
        });
        
        // Attendre tous les résultats (avec timeout)
        Map<String, RetrievalResult> results = new HashMap<>();
        
        try {
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.values().toArray(new CompletableFuture[0])
            );
            
            // Timeout global
            allOf.get(config.getRetrievers().getParallelTimeout(), TimeUnit.MILLISECONDS);
            
            // Collecter résultats
            for (Map.Entry<String, CompletableFuture<RetrievalResult>> entry : futures.entrySet()) {
                try {
                    RetrievalResult result = entry.getValue().get();
                    results.put(entry.getKey(), result);
                    
                    log.info("✅ [{}] Retrieved {} chunks (top={}, {}ms)", 
                        entry.getKey(), 
                        result.getTotalFound(),
                        String.format("%.3f", result.getTopScore()),
                        result.getDurationMs());
                    
                } catch (Exception e) {
                    log.error("❌ [{}] Failed", entry.getKey(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Parallel retrieval timeout or error", e);
            
            // Collecter ce qui est disponible
            futures.forEach((name, future) -> {
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    try {
                        results.put(name, future.get());
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        
        long totalDuration = System.currentTimeMillis() - startTime;
        int totalChunks = results.values().stream()
            .mapToInt(RetrievalResult::getTotalFound)
            .sum();
        
        log.info("✅ Parallel retrieval complete: {} retrievers, {} total chunks, {}ms", 
            results.size(), totalChunks, totalDuration);
        
        return results;
    }
    
    /**
     * Compte les retrievers activés
     */
    private long countEnabledRetrievers(RoutingDecision decision) {
        return decision.getRetrievers().values().stream()
            .filter(RoutingDecision.RetrieverConfig::isEnabled)
            .count();
    }
}