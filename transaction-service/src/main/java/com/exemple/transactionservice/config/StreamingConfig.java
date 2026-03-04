package com.exemple.transactionservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * Configuration complète pour Streaming API
 */
@Configuration
@ConfigurationProperties(prefix = "streaming")
@Data
public class StreamingConfig {
    
    // ========================================================================
    // SSE CONFIGURATION
    // ========================================================================
    
    private Sse sse = new Sse();
    
    @Data
    public static class Sse {
        private boolean enabled = true;
        private long timeoutMs = 300000; // 5 minutes
        private long heartbeatIntervalMs = 15000; // 15 secondes
        private int maxConcurrentConnections = 1000;
        private String contentType = "text/event-stream";
    }
    
    // ========================================================================
    // WEBSOCKET CONFIGURATION
    // ========================================================================
    
    private WebSocket websocket = new WebSocket();
    
    @Data
    public static class WebSocket {
        private boolean enabled = true;
        private String endpoint = "/ws/assistant";
        private long timeoutMs = 600000; // 10 minutes
        private long heartbeatIntervalMs = 30000; // 30 secondes
        private int maxConcurrentConnections = 500;
        private int maxMessageSize = 65536; // 64KB
        private boolean allowedOrigins = true;
    }
    
    // ========================================================================
    // EVENT EMISSION
    // ========================================================================
    
    private Events events = new Events();
    
    @Data
    public static class Events {
        private boolean enableAllEvents = true;
        private boolean enableQueryReceived = true;
        private boolean enableQueryTransformed = true;
        private boolean enableRoutingDecision = true;
        private boolean enableRetrievalProgress = true;
        private boolean enableRetrievalComplete = true;
        private boolean enableAggregationComplete = true;
        private boolean enableContextReady = true;
        private boolean enableGenerationStart = true;
        private boolean enableToken = true;
        private boolean enableCitation = true;
        private boolean enableGenerationComplete = true;
        private boolean enableComplete = true;
        private boolean enableError = true;
        
        // Token buffering
        private int tokenBufferSize = 5;
        private long tokenBufferFlushMs = 50;
    }
    
    // ========================================================================
    // CONVERSATION MANAGER
    // ========================================================================
    
    private Conversation conversation = new Conversation();
    
    @Data
    public static class Conversation {
        private boolean enabled = true;
        private String storageType = "redis"; // "redis" | "memory"
        private String redisKeyPrefix = "conversation:";
        private long ttlSeconds = 3600; // 1 hour
        private int maxMessagesPerConversation = 100;
        private int maxContextTokens = 8000;
        private boolean enableHistory = true;
        private boolean enableContextEnrichment = true;
    }
    
    // ========================================================================
    // CLAUDE API CONFIGURATION
    // ========================================================================
    
    private OpenAi openAi = new OpenAi();
    
    @Data
    public static class OpenAi {
        private String apiKey = "${OPENAI_API_KEY}";
        private String apiUrl = "https://api.openai.com/v1/chat/completions";
        private String model = "gpt-4o";
        private String openAiVersion = "2023-06-01";
        private double temperature = 0.7;
        private int maxTokens = 2000;
        private boolean stream = true;
        private int timeoutMs = 30000;
        private int retryAttempts = 3;
        private long retryDelayMs = 1000;
    }
    
    // ========================================================================
    // RATE LIMITING
    // ========================================================================
    
    private RateLimit rateLimit = new RateLimit();
    
    @Data
    public static class RateLimit {
        private boolean enabled = true;
        private int requestsPerHourPerUser = 1000;
        private int concurrentStreamsPerUser = 5;
        private int tokensPerDayPerUser = 50000;
        private String redisKeyPrefix = "rate_limit:";
    }
    
    // ========================================================================
    // PERFORMANCE
    // ========================================================================
    
    private Performance performance = new Performance();
    
    @Data
    public static class Performance {
        private int executorThreadPoolSize = 10;
        private int maxQueueSize = 100;
        private boolean enableMetrics = true;
        private boolean enableTracing = false;
    }
}