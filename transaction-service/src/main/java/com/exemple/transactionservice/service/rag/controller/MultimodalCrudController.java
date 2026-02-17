package com.exemple.transactionservice.service.rag.controller;

import com.exemple.transactionservice.service.rag.ingestion.IngestionOrchestrator;
import com.exemple.transactionservice.service.rag.ingestion.repository.EmbeddingRepository;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.TextDeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.cache.EmbeddingCache;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/crud")
@Tag(name = "CRUD Embeddings", description = "API CRUD pour gestion des embeddings")
public class MultimodalCrudController {

    private final EmbeddingRepository embeddingRepository;
    private final IngestionOrchestrator ingestionService;
    private final DeduplicationService deduplicationService;
    private final TextDeduplicationService textDeduplicationService;
    private final EmbeddingCache embeddingCache;
    

    public MultimodalCrudController(
            EmbeddingRepository embeddingRepository,  
            IngestionOrchestrator ingestionService,
            DeduplicationService deduplicationService,
            TextDeduplicationService textDeduplicationService,
            EmbeddingCache embeddingCache) {

        this.embeddingRepository = embeddingRepository;  
        this.ingestionService = ingestionService;
        this.deduplicationService = deduplicationService;
        this.textDeduplicationService = textDeduplicationService;
        this.embeddingCache = embeddingCache;
        
        log.info("✅ MultimodalCrudController initialisé");
    }

    // ========================================================================
    // SUPPRESSION INDIVIDUELLE
    // ========================================================================
    
