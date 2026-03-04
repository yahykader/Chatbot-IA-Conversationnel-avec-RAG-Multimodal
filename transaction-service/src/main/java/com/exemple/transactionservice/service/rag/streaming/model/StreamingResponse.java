package com.exemple.transactionservice.service.rag.streaming.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Response finale du streaming
 */
@Data
@Builder
public class StreamingResponse {
    private String sessionId;
    private String conversationId;
    private String query;
    private String answer;
    private List<SourceReference> sources;
    private List<Citation> citations;
    private Metadata metadata;
    
    /**
     * Metadata de la réponse
     */
    @Data
    @Builder
    public static class Metadata {
        private int tokensGenerated;
        private int chunksRetrieved;
        private int chunksSelected;
        private long retrievalDurationMs;
        private long generationDurationMs;
        private long totalDurationMs;
    }
    
    /**
     * Source référencée
     */
    @Data
    @Builder
    public static class SourceReference {
        private String file;
        private Object page;
        private double relevance;
        private String type;
    }
    
    /**
     * Citation dans la réponse
     */
    @Data
    @Builder
    public static class Citation {
        private int index;
        private String content;
        private String sourceFile;
        private Object sourcePage;
    }
}