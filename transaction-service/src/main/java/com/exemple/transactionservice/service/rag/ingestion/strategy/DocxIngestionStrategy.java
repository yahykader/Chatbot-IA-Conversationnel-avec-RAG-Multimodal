// ============================================================================
// STRATEGY - DocxIngestionStrategy.java
// Stratégie d'ingestion pour DOCX avec streaming + RAGMetrics unifié
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.strategy;

import com.exemple.transactionservice.service.rag.ingestion.progress.ProgressNotifier;
import com.exemple.transactionservice.service.rag.ingestion.cache.EmbeddingCache;
import com.exemple.transactionservice.service.rag.ingestion.analyzer.ImageSaver;
import com.exemple.transactionservice.service.rag.ingestion.analyzer.VisionAnalyzer;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.TextDeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.model.IngestionResult;
import com.exemple.transactionservice.service.rag.ingestion.tracker.IngestionTracker;
import com.exemple.transactionservice.service.rag.ingestion.util.FileUtils;
import com.exemple.transactionservice.service.rag.ingestion.util.MetadataSanitizer;
import com.exemple.transactionservice.service.rag.ingestion.util.StreamingFileReader;
import com.exemple.transactionservice.service.rag.ingestion.validation.FileSignatureValidator;
import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
import com.exemple.transactionservice.exception.DuplicateFileException;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Stratégie d'ingestion pour fichiers DOCX
 * 
 * ✅ VERSION AVEC TRACKING EMBEDDINGS PAR BATCH
 */
@Slf4j
@Component
public class DocxIngestionStrategy implements IngestionStrategy {
    
    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingStore<TextSegment> imageStore;
    private final EmbeddingModel embeddingModel;
    private final VisionAnalyzer visionAnalyzer;
    private final ImageSaver imageSaver;
    private final IngestionTracker tracker;
    private final MetadataSanitizer sanitizer;
    private final RAGMetrics ragMetrics;
    private final DeduplicationService deduplicationService;
    private final TextDeduplicationService textDeduplicationService;
    private final FileSignatureValidator signatureValidator;
    private final EmbeddingCache embeddingCache;
    
    @Autowired(required = false)
    private ProgressNotifier progressNotifier;
    
    @Value("${document.max-images-per-file:100}")
    private int maxImagesPerFile;
    
    @Value("${document.docx.timeout-seconds:300}")
    private int docxTimeoutSeconds;
    
    // Constructeur (INCHANGÉ)
    public DocxIngestionStrategy(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            EmbeddingModel embeddingModel,
            VisionAnalyzer visionAnalyzer,
            ImageSaver imageSaver,
            IngestionTracker tracker,
            MetadataSanitizer sanitizer,
            RAGMetrics ragMetrics,
            DeduplicationService deduplicationService,
            TextDeduplicationService textDeduplicationService,
            FileSignatureValidator signatureValidator,
            EmbeddingCache embeddingCache) {
        
        this.textStore = textStore;
        this.imageStore = imageStore;
        this.embeddingModel = embeddingModel;
        this.visionAnalyzer = visionAnalyzer;
        this.imageSaver = imageSaver;
        this.tracker = tracker;
        this.sanitizer = sanitizer;
        this.ragMetrics = ragMetrics;
        this.deduplicationService = deduplicationService;
        this.textDeduplicationService = textDeduplicationService;
        this.signatureValidator = signatureValidator;
        this.embeddingCache = embeddingCache;
        
        log.info("✅ [{}] Strategy initialisée avec streaming + tracking batch", getName());
    }
    
