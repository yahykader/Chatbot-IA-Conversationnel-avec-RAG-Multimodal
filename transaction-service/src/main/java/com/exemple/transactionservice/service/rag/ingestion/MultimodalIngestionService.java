// ============================================================================
// SERVICE - MultimodalIngestionService.java (VERSION COMPLÈTE MODIFIÉE)
// Orchestrateur principal d'ingestion multimodale avec gestion des doublons
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion;

import com.exemple.transactionservice.service.rag.ingestion.repository.EmbeddingRepository;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.metrics.IngestionMetrics;
import com.exemple.transactionservice.service.rag.ingestion.model.IngestionResult;
import com.exemple.transactionservice.service.rag.ingestion.security.AntivirusScanner;
import com.exemple.transactionservice.service.rag.ingestion.strategy.IngestionStrategy;
import com.exemple.transactionservice.exception.DuplicateFileException;
import com.exemple.transactionservice.exception.VirusDetectedException;
import com.exemple.transactionservice.service.rag.ingestion.tracker.IngestionTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrateur principal d'ingestion multimodale - VERSION COMPLÈTE.
 * 
 * ✨ FONCTIONNALITÉS :
 * ✅ API asynchrone (@Async) - traitement non-bloquant
 * ✅ Batch processing parallèle
 * ✅ Tracking temps réel des ingestions en cours
 * ✅ Rollback transactionnel automatique
 * ✅ Métriques Prometheus intégrées
 * ✅ Scan antivirus ClamAV (optionnel)
 * ✅ Monitoring et statistiques complètes
 * ✅ Health checks
 * ✅ Détection et gestion des doublons // ✅ AJOUTÉ
 * 
 * @author RAG Team
 * @version 2.1 (avec gestion doublons)
 */
@Slf4j
@Service
public class MultimodalIngestionService {
    
    private final List<IngestionStrategy> strategies;
    private final IngestionMetrics metrics;
    private final IngestionTracker tracker;
    private final DeduplicationService deduplicationService;
    private final AntivirusScanner antivirusScanner;
    private final EmbeddingRepository embeddingRepository;
    
    @Value("${security.antivirus.enabled:true}")
    private boolean antivirusEnabled;
    
    /**
     * Tracking des ingestions en cours (batchId → status)
     */
    private final Map<String, IngestionStatus> activeIngestions = new ConcurrentHashMap<>();
    
    public MultimodalIngestionService(
            List<IngestionStrategy> strategies,
            IngestionMetrics metrics,
            IngestionTracker tracker,
            DeduplicationService deduplicationService,
            AntivirusScanner antivirusScanner,
            EmbeddingRepository embeddingRepository) {
        
        this.strategies = strategies;
        this.metrics = metrics;
        this.tracker = tracker;
        this.deduplicationService = deduplicationService;
        this.antivirusScanner = antivirusScanner;
        this.embeddingRepository = embeddingRepository;
        
        // Trier strategies par priorité (1 = la plus élevée)
        this.strategies.sort(Comparator.comparingInt(IngestionStrategy::getPriority));
        
        log.info("✅ MultimodalIngestionService initialisé (MODE ASYNC + MONITORING + DEDUP)"); // ✅ MODIFIÉ
        log.info("📋 {} strategies chargées:", strategies.size());
        strategies.forEach(s -> 
            log.info("   • {} (priorité: {})", s.getName(), s.getPriority())
        );
        log.info("🦠 Antivirus: {}", antivirusEnabled ? "ACTIVÉ" : "DÉSACTIVÉ");
        log.info("🔐 Détection doublons: ACTIVÉE"); // ✅ AJOUTÉ
    }
    
    // ========================================================================
    // API SYNCHRONE - FICHIER UNIQUE
    // ========================================================================
    
    /**
     * Ingère un fichier de manière synchrone (bloquant).
     * Utilisé par le controller pour les uploads synchrones.
     * 
     * @param file Fichier à ingérer
     * @param batchId ID du batch
     * @return Résultat de l'ingestion
     * @throws Exception Si erreur
     */
    public IngestionResult ingestFile(MultipartFile file, String batchId) 
            throws Exception {
        
        return ingestFileInternal(file, batchId);
    }
    
    // ========================================================================
    // API ASYNCHRONE - FICHIER UNIQUE
    // ========================================================================
    
