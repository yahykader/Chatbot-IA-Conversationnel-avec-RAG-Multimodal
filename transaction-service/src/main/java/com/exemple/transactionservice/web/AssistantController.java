// // ============================================================================
// // BACKEND - AssistantController.java (v2.3 - NgRx Frontend Integration)
// // ============================================================================
// package com.exemple.transactionservice.controller;

// import com.exemple.transactionservice.dto.DuplicateInfo;
// import com.exemple.transactionservice.dto.UploadJob;
// import com.exemple.transactionservice.dto.UploadResponse;
// import com.exemple.transactionservice.dto.UploadStatusResponse;
// import com.exemple.transactionservice.dto.UploadStatus;
// import com.exemple.transactionservice.service.ConversationalAssistant;
// import com.exemple.transactionservice.service.IngestionOrchestrator;
// import com.exemple.transactionservice.service.UploadRateLimiter;
// import com.exemple.transactionservice.util.InMemoryMultipartFile;
// import io.micrometer.core.instrument.MeterRegistry;
// import lombok.Data;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.http.codec.ServerSentEvent;
// import org.springframework.web.bind.annotation.*;
// import org.springframework.web.multipart.MultipartFile;
// import reactor.core.publisher.Flux;

// import java.io.*;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.time.Duration;
// import java.time.Instant;
// import java.time.LocalDateTime;
// import java.util.*;
// import java.util.concurrent.*;
// import java.util.stream.Collectors;
// import java.security.MessageDigest;
// import java.security.NoSuchAlgorithmException;
// import java.util.concurrent.ConcurrentHashMap;

// /**
//  * ✅ AssistantController v2.3 - NgRx Frontend Integration
//  * 
//  * NOUVEAUTÉS v2.3:
//  * - Structure de réponse enrichie pour NgRx
//  * - Informations détaillées sur les duplicatas
//  * - Support CORS pour Angular
//  * - Format de réponse standardisé
//  */
// @Slf4j
// @RestController
// @RequestMapping("/api/assistant")
// public class AssistantController {

//     private final IngestionOrchestrator ingestionService;
//     private final ConversationalAssistant assistant;
//     private final UploadRateLimiter uploadRateLimiter;
//     private final MeterRegistry meterRegistry;
//     private final ScheduledExecutorService scheduler;

//     @Value("${assistant.stream.timeout-seconds:120}")
//     private int streamTimeoutSeconds;
    
//     @Value("${assistant.stream.heartbeat-interval-seconds:15}")
//     private int heartbeatIntervalSeconds;
    
//     @Value("${assistant.upload.max-file-size:20971520}")
//     private long maxFileSize;
    
//     @Value("${assistant.upload.allowed-extensions}")
//     private String allowedExtensions;
    
//     @Value("${assistant.upload.max-concurrent:3}")
//     private int maxConcurrentUploads;
    
//     @Value("${assistant.upload.temp-dir:${java.io.tmpdir}/multimodal-uploads}")
//     private String uploadTempDir;

//     // Tracking jobs upload
//     private final ConcurrentHashMap<String, UploadJob> uploadJobs = new ConcurrentHashMap<>();
    
//     // Déduplication par hash avec métadonnées complètes
//     private final ConcurrentHashMap<String, DuplicateInfo> uploadFingerprints = new ConcurrentHashMap<>();

//     public AssistantController(
//             IngestionOrchestrator ingestionService,
//             ConversationalAssistant assistant,
//             UploadRateLimiter uploadRateLimiter,
//             MeterRegistry meterRegistry) {
        
//         this.ingestionService = ingestionService;
//         this.assistant = assistant;
//         this.uploadRateLimiter = uploadRateLimiter;
//         this.meterRegistry = meterRegistry;
//         this.scheduler = Executors.newScheduledThreadPool(4);

//         log.info("✅ [Controller] Initialisé v2.3 - Timeout: {}s, Heartbeat: {}s, MaxUpload: {} MB, MaxConcurrent: {}",
//                  streamTimeoutSeconds, heartbeatIntervalSeconds, 
//                  maxFileSize / (1024 * 1024), maxConcurrentUploads);
//     }

//     // ============================================================================
//     // MÉTHODE UPLOAD COMPLÈTE - Version NgRx avec réponse enrichie
//     // ============================================================================

