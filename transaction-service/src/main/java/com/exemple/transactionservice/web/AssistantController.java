// ============================================================================
// BACKEND - AssistantController.java (v2.1 - Adapté UploadRateLimiter)
// ============================================================================
package com.exemple.transactionservice.controller;

import com.exemple.transactionservice.service.ConversationalAssistant;
import com.exemple.transactionservice.service.MultimodalIngestionService;
import com.exemple.transactionservice.service.UploadRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * ✅ AssistantController v2.1 - Adapté avec UploadRateLimiter
 */
@Slf4j
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final MultimodalIngestionService ingestionService;
    private final ConversationalAssistant assistant;
    private final UploadRateLimiter uploadRateLimiter;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService scheduler;

    @Value("${assistant.stream.timeout-seconds:120}")
    private int streamTimeoutSeconds;
    
    @Value("${assistant.stream.heartbeat-interval-seconds:15}")
    private int heartbeatIntervalSeconds;
    
    @Value("${assistant.upload.max-file-size:20971520}")
    private long maxFileSize;
    
    @Value("${assistant.upload.allowed-extensions}")
    private String allowedExtensions;
    
    @Value("${assistant.upload.max-concurrent:3}")
    private int maxConcurrentUploads;

    // Tracking jobs upload
    private final ConcurrentHashMap<String, UploadJob> uploadJobs = new ConcurrentHashMap<>();

    // ✅ CONSTRUCTEUR ADAPTÉ
    public AssistantController(
            MultimodalIngestionService ingestionService,
            ConversationalAssistant assistant,
            UploadRateLimiter uploadRateLimiter,
            MeterRegistry meterRegistry) {
        
        this.ingestionService = ingestionService;
        this.assistant = assistant;
        this.uploadRateLimiter = uploadRateLimiter;  // ✅ ASSIGNATION
        this.meterRegistry = meterRegistry;
        this.scheduler = Executors.newScheduledThreadPool(4);

        log.info("✅ [Controller] Initialisé v2.1 - Timeout: {}s, Heartbeat: {}s, MaxUpload: {} MB, MaxConcurrent: {}",
                 streamTimeoutSeconds, heartbeatIntervalSeconds, 
                 maxFileSize / (1024 * 1024), maxConcurrentUploads);
    }

    // ========================================================================
    // UPLOAD - VERSION AMÉLIORÉE AVEC UploadRateLimiter
    // ========================================================================

    /**
     * ✅ ADAPTÉ v2.1: Upload avec UploadRateLimiter service
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {
        
        Instant start = Instant.now();
        String jobId = UUID.randomUUID().toString();
        
        try {
            // ========================================
            // VALIDATION
            // ========================================
            
            // Validation vide
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Fichier vide"
                ));
            }
            
            // Validation taille
            if (file.getSize() > maxFileSize) {
                double sizeMB = file.getSize() / (1024.0 * 1024.0);
                double maxMB = maxFileSize / (1024.0 * 1024.0);
                
                log.warn("⚠️ [{}] Fichier trop volumineux: {:.2f} MB (max: {:.2f} MB)", 
                         jobId, sizeMB, maxMB);
                
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", String.format("Fichier trop volumineux: %.2f MB (max: %.2f MB)", 
                                           sizeMB, maxMB)
                ));
            }
            
            // Validation nom fichier
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Nom de fichier invalide"
                ));
            }
            
            // Validation extension
            String extension = getFileExtension(filename).toLowerCase();
            if (!isExtensionAllowed(extension)) {
                log.warn("⚠️ [{}] Extension non autorisée: {} (fichier: {})", 
                         jobId, extension, filename);
                
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Type de fichier non autorisé: " + extension,
                    "allowed", Arrays.asList(allowedExtensions.split(","))
                ));
            }
            
            // Sanitize nom fichier
            String sanitizedFilename = sanitizeFilename(filename);
            
            // ========================================
            // ✅ RATE LIMITING AVEC UploadRateLimiter
            // ========================================
            
            if (!uploadRateLimiter.tryAcquire(userId)) {
                log.warn("⚠️ [{}] Rate limit upload dépassé pour user: {}", jobId, userId);
                
                return ResponseEntity.status(429).body(Map.of(
                    "success", false,
                    "error", String.format("Trop d'uploads simultanés (max: %d)", maxConcurrentUploads),
                    "maxConcurrent", maxConcurrentUploads
                ));
            }
            
            // ========================================
            // UPLOAD ASYNC
            // ========================================
            
            try {
                log.info("📤 [{}] Upload démarré: {} ({} KB) - User: {}", 
                         jobId, sanitizedFilename, file.getSize() / 1024, userId);
                
                // Créer job
                UploadJob job = new UploadJob(jobId, sanitizedFilename, file.getSize());
                uploadJobs.put(jobId, job);
                
                // ✅ Processing async
                CompletableFuture.runAsync(() -> {
                    try {
                        job.setStatus(UploadStatus.PROCESSING);
                        job.setProgress(10);
                        
                        // Ingestion (30s-2min)
                        ingestionService.ingestFile(file);
                        
                        job.setStatus(UploadStatus.COMPLETED);
                        job.setProgress(100);
                        
                        Duration duration = Duration.between(start, Instant.now());
                        log.info("✅ [{}] Upload terminé: {} en {}ms", 
                                 jobId, sanitizedFilename, duration.toMillis());
                        
                        // Métriques
                        recordUploadMetrics(sanitizedFilename, file.getSize(), true, duration);
                        
                    } catch (Exception e) {
                        log.error("❌ [{}] Erreur ingestion: {}", jobId, sanitizedFilename, e);
                        
                        job.setStatus(UploadStatus.FAILED);
                        job.setProgress(0);
                        job.setErrorMessage(e.getMessage());
                        
                        recordUploadMetrics(sanitizedFilename, file.getSize(), false, 
                                          Duration.between(start, Instant.now()));
                        
                    } finally {
                        // ✅ LIBÉRER SLOT avec UploadRateLimiter
                        uploadRateLimiter.release(userId);
                        
                        // Nettoyer job après 5 minutes
                        scheduler.schedule(() -> uploadJobs.remove(jobId), 5, TimeUnit.MINUTES);
                    }
                });
                
                // Retour immédiat
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Upload démarré",
                    "jobId", jobId,
                    "filename", sanitizedFilename,
                    "size", file.getSize()
                ));
                
            } catch (Exception e) {
                // ✅ Libérer en cas d'erreur
                uploadRateLimiter.release(userId);
                throw e;
            }
            
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ [{}] Validation échouée: {}", jobId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
            
        } catch (Exception e) {
            log.error("❌ [{}] Erreur upload", jobId, e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Erreur serveur lors de l'upload"
            ));
        }
    }

    /**
     * Endpoint status upload
     */
    @GetMapping("/upload/status/{jobId}")
    public ResponseEntity<?> getUploadStatus(@PathVariable String jobId) {
        UploadJob job = uploadJobs.get(jobId);
        
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(Map.of(
            "jobId", jobId,
            "filename", job.getFilename(),
            "status", job.getStatus().name().toLowerCase(),
            "progress", job.getProgress(),
            "message", job.getMessage(),
            "error", job.getErrorMessage() != null ? job.getErrorMessage() : ""
        ));
    }

    // ========================================================================
    // CHAT STREAMING
    // ========================================================================

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam("userId") String userId,
            @RequestParam("message") String message) {

        String sessionId = UUID.randomUUID().toString();
        Instant start = Instant.now();
        final int FLUSH_SIZE = 80;

        log.info("💬 [{}] Chat streaming - User: {}, Message: '{}'",
                 sessionId, userId, truncateMessage(message));

        return Flux.defer(() -> {
            StringBuilder fullResponse = new StringBuilder();
            StringBuilder buffer = new StringBuilder();

            // Heartbeat
            Flux<ServerSentEvent<String>> heartbeat = Flux.interval(
                Duration.ofSeconds(heartbeatIntervalSeconds)
            )
            .map(tick -> ServerSentEvent.<String>builder()
                .event("heartbeat")
                .id(sessionId)
                .data("ping")
                .build())
            .takeUntil(event -> false);

            // Stream principal
            Flux<ServerSentEvent<String>> responseStream = assistant.chatStream(userId, message)
                .timeout(Duration.ofSeconds(streamTimeoutSeconds))
                .flatMap(token -> {
                    if (token == null || token.isEmpty()) {
                        return Flux.empty();
                    }

                    fullResponse.append(token);
                    buffer.append(token);

                    boolean flush = buffer.length() >= FLUSH_SIZE || 
                                  token.contains("\n") || 
                                  endsWithPunctuation(token);
                    
                    if (!flush) {
                        return Flux.empty();
                    }

                    String chunk = buffer.toString();
                    buffer.setLength(0);

                    return Flux.just(ServerSentEvent.<String>builder()
                        .event("chunk")
                        .id(sessionId)
                        .data(chunk)
                        .build());
                })
                .concatWith(Flux.defer(() -> {
                    Flux<ServerSentEvent<String>> lastChunk = Flux.empty();
                    if (buffer.length() > 0) {
                        lastChunk = Flux.just(ServerSentEvent.<String>builder()
                            .event("chunk")
                            .id(sessionId)
                            .data(buffer.toString())
                            .build());
                        buffer.setLength(0);
                    }

                    Flux<ServerSentEvent<String>> finalEvents = Flux.just(
                        ServerSentEvent.<String>builder()
                            .event("final")
                            .id(sessionId)
                            .data(fullResponse.toString())
                            .build(),
                        ServerSentEvent.<String>builder()
                            .event("done")
                            .id(sessionId)
                            .data("[DONE]")
                            .build()
                    );

                    return lastChunk.concatWith(finalEvents);
                }))
                .doOnComplete(() -> {
                    Duration duration = Duration.between(start, Instant.now());
                    log.info("✅ [{}] SSE terminé en {}ms ({} chars)", 
                             sessionId, duration.toMillis(), fullResponse.length());
                    
                    recordChatMetrics(userId, fullResponse.length(), duration, true);
                })
                .onErrorResume(err -> {
                    log.error("❌ [{}] Erreur SSE", sessionId, err);
                    
                    recordChatMetrics(userId, fullResponse.length(), 
                                    Duration.between(start, Instant.now()), false);
                    
                    return Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .id(sessionId)
                        .data("ERROR: " + (err.getMessage() != null ? 
                                          err.getMessage() : "Erreur inconnue"))
                        .build());
                });

            return Flux.merge(
                heartbeat.takeUntilOther(responseStream.last()),
                responseStream
            );
        });
    }

    // ========================================================================
    // MÉTHODES PRIVÉES - VALIDATION
    // ========================================================================

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) return "";
        return filename.substring(lastDot + 1);
    }

    private boolean isExtensionAllowed(String extension) {
        Set<String> allowed = Arrays.stream(allowedExtensions.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
        
        return allowed.contains(extension.toLowerCase());
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "unknown";
        
        filename = filename.replaceAll("\\.\\./", "");
        filename = filename.replaceAll("\\.\\\\", "");
        filename = filename.replaceAll("/", "_");
        filename = filename.replaceAll("\\\\", "_");
        filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        if (filename.length() > 255) {
            String extension = getFileExtension(filename);
            String name = filename.substring(0, 255 - extension.length() - 1);
            filename = name + "." + extension;
        }
        
        return filename;
    }

    private boolean endsWithPunctuation(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        char last = token.charAt(token.length() - 1);
        return last == '.' || last == '!' || last == '?' || 
               last == ';' || last == ':' || last == ',';
    }

    private String truncateMessage(String message) {
        if (message == null) return "null";
        if (message.length() <= 100) return message;
        return message.substring(0, 97) + "...";
    }

    // ========================================================================
    // MÉTHODES PRIVÉES - MÉTRIQUES
    // ========================================================================

    private void recordUploadMetrics(
            String filename, 
            long sizeBytes, 
            boolean success,
            Duration duration) {
        
        try {
            String extension = getFileExtension(filename);
            
            meterRegistry.counter("uploads.total",
                "success", String.valueOf(success),
                "extension", extension
            ).increment();
            
            if (success) {
                meterRegistry.counter("uploads.bytes.total",
                    "extension", extension
                ).increment(sizeBytes);
            }
            
            meterRegistry.summary("uploads.size.bytes",
                "extension", extension
            ).record(sizeBytes);
            
            if (success) {
                meterRegistry.timer("uploads.duration",
                    "extension", extension
                ).record(duration);
            }
            
        } catch (Exception e) {
            log.warn("⚠️ Erreur enregistrement métriques upload", e);
        }
    }

    private void recordChatMetrics(
            String userId, 
            int responseLength,
            Duration duration,
            boolean success) {
        
        try {
            meterRegistry.counter("chat.messages.total",
                "success", String.valueOf(success)
            ).increment();
            
            meterRegistry.timer("chat.duration",
                "success", String.valueOf(success)
            ).record(duration);
            
            if (success) {
                meterRegistry.summary("chat.response.length").record(responseLength);
            }
            
        } catch (Exception e) {
            log.warn("⚠️ Erreur enregistrement métriques chat", e);
        }
    }

    // ========================================================================
    // CLASSES INTERNES
    // ========================================================================

    @Data
    private static class UploadJob {
        private final String jobId;
        private final String filename;
        private final long fileSize;
        private UploadStatus status = UploadStatus.PENDING;
        private int progress = 0;
        private String errorMessage;
        private final Instant createdAt = Instant.now();
        
        public UploadJob(String jobId, String filename, long fileSize) {
            this.jobId = jobId;
            this.filename = filename;
            this.fileSize = fileSize;
        }
        
        public String getMessage() {
            return switch (status) {
                case PENDING -> "Upload en attente...";
                case PROCESSING -> "Traitement en cours (" + progress + "%)...";
                case COMPLETED -> "Upload terminé";
                case FAILED -> "Échec: " + (errorMessage != null ? errorMessage : "Erreur inconnue");
            };
        }
    }

    private enum UploadStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    @Data
    public static class ChatRequest {
        private String userId;
        private String message;
    }
}

/*
 * ============================================================================
 * CHANGEMENTS VERSION 2.1 (Adapté UploadRateLimiter)
 * ============================================================================
 * 
 * ✅ Injection UploadRateLimiter
 *    - Service externe au lieu de LoadingCache interne
 *    - Réutilisable par d'autres controllers
 *    - Configuration centralisée dans UploadRateLimiter
 * 
 * ✅ Utilisation Simplifiée
 *    - uploadRateLimiter.tryAcquire(userId)
 *    - uploadRateLimiter.release(userId)
 *    - Pas de gestion cache complexe dans controller
 * 
 * ✅ Avantages Architecture
 *    - Séparation concerns (controller vs rate limiting)
 *    - Testabilité (mock UploadRateLimiter)
 *    - Réutilisabilité (autres endpoints upload)
 *    - Configuration unique (dans UploadRateLimiter)
 * 
 * CHANGEMENTS MINEURS:
 * - Suppression LoadingCache<String, Semaphore> dans controller
 * - Ajout @Autowired UploadRateLimiter dans constructeur
 * - Utilisation uploadRateLimiter.tryAcquire/release
 * - Reste du code identique (validation, async, métriques)
 */