    // ========================================================================
    // MÉTHODES PUBLIQUES (TOUTES INCHANGÉES)
    // ========================================================================
    
    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return "docx".equals(extension);
    }

    @Override
    public IngestionResult ingest(MultipartFile file, String batchId) throws Exception {
        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();
        
        long startTime = System.currentTimeMillis();
        
        try {
            if (progressNotifier != null) {
                progressNotifier.uploadStarted(batchId, filename, fileSize);
            }
            
            log.info("📘 [{}] Processing DOCX: {} ({} MB)", 
                getName(), filename, fileSize / 1_000_000);
            
            if (file.isEmpty() || fileSize == 0) {
                if (progressNotifier != null) {
                    progressNotifier.error(batchId, filename, "Empty file");
                }
                throw new IOException("Empty DOCX: " + filename);
            }
            
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "VALIDATION", 8, 
                    "File validation...");
            }
            
            signatureValidator.validate(file, "docx");
            
            if (progressNotifier != null) {
                progressNotifier.processingStarted(batchId, filename);
            }
            
            IngestionResult result;
            
            if (StreamingFileReader.requiresStreaming(file)) {
                log.info("📖 [{}] STREAMING enabled: {} MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestWithStreaming(file, filename, batchId);
            } else {
                log.debug("📄 [{}] Normal mode: {} MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestNormal(file, filename, batchId);
            }
            
            // ✅ AJOUT: Cleanup local cache + stats
            textDeduplicationService.clearLocalCache();
            var dedupStats = textDeduplicationService.getStats(batchId);
            log.info("📊 [Dedup] Stats - Total indexés: {}, Cache local: {}", 
            dedupStats.totalIndexed(), dedupStats.localCacheSize());
            
            long duration = System.currentTimeMillis() - startTime;
            int totalEmbeddings = result.textEmbeddings() + result.imageEmbeddings();
            
            ragMetrics.recordStrategyProcessing(
                getName(),
                duration,
                totalEmbeddings
            );
            
            if (progressNotifier != null) {
                progressNotifier.completed(batchId, filename, 
                    result.textEmbeddings(), result.imageEmbeddings());
            }
            
            log.info("✅ [{}] DOCX processed: {} - text={} images={} duration={}ms mode={}",
                getName(), filename, result.textEmbeddings(), 
                result.imageEmbeddings(), duration,
                StreamingFileReader.requiresStreaming(file) ? "STREAMING" : "NORMAL");
            
            return result;
            
        } catch (Exception e) {
            // ✅ AJOUT: Cleanup local cache même en cas d'erreur
            textDeduplicationService.clearLocalCache();
            
            if (progressNotifier != null) {
                progressNotifier.error(batchId, filename, e.getMessage());
            }
            
            log.error("❌ [{}] DOCX processing error: {}", getName(), filename, e);
            throw e;
        }
    }
    
    // ========================================================================
    // MÉTHODES PRIVÉES - STREAMING (TOUTES INCHANGÉES)
    // ========================================================================
    
    private IngestionResult ingestNormal(MultipartFile file, String filename, 
                                          String batchId) throws Exception {
        try (InputStream is = file.getInputStream()) {
            XWPFDocument document = openDocxWithTimeout(is, filename);
            return processDocument(document, filename, batchId);
        }
    }
    
    private IngestionResult ingestWithStreaming(MultipartFile file, String filename, 
                                                 String batchId) throws Exception {
        Path tempFile = null;
        try {
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "STREAMING", 18, 
                    "Loading DOCX in streaming...");
            }
            
            log.debug("💾 [{}] Creating temp file...", getName());
            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                if (bytesWritten % (50 * 1024 * 1024) == 0) {
                    log.info("📊 [{}] Saved: {} MB", 
                        getName(), bytesWritten / 1_000_000);
                }
            });
            
            try (FileInputStream fis = new FileInputStream(tempFile.toFile())) {
                XWPFDocument document = openDocxWithTimeout(fis, filename);
                return processDocument(document, filename, batchId);
            }
        } finally {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }
    
    private XWPFDocument openDocxWithTimeout(InputStream is, String filename) 
            throws IOException, TimeoutException {
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<XWPFDocument> future = executor.submit(() -> {
                try {
                    return new XWPFDocument(is);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            
            try {
                return future.get(docxTimeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new TimeoutException("DOCX open timeout: " + filename);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                throw new IOException("DOCX open error", cause);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("DOCX open interrupted", e);
        } finally {
            executor.shutdownNow();
        }
    }
    
    private IngestionResult processDocument(XWPFDocument document, String filename, 
                                             String batchId) throws Exception {
        try {
            boolean hasImages = hasImagesInDocument(document);
            
            if (hasImages) {
                return processDocxWithImages(document, filename, batchId);
            } else {
                return processDocxTextOnly(document, filename, batchId);
            }
        } finally {
            try {
                document.close();
            } catch (IOException e) {
                log.warn("⚠️ Document close error", e);
            }
        }
    }
    
    private boolean hasImagesInDocument(XWPFDocument document) {
        try {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                for (XWPFRun run : paragraph.getRuns()) {
                    List<XWPFPicture> pictures = run.getEmbeddedPictures();
                    if (pictures != null && !pictures.isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    // ========================================================================
    // PROCESSING (INCHANGÉ SAUF analyzeAndIndexImageWithRetry)
    // ========================================================================
    
    private IngestionResult processDocxWithImages(XWPFDocument document, String filename, 
                                                   String batchId) throws Exception {
        
        int textEmbeddings = 0;
        int imageEmbeddings = 0;
        int totalImages = 0;
        int duplicates = 0;
        
        StringBuilder fullText = new StringBuilder();
        String baseFilename = FileUtils.sanitizeFilename(FileUtils.removeExtension(filename));
        String batchShort = batchId.substring(0, Math.min(8, batchId.length()));
        
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "EXTRACTION", 20, 
                "Text extraction...");
        }
        
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText();
            if (text != null && !text.isBlank()) {
                fullText.append(text).append("\n");
            }
            
            for (XWPFRun run : paragraph.getRuns()) {
                List<XWPFPicture> pictures = run.getEmbeddedPictures();
                if (pictures != null) {
                    for (XWPFPicture picture : pictures) {
                        if (totalImages >= maxImagesPerFile) break;
                        
                        try {
                            XWPFPictureData pictureData = picture.getPictureData();
                            if (pictureData == null) continue;
                            
                            byte[] imageBytes = pictureData.getData();
                            BufferedImage image = ImageIO.read(
                                new ByteArrayInputStream(imageBytes)
                            );
                            if (image == null) continue;
                            
                            totalImages++;
                            
                            if (progressNotifier != null) {
                                progressNotifier.imageProgress(batchId, filename, 
                                    totalImages, maxImagesPerFile);
                            }
                            
                            String imageName = String.format("%s_batch%s_img%d",
                                baseFilename, batchShort, totalImages);
                            
                            String savedPath = imageSaver.saveImage(image, imageName);
                            
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("source", "docx");
                            metadata.put("filename", filename);
                            metadata.put("imageNumber", totalImages);
                            metadata.put("savedPath", savedPath);
                            metadata.put("batchId", batchId);
                            
                            // ✅ Passer batchId à la méthode
                            String embeddingId = analyzeAndIndexImageWithRetry(
                                image, imageName, metadata, batchId
                            );
                            
                            tracker.addImageEmbeddingId(batchId, embeddingId);
                            imageEmbeddings++;
                            
                        } catch (Exception e) {
                            log.warn("⚠️ Image extraction error", e);
                        }
                    }
                }
            }
        }
        
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "CHUNKING", 30, 
                "Text chunking...");
        }
        
        if (fullText.length() > 0) {
            var chunkResult = chunkAndIndexText(fullText.toString(), filename, batchId);
            textEmbeddings = chunkResult.indexed();
            duplicates = chunkResult.duplicates();
        }
        
        if (duplicates > 0) {
            log.info("⏭️ [Dedup] {} duplicates skipped, {} new indexed", 
                duplicates, textEmbeddings);
        }
        
        Map<String, Object> resultMetadata = new HashMap<>();
        resultMetadata.put("strategy", getName());
        resultMetadata.put("filename", filename);
        resultMetadata.put("hasImages", true);
        
        return new IngestionResult(textEmbeddings, imageEmbeddings, resultMetadata);
    }
    
    private IngestionResult processDocxTextOnly(XWPFDocument document, String filename, 
                                                 String batchId) throws Exception {
        
        StringBuilder fullText = new StringBuilder();
        
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText();
            if (text != null && !text.isBlank()) {
                fullText.append(text).append("\n");
            }
        }
        
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    String text = cell.getText();
                    if (text != null && !text.isBlank()) {
                        fullText.append(text).append(" ");
                    }
                }
                fullText.append("\n");
            }
        }
        
        if (fullText.length() == 0) {
            throw new IllegalArgumentException("Empty DOCX: " + filename);
        }
        
        var chunkResult = chunkAndIndexText(fullText.toString(), filename, batchId);
        int textEmbeddings = chunkResult.indexed();
        int duplicates = chunkResult.duplicates();
        
        if (duplicates > 0) {
            log.info("⏭️ [Dedup] {} duplicates skipped, {} new indexed", 
                duplicates, textEmbeddings);
        }
        
        Map<String, Object> resultMetadata = new HashMap<>();
        resultMetadata.put("strategy", getName());
        resultMetadata.put("filename", filename);
        resultMetadata.put("hasImages", false);
        
        return new IngestionResult(textEmbeddings, 0, resultMetadata);
    }
    
    // ========================================================================
    // ✅ MODIFIÉ: analyzeAndIndexImageWithRetry avec tracking batch
    // ========================================================================
    
    @Retryable(
        value = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private String analyzeAndIndexImageWithRetry(
            BufferedImage image, 
            String imageName,
            Map<String, Object> additionalMetadata,
            String batchId) throws IOException {  // ✅ Ajouter batchId
        
        long start = System.currentTimeMillis();
        
        try {
            String description = visionAnalyzer.analyzeImage(image);
            
            Map<String, Object> metadata = new HashMap<>(
                sanitizer.sanitize(additionalMetadata)
            );
            metadata.put("imageName", imageName);
            metadata.put("type", "image");
            metadata.put("width", image.getWidth());
            metadata.put("height", image.getHeight());
            
            TextSegment segment = TextSegment.from(
                description, Metadata.from(metadata)
            );
            
            // ✅ MODIFIÉ: Utiliser getAndTrack + put avec batchId
            Embedding embedding = embeddingCache.getAndTrack(description, batchId);
            
            if (embedding == null) {
                // Cache miss - Créer l'embedding
                embedding = embeddingModel.embed(description).content();
                
                // ✅ Stocker avec tracking batch
                embeddingCache.put(description, embedding, batchId);
            }
            
            long duration = System.currentTimeMillis() - start;
            
            // ✅ MÉTRIQUE: API call (Vision API) (INCHANGÉE)
            ragMetrics.recordApiCall("vision_analyze", duration);
            
            return imageStore.add(embedding, segment);
            
        } catch (Exception e) {
            // ✅ MÉTRIQUE: API error (INCHANGÉE)
            ragMetrics.recordApiError("vision_analyze");
            
            if (e instanceof IOException || e instanceof TimeoutException) {
                throw e;
            }
            throw new IOException("Vision API error", e);
        }
    }
    
    // ========================================================================
    // ✅ MODIFIÉ: indexText avec tracking batch
    // ========================================================================
    
    private String indexText(String text, Metadata metadata, String batchId) {
        
        // ✅ Vérification déduplication texte (INCHANGÉE)
        if (!textDeduplicationService.checkAndMark(text, batchId)) {
            log.debug("⏭️ [Dedup] Duplicate text, skip: {}", 
                truncate(text, 50));
            return null;
        }
        
        log.debug("✅ [Dedup] New text, indexing: {}", 
            truncate(text, 50));
        
        long start = System.currentTimeMillis();
        
        TextSegment segment = TextSegment.from(text, metadata);
        
        // ✅ MODIFIÉ: Utiliser getAndTrack + put avec batchId
        Embedding embedding = embeddingCache.getAndTrack(text, batchId);
        
        if (embedding == null) {
            // Cache miss - Créer l'embedding
            long embedStart = System.currentTimeMillis();
            embedding = embeddingModel.embed(text).content();
            long embedDuration = System.currentTimeMillis() - embedStart;
            
            // ✅ MÉTRIQUE: API call embedding (INCHANGÉE)
            ragMetrics.recordApiCall("embed_text", embedDuration);
            
            // ✅ Stocker avec tracking batch
            embeddingCache.put(text, embedding, batchId);
        }
        
        String embeddingId = textStore.add(embedding, segment);
        
        long duration = System.currentTimeMillis() - start;
        
        // ✅ MÉTRIQUE: Vector store operation (INCHANGÉE)
        ragMetrics.recordVectorStoreOperation("insert", duration, 1);
        
        return embeddingId;
    }
    
    // ========================================================================
    // CHUNKING (INCHANGÉ)
    // ========================================================================
    
    private record ChunkResult(int indexed, int duplicates) {}
    
    private ChunkResult chunkAndIndexText(String text, String filename, String batchId) {
        int chunkSize = 1000;
        int overlap = 100;
        int indexed = 0;
        int duplicates = 0;
        int chunkIndex = 0;
        
        int estimatedChunks = text.length() <= chunkSize ? 1 : 
            (int) Math.ceil(text.length() / (double)(chunkSize - overlap));
        
        if (text.length() <= chunkSize) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("source", filename);
            meta.put("type", "docx_text");
            meta.put("chunkIndex", 0);
            meta.put("batchId", batchId);
            
            Metadata metadata = Metadata.from(sanitizer.sanitize(meta));
            
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "EMBEDDING", 50, 
                    "Creating embedding...");
            }
            
            String embeddingId = indexText(text.trim(), metadata, batchId);
            
            if (embeddingId != null) {
                tracker.addTextEmbeddingId(batchId, embeddingId);
                
                if (progressNotifier != null) {
                    progressNotifier.embeddingProgress(batchId, filename, 1, 1);
                }
                
                return new ChunkResult(1, 0);
            }
            return new ChunkResult(0, 1);
        }
        
        int start = 0;
        
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();
            
            if (chunk.length() > 10) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("source", filename);
                meta.put("type", "docx_text");
                meta.put("chunkIndex", chunkIndex);
                meta.put("batchId", batchId);
                
                Metadata metadata = Metadata.from(sanitizer.sanitize(meta));
                
                String embeddingId = indexText(chunk, metadata, batchId);
                
                if (embeddingId != null) {
                    tracker.addTextEmbeddingId(batchId, embeddingId);
                    indexed++;
                    
                    if (indexed % 10 == 0 || indexed == estimatedChunks) {
                        if (progressNotifier != null) {
                            progressNotifier.embeddingProgress(batchId, filename, 
                                indexed, estimatedChunks);
                        }
                    }
                } else {
                    duplicates++;
                }
                
                chunkIndex++;
            }
            
            start += Math.max(1, chunkSize - overlap);
        }
        
        if (duplicates > 0) {
            log.info("✅ [{}] {} chunks indexed ({} duplicates skipped)", 
                getName(), indexed, duplicates);
        } else {
            log.info("✅ [{}] {} chunks indexed", getName(), indexed);
        }
        
        return new ChunkResult(indexed, duplicates);
    }
    
    // ========================================================================
    // HELPERS (INCHANGÉS)
    // ========================================================================
    
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    @Override
    public String getName() {
        return "DOCX";
    }
    
    @Override
    public int getPriority() {
        return 2;
    }
}