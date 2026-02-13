// ============================================================================
// STRATEGY - ImageIngestionStrategy.java
// Stratégie d'ingestion pour images avec streaming + RAGMetrics unifié
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.strategy;

import com.exemple.transactionservice.service.rag.ingestion.cache.EmbeddingCache;
import com.exemple.transactionservice.service.rag.ingestion.analyzer.ImageSaver;
import com.exemple.transactionservice.service.rag.ingestion.analyzer.VisionAnalyzer;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
import com.exemple.transactionservice.service.rag.ingestion.model.IngestionResult;
import com.exemple.transactionservice.service.rag.ingestion.progress.ProgressNotifier;
import com.exemple.transactionservice.service.rag.ingestion.tracker.IngestionTracker;
import com.exemple.transactionservice.service.rag.ingestion.util.FileUtils;
import com.exemple.transactionservice.service.rag.ingestion.util.MetadataSanitizer;
import com.exemple.transactionservice.service.rag.ingestion.util.StreamingFileReader;
import com.exemple.transactionservice.service.rag.ingestion.validation.FileSignatureValidator;
import com.exemple.transactionservice.exception.DuplicateFileException;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Stratégie d'ingestion pour fichiers images
 * 
 * ✅ ADAPTÉ AVEC RAGMetrics unifié
 * 
 * Fonctionnalités:
 * - Streaming pour grosses images (>100MB)
 * - Progress temps réel via ProgressNotifier
 * - Déduplication fichier
 * - Vision API pour description
 * - Métriques Prometheus via RAGMetrics
 * - Cache embeddings
 * - Retry automatique
 * - Validation dimensions
 * 
    Upload Image
        ↓
    IngestionOrchestrator
        ├── isDuplicateAndRecord() ✅ (vérification unique)
        ├── Si doublon: throw DuplicateFileException
        └── Si OK: Call ImageIngestionStrategy.ingest()
            ↓
            ImageIngestionStrategy
            ├── validateSecurity() ✅
            ├── validateBasic() ✅
            ├── ingestWithStreaming() ou ingestNormal() ✅
            └── Return result ✅
 */
@Slf4j
@Component
public class ImageIngestionStrategy implements IngestionStrategy {
    
    private final EmbeddingStore<TextSegment> imageStore;
    private final EmbeddingModel embeddingModel;
    private final VisionAnalyzer visionAnalyzer;
    private final ImageSaver imageSaver;
    private final IngestionTracker tracker;
    private final MetadataSanitizer sanitizer;
    private final RAGMetrics ragMetrics;  // ✅ Métriques unifiées
    private final DeduplicationService deduplicationService;
    private final FileSignatureValidator signatureValidator;
    private final EmbeddingCache embeddingCache;
    
    @Autowired(required = false)
    private ProgressNotifier progressNotifier;
    
    @Value("${document.images.max-width:4096}")
    private int maxImageWidth;
    
    @Value("${document.images.max-height:4096}")
    private int maxImageHeight;
    
    @Value("${document.images.min-width:10}")
    private int minImageWidth;
    
