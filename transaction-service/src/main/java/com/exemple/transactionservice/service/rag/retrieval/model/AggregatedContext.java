package com.exemple.transactionservice.service.rag.retrieval.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Contexte agrégé après fusion
 */
@Data
@Builder
public class AggregatedContext {
    
    @Data
    @Builder
    public static class SelectedChunk {
        private String id;
        private String content;
        private Map<String, Object> metadata;
        private double finalScore;
        private Map<String, Double> scoresByRetriever;
        private List<String> retrieversUsed;
    }
    
    private List<SelectedChunk> chunks;
    private int inputChunks;
    private int deduplicatedChunks;
    private int rrfCandidates;
    private int finalSelected;
    private String fusionMethod;
    private long durationMs;
    private Map<String, Integer> chunksByRetriever;
}
