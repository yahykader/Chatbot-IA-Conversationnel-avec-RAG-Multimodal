package com.exemple.transactionservice.service.rag.streaming.model;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.*;

/**
 * Événement de streaming
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamingEvent {
    
    /**
     * Types d'événements
     */
    public enum Type {
        // Connection
        CONNECTED,
        DISCONNECTED,
        
        // Query processing
        QUERY_RECEIVED,
        QUERY_STORED,
        QUERY_TRANSFORMED,
        ROUTING_DECISION,
        
        // Retrieval
        RETRIEVAL_START,
        RETRIEVAL_PROGRESS,
        RETRIEVAL_COMPLETE,
        
        // Aggregation
        AGGREGATION_PROGRESS,
        AGGREGATION_COMPLETE,
        RERANKING_COMPLETE,
        CONTEXT_READY,
        
        // Generation
        GENERATION_START,
        TOKEN,
        CITATION,
        GENERATION_COMPLETE,
        
        // Finalization
        FORMATTING_COMPLETE,
        COMPLETE,
        
        // Errors
        ERROR,
        CANCELLED,
        
        // Heartbeat
        HEARTBEAT
    }
    
    private Type type;
    private String sessionId;
    private String conversationId;
    private Map<String, Object> data;
    private Instant timestamp;
    
    /**
     * Convertit en format SSE
     */
    public String toSSE() {
        StringBuilder sb = new StringBuilder();
        
        // Event type
        sb.append("event: ").append(type.name().toLowerCase()).append("\n");
        
        // Data (JSON)
        sb.append("data: ").append(toJson()).append("\n\n");
        
        return sb.toString();
    }
    
    /**
     * Convertit en JSON (simple)
     */
    private String toJson() {
        // Use Jackson in production
        Map<String, Object> json = new HashMap<>();
        json.put("type", type.name().toLowerCase());
        if (sessionId != null) json.put("sessionId", sessionId);
        if (conversationId != null) json.put("conversationId", conversationId);
        if (data != null) json.put("data", data);
        if (timestamp != null) json.put("timestamp", timestamp.toString());
        
        return convertToJson(json);
    }
    
    // Simple JSON converter (use Jackson ObjectMapper in production)
    private String convertToJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    /**
     * Factory methods pour événements communs
     */
    
    public static StreamingEvent connected(String sessionId, String conversationId) {
        return StreamingEvent.builder()
            .type(Type.CONNECTED)
            .sessionId(sessionId)
            .conversationId(conversationId)
            .data(Map.of(
                "message", "Connected to streaming endpoint",
                "timestamp", Instant.now().toString()
            ))
            .timestamp(Instant.now())
            .build();
    }
    
    public static StreamingEvent queryReceived(String sessionId, String query) {
        return StreamingEvent.builder()
            .type(Type.QUERY_RECEIVED)
            .sessionId(sessionId)
            .data(Map.of(
                "query", query,
                "length", query.length()
            ))
            .timestamp(Instant.now())
            .build();
    }
    
    public static StreamingEvent token(String sessionId, String text, int index) {
        return StreamingEvent.builder()
            .type(Type.TOKEN)
            .sessionId(sessionId)
            .data(Map.of(
                "text", text,
                "index", index
            ))
            .timestamp(Instant.now())
            .build();
    }
    
    public static StreamingEvent error(String sessionId, String message, String code) {
        return StreamingEvent.builder()
            .type(Type.ERROR)
            .sessionId(sessionId)
            .data(Map.of(
                "message", message,
                "code", code != null ? code : "UNKNOWN_ERROR"
            ))
            .timestamp(Instant.now())
            .build();
    }
    
    public static StreamingEvent heartbeat(String sessionId) {
        return StreamingEvent.builder()
            .type(Type.HEARTBEAT)
            .sessionId(sessionId)
            .data(Map.of("timestamp", Instant.now().toString()))
            .timestamp(Instant.now())
            .build();
    }
}