    @Value("${document.images.min-height:10}")
    private int minImageHeight;
    
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        "png", "jpg", "jpeg", "gif", "bmp", "tiff", "tif", "webp", "svg"
    );
    
    public ImageIngestionStrategy(
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            EmbeddingModel embeddingModel,
            VisionAnalyzer visionAnalyzer,
            ImageSaver imageSaver,
            IngestionTracker tracker,
            MetadataSanitizer sanitizer,
            RAGMetrics ragMetrics,  // ✅ Injection RAGMetrics
            DeduplicationService deduplicationService,
            FileSignatureValidator signatureValidator,
            EmbeddingCache embeddingCache) {
        
        this.imageStore = imageStore;
        this.embeddingModel = embeddingModel;
        this.visionAnalyzer = visionAnalyzer;
        this.imageSaver = imageSaver;
        this.tracker = tracker;
        this.sanitizer = sanitizer;
        this.ragMetrics = ragMetrics;  // ✅ Utilisation metrics unifié
        this.deduplicationService = deduplicationService;
        this.signatureValidator = signatureValidator;
        this.embeddingCache = embeddingCache;
        
        log.info("✅ [{}] Strategy initialisée avec streaming + RAGMetrics", getName());
    }
    
    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    @Override
    public IngestionResult ingest(MultipartFile file, String batchId) throws Exception {
        String filename = file.getOriginalFilename();
        String extension = getExtension(filename);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // ========================================================================
            // PROGRESS - Upload started
            // ========================================================================
            if (progressNotifier != null) {
                progressNotifier.uploadStarted(batchId, filename, file.getSize());
            }
            
            log.info("🖼️ [{}] Processing image: {} ({} KB, type: {})", 
                getName(), filename, 
                String.format("%.2f", file.getSize() / 1024.0),
                extension.toUpperCase());
            
            // ========================================================================
            // VALIDATION - Security & Basic
            // ========================================================================
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "VALIDATION", 10, 
                    "Image validation...");
            }
            
            validateSecurity(file, extension);
            validateBasic(file, filename);
            
            // ========================================================================
            // PROGRESS - Upload completed
            // ========================================================================
            if (progressNotifier != null) {
                progressNotifier.uploadCompleted(batchId, filename);
            }
            
            // ========================================================================
            // PROCESSING - Streaming ou Normal
            // ========================================================================
            if (progressNotifier != null) {
                progressNotifier.processingStarted(batchId, filename);
            }
            
            IngestionResult result;
            
            if (StreamingFileReader.requiresStreaming(file)) {
                log.info("📖 [{}] STREAMING enabled: {} MB", 
                    getName(), file.getSize() / 1_000_000);
                result = ingestWithStreaming(file, batchId);
            } else {
                log.debug("🖼️ [{}] Normal mode: {} KB", 
                    getName(), file.getSize() / 1024);
                result = ingestNormal(file, batchId);
            }
            
            // ========================================================================
            // METRICS
            // ========================================================================
            long duration = System.currentTimeMillis() - startTime;
            
            // 1 image = 1 embedding
            ragMetrics.recordStrategyProcessing(
                getName(),
                duration,
                1  // 1 image embedding
            );
            
            // ========================================================================
            // PROGRESS - Completed
            // ========================================================================
            if (progressNotifier != null) {
                progressNotifier.completed(batchId, filename, 0, 1);
            }
            
            log.info("✅ [{}] Image indexed: {} - duration={}ms mode={}",
                getName(), filename, duration,
                StreamingFileReader.requiresStreaming(file) ? "STREAMING" : "NORMAL");
            
            return result;
            
        } catch (Exception e) {
            // ========================================================================
            // ERROR HANDLING
            // ========================================================================
            
            // Progress - Error
            if (progressNotifier != null) {
                progressNotifier.error(batchId, filename, e.getMessage());
            }
            
            log.error("❌ [{}] Image processing error: {}", getName(), filename, e);
            
            // Re-throw exception
            throw e;
        }
    }
    
    private IngestionResult ingestNormal(MultipartFile file, String batchId) throws Exception {
        // Progress - Processing
        if (progressNotifier != null) {
            progressNotifier.processingStarted(batchId, file.getOriginalFilename());
        }
        
        byte[] imageBytes = file.getBytes();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        
        if (image == null) {
            throw new IllegalArgumentException("Unsupported format: " + file.getOriginalFilename());
        }
        
        return processImage(image, file.getOriginalFilename(), batchId, file.getSize());
    }
    
    private IngestionResult ingestWithStreaming(MultipartFile file, String batchId) 
            throws Exception {
        
        Path tempFile = null;
        try {
            // Progress - Streaming
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, file.getOriginalFilename(), 
                    "STREAMING", 20, "Loading image in streaming...");
            }
            
            log.debug("💾 [{}] Creating temp file...", getName());
            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                if (bytesWritten % (50 * 1024 * 1024) == 0) {
                    log.info("📊 [{}] Saved: {} MB", 
                        getName(), bytesWritten / 1_000_000);
                    
                    // Progress streaming détaillé
                    if (progressNotifier != null) {
                        int percentage = 20 + (int)((bytesWritten / (double)file.getSize()) * 30);
                        progressNotifier.notifyProgress(batchId, file.getOriginalFilename(), 
                            "STREAMING", percentage, 
                            String.format("Loading: %d MB", bytesWritten / 1_000_000));
                    }
                }
            });
            
            // Progress - Processing
            if (progressNotifier != null) {
                progressNotifier.processingStarted(batchId, file.getOriginalFilename());
            }
            
            BufferedImage image = ImageIO.read(tempFile.toFile());
            if (image == null) {
                throw new IllegalArgumentException("Unsupported format: " + file.getOriginalFilename());
            }
            
            return processImage(image, file.getOriginalFilename(), batchId, file.getSize());
            
        } finally {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }
    
    private IngestionResult processImage(BufferedImage image, String filename,
                                          String batchId, long fileSize) throws Exception {
        
        // Progress - Validation dimensions
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "VALIDATION", 60, 
                "Validating dimensions...");
        }
        
        validateImageDimensions(image, filename);
        
        // Progress - Saving image
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "SAVING", 70, 
                "Saving image...");
        }
        
        String imageName = generateImageName(filename, batchId);
        String savedImagePath = imageSaver.saveImage(image, imageName);
        
        // Progress - Vision analysis
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "VISION_ANALYSIS", 80, 
                "Vision AI analysis...");
        }
        
        long visionStart = System.currentTimeMillis();
        String description = analyzeImageWithRetry(image);
        long visionDuration = System.currentTimeMillis() - visionStart;
        
        // ✅ MÉTRIQUE: Vision API call
        ragMetrics.recordApiCall("vision_analyze", visionDuration);
        
        // Progress - Indexing
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "INDEXING", 90, 
                "Creating embedding...");
        }
        
        Map<String, Object> metadata = buildMetadata(
            filename, getExtension(filename), batchId, savedImagePath, 
            imageName, image, fileSize
        );
        
        long indexStart = System.currentTimeMillis();
        String embeddingId = indexImage(description, metadata);
        long indexDuration = System.currentTimeMillis() - indexStart;
        
        tracker.addImageEmbeddingId(batchId, embeddingId);
        
        Map<String, Object> resultMetadata = new HashMap<>();
        resultMetadata.put("strategy", getName());
        resultMetadata.put("filename", filename);
        resultMetadata.put("width", image.getWidth());
        resultMetadata.put("height", image.getHeight());
        
        return new IngestionResult(0, 1, resultMetadata);
    }
    
    private void validateSecurity(MultipartFile file, String extension) throws Exception {
        signatureValidator.validate(file, extension);
    }
    
    private void checkDuplication(MultipartFile file, String filename) throws Exception {
        DeduplicationService.DuplicationInfo dupInfo = 
            deduplicationService.checkDuplication(file);
        
        if (dupInfo.isDuplicate()) {
            throw new DuplicateFileException(
                String.format("Already processed (batch: %s)", 
                    dupInfo.originalBatchId()),
                dupInfo.originalBatchId()
            );
        }
    }
    
    private void validateBasic(MultipartFile file, String filename) {
        if (file.isEmpty() || file.getSize() == 0) {
            throw new IllegalArgumentException("Empty file: " + filename);
        }
    }
    
    private void validateImageDimensions(BufferedImage image, String filename) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        if (width < minImageWidth || height < minImageHeight) {
            throw new IllegalArgumentException(
                String.format("Image too small: %dx%d px (min: %dx%d)",
                    width, height, minImageWidth, minImageHeight)
            );
        }
        
        if (width > maxImageWidth || height > maxImageHeight) {
            throw new IllegalArgumentException(
                String.format("Image too large: %dx%d px (max: %dx%d)",
                    width, height, maxImageWidth, maxImageHeight)
            );
        }
    }
    
    @Retryable(
        value = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private String analyzeImageWithRetry(BufferedImage image) throws IOException {
        try {
            return visionAnalyzer.analyzeImage(image);
        } catch (Exception e) {
            // ✅ MÉTRIQUE: Vision API error
            ragMetrics.recordApiError("vision_analyze");
            
            if (e instanceof IOException || e instanceof TimeoutException) {
                throw e;
            }
            throw new IOException("Vision API error", e);
        }
    }
    
    private String generateImageName(String filename, String batchId) {
        String baseFilename = FileUtils.sanitizeFilename(FileUtils.removeExtension(filename));
        return FileUtils.generateImageName(baseFilename, batchId, 0);
    }
    
    private Map<String, Object> buildMetadata(String filename, String extension,
                                               String batchId, String savedImagePath,
                                               String imageName, BufferedImage image,
                                               long fileSize) {
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "image");
        metadata.put("filename", filename);
        metadata.put("extension", extension);
        metadata.put("type", "image");
        metadata.put("batchId", batchId);
        metadata.put("savedPath", savedImagePath);
        metadata.put("imageName", imageName);
        metadata.put("width", image.getWidth());
        metadata.put("height", image.getHeight());
        metadata.put("fileSize", fileSize);
        
        return metadata;
    }
    
    private String indexImage(String description, Map<String, Object> metadata) {
        long embedStart = System.currentTimeMillis();
        
        TextSegment segment = TextSegment.from(
            description,
            Metadata.from(sanitizer.sanitize(metadata))
        );
        
        Embedding embedding = embeddingCache.getOrCompute(
            description,
            () -> {
                long apiStart = System.currentTimeMillis();
                Embedding emb = embeddingModel.embed(description).content();
                long apiDuration = System.currentTimeMillis() - apiStart;
                
                // ✅ MÉTRIQUE: Embedding API call
                ragMetrics.recordApiCall("embed_text", apiDuration);
                
                return emb;
            }
        );
        
        long storeStart = System.currentTimeMillis();
        String embeddingId = imageStore.add(embedding, segment);
        long storeDuration = System.currentTimeMillis() - storeStart;
        
        // ✅ MÉTRIQUE: Vector store operation
        ragMetrics.recordVectorStoreOperation("insert", storeDuration, 1);
        
        return embeddingId;
    }
    
    private String getExtension(String filename) {
        if (filename == null || filename.isBlank()) return "unknown";
        
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) return "unknown";
        
        return filename.substring(lastDot + 1).toLowerCase();
    }
    
    @Override
    public String getName() {
        return "IMAGE";
    }
    
    @Override
    public int getPriority() {
        return 4;
    }
}

/*
 * Progress Steps for IMAGE:
 * 5%   - Upload started
 * 10%  - Image validation
 * 15%  - Upload completed
 * 20-50% - Streaming (if >100MB)
 * 60%  - Dimension validation
 * 70%  - Saving image
 * 80%  - Vision AI analysis
 * 90%  - Creating embedding
 * 100% - Completed (0 text, 1 image)
 */