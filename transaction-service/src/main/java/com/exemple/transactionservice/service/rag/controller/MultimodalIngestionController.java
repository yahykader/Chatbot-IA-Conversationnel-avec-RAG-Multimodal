// ============================================================================
// CONTROLLER - MultimodalIngestionController.java (VERSION COMPLÈTE OPTIMISÉE)
// API REST pour ingestion multimodale avec gestion avancée des doublons
// ============================================================================
package com.exemple.transactionservice.service.rag.controller;

import com.exemple.transactionservice.service.rag.ingestion.IngestionOrchestrator;
import com.exemple.transactionservice.service.rag.ingestion.model.IngestionResult;
import com.exemple.transactionservice.service.rag.ingestion.tracker.IngestionTracker;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.progress.ProgressService;
import com.exemple.transactionservice.exception.DuplicateFileException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Contrôleur REST pour l'ingestion multimodale de documents.
 * VERSION COMPLÈTE OPTIMISÉE avec gestion avancée des doublons.
 * 
 * ✅ Support 1000+ formats de fichiers
 * ✅ Streaming automatique pour fichiers >100MB
 * ✅ Traitement synchrone et asynchrone
 * ✅ Gestion batch transactionnelle
 * ✅ Rollback en cas d'erreur
 * ✅ Monitoring temps réel
 * ✅ Health checks détaillés
 * ✅ Statistiques complètes
 * ✅ Détection doublons AVANT traitement async
 * ✅ Notifications WebSocket pour erreurs
 * ✅ Info batch existant dans réponses 409
 * 
 * @author RAG Team
 * @version 3.0 (Gestion doublons optimisée)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ingestion")
@Tag(name = "Ingestion", description = "API d'ingestion multimodale avec monitoring avancé")
public class MultimodalIngestionController {
    
    private final IngestionOrchestrator ingestionService;
    private final IngestionTracker tracker;
    private final DeduplicationService deduplicationService;
    private final ProgressService progressService;
    
    public MultimodalIngestionController(
            IngestionOrchestrator ingestionService,
            IngestionTracker tracker,
            DeduplicationService deduplicationService,
            ProgressService progressService) {
        
        this.ingestionService = ingestionService;
        this.tracker = tracker;
        this.deduplicationService = deduplicationService;
        this.progressService = progressService;
        
        log.info("✅ Controller initialisé (monitoring + gestion doublons optimisée)");
    }
    