//     @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//     public ResponseEntity<UploadResponse> uploadFile(
//             @RequestParam("file") MultipartFile file,
//             @RequestParam(value = "userId", defaultValue = "1") Long userId) {
        
//         Instant start = Instant.now();
//         String jobId = UUID.randomUUID().toString();
        
//         try {
//             // ========================================================================
//             // VALIDATION
//             // ========================================================================
            
//             // Validation vide
//             if (file.isEmpty()) {
//                 return ResponseEntity.badRequest().body(
//                     UploadResponse.error("Fichier vide", null, file.getOriginalFilename())
//                 );
//             }
            
//             // Validation taille
//             if (file.getSize() > maxFileSize) {
//                 double sizeMB = file.getSize() / (1024.0 * 1024.0);
//                 double maxMB = maxFileSize / (1024.0 * 1024.0);
                
//                 log.warn("⚠️ [{}] Fichier trop volumineux: {:.2f} MB (max: {:.2f} MB)", 
//                         jobId, sizeMB, maxMB);
                
//                 return ResponseEntity.badRequest().body(
//                     UploadResponse.error(
//                         String.format("Fichier trop volumineux: %.2f MB (max: %.2f MB)", sizeMB, maxMB),
//                         null,
//                         file.getOriginalFilename()
//                     )
//                 );
//             }
            
//             // Validation nom fichier
//             String filename = file.getOriginalFilename();
//             if (filename == null || filename.isBlank()) {
//                 return ResponseEntity.badRequest().body(
//                     UploadResponse.error("Nom de fichier invalide", null, filename)
//                 );
//             }
            
//             // Validation extension
//             String extension = getFileExtension(filename).toLowerCase();
//             if (!isExtensionAllowed(extension)) {
//                 log.warn("⚠️ [{}] Extension non autorisée: {} (fichier: {})", 
//                         jobId, extension, filename);
                
//                 return ResponseEntity.badRequest().body(
//                     UploadResponse.error(
//                         "Type de fichier non autorisé: " + extension,
//                         null,
//                         filename
//                     )
//                 );
//             }
            
//             // Sanitize nom fichier
//             String sanitizedFilename = sanitizeFilename(filename);
            
//             // ========================================================================
//             // RATE LIMITING
//             // ========================================================================
            
//             if (!uploadRateLimiter.tryAcquire(String.valueOf(userId))) {
//                 log.warn("⚠️ [{}] Rate limit upload dépassé pour user: {}", jobId, userId);
                
//                 return ResponseEntity.status(429).body(
//                     UploadResponse.error(
//                         String.format("Trop d'uploads simultanés (max: %d)", maxConcurrentUploads),
//                         null,
//                         sanitizedFilename
//                     )
//                 );
//             }
            
//             // ========================================================================
//             // ✅ COPIE EN MÉMOIRE + DÉDUPLICATION (fingerprint)
//             // ========================================================================
            
//             try {
//                 log.info("📤 [{}] Upload démarré: {} ({} KB) - User: {}", 
//                         jobId, sanitizedFilename, file.getSize() / 1024, userId);
                
//                 // 1) Copier le fichier en mémoire AVANT l'async
//                 byte[] fileContent = file.getBytes();
                
//                 log.info("💾 [{}] Fichier copié en mémoire: {} bytes ({} KB)", 
//                         jobId, fileContent.length, fileContent.length / 1024);
                
//                 // 2) Fingerprint pour idempotence (anti-double upload/retry client)
//                 String fingerprint = userId + ":" + sha256(fileContent);

//                 // Si upload identique déjà en cours ou terminé => renvoyer info duplicata
//                 DuplicateInfo existingUpload = uploadFingerprints.get(fingerprint);
//                 if (existingUpload != null) {
//                     log.warn("⚠️ [{}] Upload dupliqué détecté (fingerprint match). Job existant: {} file={}",
//                             jobId, existingUpload.getJobId(), sanitizedFilename);

//                     // Important: on ne traite pas => on libère immédiatement le slot rate limiter
//                     uploadRateLimiter.release(String.valueOf(userId));

//                     // Retourner une réponse de duplicata avec toutes les infos
//                     return ResponseEntity.ok(
//                         UploadResponse.duplicate(
//                             existingUpload.getJobId(),
//                             sanitizedFilename,
//                             file.getSize(),
//                             existingUpload
//                         )
//                     );
//                 }
                
