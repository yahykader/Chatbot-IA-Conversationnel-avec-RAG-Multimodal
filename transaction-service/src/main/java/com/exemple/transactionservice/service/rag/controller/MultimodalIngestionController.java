// ============================================================================
// CONTROLLER - MultimodalIngestionController.java (VERSION COMPLÈTE MODIFIÉE)
// API REST pour ingestion multimodale avec gestion des doublons
// ============================================================================
package com.exemple.transactionservice.service.rag.controller;

import com.exemple.transactionservice.service.rag.ingestion.IngestionOrchestrator;
import com.exemple.transactionservice.service.rag.ingestion.model.IngestionResult;
import com.exemple.transactionservice.service.rag.ingestion.tracker.IngestionTracker;
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
import java.util.stream.Collectors;

/**
 * Contrôleur REST pour l'ingestion multimodale de documents.
 * VERSION COMPLÈTE avec monitoring avancé + gestion des doublons.
 * 
 * ✅ Support 1000+ formats de fichiers
 * ✅ Streaming automatique pour fichiers >100MB
 * ✅ Traitement synchrone et asynchrone
 * ✅ Gestion batch transactionnelle
 * ✅ Rollback en cas d'erreur
 * ✅ Monitoring temps réel
 * ✅ Health checks détaillés
 * ✅ Statistiques complètes
 * ✅ Détection automatique des doublons // ✅ AJOUTÉ
 * 
 * @author RAG Team
 * @version 2.1 (avec gestion doublons)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ingestion")
@Tag(name = "Ingestion", description = "API d'ingestion multimodale avec monitoring")
public class MultimodalIngestionController {
    
    private final IngestionOrchestrator ingestionService;
    private final IngestionTracker tracker;
    
    public MultimodalIngestionController(
            IngestionOrchestrator ingestionService,
            IngestionTracker tracker) {
        this.ingestionService = ingestionService;
        this.tracker = tracker;
        log.info("✅ Controller initialisé (avec monitoring avancé + gestion doublons)"); // ✅ MODIFIÉ
    }
    
    // ========================================================================
    // UPLOAD SYNCHRONE
    // ========================================================================
    
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload fichier (synchrone)", 
               description = "Streaming automatique >100MB, détection doublons automatique") // ✅ MODIFIÉ
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
                .duplicate(false) // ✅ AJOUTÉ
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Validation échouée: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(IngestionResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        
        // ✅ NOUVEAU CATCH AJOUTÉ ICI:
        } catch (DuplicateFileException e) {
            log.warn("⚠️ Doublon détecté: {} (batch existant: {})", 
                file.getOriginalFilename(), e.getExistingBatchId());
            
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(IngestionResponse.builder()
                    .success(false)
                    .filename(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .message("⚠️ Ce fichier a déjà été uploadé et traité")
                    .batchId(String.valueOf(e.getExistingBatchId()))
                    .duplicate(true)
                    .build());
        // FIN DU NOUVEAU CATCH
                    
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
    // UPLOAD ASYNCHRONE
    // ========================================================================
    
    @PostMapping(value = "/upload/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload asynchrone", 
               description = "Retourne immédiatement avec batchId")
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
            
            final String finalBatchId = batchId;
            ingestionService.ingestFileAsync(file, batchId)
                .thenAccept(result -> 
                    log.info("✅ Async OK: {} - text={} images={}",
                        file.getOriginalFilename(),
                        result.textEmbeddings(),
                        result.imageEmbeddings()))
                .exceptionally(ex -> {
                    // ✅ VÉRIFICATION DOUBLON AJOUTÉE:
                    if (ex.getCause() instanceof DuplicateFileException) {
                        DuplicateFileException dupEx = (DuplicateFileException) ex.getCause();
                        log.warn("⚠️ Async - Doublon détecté: {} (batch: {})", 
                            file.getOriginalFilename(), dupEx.getExistingBatchId());
                    } else {
                        log.error("❌ Async erreur: {}", file.getOriginalFilename(), ex);
                    }
                    return null;
                });
            
            AsyncResponse response = AsyncResponse.builder()
                .accepted(true)
                .batchId(batchId)
                .filename(file.getOriginalFilename())
                .message("Traitement démarré")
                .statusUrl("/api/v1/ingestion/status/" + batchId)
                .build();
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (Exception e) {
            log.error("❌ Erreur démarrage async", e);
            return ResponseEntity.badRequest()
                .body(AsyncResponse.builder()
                    .accepted(false)
                    .message(e.getMessage())
                    .build());
        }
    }
    
    // ========================================================================
    // UPLOAD BATCH
    // ========================================================================
    
    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload multiple fichiers", 
               description = "Traitement batch asynchrone")
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
            
            final String finalBatchId = batchId;
            ingestionService.ingestBatch(files, batchId)
                .thenAccept(results -> 
                    log.info("✅ Batch OK: {}/{} fichiers",
                        results.size(), files.size()))
                .exceptionally(ex -> {
                    // ✅ VÉRIFICATION DOUBLON AJOUTÉE:
                    if (ex.getCause() instanceof DuplicateFileException) {
                        log.warn("⚠️ Batch - Doublon(s) détecté(s)");
                    } else {
                        log.error("❌ Batch erreur", ex);
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
                .message("Batch en cours")
                .statusUrl("/api/v1/ingestion/status/" + batchId)
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
    // ✨ UPLOAD BATCH DÉTAILLÉ
    // ========================================================================

    /**
     * ✨ NOUVEAU : Upload batch avec résultat détaillé par fichier
     */
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
            
            final String finalBatchId = batchId;
            
            // Appel à la méthode détaillée du service
            ingestionService.ingestBatchDetailed(files, batchId)
                .thenAccept(batchResult -> 
                    log.info("✅ Batch détaillé OK: {}/{} succès, {} doublons, durée={}ms", // ✅ MODIFIÉ
                        batchResult.successCount(),
                        files.size(),
                        batchResult.duplicateCount(), // ✅ AJOUTÉ
                        batchResult.durationMs()))
                .exceptionally(ex -> {
                    // ✅ VÉRIFICATION DOUBLON AJOUTÉE:
                    if (ex.getCause() instanceof DuplicateFileException) {
                        log.warn("⚠️ Batch détaillé - Doublon(s) détecté(s)");
                    } else {
                        log.error("❌ Batch détaillé erreur", ex);
                    }
                    return null;
                });
            
            BatchDetailedResponse response = BatchDetailedResponse.builder()
                .accepted(true)
                .batchId(batchId)
                .fileCount(files.size())
                .totalSize(totalSize)
                .message("Batch détaillé en cours de traitement")
                .statusUrl("/api/v1/ingestion/status/" + batchId)
                .resultUrl("/api/v1/ingestion/batch/result/" + batchId)
                .build();
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (Exception e) {
            log.error("❌ Erreur batch détaillé", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BatchDetailedResponse.builder()
                    .accepted(false)
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
    // ✨ NOUVEAUX ENDPOINTS - MONITORING AVANCÉ
    // ========================================================================
    
    /**
     * ✨ NOUVEAU : Ingestions actives en temps réel
     */
    @GetMapping("/active")
    @Operation(summary = "Liste des ingestions en cours",
               description = "Monitoring temps réel des ingestions actives")
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
    
    /**
     * ✨ NOUVEAU : Statistiques globales du service
     */
    @GetMapping("/stats")
    @Operation(summary = "Statistiques globales",
               description = "Stats détaillées sur le service d'ingestion")
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
    
    /**
     * ✨ NOUVEAU : Health check détaillé
     */
    @GetMapping("/health/detailed")
    @Operation(summary = "Health check détaillé",
               description = "État détaillé du service avec tous les composants")
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
    
    /**
     * ✨ NOUVEAU : Liste des strategies disponibles
     */
    @GetMapping("/strategies")
    @Operation(summary = "Liste des strategies",
               description = "Toutes les strategies d'ingestion disponibles")
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
    
    // ========================================================================
    // HEALTH BASIQUE (conservé pour compatibilité)
    // ========================================================================
    
    @GetMapping("/health")
    @Operation(summary = "Health check basique")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "ingestion");
        health.put("timestamp", new Date());
        health.put("streaming", true);
        health.put("maxFileSize", "5GB");
        health.put("duplicateDetection", true); // ✅ AJOUTÉ
        
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
    // DTOs EXISTANTS
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
        private Boolean duplicate; // ✅ AJOUTÉ
    }
    
    @Data
    @Builder
    public static class AsyncResponse {
        private Boolean accepted;
        private String batchId;
        private String filename;
        private String message;
        private String statusUrl;
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
    
    // ========================================================================
    // ✨ NOUVEAUX DTOs - MONITORING
    // ========================================================================
    
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

    /**
     * ✨ NOUVEAU DTO : Réponse batch détaillé
     */
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
    }
}