    // ========================================================================
    // UPLOAD SYNCHRONE
    // ========================================================================
    
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload fichier (synchrone)", 
               description = "Streaming automatique >100MB, détection doublons automatique")
    public ResponseEntity<IngestionResponse> uploadFile(
            @Parameter(description = "Fichier à ingérer")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "ID batch (optionnel)")
            @RequestParam(value = "batchId", required = false) String batchId) {
        
        try {
            validateFile(file);
            
            if (batchId == null || batchId.isBlank()) {
                batchId = UUID.randomUUID().toString();
            }
            
            long start = System.currentTimeMillis();
            
            log.info("📥 Upload: {} ({} MB) - batch: {}",
                file.getOriginalFilename(),
                file.getSize() / 1_000_000,
                batchId);
            
            IngestionResult result = ingestionService.ingestFile(file, batchId);
            
            long duration = System.currentTimeMillis() - start;
            
            log.info("✅ Ingestion OK: text={} images={} durée={}ms",
                result.textEmbeddings(),
                result.imageEmbeddings(),
                duration);
            
            IngestionResponse response = IngestionResponse.builder()
                .success(true)
                .batchId(batchId)
                .filename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .textEmbeddings(result.textEmbeddings())
                .imageEmbeddings(result.imageEmbeddings())
                .durationMs(duration)
                .streamingUsed(file.getSize() > 100_000_000)
                .message("Ingestion réussie")
                .duplicate(false)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Validation échouée: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(IngestionResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        
        } catch (DuplicateFileException e) {
            log.warn("⚠️ Doublon détecté: {} (batch existant: {})", 
                file.getOriginalFilename(), e.getExistingBatchId());
            
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(IngestionResponse.builder()
                    .success(false)
                    .filename(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .message("⚠️ Ce fichier a déjà été uploadé et traité")
                    .batchId(e.getExistingBatchId())
                    .duplicate(true)
                    .existingBatchId(e.getExistingBatchId())
                    .build());
                    
        } catch (Exception e) {
            log.error("❌ Erreur ingestion: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(IngestionResponse.builder()
                    .success(false)
                    .message("Erreur: " + e.getMessage())
                    .build());
        }
    }
    
    // ========================================================================
    // UPLOAD ASYNCHRONE OPTIMISÉ
    // ========================================================================
    
    @PostMapping(value = "/upload/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload asynchrone", 
               description = "Retourne immédiatement avec batchId, détection doublons avant traitement")
    public ResponseEntity<AsyncResponse> uploadFileAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "batchId", required = false) String batchId) {
        
        try {
            validateFile(file);
            
            if (batchId == null || batchId.isBlank()) {
                batchId = UUID.randomUUID().toString();
            }
            
            log.info("📥 Upload async: {} - batch: {}",
                file.getOriginalFilename(), batchId);
            
            // ========================================================================
            // AMÉLIORATION 1: Vérifier doublon AVANT le traitement async
            // ========================================================================
            try {
                byte[] fileContent = file.getBytes();
                String hash = deduplicationService.computeHash(fileContent);
                
                if (deduplicationService.isDuplicateByHash(hash)) {
                    String existingBatchId = deduplicationService.getExistingBatchId(hash);
                    
                    log.warn("⚠️ Async - Doublon détecté AVANT traitement: {} (batch existant: {})", 
                        file.getOriginalFilename(), existingBatchId);
                    
                    // ✅ Retourner immédiatement une erreur 409
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(AsyncResponse.builder()
                            .accepted(false)
                            .batchId(existingBatchId)
                            .filename(file.getOriginalFilename())
                            .message("⚠️ Ce fichier a déjà été uploadé")
                            .statusUrl("/api/v1/ingestion/status/" + existingBatchId)
                            .duplicate(true)
                            .existingBatchId(existingBatchId)
                            .build());
                }
            } catch (Exception e) {
                log.warn("⚠️ Erreur vérification doublon (traitement continue): {}", e.getMessage());
                // Continue l'upload si erreur dedup (ne pas bloquer)
            }
            
            // ========================================================================
            // AMÉLIORATION 2: Gérer les erreurs dans le CompletableFuture
            // ========================================================================
            final String finalBatchId = batchId;
            
            ingestionService.ingestFileAsync(file, batchId)
                .thenAccept(result -> 
                    log.info("✅ Async OK: {} - text={} images={}",
                        file.getOriginalFilename(),
                        result.textEmbeddings(),
                        result.imageEmbeddings()))
                .exceptionally(ex -> {
                    // Gestion doublon (si pas détecté avant)
                    if (ex.getCause() instanceof DuplicateFileException) {
                        DuplicateFileException dupEx = (DuplicateFileException) ex.getCause();
                        
                        log.warn("⚠️ Async - Doublon détecté PENDANT traitement: {} (batch: {})", 
                            file.getOriginalFilename(), dupEx.getExistingBatchId());
                        
                        // ✅ AMÉLIORATION: Envoyer notification WebSocket
                        try {
                            progressService.error(
                                finalBatchId, 
                                file.getOriginalFilename(), 
                                "⚠️ Fichier déjà uploadé (batch: " + dupEx.getExistingBatchId() + ")"
                            );
                        } catch (Exception e) {
                            log.debug("Impossible d'envoyer notification WebSocket", e);
                        }
                        
                    } else {
                        log.error("❌ Async erreur: {}", file.getOriginalFilename(), ex);
                        
                        // ✅ AMÉLIORATION: Envoyer notification WebSocket erreur
                        try {
                            progressService.error(
                                finalBatchId, 
                                file.getOriginalFilename(), 
                                "❌ Erreur: " + ex.getMessage()
                            );
                        } catch (Exception e) {
                            log.debug("Impossible d'envoyer notification WebSocket", e);
                        }
                    }
                    return null;
                });
            
            // Retour immédiat ACCEPTED (car pas de doublon détecté)
            AsyncResponse response = AsyncResponse.builder()
                .accepted(true)
                .batchId(batchId)
                .filename(file.getOriginalFilename())
                .message("Traitement démarré")
                .statusUrl("/api/v1/ingestion/status/" + batchId)
                .duplicate(false)
                .build();
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (Exception e) {
            log.error("❌ Erreur démarrage async", e);
            return ResponseEntity.badRequest()
                .body(AsyncResponse.builder()
                    .accepted(false)
                    .message(e.getMessage())
                    .duplicate(false)
                    .build());
        }
    }
    
    // ========================================================================
    // UPLOAD BATCH OPTIMISÉ
    // ========================================================================
    
    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload multiple fichiers", 
               description = "Traitement batch asynchrone avec détection doublons pré-traitement")
    public ResponseEntity<BatchResponse> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "batchId", required = false) String batchId) {
        
        try {
            if (files == null || files.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(BatchResponse.builder()
                        .success(false)
                        .message("Aucun fichier fourni")
                        .build());
            }
            
            if (batchId == null || batchId.isBlank()) {
                batchId = UUID.randomUUID().toString();
            }
            
            long totalSize = files.stream()
                .mapToLong(MultipartFile::getSize)
                .sum();
            
            log.info("📥 Batch: {} fichiers ({} MB) - batch: {}",
                files.size(), totalSize / 1_000_000, batchId);
            
            // ========================================================================
            // ✅ AMÉLIORATION: Pré-vérifier les doublons avant le traitement
            // ========================================================================
            List<String> duplicates = new ArrayList<>();
            Map<String, String> duplicateInfo = new HashMap<>();
            
            for (MultipartFile file : files) {
                try {
                    byte[] content = file.getBytes();
                    String hash = deduplicationService.computeHash(content);
                    
                    if (deduplicationService.isDuplicateByHash(hash)) {
                        String existingBatchId = deduplicationService.getExistingBatchId(hash);
                        duplicates.add(file.getOriginalFilename());
                        duplicateInfo.put(file.getOriginalFilename(), existingBatchId);
                        
                        log.warn("⚠️ Doublon détecté dans batch: {} (batch existant: {})", 
                            file.getOriginalFilename(), existingBatchId);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Erreur vérification doublon pour: {}", 
                        file.getOriginalFilename(), e);
                }
            }
            
            // ========================================================================
            // ✅ AMÉLIORATION: Informer le client si des doublons sont détectés
            // ========================================================================
            if (!duplicates.isEmpty()) {
                log.warn("⚠️ Batch contient {} doublon(s) sur {} fichiers", 
                    duplicates.size(), files.size());
            }
            
            final String finalBatchId = batchId;
            final int duplicateCount = duplicates.size();
            
            ingestionService.ingestBatch(files, batchId)
                .thenAccept(results -> 
                    log.info("✅ Batch OK: {}/{} fichiers traités ({} doublons skippés)",
                        results.size(), files.size(), duplicateCount))
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof DuplicateFileException) {
                        log.warn("⚠️ Batch - Doublon(s) détecté(s) pendant traitement");
                    } else {
                        log.error("❌ Batch erreur", ex);
                    }
                    
                    // ✅ Envoyer notification WebSocket
                    try {
                        progressService.error(
                            finalBatchId, 
                            "batch", 
                            "Erreur traitement batch: " + ex.getMessage()
                        );
                    } catch (Exception e) {
                        log.debug("Impossible d'envoyer notification WebSocket", e);
                    }
                    
                    return null;
                });
            
            List<String> filenames = files.stream()
                .map(MultipartFile::getOriginalFilename)
                .toList();
            
            BatchResponse response = BatchResponse.builder()
                .success(true)
                .batchId(batchId)
                .fileCount(files.size())
                .filenames(filenames)
                .totalSize(totalSize)
                .message(duplicates.isEmpty() 
                    ? "Batch en cours" 
                    : String.format("Batch en cours (%d doublon(s) seront skippés)", duplicateCount))
                .statusUrl("/api/v1/ingestion/status/" + batchId)
                .duplicateCount(duplicateCount)
                .duplicateFiles(duplicates)
                .duplicateInfo(duplicateInfo)
                .build();
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (Exception e) {
            log.error("❌ Erreur batch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BatchResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    // ========================================================================
    // UPLOAD BATCH DÉTAILLÉ OPTIMISÉ
    // ========================================================================

    @PostMapping(value = "/upload/batch/detailed", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload batch avec détails par fichier", 
            description = "Retourne le résultat détaillé pour chaque fichier du batch")
    public ResponseEntity<BatchDetailedResponse> uploadBatchDetailed(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "batchId", required = false) String batchId) {
        
        try {
            if (files == null || files.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(BatchDetailedResponse.builder()
                        .success(false)
                        .message("Aucun fichier fourni")
                        .build());
            }
            
            if (batchId == null || batchId.isBlank()) {
                batchId = UUID.randomUUID().toString();
            }
            
            long totalSize = files.stream()
                .mapToLong(MultipartFile::getSize)
                .sum();
            
            log.info("📥 Batch détaillé: {} fichiers ({} MB) - batch: {}",
                files.size(), totalSize / 1_000_000, batchId);
            
            // ========================================================================
            // ✅ AMÉLIORATION: Pré-vérifier les doublons
            // ========================================================================
            List<String> duplicates = new ArrayList<>();
            Map<String, String> duplicateInfo = new HashMap<>();
            
            for (MultipartFile file : files) {
                try {
                    byte[] content = file.getBytes();
                    String hash = deduplicationService.computeHash(content);
                    
                    if (deduplicationService.isDuplicateByHash(hash)) {
                        String existingBatchId = deduplicationService.getExistingBatchId(hash);
                        duplicates.add(file.getOriginalFilename());
                        duplicateInfo.put(file.getOriginalFilename(), existingBatchId);
                        
                        log.warn("⚠️ Doublon batch détaillé: {} (batch: {})", 
                            file.getOriginalFilename(), existingBatchId);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Erreur vérif doublon: {}", file.getOriginalFilename(), e);
                }
            }
            
            final String finalBatchId = batchId;
            final int duplicateCount = duplicates.size();
            
            // Appel à la méthode détaillée du service
            ingestionService.ingestBatchDetailed(files, batchId)
                .thenAccept(batchResult -> 
                    log.info("✅ Batch détaillé OK: {}/{} succès, {} doublons, durée={}ms",
                        batchResult.successCount(),
                        files.size(),
                        batchResult.duplicateCount(),
                        batchResult.durationMs()))
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof DuplicateFileException) {
                        log.warn("⚠️ Batch détaillé - Doublon(s) détecté(s)");
                    } else {
                        log.error("❌ Batch détaillé erreur", ex);
                    }
                    
                    // ✅ WebSocket notification
                    try {
                        progressService.error(
                            finalBatchId, 
                            "batch-detailed", 
                            "Erreur: " + ex.getMessage()
                        );
                    } catch (Exception e) {
                        log.debug("Impossible d'envoyer notification WebSocket", e);
                    }
                    
                    return null;
                });
            
            BatchDetailedResponse response = BatchDetailedResponse.builder()
                .accepted(true)
                .success(true)
                .batchId(batchId)
                .fileCount(files.size())
                .totalSize(totalSize)
                .message(duplicates.isEmpty()
                    ? "Batch détaillé en cours de traitement"
                    : String.format("Batch en cours (%d doublon(s) détectés)", duplicateCount))
                .statusUrl("/api/v1/ingestion/status/" + batchId)
                .resultUrl("/api/v1/ingestion/batch/result/" + batchId)
                .duplicateCount(duplicateCount)
                .duplicateFiles(duplicates)
                .duplicateInfo(duplicateInfo)
                .build();
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (Exception e) {
            log.error("❌ Erreur batch détaillé", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BatchDetailedResponse.builder()
                    .accepted(false)
                    .success(false)
                    .message("Erreur: " + e.getMessage())
                    .build());
        }
    }
    
    // ========================================================================
    // STATUS
    // ========================================================================
    
    @GetMapping("/status/{batchId}")
    @Operation(summary = "Statut d'une ingestion")
    public ResponseEntity<StatusResponse> getStatus(@PathVariable String batchId) {
        try {
            var textIds = tracker.getTextEmbeddingIds(batchId);
            var imageIds = tracker.getImageEmbeddingIds(batchId);
            
            if (textIds.isEmpty() && imageIds.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(StatusResponse.builder()
                        .found(false)
                        .batchId(batchId)
                        .message("Batch non trouvé")
                        .build());
            }
            
            StatusResponse response = StatusResponse.builder()
                .found(true)
                .batchId(batchId)
                .textEmbeddings(textIds.size())
                .imageEmbeddings(imageIds.size())
                .totalEmbeddings(textIds.size() + imageIds.size())
                .message("Batch trouvé")
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erreur status: {}", batchId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(StatusResponse.builder()
                    .found(false)
                    .batchId(batchId)
                    .message("Erreur: " + e.getMessage())
                    .build());
        }
    }
    
    // ========================================================================
    // ROLLBACK
    // ========================================================================
    
    @DeleteMapping("/rollback/{batchId}")
    @Operation(summary = "Rollback d'une ingestion")
    public ResponseEntity<RollbackResponse> rollback(@PathVariable String batchId) {
        try {
            log.info("🔄 Rollback: {}", batchId);
            
            int deleted = tracker.rollbackBatch(batchId);
            
            log.info("✅ Rollback OK: {} - {} embeddings supprimés", batchId, deleted);
            
            RollbackResponse response = RollbackResponse.builder()
                .success(true)
                .batchId(batchId)
                .deletedCount(deleted)
                .message("Rollback réussi - " + deleted + " embeddings supprimés")
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erreur rollback: {}", batchId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RollbackResponse.builder()
                    .success(false)
                    .batchId(batchId)
                    .message("Erreur: " + e.getMessage())
                    .build());
        }
    }
    
    // ========================================================================
    // MONITORING AVANCÉ
    // ========================================================================
    
    @GetMapping("/active")
    @Operation(summary = "Liste des ingestions en cours")
    public ResponseEntity<ActiveIngestionsResponse> getActiveIngestions() {
        try {
            var activeList = ingestionService.getActiveIngestions();
            
            List<Map<String, Object>> ingestions = activeList.stream()
                .map(status -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("batchId", status.getBatchId());
                    map.put("filename", status.getFilename());
                    map.put("strategy", status.getStrategy());
                    map.put("startTime", status.getStartTime());
                    map.put("completed", status.isCompleted());
                    map.put("success", status.isSuccess());
                    map.put("duration", status.getDuration());
                    return map;
                })
                .toList();
            
            ActiveIngestionsResponse response = ActiveIngestionsResponse.builder()
                .count(ingestions.size())
                .ingestions(ingestions)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erreur récupération ingestions actives", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/stats")
    @Operation(summary = "Statistiques globales")
    public ResponseEntity<StatsResponse> getStats() {
        try {
            var stats = ingestionService.getStats();
            
            StatsResponse response = StatsResponse.builder()
                .strategiesCount(stats.strategiesCount())
                .activeIngestions(stats.activeIngestions())
                .trackerBatches(stats.trackerBatches())
                .trackerEmbeddings(stats.trackerEmbeddings())
                .filesInProgress(stats.filesInProgress())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erreur récupération stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/health/detailed")
    @Operation(summary = "Health check détaillé")
    public ResponseEntity<DetailedHealthResponse> healthDetailed() {
        try {
            var healthReport = ingestionService.getHealthReport();
            
            DetailedHealthResponse response = DetailedHealthResponse.builder()
                .status(healthReport.status())
                .healthy(healthReport.isHealthy())
                .strategies(healthReport.strategies())
                .activeIngestions(healthReport.activeIngestions())
                .trackerBatches(healthReport.trackerBatches())
                .redisHealthy(healthReport.redisHealthy())
                .antivirusHealthy(healthReport.antivirusHealthy())
                .antivirusEnabled(healthReport.antivirusEnabled())
                .timestamp(new Date())
                .build();
            
            HttpStatus httpStatus = healthReport.isHealthy() ? 
                HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            
            return ResponseEntity.status(httpStatus).body(response);
            
        } catch (Exception e) {
            log.error("❌ Erreur health check détaillé", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DetailedHealthResponse.builder()
                    .status("ERROR")
                    .healthy(false)
                    .timestamp(new Date())
                    .build());
        }
    }
    
    @GetMapping("/strategies")
    @Operation(summary = "Liste des strategies")
    public ResponseEntity<StrategiesResponse> getStrategies() {
        try {
            var strategiesInfo = ingestionService.getStrategiesInfo();
            
            List<Map<String, Object>> strategies = strategiesInfo.stream()
                .map(info -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", info.name());
                    map.put("priority", info.priority());
                    map.put("className", info.className());
                    return map;
                })
                .toList();
            
            StrategiesResponse response = StrategiesResponse.builder()
                .count(strategies.size())
                .strategies(strategies)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erreur récupération strategies", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/health")
    @Operation(summary = "Health check basique")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "ingestion");
        health.put("timestamp", new Date());
        health.put("streaming", true);
        health.put("maxFileSize", "5GB");
        health.put("duplicateDetection", true);
        health.put("websocketProgress", true);
        
        return ResponseEntity.ok(health);
    }
    
    // ========================================================================
    // VALIDATION
    // ========================================================================
    
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide");
        }
        
        if (file.getOriginalFilename() == null) {
            throw new IllegalArgumentException("Nom fichier absent");
        }
        
        long maxSize = 5L * 1024 * 1024 * 1024; // 5GB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                String.format("Fichier trop gros: %d MB (max: 5000 MB)",
                    file.getSize() / 1_000_000));
        }
    }
    
    // ========================================================================
    // DTOs OPTIMISÉS
    // ========================================================================
    
    @Data
    @Builder
    public static class IngestionResponse {
        private Boolean success;
        private String batchId;
        private String filename;
        private Long fileSize;
        private Integer textEmbeddings;
        private Integer imageEmbeddings;
        private Long durationMs;
        private Boolean streamingUsed;
        private String message;
        private Boolean duplicate;
        private String existingBatchId;  // ✅ NOUVEAU
    }
    
    @Data
    @Builder
    public static class AsyncResponse {
        private Boolean accepted;
        private String batchId;
        private String filename;
        private String message;
        private String statusUrl;
        private Boolean duplicate;        // ✅ NOUVEAU
        private String existingBatchId;   // ✅ NOUVEAU
    }
    
    @Data
    @Builder
    public static class BatchResponse {
        private Boolean success;
        private String batchId;
        private Integer fileCount;
        private List<String> filenames;
        private Long totalSize;
        private String message;
        private String statusUrl;
        private Integer duplicateCount;           // ✅ NOUVEAU
        private List<String> duplicateFiles;      // ✅ NOUVEAU
        private Map<String, String> duplicateInfo; // ✅ NOUVEAU: filename → existingBatchId
    }
    
    @Data
    @Builder
    public static class BatchDetailedResponse {
        private Boolean accepted;
        private Boolean success;
        private String batchId;
        private Integer fileCount;
        private Long totalSize;
        private String message;
        private String statusUrl;
        private String resultUrl;
        private Integer duplicateCount;           // ✅ NOUVEAU
        private List<String> duplicateFiles;      // ✅ NOUVEAU
        private Map<String, String> duplicateInfo; // ✅ NOUVEAU
    }
    
    @Data
    @Builder
    public static class StatusResponse {
        private Boolean found;
        private String batchId;
        private Integer textEmbeddings;
        private Integer imageEmbeddings;
        private Integer totalEmbeddings;
        private String message;
    }
    
    @Data
    @Builder
    public static class RollbackResponse {
        private Boolean success;
        private String batchId;
        private Integer deletedCount;
        private String message;
    }
    
    @Data
    @Builder
    public static class ActiveIngestionsResponse {
        private Integer count;
        private List<Map<String, Object>> ingestions;
    }
    
    @Data
    @Builder
    public static class StatsResponse {
        private Integer strategiesCount;
        private Integer activeIngestions;
        private Integer trackerBatches;
        private Integer trackerEmbeddings;
        private Integer filesInProgress;
    }
    
    @Data
    @Builder
    public static class DetailedHealthResponse {
        private String status;
        private Boolean healthy;
        private Integer strategies;
        private Integer activeIngestions;
        private Integer trackerBatches;
        private Boolean redisHealthy;
        private Boolean antivirusHealthy;
        private Boolean antivirusEnabled;
        private Date timestamp;
    }
    
    @Data
    @Builder
    public static class StrategiesResponse {
        private Integer count;
        private List<Map<String, Object>> strategies;
    }
}