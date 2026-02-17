// ============================================================================
// SERVICE - MultimodalIngestionService.java
// Orchestrateur principal d'ingestion multimodale avec RAGMetrics unifié
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion;

import com.exemple.transactionservice.service.rag.ingestion.repository.EmbeddingRepository;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
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

@Slf4j
@Service
public class IngestionOrchestrator {
    
    private final List<IngestionStrategy> strategies;
    private final RAGMetrics ragMetrics;
    private final IngestionTracker tracker;
    private final DeduplicationService deduplicationService;
    private final AntivirusScanner antivirusScanner;
    private final EmbeddingRepository embeddingRepository;
    
    @Value("${security.antivirus.enabled:true}")
    private boolean antivirusEnabled;
    
    private final Map<String, IngestionStatus> activeIngestions = new ConcurrentHashMap<>();
    
    public IngestionOrchestrator(
            List<IngestionStrategy> strategies,
            RAGMetrics ragMetrics,
            IngestionTracker tracker,
            DeduplicationService deduplicationService,
            AntivirusScanner antivirusScanner,
            EmbeddingRepository embeddingRepository) {
        
        this.strategies = strategies;
        this.ragMetrics = ragMetrics;
        this.tracker = tracker;
        this.deduplicationService = deduplicationService;
        this.antivirusScanner = antivirusScanner;
        this.embeddingRepository = embeddingRepository;
        
        this.strategies.sort(Comparator.comparingInt(IngestionStrategy::getPriority));
        
        log.info("✅ MultimodalIngestionService initialisé");
        log.info("📋 {} strategies | 🦠 Antivirus: {} | 🔐 Dedup: ACTIVÉE",
            strategies.size(), antivirusEnabled ? "ON" : "OFF");
    }
    
    // ========================================================================
    // API SYNCHRONE (INCHANGÉE)
    // ========================================================================
    
    public IngestionResult ingestFile(MultipartFile file, String batchId) 
            throws Exception {
        return ingestFileInternal(file, batchId);
    }
    
    // ========================================================================
    // API ASYNCHRONE - FICHIER UNIQUE (INCHANGÉE)
    // ========================================================================
    