//                 // 3) Enregistrer ce nouveau fingerprint
//                 DuplicateInfo duplicateInfo = new DuplicateInfo(
//                     jobId,
//                     sanitizedFilename,
//                     LocalDateTime.now(),
//                     fingerprint,
//                     file.getSize()
//                 );
//                 uploadFingerprints.put(fingerprint, duplicateInfo);
                
//                 // 4) Créer un MultipartFile en mémoire (thread-safe)
//                 final MultipartFile inMemoryFile = new InMemoryMultipartFile(
//                     file.getName(),
//                     filename,  // Garder le nom original
//                     file.getContentType(),
//                     fileContent
//                 );
                
//                 log.info("✅ [{}] InMemoryMultipartFile créé: {} bytes disponibles", 
//                         jobId, inMemoryFile.getSize());
                
//                 // 5) Créer le job de tracking
//                 UploadJob job = new UploadJob(jobId, sanitizedFilename, file.getSize());
//                 uploadJobs.put(jobId, job);
                
//                 // ========================================================================
//                 // TRAITEMENT ASYNCHRONE
//                 // ========================================================================
                
//                 CompletableFuture.runAsync(() -> {
//                     try {
//                         job.setStatus(UploadStatus.PROCESSING);
//                         job.setProgress(10);
                        
//                         log.info("🔄 [{}] Ingestion en cours: {}", jobId, sanitizedFilename);
                        
//                         // ✅ INGESTION avec fichier en mémoire (thread-safe)
//                         ingestionService.ingestFile(inMemoryFile);
                        
//                         job.setStatus(UploadStatus.COMPLETED);
//                         job.setProgress(100);
//                         job.setCompletedAt(Instant.now());
                        
//                         Duration duration = Duration.between(start, Instant.now());
//                         log.info("✅ [{}] Upload terminé avec succès: {} en {}ms", 
//                                 jobId, sanitizedFilename, duration.toMillis());
                        
//                         // Métriques
//                         recordUploadMetrics(sanitizedFilename, file.getSize(), true, duration);
                        
//                     } catch (Exception e) {
//                         log.error("❌ [{}] Erreur lors de l'ingestion: {}", jobId, sanitizedFilename, e);
                        
//                         job.setStatus(UploadStatus.FAILED);
//                         job.setProgress(0);
//                         job.setErrorMessage(e.getMessage());
                        
//                         // Supprimer le fingerprint en cas d'échec pour permettre retry
//                         uploadFingerprints.remove(fingerprint);
                        
//                         recordUploadMetrics(sanitizedFilename, file.getSize(), false, 
//                                         Duration.between(start, Instant.now()));
                        
//                     } finally {
//                         // ✅ Libérer le slot du rate limiter
//                         uploadRateLimiter.release(String.valueOf(userId));
                        
//                         // ✅ Nettoyer le job après 5 minutes (garder le fingerprint plus longtemps)
//                         scheduler.schedule(() -> {
//                             uploadJobs.remove(jobId);
//                             log.debug("🗑️ [{}] Job nettoyé du cache", jobId);
//                         }, 5, TimeUnit.MINUTES);
                        
//                         // Nettoyer le fingerprint après 1 heure
//                         scheduler.schedule(() -> {
//                             uploadFingerprints.remove(fingerprint);
//                             log.debug("🗑️ [{}] Fingerprint nettoyé: {}", jobId, fingerprint.substring(0, 16) + "...");
//                         }, 1, TimeUnit.HOURS);
//                     }
//                 });
                
//                 // ========================================================================
//                 // RETOUR IMMÉDIAT AU CLIENT - Format NgRx
//                 // ========================================================================
                
//                 return ResponseEntity.ok(
//                     UploadResponse.success(jobId, sanitizedFilename, file.getSize())
//                 );
                
//             } catch (IOException e) {
//                 // Erreur lors de la lecture du fichier
//                 log.error("❌ [{}] Erreur lecture fichier: {}", jobId, e.getMessage());
//                 uploadRateLimiter.release(String.valueOf(userId));
                
