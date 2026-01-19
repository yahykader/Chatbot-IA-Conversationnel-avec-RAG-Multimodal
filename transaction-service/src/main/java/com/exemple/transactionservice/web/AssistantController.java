// ============================================================================
// BACKEND - AssistantController.java (AVEC SSE)
// ============================================================================
package com.exemple.transactionservice.controller;

import com.exemple.transactionservice.service.ConversationalAssistant;
import com.exemple.transactionservice.service.MultimodalIngestionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/assistant")
@CrossOrigin(origins = "http://localhost:4200")
public class AssistantController {

        @Value("${assistant.stream.timeout-seconds:120}")
        private int streamTimeoutSeconds;
    
    private final MultimodalIngestionService ingestionService;
    private final ConversationalAssistant assistant;
    
    public AssistantController(
            MultimodalIngestionService ingestionService,
            ConversationalAssistant assistant) {
        this.ingestionService = ingestionService;
        this.assistant = assistant;
    }
    
    /**
     * Upload de fichiers pour ingestion
     */
    @PostMapping(value = {"/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            log.info("📤 Réception du fichier: {}", file.getOriginalFilename());
            ingestionService.ingestFile(file);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Fichier uploadé et indexé avec succès",
                    "filename", file.getOriginalFilename(),
                    "size", file.getSize()
            ));
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'upload", e);
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }
    
 @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chatStream(
        @RequestParam("userId") String userId,
        @RequestParam("message") String message) {

    String sessionId = UUID.randomUUID().toString();
    Instant start = Instant.now();

    final int FLUSH_SIZE = 80;

    return Flux.defer(() -> {
        StringBuilder full = new StringBuilder();
        StringBuilder buf = new StringBuilder();

        return assistant.chatStream(userId, message)
                .timeout(Duration.ofSeconds(streamTimeoutSeconds))
                .flatMap(token -> {
                    if (token == null || token.isEmpty()) return Flux.empty();

                    full.append(token);
                    buf.append(token);

                    boolean flush = buf.length() >= FLUSH_SIZE || token.contains("\n") || token.matches(".*[\\.!\\?\\;\\:]$");
                    if (!flush) return Flux.empty();

                    String chunk = buf.toString();
                    buf.setLength(0);

                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("chunk")
                            .id(sessionId)
                            .data(chunk)
                            .build());
                })
                .concatWith(Flux.defer(() -> {
                    // flush buffer restant
                    Flux<ServerSentEvent<String>> lastChunk = Flux.empty();
                    if (buf.length() > 0) {
                        lastChunk = Flux.just(ServerSentEvent.<String>builder()
                                .event("chunk")
                                .id(sessionId)
                                .data(buf.toString())
                                .build());
                        buf.setLength(0);
                    }

                    // event final avec réponse complète
                    Flux<ServerSentEvent<String>> finalEvent = Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event("final")
                                    .id(sessionId)
                                    .data(full.toString())
                                    .build(),
                            ServerSentEvent.<String>builder()
                                    .event("done")
                                    .id(sessionId)
                                    .data("[DONE]")
                                    .build()
                    );

                    return lastChunk.concatWith(finalEvent);
                }))
                .doOnComplete(() -> {
                    Duration duration = Duration.between(start, Instant.now());
                    log.info("✅ [{}] SSE terminé en {}ms ({} chars)", sessionId, duration.toMillis(), full.length());
                })
                .onErrorResume(err -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .id(sessionId)
                        .data("ERROR: " + (err.getMessage() != null ? err.getMessage() : "Erreur inconnue"))
                        .build()));
    });
}

    private String truncateMessage(String message) {
        if (message == null) return "null";
        if (message.length() <= 100) return message;
        return message.substring(0, 97) + "...";
 }
    
    /**
     * Chat classique (non-streaming)
     */
   /*
   @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        try {
            log.info("💬 Message de {}: {}", request.getUserId(), request.getMessage());
            
            // Collecter tout le stream en un seul message
            String response = assistant.chatStream(
                    request.getUserId(),
                    request.getMessage()
            ).collectList()
             .map(tokens -> String.join("", tokens))
             .block();
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "response", response,
                    "userId", request.getUserId()
            ));
        } catch (Exception e) {
            log.error("❌ Erreur lors du chat", e);
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }
   
   */ 
    
    @Data
    public static class ChatRequest {
        private String userId;
        private String message;
    }
}
