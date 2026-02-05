package com.exemple.transactionservice.service.rag.streaming.model;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.*;

/**
 * Requête de streaming
 */
@Data
@Builder
public class StreamingRequest {
    private String query;
    private String conversationId;
    private String userId;
    private Map<String, Object> options;
    
    @Builder.Default
    private int maxChunks = 10;
    
    @Builder.Default
    private boolean streaming = true;
    
    @Builder.Default
    private boolean includeImages = true;
    
    @Builder.Default
    private double temperature = 0.7;
}