//                 return ResponseEntity.status(500).body(
//                     UploadResponse.error("Impossible de lire le fichier: " + e.getMessage(), jobId, sanitizedFilename)
//                 );
//             }
            
//         } catch (IllegalArgumentException e) {
//             log.warn("⚠️ [{}] Validation échouée: {}", jobId, e.getMessage());
//             return ResponseEntity.badRequest().body(
//                 UploadResponse.error(e.getMessage(), jobId, file.getOriginalFilename())
//             );
            
//         } catch (Exception e) {
//             log.error("❌ [{}] Erreur inattendue lors de l'upload", jobId, e);
//             return ResponseEntity.status(500).body(
//                 UploadResponse.error("Erreur serveur lors de l'upload", jobId, file.getOriginalFilename())
//             );
//         }
//     }

//     /**
//      * SHA-256 hex (idempotence fingerprint)
//      */
//     private static String sha256(byte[] data) {
//         try {
//             MessageDigest md = MessageDigest.getInstance("SHA-256");
//             byte[] digest = md.digest(data);
//             StringBuilder sb = new StringBuilder(digest.length * 2);
//             for (byte b : digest) {
//                 sb.append(String.format("%02x", b));
//             }
//             return sb.toString();
//         } catch (NoSuchAlgorithmException e) {
//             throw new IllegalStateException("SHA-256 not available", e);
//         }
//     }

//     /**
//      * Endpoint status upload - Format NgRx
//      */
//     @GetMapping("/upload/status/{jobId}")
//     public ResponseEntity<UploadStatusResponse> getUploadStatus(@PathVariable String jobId) {
//         UploadJob job = uploadJobs.get(jobId);
        
//         if (job == null) {
//             return ResponseEntity.notFound().build();
//         }
        
//         return ResponseEntity.ok(new UploadStatusResponse(
//             jobId,
//             job.getFilename(),
//             job.getStatus().name().toLowerCase(),
//             job.getProgress(),
//             job.getMessage(),
//             job.getErrorMessage(),
//             job.getCreatedAt(),
//             job.getCompletedAt()
//         ));
//     }

//     /**
//      * Endpoint pour lister tous les uploads d'un utilisateur
//      */
//     @GetMapping("/uploads")
//     public ResponseEntity<List<UploadStatusResponse>> listUploads(
//             @RequestParam(value = "userId", required = false) Long userId) {
        
//         List<UploadStatusResponse> uploads = uploadJobs.values().stream()
//             .map(job -> new UploadStatusResponse(
//                 job.getJobId(),
//                 job.getFilename(),
//                 job.getStatus().name().toLowerCase(),
//                 job.getProgress(),
//                 job.getMessage(),
//                 job.getErrorMessage(),
//                 job.getCreatedAt(),
//                 job.getCompletedAt()
//             ))
//             .collect(Collectors.toList());
        
//         return ResponseEntity.ok(uploads);
//     }

//     // ========================================================================
//     // CHAT STREAMING (inchangé)
//     // ========================================================================

//     @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//     public Flux<ServerSentEvent<String>> chatStream(
//             @RequestParam("userId") String userId,
//             @RequestParam("message") String message) {

//         String sessionId = UUID.randomUUID().toString();
//         Instant start = Instant.now();
//         final int FLUSH_SIZE = 80;

//         log.info("💬 [{}] Chat streaming - User: {}, Message: '{}'",
//                  sessionId, userId, truncateMessage(message));

//         return Flux.defer(() -> {
//             StringBuilder fullResponse = new StringBuilder();
//             StringBuilder buffer = new StringBuilder();

//             Flux<ServerSentEvent<String>> heartbeat = Flux.interval(
//                 Duration.ofSeconds(heartbeatIntervalSeconds)
//             )
//             .map(tick -> ServerSentEvent.<String>builder()
//                 .event("heartbeat")
//                 .id(sessionId)
//                 .data("ping")
//                 .build())
//             .takeUntil(event -> false);

//             Flux<ServerSentEvent<String>> responseStream = assistant.chatStream(userId, message)
//                 .timeout(Duration.ofSeconds(streamTimeoutSeconds))
//                 .flatMap(token -> {
//                     if (token == null || token.isEmpty()) {
//                         return Flux.empty();
//                     }

//                     fullResponse.append(token);
//                     buffer.append(token);

