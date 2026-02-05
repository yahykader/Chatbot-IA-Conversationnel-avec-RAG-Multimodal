
package com.exemple.transactionservice.service.rag.streaming.model;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.*;
/**
 * État de conversation
 */
@Data
@Builder
public class ConversationState {
    
    @Data
    @Builder
    public static class Message {
        private String role; // "user" | "assistant"
        private String content;
        private Instant timestamp;
        private Map<String, Object> metadata;
        private List<SourceReference> sources;
    }
    
    @Data
    @Builder
    public static class SourceReference {
        private String file;
        private Object page;
        private double relevance;
    }
    
    @Data
    @Builder
    public static class ContextItem {
        private String docId;
        private double relevance;
        private List<Integer> usedInMessages;
    }
    
    private String conversationId;
    private String userId;
    private Instant createdAt;
    private Instant lastActivity;
    private List<Message> messages;
    private List<ContextItem> context;
    private Map<String, Object> metadata;
    
    @Builder.Default
    private int ttlSeconds = 3600;
}



















































