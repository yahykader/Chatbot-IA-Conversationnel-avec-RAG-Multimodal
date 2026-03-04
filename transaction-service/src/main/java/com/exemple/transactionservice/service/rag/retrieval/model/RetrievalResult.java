package com.exemple.transactionservice.service.rag.retrieval.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Résultat de retrieval d'un retriever
 */
@Data
@Builder
public class RetrievalResult {
    
    @Data
    @Builder
    public static class ScoredChunk {
        private String id;
        private String content;
        private Map<String, Object> metadata;
        private double score;
        private String retrieverName;
        private int rank;
    }
    
    private String retrieverName;
    private List<ScoredChunk> chunks;
    private int totalFound;
    private double topScore;
    private long durationMs;
    private int cacheHits;
    private int cacheMisses;
}