    @Async
    public CompletableFuture<IngestionResult> ingestFileAsync(
            MultipartFile file,
            String batchId) {
        
        try {
            log.info("📥 [ASYNC] Start: {} (batch: {})",
                file.getOriginalFilename(), batchId);
            
            IngestionResult result = ingestFileInternal(file, batchId);
            
            log.info("✅ [ASYNC] Done: {} - text={} images={}",
                file.getOriginalFilename(),
                result.textEmbeddings(),
                result.imageEmbeddings());
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("❌ [ASYNC] Error: {}", file.getOriginalFilename(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    // ========================================================================
    // API ASYNCHRONE - BATCH (INCHANGÉE)
    // ========================================================================
    
    @Async
    public CompletableFuture<List<IngestionResult>> ingestBatch(
            List<MultipartFile> files,
            String batchId) {
        
        log.info("📦 [BATCH] Start: {} files (batchId: {})",
            files.size(), batchId);
        
        try {
            long startTime = System.currentTimeMillis();
            List<IngestionResult> results = new ArrayList<>();
            
            for (MultipartFile file : files) {
                try {
                    IngestionResult result = ingestFileInternal(file, batchId);
                    results.add(result);
                    
                } catch (Exception e) {
                    log.error("❌ [BATCH] Error: {}", file.getOriginalFilename(), e);
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            int totalText = results.stream()
                .mapToInt(IngestionResult::textEmbeddings)
                .sum();
            
            int totalImages = results.stream()
                .mapToInt(IngestionResult::imageEmbeddings)
                .sum();
            
            log.info("✅ [BATCH] Done: {}/{} success, {}ms, text={}, images={}",
                results.size(), files.size(), duration, totalText, totalImages);
            
            return CompletableFuture.completedFuture(results);
            
        } catch (Exception e) {
            log.error("❌ [BATCH] Global error", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    @Async
    public CompletableFuture<BatchIngestionResult> ingestBatchDetailed(
            List<MultipartFile> files,
            String batchId) {
        
        log.info("📦 [BATCH-DETAILED] Start: {} files", files.size());
        
        long startTime = System.currentTimeMillis();
        List<FileIngestionResult> fileResults = new ArrayList<>();
        
        for (MultipartFile file : files) {
            FileIngestionResult fileResult = processFileForBatch(file, batchId);
            fileResults.add(fileResult);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        int successCount = (int) fileResults.stream()
            .filter(FileIngestionResult::success)
            .count();
        
        int duplicateCount = (int) fileResults.stream()
            .filter(FileIngestionResult::duplicate)
            .count();
        
        int errorCount = files.size() - successCount - duplicateCount;
        
        int totalText = fileResults.stream()
            .filter(r -> r.success() && r.result() != null)
            .mapToInt(r -> r.result().textEmbeddings())
            .sum();
        
        int totalImages = fileResults.stream()
            .filter(r -> r.success() && r.result() != null)
            .mapToInt(r -> r.result().imageEmbeddings())
            .sum();
        
        log.info("✅ [BATCH-DETAILED] Done: {} success, {} duplicates, {} errors, {}ms",
            successCount, duplicateCount, errorCount, duration);
        
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
    
    // ========================================================================
    // LOGIQUE INGESTION INTERNE (TOUTES INCHANGÉES)
    // ========================================================================
    
    private IngestionResult ingestFileInternal(
            MultipartFile file,
            String batchId) throws Exception {
        
        String filename = file.getOriginalFilename();
        String extension = getExtension(filename);
        
        log.info("📄 Ingestion: {} ({}, {} KB, batch: {})",
            filename, extension.toUpperCase(),
            file.getSize() / 1024, batchId);
        
        ragMetrics.startIngestion();
        
        IngestionStatus status = new IngestionStatus(batchId, filename);
        activeIngestions.put(batchId, status);
        
        long startTime = System.currentTimeMillis();
        String strategyName = "unknown";
        
        try {
            // 0. SCAN ANTIVIRUS
            if (antivirusEnabled) {
                log.debug("🦠 Antivirus scan: {}", filename);
                
                byte[] fileBytes = file.getBytes();
                File tempFile = File.createTempFile("scan-", ".tmp");
                Files.write(tempFile.toPath(), fileBytes);
                
                AntivirusScanner.ScanResult scanResult = antivirusScanner.scanFile(tempFile);
                tempFile.delete();
                
                if (!scanResult.isClean()) {
                    log.error("🚨 VIRUS DETECTED: {} - {}",
                        filename, scanResult.getVirusName());
                    
                    ragMetrics.recordVirusDetected(scanResult.getVirusName());
                    
                    throw new VirusDetectedException(
                        "Virus detected: " + scanResult.getVirusName()
                    );
                }
                
                log.debug("✅ File clean");
            }
            
            // 1. SÉLECTION STRATEGY
            IngestionStrategy strategy = selectStrategy(file, extension);
            
            if (strategy == null) {
                throw new UnsupportedOperationException(
                    "No strategy for: " + extension
                );
            }
            
            strategyName = strategy.getName();
            status.setStrategy(strategyName);
            
            log.info("🎯 Strategy: {} (priority: {})",
                strategyName, strategy.getPriority());
            
            // 2. VÉRIFICATION DOUBLON
            byte[] fileBytes = file.getBytes();
            String fileHash = deduplicationService.calculateHash(fileBytes);

            if (deduplicationService.isDuplicateAndRecord(fileHash, strategyName)) {
                String existingBatchId = deduplicationService.getExistingBatchId(fileHash);
                ragMetrics.recordDuplicate(strategyName);
                
                log.warn("⚠️ Duplicate: {} (existing batch: {})", 
                    filename, existingBatchId);
                
                throw new DuplicateFileException(
                    "Duplicate file: " + filename, 
                    existingBatchId
                );
            }
            
            deduplicationService.registerFile(fileHash, batchId, filename);

            // 3. INGESTION
            IngestionResult result = strategy.ingest(file, batchId);
            
            // 4. SUCCÈS - MÉTRIQUES
            long duration = System.currentTimeMillis() - startTime;
            status.complete(true, duration);
            
            int totalEmbeddings = result.textEmbeddings() + result.imageEmbeddings();
            int chunks = Math.max(result.textEmbeddings(), 0);
            
            ragMetrics.recordStrategyProcessing(strategyName, duration, chunks);
            ragMetrics.recordIngestionSuccess(strategyName, duration, totalEmbeddings);
            
            log.info("✅ Success: {} - strategy={}, text={}, images={}, {}ms",
                filename, strategyName,
                result.textEmbeddings(), result.imageEmbeddings(), duration);
            
            return result;
            
        } catch (Exception e) {
            boolean isDuplicate = e instanceof DuplicateFileException;
            
            if (isDuplicate) {
                DuplicateFileException dupEx = (DuplicateFileException) e;
                log.warn("⚠️ Duplicate: {} (existing batch: {})", 
                    filename, dupEx.getExistingBatchId());
            } else {
                log.error("❌ Failure: {} - Rolling back...", filename, e);
                
                try {
                    int rolledBack = tracker.rollbackBatch(batchId);
                    log.info("🔄 Rollback: {} embeddings deleted", rolledBack);
                } catch (Exception rollbackError) {
                    log.error("❌ Rollback error: {}", rollbackError.getMessage());
                }
                
                ragMetrics.recordIngestionError(strategyName, e.getClass().getSimpleName());
            }
            
            long duration = System.currentTimeMillis() - startTime;
            status.complete(false, duration);
            
            throw e;
            
        } finally {
            ragMetrics.endIngestion();
            
            CompletableFuture.delayedExecutor(60, TimeUnit.SECONDS)
                .execute(() -> activeIngestions.remove(batchId));
        }
    }
    
    // ========================================================================
    // PRIVATE HELPERS (TOUTES INCHANGÉES)
    // ========================================================================
    
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
                false
            );
            
        } catch (Exception e) {
            boolean isDuplicate = e instanceof DuplicateFileException;
            
            if (isDuplicate) {
                DuplicateFileException dupEx = (DuplicateFileException) e;
                
                log.warn("⚠️ [BATCH] Duplicate: {} (batch: {})", 
                    file.getOriginalFilename(), dupEx.getExistingBatchId());
                
                return new FileIngestionResult(
                    file.getOriginalFilename(),
                    false,
                    "DUPLICATE - Already processed (batch: " + dupEx.getExistingBatchId() + ")",
                    null,
                    true
                );
            } else {
                log.error("❌ [BATCH] Failed: {}", file.getOriginalFilename(), e);
                return new FileIngestionResult(
                    file.getOriginalFilename(),
                    false,
                    e.getMessage(),
                    null,
                    false
                );
            }
        }
    }
    
    private IngestionStrategy selectStrategy(MultipartFile file, String extension) {
        for (IngestionStrategy strategy : strategies) {
            if (strategy.canHandle(file, extension)) {
                return strategy;
            }
        }
        return null;
    }
    
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
    // UTILITAIRES DOUBLONS (TOUTES INCHANGÉES)
    // ========================================================================
    
    public boolean fileExists(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String fileHash = deduplicationService.calculateHash(fileBytes);
            return deduplicationService.isDuplicate(fileHash);
            
        } catch (Exception e) {
            log.error("❌ FileExists check error: {}", 
                file.getOriginalFilename(), e);
            return false;
        }
    }
    
    public String getExistingBatchId(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String fileHash = deduplicationService.calculateHash(fileBytes);
            
            if (deduplicationService.isDuplicate(fileHash)) {
                String batchId = deduplicationService.getExistingBatchId(fileHash);
                return batchId;
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("❌ ExistingBatch check error: {}", 
                file.getOriginalFilename(), e);
            return null;
        }
    }
    
    // ========================================================================
    // MONITORING (TOUTES INCHANGÉES)
    // ========================================================================
    
    public List<IngestionStatus> getActiveIngestions() {
        return new ArrayList<>(activeIngestions.values());
    }
    
    public Optional<IngestionStatus> getIngestionStatus(String batchId) {
        return Optional.ofNullable(activeIngestions.get(batchId));
    }
    
    public ServiceStats getStats() {
        return new ServiceStats(
            strategies.size(),
            activeIngestions.size(),
            tracker.getActiveBatchCount(),
            tracker.getTotalEmbeddingCount(),
            ragMetrics.getActiveIngestions()
        );
    }
    
    public void logStats() {
        ServiceStats stats = getStats();
        log.info("📊 Service Stats:");
        log.info("   • Strategies: {}", stats.strategiesCount());
        log.info("   • Active ingestions: {}", stats.activeIngestions());
        log.info("   • Tracked batches: {}", stats.trackerBatches());
        log.info("   • Tracked embeddings: {}", stats.trackerEmbeddings());
        log.info("   • Files in progress: {}", stats.filesInProgress());
    }
    
    public HealthReport getHealthReport() {
        boolean redisHealthy = deduplicationService.isRedisAvailable();
        boolean antivirusHealthy = antivirusEnabled ? 
            antivirusScanner.isAvailable() : true;
        ServiceStats stats = getStats();
        
        String status = "HEALTHY";
        if (!redisHealthy || !antivirusHealthy) {
            status = "DEGRADED";
        } else if (stats.activeIngestions() > 50) {
            status = "OVERLOADED";
        }
        
        return new HealthReport(
            status,
            strategies.size(),
            stats.activeIngestions(),
            stats.trackerBatches(),
            redisHealthy,
            antivirusHealthy,
            antivirusEnabled
        );
    }
    
    public List<String> getAvailableStrategies() {
        return strategies.stream()
            .map(IngestionStrategy::getName)
            .toList();
    }
    
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
    // CLASSES INTERNES (TOUTES INCHANGÉES)
    // ========================================================================
    
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
        
        public String getBatchId() { return batchId; }
        public String getFilename() { return filename; }
        public String getStrategy() { return strategy; }
        public boolean isCompleted() { return completed; }
        public boolean isSuccess() { return success; }
        public long getDuration() { return duration; }
        public long getStartTime() { return startTime; }
    }
    
    public record FileIngestionResult(
        String filename,
        boolean success,
        String errorMessage,
        IngestionResult result,
        boolean duplicate
    ) {
        public static FileIngestionResult ok(String filename, IngestionResult result) {
            return new FileIngestionResult(filename, true, null, result, false);
        }

        public static FileIngestionResult duplicate(String filename, String existingBatchId) {
            String msg = "DUPLICATE - Already processed" + 
                (existingBatchId != null ? " (batch: " + existingBatchId + ")" : "");
            return new FileIngestionResult(filename, false, msg, null, true);
        }

        public static FileIngestionResult error(String filename, String message) {
            return new FileIngestionResult(filename, false, message, null, false);
        }
    }
    
    public record BatchIngestionResult(
        String batchId,
        List<FileIngestionResult> fileResults,
        int successCount,
        int failureCount,
        int totalTextEmbeddings,
        int totalImageEmbeddings,
        long durationMs,
        int duplicateCount
    ) {}
    
    public record ServiceStats(
        int strategiesCount,
        int activeIngestions,
        int trackerBatches,
        int trackerEmbeddings,
        int filesInProgress
    ) {}
    
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
    
    public record StrategyInfo(
        String name,
        int priority,
        String className
    ) {}
}

