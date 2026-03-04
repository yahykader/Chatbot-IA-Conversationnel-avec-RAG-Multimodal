package com.exemple.transactionservice.service.rag.retrieval.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;


/**
 * Décision de routing
 */
@Data
@Builder
public class RoutingDecision {
    
    public enum Strategy {
        TEXT_ONLY,
        IMAGE_ONLY,
        HYBRID,
        STRUCTURED
    }
    
    public enum Priority {
        LOW, MEDIUM, HIGH
    }
    
    @Data
    @Builder
    public static class RetrieverConfig {
        private boolean enabled;
        private Priority priority;
        private int topK;
        private long estimatedLatencyMs;
    }
    
    private Strategy strategy;
    private double confidence;
    private Map<String, RetrieverConfig> retrievers;
    private long estimatedTotalDurationMs;
    private boolean parallelExecution;
}
