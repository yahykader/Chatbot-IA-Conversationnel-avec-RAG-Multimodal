package com.exemple.transactionservice.service.rag.controller;

import com.exemple.transactionservice.service.rag.ingestion.MultimodalIngestionService;
import com.exemple.transactionservice.service.rag.ingestion.repository.EmbeddingRepository;
import com.exemple.transactionservice.service.rag.ingestion.model.IngestionResult;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/crud")
@Tag(name = "CRUD Embeddings", description = "API CRUD pour gestion des embeddings")
public class MultimodalCrudController {

    private final EmbeddingRepository embeddingRepository;
    private final MultimodalIngestionService ingestionService;
    

    public MultimodalCrudController(
            EmbeddingRepository embeddingRepository,  
            MultimodalIngestionService ingestionService) {

        this.embeddingRepository = embeddingRepository;  
        this.ingestionService = ingestionService;
        
        log.info("✅ MultimodalCrudController initialisé");
    }

    /**
     * ✨ Supprime un embedding spécifique par son ID
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

    /**
     * Supprime tous les fichiers d'un batch.
     */
    @DeleteMapping("/batch/{batchId}/files")
    @Operation(summary = "Supprimer tous les fichiers d'un batch",
            description = "Supprime tous les embeddings (texte + images) d'un batch spécifique")
    public ResponseEntity<DeleteResponse> deleteBatchFiles(
            @PathVariable String batchId) {
        
        try {
            log.info("🗑️ DELETE /batch/{}/files", batchId);
            
            // Vérifier si le batch existe
            if (!ingestionService.batchExists(batchId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(DeleteResponse.builder()
                        .success(false)
                        .batchId(batchId)
                        .message("Batch non trouvé")
                        .build());
            }
            
            // Récupérer stats avant suppression
            Map<String, Integer> stats = ingestionService.getBatchStats(batchId);
            
            // Supprimer
            int deleted = ingestionService.deleteBatch(batchId);
            
            log.info("✅ Batch supprimé: {} - {} embeddings", batchId, deleted);
            
            return ResponseEntity.ok(DeleteResponse.builder()
                .success(true)
                .deletedCount(deleted)
                .batchId(batchId)
                .message(String.format("Batch supprimé: %d embeddings " +
                    "(text: %d, images: %d)",
                    deleted,
                    stats.get("textEmbeddings"),
                    stats.get("imageEmbeddings")))
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
     * ✨ Supprime une liste d'embeddings texte
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
     * ✨ Supprime une liste d'embeddings image
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

    /**
     * ✨ Supprime TOUS les fichiers (DANGEREUX!)
     */
    @DeleteMapping("/files/all")
    @Operation(summary = "Supprimer TOUS les fichiers",
            description = "⚠️ DANGER: Supprime TOUS les embeddings du système. " +
                            "Nécessite confirmation='DELETE_ALL_FILES'")
    public ResponseEntity<DeleteResponse> deleteAllFiles(
            @Parameter(description = "Confirmation requise: DELETE_ALL_FILES", 
                    required = true)
            @RequestParam(required = true) String confirmation) {
        
        try {
            // Vérification de sécurité stricte
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
            
            int deleted = embeddingRepository.deleteAllFiles();
            
            log.warn("✅ Suppression globale effectuée: {} embeddings", deleted);
            
            return ResponseEntity.ok(DeleteResponse.builder()
                .success(true)
                .deletedCount(deleted)
                .message(String.format("TOUS les fichiers supprimés: %d embeddings", deleted))
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
// ✨ NOUVEAUX ENDPOINTS À AJOUTER (OPTIONNELS)
// ========================================================================

    /**
     * ✨ OPTIONNEL : Vérifie si un fichier existe déjà (doublon)
     * 
     * Utile pour :
     * - Vérifier avant upload côté frontend
     * - Afficher un avertissement à l'utilisateur
     * - Éviter les uploads inutiles
     */
    @PostMapping(value = "/check-duplicate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Vérifier si un fichier existe déjà",
            description = "Vérifie si le fichier a déjà été uploadé sans l'ingérer")
    public ResponseEntity<DuplicateCheckResponse> checkDuplicate(
            @Parameter(description = "Fichier à vérifier")
            @RequestParam("file") MultipartFile file) {
        
        try {
            log.info("🔍 POST /check-duplicate - {}", file.getOriginalFilename());
            
            // Vérifier si le fichier existe
            boolean exists = ingestionService.fileExists(file);
            
            if (exists) {
                // Récupérer le batchId existant
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
     * ✨ OPTIONNEL : Récupère les informations d'un batch
     * 
     * Utile pour :
     * - Afficher les détails d'un batch avant suppression
     * - Vérifier l'état d'un batch
     * - Debug et monitoring
     */
    @GetMapping("/batch/{batchId}/info")
    @Operation(summary = "Informations sur un batch",
            description = "Récupère les détails d'un batch spécifique")
    public ResponseEntity<BatchInfoResponse> getBatchInfo(
            @PathVariable String batchId) {
        
        try {
            log.info("📊 GET /batch/{}/info", batchId);
            
            // Vérifier si le batch existe
            if (!ingestionService.batchExists(batchId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BatchInfoResponse.builder()
                        .found(false)
                        .batchId(batchId)
                        .message("Batch non trouvé")
                        .build());
            }
            
            // Récupérer les stats
            Map<String, Integer> stats = ingestionService.getBatchStats(batchId);
            
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
     * ✨ OPTIONNEL : Statistiques globales sur les doublons
     * 
     * Utile pour :
     * - Dashboard de monitoring
     * - Métriques de l'application
     * - Analytics
     */
    @GetMapping("/stats/system")
    @Operation(summary = "Statistiques globales du système",
            description = "Récupère les stats complètes sur l'ingestion et les doublons")
    public ResponseEntity<SystemStatsResponse> getSystemStats() {
        
        try {
            log.info("📊 GET /stats/system");
            
            // Récupérer les stats du service
            var serviceStats = ingestionService.getStats();
            
            // Récupérer le health report
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
// ✨ NOUVEAUX DTOs À AJOUTER (OPTIONNELS)
// ========================================================================

    /**
     * ✨ DTO : Réponse pour la vérification de doublon
     */
    @Data
    @Builder
    public static class DuplicateCheckResponse {
        private Boolean isDuplicate;
        private String filename;
        private String existingBatchId;
        private String message;
    }

    /**
     * ✨ DTO : Réponse pour les infos d'un batch
     */
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

    /**
     * ✨ DTO : Réponse pour les stats système
     */
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


    // ========================================================================
    // ✨ NOUVEAU DTO - DeleteResponse
    // ========================================================================

    /**
     * Réponse pour les opérations de suppression
     */
    @Data
    @Builder
    public static class DeleteResponse {
        private Boolean success;
        private Integer deletedCount;
        private String embeddingId;      // Pour suppression individuelle
        private String batchId;          // Pour suppression batch
        private String type;             // "text" ou "image"
        private String message;
        private java.util.Date timestamp;
    }
    
}
    // ========================================================================
    // ✨ ENDPOINTS BONUS - RECHERCHE (OPTIONNEL)
    // ========================================================================

    /**
     * ✨ BONUS: Recherche des fichiers similaires (texte)
    //  */
    // @PostMapping("/search/text")
    // @Operation(summary = "Rechercher des fichiers texte similaires",
    //         description = "Recherche par similarité dans les embeddings texte")
    // public ResponseEntity<SearchResponse> searchText(
    //         @Parameter(description = "Texte de la requête")
    //         @RequestParam String query,
    //         @Parameter(description = "Nombre maximum de résultats")
    //         @RequestParam(defaultValue = "10") int maxResults,
    //         @Parameter(description = "Score minimum (0.0-1.0)")
    //         @RequestParam(defaultValue = "0.7") double minScore) {
        
    //     try {
    //         log.info("🔍 POST /search/text - query: '{}', max: {}, min: {}", 
    //             query, maxResults, minScore);
            
    //         // Note: Nécessite un EmbeddingModel pour convertir query en embedding
    //         // Pour l'instant, retourner une erreur "Not Implemented"
            
    //         return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
    //             .body(SearchResponse.builder()
    //                 .success(false)
    //                 .message("Recherche non implémentée - nécessite EmbeddingModel")
    //                 .build());
            
    //         // TODO: Implémenter quand EmbeddingModel est disponible
    //         /*
    //         Embedding queryEmbedding = embeddingModel.embed(query).content();
            
    //         List<EmbeddingMatch<TextSegment>> matches = 
    //             ingestionService.searchText(queryEmbedding, maxResults, minScore);
            
    //         List<Map<String, Object>> results = matches.stream()
    //             .map(match -> {
    //                 Map<String, Object> map = new HashMap<>();
    //                 map.put("score", match.score());
    //                 map.put("embeddingId", match.embeddingId());
    //                 map.put("text", match.embedded().text());
    //                 map.put("metadata", match.embedded().metadata().toMap());
    //                 return map;
    //             })
    //             .toList();
            
    //         return ResponseEntity.ok(SearchResponse.builder()
    //             .success(true)
    //             .resultCount(results.size())
    //             .results(results)
    //             .query(query)
    //             .build());
    //         */
            
    //     } catch (Exception e) {
    //         log.error("❌ Erreur recherche text", e);
    //         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    //             .body(SearchResponse.builder()
    //                 .success(false)
    //                 .message("Erreur: " + e.getMessage())
    //                 .build());
    //     }
    // }

    // /**
    //  * ✨ BONUS: Recherche des fichiers similaires (image)
    //  */
    // @PostMapping("/search/image")
    // @Operation(summary = "Rechercher des images similaires",
    //         description = "Recherche par similarité dans les embeddings image")
    // public ResponseEntity<SearchResponse> searchImage(
    //         @Parameter(description = "Description de l'image recherchée")
    //         @RequestParam String description,
    //         @Parameter(description = "Nombre maximum de résultats")
    //         @RequestParam(defaultValue = "10") int maxResults,
    //         @Parameter(description = "Score minimum (0.0-1.0)")
    //         @RequestParam(defaultValue = "0.7") double minScore) {
        
    //     try {
    //         log.info("🔍 POST /search/image - description: '{}', max: {}, min: {}", 
    //             description, maxResults, minScore);
            
    //         // Note: Nécessite un EmbeddingModel pour convertir description en embedding
    //         return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
    //             .body(SearchResponse.builder()
    //                 .success(false)
    //                 .message("Recherche image non implémentée - nécessite EmbeddingModel")
    //                 .build());
            
    //     } catch (Exception e) {
    //         log.error("❌ Erreur recherche image", e);
    //         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    //             .body(SearchResponse.builder()
    //                 .success(false)
    //                 .message("Erreur: " + e.getMessage())
    //                 .build());
    //     }
    // }

    // // ========================================================================
    // // ✨ NOUVEAU DTO - SearchResponse
    // // ========================================================================

    // /**
    //  * Réponse pour les recherches
    //  */
    // @Data
    // @Builder
    // public static class SearchResponse {
    //     private Boolean success;
    //     private String query;
    //     private Integer resultCount;
    //     private List<Map<String, Object>> results;
    //     private String message;
    // }