    /**
     * Ingère un fichier de manière asynchrone (non-bloquant).
     * Retourne immédiatement un CompletableFuture.
     * 
     * @param file Fichier à ingérer
     * @param batchId ID du batch
     * @return CompletableFuture avec résultat final
     */
    @Async
    public CompletableFuture<IngestionResult> ingestFileAsync(
            MultipartFile file,
            String batchId) {
        
        try {
            log.info("📥 [ASYNC] Démarrage: {} - batch: {}",
                file.getOriginalFilename(), batchId);
            
            IngestionResult result = ingestFileInternal(file, batchId);
            
            log.info("✅ [ASYNC] Terminé: {} - text={} images={}",
                file.getOriginalFilename(),
                result.textEmbeddings(),
                result.imageEmbeddings());
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("❌ [ASYNC] Erreur: {} - {}",
                file.getOriginalFilename(), e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    // ========================================================================
    // API ASYNCHRONE - BATCH
    // ========================================================================
    
    /**
     * Ingère plusieurs fichiers en parallèle (batch async).
     * 
     * @param files Liste de fichiers à ingérer
     * @param batchId ID du batch (même pour tous les fichiers)
     * @return CompletableFuture avec résultat batch agrégé
     */
    @Async
    public CompletableFuture<List<IngestionResult>> ingestBatch(
            List<MultipartFile> files,
            String batchId) {
        
        log.info("📦 [BATCH] Début: {} fichiers (batchId: {})",
            files.size(), batchId);
        
        try {
            long startTime = System.currentTimeMillis();
            List<IngestionResult> results = new ArrayList<>();
            
            // Traiter chaque fichier
            for (MultipartFile file : files) {
                try {
                    log.debug("📄 [BATCH] Traitement: {}",
                        file.getOriginalFilename());
                    
                    IngestionResult result = ingestFileInternal(file, batchId);
                    results.add(result);
                    
                    log.debug("✅ [BATCH] OK: {} - text={} images={}",
                        file.getOriginalFilename(),
                        result.textEmbeddings(),
                        result.imageEmbeddings());
                    
                } catch (Exception e) {
                    log.error("❌ [BATCH] Erreur: {} - {}",
                        file.getOriginalFilename(), e.getMessage());
                    
                    // Continue avec les autres fichiers
                    // Si vous voulez un comportement "tout ou rien", décommentez :
                    // throw e;
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            // Calculer statistiques
            int totalText = results.stream()
                .mapToInt(IngestionResult::textEmbeddings)
                .sum();
            
            int totalImages = results.stream()
                .mapToInt(IngestionResult::imageEmbeddings)
                .sum();
            
            log.info("✅ [BATCH] Terminé: {}/{} succès, durée={}ms, " +
                     "embeddings (text={}, images={})",
                results.size(), files.size(), duration, totalText, totalImages);
            
            return CompletableFuture.completedFuture(results);
            
        } catch (Exception e) {
            log.error("❌ [BATCH] Erreur globale batch", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Ingère plusieurs fichiers avec résultat batch détaillé.
     * 
     * @param files Liste de fichiers
     * @param batchId ID du batch
     * @return CompletableFuture avec résultat batch agrégé
     */
    @Async
    public CompletableFuture<BatchIngestionResult> ingestBatchDetailed(
            List<MultipartFile> files,
            String batchId) {
        
        log.info("📦 [BATCH-DETAILED] Début: {} fichiers", files.size());
        
        long startTime = System.currentTimeMillis();
        List<FileIngestionResult> fileResults = new ArrayList<>();
        
        // Traiter chaque fichier
        for (MultipartFile file : files) {
            FileIngestionResult fileResult = processFileForBatch(file, batchId);
            fileResults.add(fileResult);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Calculer statistiques
        int successCount = (int) fileResults.stream()
            .filter(FileIngestionResult::success)
            .count();
        
        // ✅ AJOUTÉ : Comptage des doublons
        int duplicateCount = (int) fileResults.stream()
            .filter(FileIngestionResult::duplicate)
            .count();
        
        int errorCount = files.size() - successCount - duplicateCount;
        
        int totalText = fileResults.stream()
            .filter(r -> r.success && r.result != null)
            .mapToInt(r -> r.result.textEmbeddings())
            .sum();
        
        int totalImages = fileResults.stream()
            .filter(r -> r.success && r.result != null)
            .mapToInt(r -> r.result.imageEmbeddings())
            .sum();
        
        log.info("✅ [BATCH-DETAILED] Terminé: {}/{} succès, {} doublons, {} erreurs, durée={}ms", // ✅ MODIFIÉ
            successCount, files.size(), duplicateCount, errorCount, duration);
        
        BatchIngestionResult result = new BatchIngestionResult(
            batchId,
            fileResults,
            successCount,
            errorCount,
            totalText,
            totalImages,
            duration,
            duplicateCount
        );
        
        return CompletableFuture.completedFuture(result);
    }
    

    /**
     * Supprime tous les embeddings d'un batch.
     * 
     * @param batchId ID du batch à supprimer
     * @return Nombre d'embeddings supprimés
     */
    public int deleteBatch(String batchId) {
        try {
            log.info("🗑️ [Service] Suppression batch: {}", batchId);
            
            // Option 1 : Utiliser le tracker pour récupérer les IDs
            List<String> textIds = tracker.getTextEmbeddingIds(batchId);
            List<String> imageIds = tracker.getImageEmbeddingIds(batchId);
            
            int deleted = embeddingRepository.deleteBatchFiles(textIds, imageIds);
            
            // Nettoyer le tracker
            tracker.clearBatch(batchId);
            
            log.info("✅ [Service] Batch supprimé: {} - {} embeddings", batchId, deleted);
            return deleted;
            
        } catch (Exception e) {
            log.error("❌ [Service] Erreur suppression batch: {}", batchId, e);
            throw new RuntimeException("Erreur suppression batch", e);
        }
    }
    
    /**
     * Vérifie si un batch existe.
     */
    public boolean batchExists(String batchId) {
        return embeddingRepository.batchExists(batchId);
    }
    
    /**
     * Retourne les statistiques d'un batch.
     */
    public Map<String, Integer> getBatchStats(String batchId) {
        return embeddingRepository.countBatchFiles(batchId);
    }

    /**
     * Traite un fichier pour un batch (avec gestion erreurs)
     */
    private FileIngestionResult processFileForBatch(
            MultipartFile file,
            String batchId) {
        
        try {
            IngestionResult result = ingestFileInternal(file, batchId);
            return new FileIngestionResult(
                file.getOriginalFilename(),
                true,
                null,
                result,
                false // ✅ AJOUTÉ : pas un doublon
            );
            
        } catch (Exception e) {
            // ✅ MODIFIÉ : Distinguer les doublons des autres erreurs
            boolean isDuplicate = e instanceof DuplicateFileException;
            
            if (isDuplicate) {
                DuplicateFileException dupEx = (DuplicateFileException) e;
                
                log.warn("⚠️ [BATCH] Doublon: {} (batch existant: {})", 
                    file.getOriginalFilename(), dupEx.getExistingBatchId());
                
                return new FileIngestionResult(
                    file.getOriginalFilename(),
                    false,
                    "DUPLICATE - Fichier déjà traité (batch: " + dupEx.getExistingBatchId() + ")",
                    null,
                    true // ✅ AJOUTÉ : c'est un doublon
                );
            } else {
                log.error("❌ [BATCH] Échec: {}", file.getOriginalFilename(), e);
                return new FileIngestionResult(
                    file.getOriginalFilename(),
                    false,
                    e.getMessage(),
                    null,
                    false // ✅ AJOUTÉ : pas un doublon, erreur réelle
                );
            }
        }
    }
    
    // ========================================================================
    // LOGIQUE INGESTION INTERNE
    // ========================================================================
    
    /**
     * Logique d'ingestion interne (appelée par async et sync).
     * Contient toute la logique métier d'ingestion.
     */
    private IngestionResult ingestFileInternal(
            MultipartFile file,
            String batchId) throws Exception {
        
        String filename = file.getOriginalFilename();
        String extension = getExtension(filename);
        
        log.info("📄 [Ingestion] Début: {} (ext: {}, {} KB, batch: {})",
            filename, extension.toUpperCase(),
            file.getSize() / 1024, batchId);
        
        // Tracking: marquer comme en cours
        IngestionStatus status = new IngestionStatus(batchId, filename);
        activeIngestions.put(batchId, status);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 0. SCAN ANTIVIRUS (si activé)
            if (antivirusEnabled) {
                log.debug("🦠 [Antivirus] Scan: {}", filename);
                
                byte[] fileBytes = file.getBytes();
                File tempFile = File.createTempFile("scan-", ".tmp");
                Files.write(tempFile.toPath(), fileBytes);
                
                AntivirusScanner.ScanResult scanResult = antivirusScanner.scanFile(tempFile);
                tempFile.delete();
                
                if (!scanResult.isClean()) {
                    log.error("🚨 [Antivirus] VIRUS DÉTECTÉ: {} - {}",
                        filename, scanResult.getVirusName());
                    
                    metrics.recordVirusDetected(scanResult.getVirusName());
                    
                    throw new VirusDetectedException(
                        "Virus détecté: " + scanResult.getVirusName()
                    );
                }
                
                log.debug("✅ [Antivirus] Fichier propre");
            }
            
            // 1. SÉLECTION STRATEGY
            IngestionStrategy strategy = selectStrategy(file, extension);
            
            if (strategy == null) {
                throw new UnsupportedOperationException(
                    "Aucune strategy disponible pour: " + extension
                );
            }
            
            log.info("🎯 [Ingestion] Strategy: {} (priorité: {})",
                strategy.getName(), strategy.getPriority());
            
            status.setStrategy(strategy.getName());
            
            // 2. INGESTION
            // Toutes les validations (sécurité, dédup) sont dans la strategy
            IngestionResult result = strategy.ingest(file, batchId);
            
            // 3. SUCCÈS
            long duration = System.currentTimeMillis() - startTime;
            status.complete(true, duration);
            
            log.info("✅ [Ingestion] Succès: {} - strategy={}, " +
                     "text={}, images={}, durée={}ms",
                filename, strategy.getName(),
                result.textEmbeddings(), result.imageEmbeddings(), duration);
            
            return result;
            
        } catch (Exception e) {
            // ✅ MODIFIÉ : GESTION INTELLIGENTE DES DOUBLONS
            boolean isDuplicate = e instanceof DuplicateFileException;
            
            if (isDuplicate) {
                // C'est un doublon - pas besoin de rollback (rien n'a été créé)
                DuplicateFileException dupEx = (DuplicateFileException) e;
                
                log.warn("⚠️ [Ingestion] Doublon détecté: {} (batch existant: {})", 
                    filename, dupEx.getExistingBatchId());
                
                // Enregistrer la métrique de doublon
                if (metrics != null) {
                    metrics.recordDuplicate("DUPLICATE");
                }
                
            } else {
                // ROLLBACK AUTOMATIQUE (erreur réelle)
                log.error("❌ [Ingestion] Échec: {} - Rollback...", filename, e);
                
                try {
                    int rolledBack = tracker.rollbackBatch(batchId);
                    log.info("🔄 [Rollback] {} embeddings supprimés", rolledBack);
                } catch (Exception rollbackError) {
                    log.error("❌ [Rollback] Erreur: {}", rollbackError.getMessage());
                }
            }
            // FIN DE LA MODIFICATION
            
            long duration = System.currentTimeMillis() - startTime;
            status.complete(false, duration);
            
            throw e;
            
        } finally {
            // Nettoyer le tracking après 60 secondes
            CompletableFuture.delayedExecutor(60, TimeUnit.SECONDS)
                .execute(() -> activeIngestions.remove(batchId));
        }
    }
    
    // ========================================================================
    // SÉLECTION STRATEGY
    // ========================================================================
    
    /**
     * Sélectionne la strategy appropriée pour un fichier.
     * Utilise l'extension et la priorité (ordre croissant).
     */
    private IngestionStrategy selectStrategy(MultipartFile file, String extension) {
        
        // Parcourir strategies par ordre de priorité (déjà triées)
        for (IngestionStrategy strategy : strategies) {
            if (strategy.canHandle(file, extension)) {
                log.debug("✓ [Strategy] {} accepte {}",
                    strategy.getName(), extension.toUpperCase());
                return strategy;
            }
        }
        
        log.warn("⚠️ [Strategy] Aucune strategy pour: {}", extension.toUpperCase());
        return null;
    }
    
    // ========================================================================
    // ✅ NOUVELLES MÉTHODES UTILITAIRES - GESTION DOUBLONS
    // ========================================================================
    
    /**
     * ✅ NOUVELLE MÉTHODE : Vérifie si un fichier existe déjà.
     * 
     * @param file Fichier à vérifier
     * @return true si le fichier existe déjà, false sinon
     */
    public boolean fileExists(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String fileHash = deduplicationService.calculateHash(fileBytes);
            boolean exists = deduplicationService.isDuplicate(fileHash);
            
            if (exists) {
                log.debug("🔍 [FileExists] Fichier trouvé: {} (hash: {}...)", 
                    file.getOriginalFilename(), fileHash.substring(0, 16));
            }
            
            return exists;
            
        } catch (Exception e) {
            log.error("❌ [FileExists] Erreur vérification: {}", 
                file.getOriginalFilename(), e);
            return false;
        }
    }
    
    /**
     * ✅ NOUVELLE MÉTHODE : Récupère le batchId existant pour un fichier (si doublon).
     * 
     * @param file Fichier à vérifier
     * @return batchId existant ou null
     */
    public String getExistingBatchId(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String fileHash = deduplicationService.calculateHash(fileBytes);
            
            if (deduplicationService.isDuplicate(fileHash)) {
                Long batchId = deduplicationService.getExistingBatchId(fileHash);
                
                if (batchId != null) {
                    log.debug("🔍 [ExistingBatch] Trouvé: batch={} pour fichier={}", 
                        batchId, file.getOriginalFilename());
                    return String.valueOf(batchId);
                }
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("❌ [ExistingBatch] Erreur: {}", 
                file.getOriginalFilename(), e);
            return null;
        }
    }
    
    // ========================================================================
    // MONITORING ET STATISTIQUES
    // ========================================================================
    
    /**
     * Retourne les ingestions en cours.
     */
    public List<IngestionStatus> getActiveIngestions() {
        return new ArrayList<>(activeIngestions.values());
    }
    
    /**
     * Retourne le statut d'une ingestion spécifique.
     */
    public Optional<IngestionStatus> getIngestionStatus(String batchId) {
        return Optional.ofNullable(activeIngestions.get(batchId));
    }
    
    /**
     * Retourne les statistiques globales du service.
     */
    public ServiceStats getStats() {
        return new ServiceStats(
            strategies.size(),
            activeIngestions.size(),
            tracker.getActiveBatchCount(),
            tracker.getTotalEmbeddingCount(),
            metrics.getFilesInProgress()
        );
    }
    
    /**
     * Log les statistiques actuelles.
     */
    public void logStats() {
        ServiceStats stats = getStats();
        log.info("📊 [Service] Stats:");
        log.info("   • Strategies: {}", stats.strategiesCount);
        log.info("   • Ingestions actives: {}", stats.activeIngestions);
        log.info("   • Batches trackés: {}", stats.trackerBatches);
        log.info("   • Embeddings trackés: {}", stats.trackerEmbeddings);
        log.info("   • Files en cours: {}", stats.filesInProgress);
    }
    
    /**
     * Health check du service.
     */
    public HealthReport getHealthReport() {
        boolean redisHealthy = deduplicationService.isRedisAvailable();
        boolean antivirusHealthy = antivirusEnabled ? 
            antivirusScanner.isAvailable() : true;
        ServiceStats stats = getStats();
        
        String status = "HEALTHY";
        if (!redisHealthy || !antivirusHealthy) {
            status = "DEGRADED";
        } else if (stats.activeIngestions > 50) {
            status = "OVERLOADED";
        }
        
        return new HealthReport(
            status,
            strategies.size(),
            stats.activeIngestions,
            stats.trackerBatches,
            redisHealthy,
            antivirusHealthy,
            antivirusEnabled
        );
    }
    
    /**
     * Retourne les noms des strategies disponibles.
     */
    public List<String> getAvailableStrategies() {
        return strategies.stream()
            .map(IngestionStrategy::getName)
            .toList();
    }
    
    /**
     * Retourne les détails de toutes les strategies.
     */
    public List<StrategyInfo> getStrategiesInfo() {
        return strategies.stream()
            .map(s -> new StrategyInfo(
                s.getName(),
                s.getPriority(),
                s.getClass().getSimpleName()
            ))
            .toList();
    }
    
    // ========================================================================
    // UTILITAIRES
    // ========================================================================
    
    /**
     * Extrait l'extension d'un nom de fichier.
     */
    private String getExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unknown";
        }
        
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "unknown";
        }
        
        return filename.substring(lastDot + 1).toLowerCase();
    }
    
    // ========================================================================
    // CLASSES INTERNES ET RECORDS
    // ========================================================================
    
    /**
     * Statut d'une ingestion en cours.
     */
    public static class IngestionStatus {
        private final String batchId;
        private final String filename;
        private final long startTime;
        private String strategy;
        private boolean completed;
        private boolean success;
        private long duration;
        
        public IngestionStatus(String batchId, String filename) {
            this.batchId = batchId;
            this.filename = filename;
            this.startTime = System.currentTimeMillis();
        }
        
        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }
        
        public void complete(boolean success, long duration) {
            this.completed = true;
            this.success = success;
            this.duration = duration;
        }
        
        // Getters
        public String getBatchId() { return batchId; }
        public String getFilename() { return filename; }
        public String getStrategy() { return strategy; }
        public boolean isCompleted() { return completed; }
        public boolean isSuccess() { return success; }
        public long getDuration() { return duration; }
        public long getStartTime() { return startTime; }
    }
    
    /**
     * Résultat d'ingestion d'un fichier (dans un batch).
     * ✅ MODIFIÉ : Ajout du champ duplicate
     */
    public record FileIngestionResult(
        String filename,
        boolean success,
        String errorMessage,
        IngestionResult result,
        boolean duplicate // ✅ AJOUTÉ
    ) {
        // Compat
        public FileIngestionResult(String filename, boolean success,
                                String errorMessage, IngestionResult result) {
            this(filename, success, errorMessage, result,
                errorMessage != null && errorMessage.startsWith("DUPLICATE"));
        }

        public static FileIngestionResult ok(String filename, IngestionResult result) {
            return new FileIngestionResult(filename, true, null, result, false);
        }

        public static FileIngestionResult duplicate(String filename, String existingBatchId) {
            String msg = (existingBatchId == null || existingBatchId.isBlank())
                    ? "DUPLICATE - Fichier déjà traité"
                    : "DUPLICATE - Fichier déjà traité (batch: " + existingBatchId + ")";
            return new FileIngestionResult(filename, false, msg, null, true);
        }

        public static FileIngestionResult error(String filename, String message) {
            return new FileIngestionResult(filename, false, message, null, false);
        }

        public static FileIngestionResult fromException(String filename, Exception e) {
            if (e instanceof DuplicateFileException dup) {
                return duplicate(filename, dup.getExistingBatchId());
            }
            return error(filename, e.getMessage() != null ? e.getMessage() : "Erreur inconnue");
        }
    }
    
    /**
     * Résultat d'ingestion batch détaillé.
     * ✅ MODIFIÉ : Ajout du champ duplicateCount
     */
    public record BatchIngestionResult(
        String batchId,
        List<FileIngestionResult> fileResults,
        int successCount,
        int failureCount,
        int totalTextEmbeddings,
        int totalImageEmbeddings,
        long durationMs,
        int duplicateCount // ✅ AJOUTÉ
    ) {
        // Constructeur de compatibilité
        public BatchIngestionResult(String batchId, List<FileIngestionResult> fileResults, 
                                    int successCount, int failureCount, 
                                    int totalTextEmbeddings, int totalImageEmbeddings, 
                                    long durationMs) {
            this(batchId, fileResults, successCount, failureCount, 
                 totalTextEmbeddings, totalImageEmbeddings, durationMs, 
                 (int) fileResults.stream()
                     .filter(FileIngestionResult::duplicate)
                     .count());
        }
    }
    
    /**
     * Statistiques du service.
     */
    public record ServiceStats(
        int strategiesCount,
        int activeIngestions,
        int trackerBatches,
        int trackerEmbeddings,
        int filesInProgress
    ) {}
    
    /**
     * Rapport de santé du service.
     */
    public record HealthReport(
        String status,
        int strategies,
        int activeIngestions,
        int trackerBatches,
        boolean redisHealthy,
        boolean antivirusHealthy,
        boolean antivirusEnabled
    ) {
        public boolean isHealthy() {
            return "HEALTHY".equals(status);
        }
    }
    
    /**
     * Information sur une strategy.
     */
    public record StrategyInfo(
        String name,
        int priority,
        String className
    ) {}

}