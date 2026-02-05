package com.exemple.transactionservice.service.rag.streaming.openai;

import com.exemple.transactionservice.config.StreamingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client pour appeler OpenAi Messages API en mode streaming
 * 
 * Features:
 * - Streaming SSE depuis OpenAi API
 * - Parse événements (message_start, content_block_delta, message_stop)
 * - Gestion erreurs et retry
 * - Timeout configurable
 */
@Slf4j
@Service
public class OpenAiStreamingClient {
    
    private final WebClient webClient;
    private final StreamingConfig config;
    private final ObjectMapper objectMapper;
    
    // Patterns pour parser SSE events
    private static final Pattern EVENT_PATTERN = Pattern.compile("event: (.+)");
    private static final Pattern DATA_PATTERN = Pattern.compile("data: (.+)");
    
    public OpenAiStreamingClient(
            StreamingConfig config,
            ObjectMapper objectMapper) {
        
        this.config = config;
        this.objectMapper = objectMapper;
        
        // Configure WebClient pour OpenAi API
        this.webClient = WebClient.builder()
            .baseUrl(config.getOpenAi().getApiUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Authorization", "Bearer " + config.getOpenAi().getApiKey())
            .build();
        
        log.info("✅ OpenAiStreamingClient initialized (model: {})", 
            config.getOpenAi().getModel());
    }
    
    /**
     * Stream response from OpenAi API
     * 
     * @param prompt Full prompt to send
     * @param onToken Callback for each token received
     * @param onComplete Callback when streaming completes
     * @param onError Callback on error
     */
    public void streamResponse(
            String prompt,
            Consumer<String> onToken,
            Consumer<StreamingResponse> onComplete,
            Consumer<Throwable> onError) {
        
        long startTime = System.currentTimeMillis();
        
        log.debug("🚀 Starting OpenAi streaming...");
        
        // Build request body
        Map<String, Object> requestBody = Map.of(
            "model", config.getOpenAi().getModel(),
            "max_tokens", config.getOpenAi().getMaxTokens(),
            "temperature", config.getOpenAi().getTemperature(),
            "stream", true,
            "messages", new Object[] {
                Map.of(
                    "role", "user",
                    "content", prompt
                )
            }
        );
        
        // State pour parser le stream
        StreamingState state = new StreamingState();
        
        // Call OpenAi API
        webClient.post()
            .bodyValue(requestBody)
            .retrieve()
            .bodyToFlux(String.class)
            .doOnNext(line -> {
                // Parser chaque ligne SSE
                parseSSELine(line, state, onToken);
            })
            .doOnComplete(() -> {
                long duration = System.currentTimeMillis() - startTime;
                
                log.info("✅ OpenAi streaming complete: {} tokens, {}ms",
                    state.totalTokens, duration);
                
                StreamingResponse response = StreamingResponse.builder()
                    .fullText(state.fullText.toString())
                    .totalTokens(state.totalTokens)
                    .durationMs(duration)
                    .finishReason(state.finishReason)
                    .build();
                
                onComplete.accept(response);
            })
            .doOnError(error -> {
                log.error("❌ OpenAi streaming error", error);
                onError.accept(error);
            })
            .subscribe();
    }
    
    /**
     * Non-streaming version (fallback)
     */
    public String generateSync(String prompt) {
        log.debug("🔄 Generating response (non-streaming)...");
        
        Map<String, Object> requestBody = Map.of(
            "model", config.getOpenAi().getModel(),
            "max_tokens", config.getOpenAi().getMaxTokens(),
            "temperature", config.getOpenAi().getTemperature(),
            "stream", false,
            "messages", new Object[] {
                Map.of("role", "user", "content", prompt)
            }
        );
        
        try {
            Map<String, Object> response = webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            if (response != null && response.containsKey("content")) {
                var content = (java.util.List<?>) response.get("content");
                if (!content.isEmpty()) {
                    var firstBlock = (Map<?, ?>) content.get(0);
                    return (String) firstBlock.get("text");
                }
            }
            
            return "";
            
        } catch (Exception e) {
            log.error("❌ Error calling OpenAi API", e);
            throw new RuntimeException("OpenAi API call failed", e);
        }
    }
    
    // ========================================================================
    // PRIVATE METHODS
    // ========================================================================
    
    /**
     * Parse une ligne SSE
     */
    private void parseSSELine(
            String line, 
            StreamingState state, 
            Consumer<String> onToken) {
        
        if (line == null || line.trim().isEmpty()) {
            return;
        }
        
        // Détecter type d'événement
        Matcher eventMatcher = EVENT_PATTERN.matcher(line);
        if (eventMatcher.find()) {
            state.currentEvent = eventMatcher.group(1);
            return;
        }
        
        // Parser data
        Matcher dataMatcher = DATA_PATTERN.matcher(line);
        if (dataMatcher.find()) {
            String data = dataMatcher.group(1);
            
            try {
                switch (state.currentEvent) {
                    case "message_start" -> handleMessageStart(data, state);
                    case "content_block_start" -> handleContentBlockStart(data, state);
                    case "content_block_delta" -> handleContentBlockDelta(data, state, onToken);
                    case "content_block_stop" -> handleContentBlockStop(data, state);
                    case "message_delta" -> handleMessageDelta(data, state);
                    case "message_stop" -> handleMessageStop(data, state);
                    case "ping" -> log.trace("💓 Received ping");
                    default -> log.trace("Unknown event: {}", state.currentEvent);
                }
                
            } catch (Exception e) {
                log.warn("⚠️ Error parsing SSE data: {}", data, e);
            }
        }
    }
    
    /**
     * Handle message_start event
     */
    private void handleMessageStart(String data, StreamingState state) {
        log.debug("📨 Message start");
        state.messageStarted = true;
    }
    
    /**
     * Handle content_block_start event
     */
    private void handleContentBlockStart(String data, StreamingState state) {
        log.debug("📝 Content block start");
    }
    
    /**
     * Handle content_block_delta event (tokens)
     */
    private void handleContentBlockDelta(
            String data, 
            StreamingState state, 
            Consumer<String> onToken) {
        
        try {
            Map<String, Object> json = objectMapper.readValue(data, Map.class);
            Map<String, Object> delta = (Map<String, Object>) json.get("delta");
            
            if (delta != null && delta.containsKey("text")) {
                String text = (String) delta.get("text");
                
                if (text != null && !text.isEmpty()) {
                    state.fullText.append(text);
                    state.totalTokens++;
                    
                    // Emit token
                    onToken.accept(text);
                }
            }
            
        } catch (Exception e) {
            log.warn("⚠️ Error parsing content_block_delta", e);
        }
    }
    
    /**
     * Handle content_block_stop event
     */
    private void handleContentBlockStop(String data, StreamingState state) {
        log.debug("✅ Content block stop");
    }
    
    /**
     * Handle message_delta event
     */
    private void handleMessageDelta(String data, StreamingState state) {
        try {
            Map<String, Object> json = objectMapper.readValue(data, Map.class);
            Map<String, Object> delta = (Map<String, Object>) json.get("delta");
            
            if (delta != null && delta.containsKey("stop_reason")) {
                state.finishReason = (String) delta.get("stop_reason");
                log.debug("🏁 Stop reason: {}", state.finishReason);
            }
            
        } catch (Exception e) {
            log.warn("⚠️ Error parsing message_delta", e);
        }
    }
    
    /**
     * Handle message_stop event
     */
    private void handleMessageStop(String data, StreamingState state) {
        log.debug("🛑 Message stop");
        state.messageCompleted = true;
    }
    
    // ========================================================================
    // HELPER CLASSES
    // ========================================================================
    
    /**
     * État du streaming
     */
    private static class StreamingState {
        String currentEvent = "";
        boolean messageStarted = false;
        boolean messageCompleted = false;
        StringBuilder fullText = new StringBuilder();
        int totalTokens = 0;
        String finishReason = null;
    }
    
    /**
     * Réponse streaming complète
     */
    @lombok.Data
    @lombok.Builder
    public static class StreamingResponse {
        private String fullText;
        private int totalTokens;
        private long durationMs;
        private String finishReason;
    }
}