//                     boolean flush = buffer.length() >= FLUSH_SIZE || 
//                                   token.contains("\n") || 
//                                   endsWithPunctuation(token);
                    
//                     if (!flush) {
//                         return Flux.empty();
//                     }

//                     String chunk = buffer.toString();
//                     buffer.setLength(0);

//                     return Flux.just(ServerSentEvent.<String>builder()
//                         .event("chunk")
//                         .id(sessionId)
//                         .data(chunk)
//                         .build());
//                 })
//                 .concatWith(Flux.defer(() -> {
//                     Flux<ServerSentEvent<String>> lastChunk = Flux.empty();
//                     if (buffer.length() > 0) {
//                         lastChunk = Flux.just(ServerSentEvent.<String>builder()
//                             .event("chunk")
//                             .id(sessionId)
//                             .data(buffer.toString())
//                             .build());
//                         buffer.setLength(0);
//                     }

//                     Flux<ServerSentEvent<String>> finalEvents = Flux.just(
//                         ServerSentEvent.<String>builder()
//                             .event("final")
//                             .id(sessionId)
//                             .data(fullResponse.toString())
//                             .build(),
//                         ServerSentEvent.<String>builder()
//                             .event("done")
//                             .id(sessionId)
//                             .data("[DONE]")
//                             .build()
//                     );

//                     return lastChunk.concatWith(finalEvents);
//                 }))
//                 .doOnComplete(() -> {
//                     Duration duration = Duration.between(start, Instant.now());
//                     log.info("✅ [{}] SSE terminé en {}ms ({} chars)", 
//                              sessionId, duration.toMillis(), fullResponse.length());
                    
//                     recordChatMetrics(userId, fullResponse.length(), duration, true);
//                 })
//                 .onErrorResume(err -> {
//                     log.error("❌ [{}] Erreur SSE", sessionId, err);
                    
//                     recordChatMetrics(userId, fullResponse.length(), 
//                                     Duration.between(start, Instant.now()), false);
                    
//                     return Flux.just(ServerSentEvent.<String>builder()
//                         .event("error")
//                         .id(sessionId)
//                         .data("ERROR: " + (err.getMessage() != null ? 
//                                           err.getMessage() : "Erreur inconnue"))
//                         .build());
//                 });

//             return Flux.merge(
//                 heartbeat.takeUntilOther(responseStream.last()),
//                 responseStream
//             );
//         });
//     }

//     // ========================================================================
//     // MÉTHODES PRIVÉES - VALIDATION (inchangées)
//     // ========================================================================

//     private String getFileExtension(String filename) {
//         if (filename == null || filename.isBlank()) {
//             return "";
//         }
        
//         int lastDot = filename.lastIndexOf('.');
//         if (lastDot == -1 || lastDot == filename.length() - 1) {
//             return "";
//         }
        
//         return filename.substring(lastDot + 1).toLowerCase();
//     }

//     private boolean isExtensionAllowed(String extension) {
//         if (allowedExtensions == null || allowedExtensions.isBlank()) {
//             return true;
//         }
        
//         String[] allowed = allowedExtensions.toLowerCase().split(",");
//         for (String ext : allowed) {
//             if (ext.trim().equals(extension)) {
//                 return true;
//             }
//         }
        
//         return false;
//     }

//     private String sanitizeFilename(String filename) {
//         if (filename == null) return "unknown";
        
//         filename = filename.replaceAll("\\.\\./", "");
//         filename = filename.replaceAll("\\.\\\\", "");
//         filename = filename.replaceAll("/", "_");
//         filename = filename.replaceAll("\\\\", "_");
//         filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        
//         if (filename.length() > 255) {
//             String extension = getFileExtension(filename);
//             String name = filename.substring(0, 255 - extension.length() - 1);
//             filename = name + "." + extension;
//         }
        
//         return filename;
//     }

//     private boolean endsWithPunctuation(String token) {
//         if (token == null || token.isEmpty()) {
//             return false;
//         }
        
//         char last = token.charAt(token.length() - 1);
//         return last == '.' || last == '!' || last == '?' || 
//                last == ';' || last == ':' || last == ',';
//     }

//     private String truncateMessage(String message) {
//         if (message == null) return "null";
//         if (message.length() <= 100) return message;
//         return message.substring(0, 97) + "...";
//     }

