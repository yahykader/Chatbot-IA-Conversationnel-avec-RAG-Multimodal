package com.exemple.transactionservice.service.rag.retrieval;

import com.exemple.transactionservice.config.RetrievalConfig;
import com.exemple.transactionservice.service.rag.retrieval.model.AggregatedContext;
import com.exemple.transactionservice.service.rag.retrieval.model.AggregatedContext.SelectedChunk;
import com.exemple.transactionservice.service.rag.retrieval.model.RetrievalResult;
import com.exemple.transactionservice.service.rag.retrieval.model.RetrievalResult.ScoredChunk;
import com.exemple.transactionservice.service.rag.retrieval.reranker.Reranker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service d'agrégation de contenu
 * 
 * Fusionne les résultats de plusieurs retrievers avec:
 * - Deduplication (par ID)
 * - RRF (Reciprocal Rank Fusion)
 * - Optional: Reranking avec cross-encoder
 * 
 * Algorithme RRF:
 * score_final = Σ [1 / (k + rank_i)]
 * où k=60 (constante), rank_i = position dans retriever i
 */
@Slf4j
@Service
public class ContentAggregatorService {
    
    private final RetrievalConfig config;
    private final Optional<Reranker> reranker;
    
    public ContentAggregatorService(
            RetrievalConfig config,
            Optional<Reranker> reranker) {
        this.config = config;
        this.reranker = reranker;
    }
    
    /**
     * Agrège les résultats de plusieurs retrievers
     */
    public AggregatedContext aggregate(
            Map<String, RetrievalResult> retrievalResults,
            String originalQuery) {
        
        long startTime = System.currentTimeMillis();
        
        // Étape 1: Collecter tous les chunks
        List<ScoredChunk> allChunks = retrievalResults.values().stream()
            .flatMap(result -> result.getChunks().stream())
            .collect(Collectors.toList());
        
        int inputChunks = allChunks.size();
        
        log.info("📥 Aggregation start: {} total chunks from {} retrievers", 
            inputChunks, retrievalResults.size());
        
        // Étape 2: Deduplication par ID
        Map<String, ScoredChunk> uniqueChunks = deduplicateChunks(allChunks);
        int deduplicatedCount = uniqueChunks.size();
        
        log.debug("🔄 Deduplicated: {} → {} unique chunks", 
            inputChunks, deduplicatedCount);
        
        // Étape 3: RRF Fusion
        List<SelectedChunk> fusedChunks = applyRRFFusion(uniqueChunks, retrievalResults);
        
        // Limiter aux top candidates
        int maxCandidates = config.getAggregator().getMaxCandidates();
        List<SelectedChunk> candidates = fusedChunks.stream()
            .limit(maxCandidates)
            .collect(Collectors.toList());
        
        log.debug("🎯 RRF fusion: {} → top {} candidates", 
            fusedChunks.size(), candidates.size());
        
        // Étape 4: Optional Reranking
        List<SelectedChunk> finalChunks;
        if (config.getReranker().isEnabled() && reranker.isPresent()) {
            finalChunks = applyReranking(candidates, originalQuery);
            log.info("🔝 Reranked: {} → {} final chunks", 
                candidates.size(), finalChunks.size());
        } else {
            finalChunks = candidates.stream()
                .limit(config.getAggregator().getFinalTopK())
                .collect(Collectors.toList());
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Statistiques par retriever
        Map<String, Integer> chunksByRetriever = finalChunks.stream()
            .flatMap(chunk -> chunk.getRetrieversUsed().stream())
            .collect(Collectors.groupingBy(
                name -> name,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));
        
        log.info("✅ Aggregation complete: {} final chunks ({}ms)", 
            finalChunks.size(), duration);
        log.info("📊 Distribution: {}", chunksByRetriever);
        
        return AggregatedContext.builder()
            .chunks(finalChunks)
            .inputChunks(inputChunks)
            .deduplicatedChunks(deduplicatedCount)
            .rrfCandidates(candidates.size())
            .finalSelected(finalChunks.size())
            .fusionMethod(config.getReranker().isEnabled() ? "rrf+reranking" : "rrf")
            .durationMs(duration)
            .chunksByRetriever(chunksByRetriever)
            .build();
    }
    
    /**
     * Deduplication par ID (garde le meilleur score)
     */
    private Map<String, ScoredChunk> deduplicateChunks(List<ScoredChunk> chunks) {
        return chunks.stream()
            .collect(Collectors.toMap(
                ScoredChunk::getId,
                chunk -> chunk,
                (existing, replacement) -> 
                    existing.getScore() > replacement.getScore() ? existing : replacement
            ));
    }
    
    /**
     * RRF (Reciprocal Rank Fusion)
     * 
     * Score formula: score = Σ [1 / (k + rank_i)]
     */
    private List<SelectedChunk> applyRRFFusion(
            Map<String, ScoredChunk> uniqueChunks,
            Map<String, RetrievalResult> retrievalResults) {
        
        int k = config.getAggregator().getRrfK();
        
        // Calculer scores RRF pour chaque chunk
        Map<String, RRFScore> rrfScores = new HashMap<>();
        
        retrievalResults.forEach((retrieverName, result) -> {
            List<ScoredChunk> chunks = result.getChunks();
            
            for (int rank = 0; rank < chunks.size(); rank++) {
                ScoredChunk chunk = chunks.get(rank);
                String chunkId = chunk.getId();
                
                // Score RRF: 1 / (k + rank + 1)
                double rrfScore = 1.0 / (k + rank + 1);
                
                RRFScore score = rrfScores.computeIfAbsent(
                    chunkId, 
                    id -> new RRFScore()
                );
                
                score.totalScore += rrfScore;
                score.scoresByRetriever.put(retrieverName, rrfScore);
                score.retrieversUsed.add(retrieverName);
            }
        });
        
        // Convertir en SelectedChunk
        List<SelectedChunk> selectedChunks = rrfScores.entrySet().stream()
            .map(entry -> {
                String chunkId = entry.getKey();
                RRFScore score = entry.getValue();
                ScoredChunk originalChunk = uniqueChunks.get(chunkId);
                
                return SelectedChunk.builder()
                    .id(chunkId)
                    .content(originalChunk.getContent())
                    .metadata(originalChunk.getMetadata())
                    .finalScore(score.totalScore)
                    .scoresByRetriever(score.scoresByRetriever)
                    .retrieversUsed(score.retrieversUsed)
                    .build();
            })
            .sorted(Comparator.comparingDouble(SelectedChunk::getFinalScore).reversed())
            .collect(Collectors.toList());
        
        return selectedChunks;
    }
    
    /**
     * Reranking avec cross-encoder (optionnel)
     */
    private List<SelectedChunk> applyReranking(
            List<SelectedChunk> candidates, 
            String query) {
        
        if (reranker.isEmpty()) {
            return candidates.stream()
                .limit(config.getAggregator().getFinalTopK())
                .collect(Collectors.toList());
        }
        
        try {
            // Appel au reranker
            List<SelectedChunk> reranked = reranker.get().rerank(query, candidates);
            
            return reranked.stream()
                .limit(config.getReranker().getTopK())
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("❌ Reranking failed, using RRF results", e);
            return candidates.stream()
                .limit(config.getAggregator().getFinalTopK())
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Helper class pour calculer scores RRF
     */
    private static class RRFScore {
        double totalScore = 0.0;
        Map<String, Double> scoresByRetriever = new HashMap<>();
        List<String> retrieversUsed = new ArrayList<>();
    }
}