    /**
     * Supprime un embedding spécifique par son ID
     */
    @DeleteMapping("/file/{embeddingId}")
    @Operation(summary = "Supprimer un fichier par ID",
            description = "Supprime un embedding texte ou image spécifique")
    public ResponseEntity<DeleteResponse> deleteFile(
            @Parameter(description = "ID de l'embedding à supprimer")
            @PathVariable String embeddingId,
            @Parameter(description = "Type: 'text' ou 'image'")
            @RequestParam(defaultValue = "text") String type) {
        
        try {
            log.info("🗑️ DELETE /file/{} (type: {})", embeddingId, type);
            
            boolean deleted = false;
            
            if ("text".equalsIgnoreCase(type)) {
                deleted = embeddingRepository.deleteText(embeddingId);
            } else if ("image".equalsIgnoreCase(type)) {
                deleted = embeddingRepository.deleteImage(embeddingId);
            } else {
                return ResponseEntity.badRequest()
                    .body(DeleteResponse.builder()
                        .success(false)
                        .message("Type invalide. Utilisez 'text' ou 'image'")
                        .build());
            }
            
            if (deleted) {
                log.info("✅ Fichier supprimé: {} ({})", embeddingId, type);
                return ResponseEntity.ok(DeleteResponse.builder()
                    .success(true)
                    .deletedCount(1)
                    .embeddingId(embeddingId)
                    .type(type)
                    .message("Fichier supprimé avec succès")
                    .build());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(DeleteResponse.builder()
                        .success(false)
                        .deletedCount(0)
                        .embeddingId(embeddingId)
                        .type(type)
                        .message("Fichier non trouvé")
                        .build());
            }
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression: {}", embeddingId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DeleteResponse.builder()
                    .success(false)
                    .message("Erreur: " + e.getMessage())
                    .build());
        }
    }

    // ========================================================================
    // ✅ SUPPRESSION PAR BATCH - AVEC NETTOYAGE REDIS
    // ========================================================================
    
    @DeleteMapping("/batch/{batchId}/files")
    @Operation(summary = "Supprimer tous les fichiers d'un batch",
            description = "Supprime tous les embeddings (texte + images) d'un batch + cache Redis")
    public ResponseEntity<DeleteResponse> deleteBatchFiles(
            @PathVariable String batchId) {
        
        try {
            log.info("🗑️ DELETE /batch/{}/files", batchId);
            
            // ✅ MODIFIÉ: Utiliser orchestrator (qui appelle cleanupRedisCaches)
            if (!embeddingRepository.batchExists(batchId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(DeleteResponse.builder()
                        .success(false)
                        .batchId(batchId)
                        .message("Batch non trouvé")
                        .build());
            }
            
            // Récupérer stats avant suppression
            Map<String, Integer> stats = embeddingRepository.getBatchStats(batchId);
            
            // ✅ Appel orchestrator (qui fait TOUT le nettoyage)
            int deleted = embeddingRepository.deleteBatch(batchId);
            
            String message = String.format(
                "Batch supprimé: %d embeddings (text: %d, images: %d) + tous les caches Redis",
                deleted,
                stats.get("textEmbeddings"),
                stats.get("imageEmbeddings")
            );
            
            log.info("✅ Batch supprimé: {} - {}", batchId, message);
            
            return ResponseEntity.ok(DeleteResponse.builder()
                .success(true)
                .deletedCount(deleted)
                .batchId(batchId)
                .message(message)
                .build());
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression batch: {}", batchId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DeleteResponse.builder()
                    .success(false)
                    .batchId(batchId)
                    .message("Erreur: " + e.getMessage())
                    .build());
        }
    }
    
    /**
     * Supprime une liste d'embeddings texte
     */
    @DeleteMapping("/files/text/batch")
    @Operation(summary = "Supprimer plusieurs fichiers texte",
            description = "Supprime une liste d'embeddings texte en batch")
    public ResponseEntity<DeleteResponse> deleteTextBatch(
            @Parameter(description = "Liste des IDs à supprimer")
            @RequestBody List<String> embeddingIds) {
        
        try {
            if (embeddingIds == null || embeddingIds.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(DeleteResponse.builder()
                        .success(false)
                        .message("Liste d'IDs vide")
                        .build());
            }
            
            log.info("🗑️ DELETE /files/text/batch - {} IDs", embeddingIds.size());
            
            int deleted = embeddingRepository.deleteTextBatch(embeddingIds);
            
            log.info("✅ Batch text supprimé: {}/{}", deleted, embeddingIds.size());
            
            return ResponseEntity.ok(DeleteResponse.builder()
                .success(true)
                .deletedCount(deleted)
                .type("text")
                .message(String.format("%d/%d embeddings texte supprimés", 
                    deleted, embeddingIds.size()))
                .build());
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression batch text", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DeleteResponse.builder()
                    .success(false)
                    .message("Erreur: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Supprime une liste d'embeddings image
     */
    @DeleteMapping("/files/image/batch")
    @Operation(summary = "Supprimer plusieurs fichiers image",
            description = "Supprime une liste d'embeddings image en batch")
    public ResponseEntity<DeleteResponse> deleteImageBatch(
            @Parameter(description = "Liste des IDs à supprimer")
            @RequestBody List<String> embeddingIds) {
        
        try {
            if (embeddingIds == null || embeddingIds.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(DeleteResponse.builder()
                        .success(false)
                        .message("Liste d'IDs vide")
                        .build());
            }
            
            log.info("🗑️ DELETE /files/image/batch - {} IDs", embeddingIds.size());
            
            int deleted = embeddingRepository.deleteImageBatch(embeddingIds);
            
            log.info("✅ Batch image supprimé: {}/{}", deleted, embeddingIds.size());
            
            return ResponseEntity.ok(DeleteResponse.builder()
                .success(true)
                .deletedCount(deleted)
                .type("image")
                .message(String.format("%d/%d embeddings image supprimés", 
                    deleted, embeddingIds.size()))
                .build());
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression batch image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DeleteResponse.builder()
                    .success(false)
                    .message("Erreur: " + e.getMessage())
                    .build());
        }
    }

    // ========================================================================
    // ✅ SUPPRESSION GLOBALE - AVEC NETTOYAGE COMPLET
    // ========================================================================
    @DeleteMapping("/files/all")
    @Operation(summary = "Supprimer TOUS les fichiers",
            description = "⚠️ DANGER: Supprime TOUS les embeddings + cache Redis + tracker. " +
                            "Nécessite confirmation='DELETE_ALL_FILES'")
    public ResponseEntity<DeleteResponse> deleteAllFiles(
            @Parameter(description = "Confirmation requise: DELETE_ALL_FILES", 
                    required = true)
            @RequestParam(required = true) String confirmation) {
        
        try {
            if (!"DELETE_ALL_FILES".equals(confirmation)) {
                log.warn("⚠️ Tentative suppression globale sans confirmation valide");
                return ResponseEntity.badRequest()
                    .body(DeleteResponse.builder()
                        .success(false)
                        .message("Confirmation requise: confirmation=DELETE_ALL_FILES")
                        .build());
            }
            
            log.warn("🚨 DELETE /files/all - SUPPRESSION GLOBALE DEMANDÉE");
            log.warn("🚨 Confirmation reçue: {}", confirmation);
            
            // 1. Supprimer les embeddings (PostgreSQL)
            int deletedEmbeddings = embeddingRepository.deleteAllFilesPlusCache();
            log.info("✅ {} embeddings supprimés de PostgreSQL", deletedEmbeddings);
    
            // 3. Nettoyer le tracker (mémoire)
            log.info("🗑️ Nettoyage tracker (mémoire)...");
            embeddingRepository.clearAllTracking();
            log.info("✅ Tracker nettoyé");
            
            String message = String.format(
                "TOUS les fichiers supprimés: %d embeddings + tous les caches Redis + tracker", 
                deletedEmbeddings
            );
            
            log.warn("✅ Suppression globale effectuée: {}", message);
            
            return ResponseEntity.ok(DeleteResponse.builder()
                .success(true)
                .deletedCount(deletedEmbeddings)
                .message(message)
                .timestamp(new java.util.Date())
                .build());
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression globale", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DeleteResponse.builder()
                    .success(false)
                    .message("Erreur: " + e.getMessage())
                    .build());
        }
    }

    // ========================================================================
    // ENDPOINTS OPTIONNELS
    // ========================================================================
    
    /**
     * Vérifie si un fichier existe déjà (doublon)
     */
    @PostMapping(value = "/check-duplicate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Vérifier si un fichier existe déjà",
            description = "Vérifie si le fichier a déjà été uploadé sans l'ingérer")
    public ResponseEntity<DuplicateCheckResponse> checkDuplicate(
            @Parameter(description = "Fichier à vérifier")
            @RequestParam("file") MultipartFile file) {
        
        try {
            log.info("🔍 POST /check-duplicate - {}", file.getOriginalFilename());
            
            boolean exists = ingestionService.fileExists(file);
            
            if (exists) {
                String existingBatchId = ingestionService.getExistingBatchId(file);
                
                log.info("⚠️ Doublon détecté: {} (batch: {})", 
                    file.getOriginalFilename(), existingBatchId);
                
                return ResponseEntity.ok(DuplicateCheckResponse.builder()
                    .isDuplicate(true)
                    .filename(file.getOriginalFilename())
                    .existingBatchId(existingBatchId)
                    .message("Ce fichier existe déjà dans le système")
                    .build());
            }
            
            log.info("✅ Fichier non trouvé: {}", file.getOriginalFilename());
            
            return ResponseEntity.ok(DuplicateCheckResponse.builder()
                .isDuplicate(false)
                .filename(file.getOriginalFilename())
                .message("Fichier non trouvé - peut être uploadé")
                .build());
            
        } catch (Exception e) {
            log.error("❌ Erreur vérification doublon: {}", 
                file.getOriginalFilename(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DuplicateCheckResponse.builder()
                    .isDuplicate(false)
                    .filename(file.getOriginalFilename())
                    .message("Erreur: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Récupère les informations d'un batch
     */
    @GetMapping("/batch/{batchId}/info")
    @Operation(summary = "Informations sur un batch",
            description = "Récupère les détails d'un batch spécifique")
    public ResponseEntity<BatchInfoResponse> getBatchInfo(
            @PathVariable String batchId) {
        
        try {
            log.info("📊 GET /batch/{}/info", batchId);
            
            if (!embeddingRepository.batchExists(batchId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BatchInfoResponse.builder()
                        .found(false)
                        .batchId(batchId)
                        .message("Batch non trouvé")
                        .build());
            }
            
            Map<String, Integer> stats = embeddingRepository.getBatchStats(batchId);
            
            return ResponseEntity.ok(BatchInfoResponse.builder()
                .found(true)
                .batchId(batchId)
                .textEmbeddings(stats.get("textEmbeddings"))
                .imageEmbeddings(stats.get("imageEmbeddings"))
                .totalEmbeddings(stats.get("textEmbeddings") + stats.get("imageEmbeddings"))
                .message("Batch trouvé")
                .build());
            
        } catch (Exception e) {
            log.error("❌ Erreur récupération info batch: {}", batchId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BatchInfoResponse.builder()
                    .found(false)
                    .batchId(batchId)
                    .message("Erreur: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Statistiques globales du système
     */
    @GetMapping("/stats/system")
    @Operation(summary = "Statistiques globales du système",
            description = "Récupère les stats complètes sur l'ingestion et les doublons")
    public ResponseEntity<SystemStatsResponse> getSystemStats() {
        
        try {
            log.info("📊 GET /stats/system");
            
            var serviceStats = ingestionService.getStats();
            var healthReport = ingestionService.getHealthReport();
            
            return ResponseEntity.ok(SystemStatsResponse.builder()
                .totalStrategies(serviceStats.strategiesCount())
                .activeIngestions(serviceStats.activeIngestions())
                .trackedBatches(serviceStats.trackerBatches())
                .totalEmbeddings(serviceStats.trackerEmbeddings())
                .filesInProgress(serviceStats.filesInProgress())
                .redisHealthy(healthReport.redisHealthy())
                .systemStatus(healthReport.status())
                .build());
            
        } catch (Exception e) {
            log.error("❌ Erreur récupération stats système", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ========================================================================
    // DTOs
    // ========================================================================

    @Data
    @Builder
    public static class DeleteResponse {
        private Boolean success;
        private Integer deletedCount;
        private String embeddingId;
        private String batchId;
        private String type;
        private String message;
        private java.util.Date timestamp;
    }
    
    @Data
    @Builder
    public static class DuplicateCheckResponse {
        private Boolean isDuplicate;
        private String filename;
        private String existingBatchId;
        private String message;
    }

    @Data
    @Builder
    public static class BatchInfoResponse {
        private Boolean found;
        private String batchId;
        private Integer textEmbeddings;
        private Integer imageEmbeddings;
        private Integer totalEmbeddings;
        private String message;
    }

    @Data
    @Builder
    public static class SystemStatsResponse {
        private Integer totalStrategies;
        private Integer activeIngestions;
        private Integer trackedBatches;
        private Integer totalEmbeddings;
        private Integer filesInProgress;
        private Boolean redisHealthy;
        private String systemStatus;
    }
}