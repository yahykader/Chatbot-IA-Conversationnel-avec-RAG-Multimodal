package com.exemple.transactionservice.service.rag.retrieval.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;
/**
 * Prompt final injecté
 */
@Data
@Builder
public class InjectedPrompt {
    
    @Data
    @Builder
    public static class PromptStructure {
        private String systemPrompt;
        private String documentsContext;
        private String userQuery;
        private String instructions;
        private int systemTokens;
        private int contextTokens;
        private int queryTokens;
        private int instructionsTokens;
        private int totalTokens;
    }
    
    @Data
    @Builder
    public static class SourceReference {
        private String file;
        private Object page; // Integer ou String
        private double relevance;
        private int tokens;
        private String type; // "text" | "image" | "table"
    }
    
    private String fullPrompt;
    private PromptStructure structure;
    private List<SourceReference> sources;
    private double contextUsagePercent;
    private long durationMs;
}