// ============================================================================
// CONFIGURATION PROPERTIES - OpenAiProperties.java
// ============================================================================
package com.exemple.transactionservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {
    
    @NotBlank(message = "OpenAI API key is required")
    private String apiKey;
    
    private Embedding embedding = new Embedding();
    private Chat chat = new Chat();
    
    @Data
    public static class Embedding {
        private String model = "text-embedding-3-small";
        
        @Min(384)
        @Max(3072)
        private int dimension = 1536;
    }
    
    @Data
    public static class Chat {
        private String model = "gpt-4o";
        
        @Min(0)
        @Max(2)
        private double temperature = 0.7;
        
        @Positive
        private int maxTokens = 2000;
        
        @Positive
        private int timeoutSeconds = 60;
        
        @Min(0)
        @Max(10)
        private int maxRetries = 3;
        
        private boolean logRequests = false;
        private boolean logResponses = false;
    }
}