//     // ========================================================================
//     // MÉTHODES PRIVÉES - MÉTRIQUES (inchangées)
//     // ========================================================================

//     private void recordUploadMetrics(
//             String filename, 
//             long sizeBytes, 
//             boolean success,
//             Duration duration) {
        
//         try {
//             String extension = getFileExtension(filename);
            
//             meterRegistry.counter("uploads.total",
//                 "success", String.valueOf(success),
//                 "extension", extension
//             ).increment();
            
//             if (success) {
//                 meterRegistry.counter("uploads.bytes.total",
//                     "extension", extension
//                 ).increment(sizeBytes);
//             }
            
//             meterRegistry.summary("uploads.size.bytes",
//                 "extension", extension
//             ).record(sizeBytes);
            
//             if (success) {
//                 meterRegistry.timer("uploads.duration",
//                     "extension", extension
//                 ).record(duration);
//             }
            
//         } catch (Exception e) {
//             log.warn("⚠️ Erreur enregistrement métriques upload", e);
//         }
//     }

//     private void recordChatMetrics(
//             String userId, 
//             int responseLength,
//             Duration duration,
//             boolean success) {
        
//         try {
//             meterRegistry.counter("chat.messages.total",
//                 "success", String.valueOf(success)
//             ).increment();
            
//             meterRegistry.timer("chat.duration",
//                 "success", String.valueOf(success)
//             ).record(duration);
            
//             if (success) {
//                 meterRegistry.summary("chat.response.length").record(responseLength);
//             }
            
//         } catch (Exception e) {
//             log.warn("⚠️ Erreur enregistrement métriques chat", e);
//         }
//     }
// }

// /*
//  * ============================================================================
//  * CHANGEMENTS VERSION 2.3 (NgRx Frontend Integration)
//  * ============================================================================
//  * 
//  * ✅ STRUCTURE DE RÉPONSE ENRICHIE
//  *    - UploadResponse avec tous les champs nécessaires pour NgRx
//  *    - Format standardisé: success, duplicate, error
//  *    - Informations détaillées sur les duplicatas
//  * 
//  * ✅ GESTION COMPLÈTE DES DUPLICATAS
//  *    - DuplicateInfo avec métadonnées complètes
//  *    - Cache des fingerprints avec nettoyage automatique (1h)
//  *    - Informations renvoyées au frontend pour décision utilisateur
//  * 
//  * ✅ SUPPORT CORS
//  *    - Configuration CORS pour Angular (localhost:4200)
//  *    - Paramétrable via application.properties
//  * 
//  * ✅ ENDPOINTS SUPPLÉMENTAIRES
//  *    - GET /upload/status/{jobId} - Statut détaillé d'un upload
//  *    - GET /uploads - Liste tous les uploads (optionnel: par userId)
//  * 
//  * ✅ COMPATIBILITÉ NgRx
//  *    - Types de retour typés (UploadResponse, UploadStatusResponse)
//  *    - Structure JSON cohérente
//  *    - Support de tous les cas d'usage NgRx
//  * 
//  * CONFIGURATION application.properties:
//  * assistant.cors.allowed-origins=http://localhost:4200
//  * 
//  * EXEMPLE RÉPONSE SUCCESS:
//  * {
//  *   "jobId": "abc-123",
//  *   "fileName": "document.pdf",
//  *   "status": "processing",
//  *   "message": "Upload démarré avec succès",
//  *   "isDuplicate": false,
//  *   "fileSize": 1024000,
//  *   "fileSizeKB": 1000
//  * }
//  * 
//  * EXEMPLE RÉPONSE DUPLICATE:
//  * {
//  *   "jobId": "existing-456",
//  *   "fileName": "document.pdf",
//  *   "status": "duplicate",
//  *   "message": "Fichier déjà uploadé",
//  *   "isDuplicate": true,
//  *   "existingJobId": "existing-456",
//  *   "duplicateInfo": {
//  *     "jobId": "existing-456",
//  *     "originalFileName": "document.pdf",
//  *     "uploadedAt": "2026-01-24T18:28:10",
//  *     "fingerprint": "abc123...",
//  *     "fileSize": 1024000
//  *   },
//  *   "fileSize": 1024000,
//  *   "fileSizeKB": 1000
//  * }
//  */