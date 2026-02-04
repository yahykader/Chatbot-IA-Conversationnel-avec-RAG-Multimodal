// ============================================================================
// STRATEGY - DocxIngestionStrategy.java (VERSION AVEC STREAMING + PROGRESS)
// Stratégie d'ingestion pour DOCX avec streaming gros fichiers (>100MB)
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.strategy;

import com.exemple.transactionservice.service.rag.ingestion.progress.ProgressNotifier;
import com.exemple.transactionservice.service.rag.ingestion.cache.EmbeddingCache;
import com.exemple.transactionservice.service.rag.ingestion.analyzer.ImageSaver;
import com.exemple.transactionservice.service.rag.ingestion.analyzer.VisionAnalyzer;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.TextDeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.metrics.IngestionMetrics;
import com.exemple.transactionservice.service.rag.ingestion.model.IngestionResult;
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
 * Stratégie d'ingestion pour fichiers DOCX avec streaming automatique + progress temps réel.
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
    private final IngestionMetrics metrics;
    private final DeduplicationService deduplicationService;
    private final TextDeduplicationService textDeduplicationService;
    private final FileSignatureValidator signatureValidator;
    private final EmbeddingCache embeddingCache;

    // ✅ AJOUT : ProgressNotifier (injection optionnelle)
    @Autowired(required = false)
    private ProgressNotifier progressNotifier;
    
    @Value("${document.max-images-per-file:100}")
    private int maxImagesPerFile;
    
    @Value("${document.docx.timeout-seconds:300}")
    private int docxTimeoutSeconds;
    
    public DocxIngestionStrategy(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            EmbeddingModel embeddingModel,
            VisionAnalyzer visionAnalyzer,
            ImageSaver imageSaver,
            IngestionTracker tracker,
            MetadataSanitizer sanitizer,
            IngestionMetrics metrics,
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
        this.metrics = metrics;
        this.deduplicationService = deduplicationService;
        this.textDeduplicationService = textDeduplicationService;
        this.signatureValidator = signatureValidator;
        this.embeddingCache = embeddingCache;
        
        log.info("✅ [{}] Strategy initialisée avec streaming", getName());
    }
    
    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return "docx".equals(extension);
    }
    
    @Override
    public IngestionResult ingest(MultipartFile file, String batchId) throws Exception {
        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();
        
        long startTime = System.currentTimeMillis();
        metrics.startProcessing();
        
        try {
            // ✅ Progress - Upload started
            if (progressNotifier != null) {
                progressNotifier.uploadStarted(batchId, filename, fileSize);
            }

            log.info("📘 [{}] Traitement DOCX: {} ({} MB)", 
                getName(), filename, fileSize / 1_000_000);
            
            if (file.isEmpty() || fileSize == 0) {
                if (progressNotifier != null) {
                    progressNotifier.error(batchId, filename, "Fichier vide");
                }
                throw new IOException("Fichier DOCX vide: " + filename);
            }
            
            // ✅ Progress - Validation
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "VALIDATION", 8, "Validation du fichier...");
            }

            signatureValidator.validate(file, "docx");

            // ✅ Progress - Vérification déduplication
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "DEDUPLICATION", 10, "Vérification des duplicates...");
            }

            DeduplicationService.DuplicationInfo dupInfo = 
                deduplicationService.checkDuplication(file);
            
            if (dupInfo.isDuplicate()) {
                metrics.recordDuplicate(getName());

                if (progressNotifier != null) {
                    progressNotifier.error(batchId, filename, 
                        "Fichier déjà traité (batch: " + dupInfo.originalBatchId() + ")");
                }

                throw new DuplicateFileException(
                    String.format("DOCX déjà traité (batch: %s)", 
                        dupInfo.originalBatchId()),
                    dupInfo.originalBatchId()
                );
            }
 
            // ✅ Progress - Début traitement
            if (progressNotifier != null) {
                progressNotifier.processingStarted(batchId, filename);
            }

            IngestionResult result;
            
            if (StreamingFileReader.requiresStreaming(file)) {
                log.info("📖 [{}] STREAMING activé: {} MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestWithStreaming(file, filename, batchId);
            } else {
                result = ingestNormal(file, filename, batchId);
            }
            
            deduplicationService.markAsIngested(file, batchId);
            
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordSuccess(getName(), duration,
                result.textEmbeddings(), result.imageEmbeddings());
            metrics.recordFileSize(getName(), fileSize);
 
            // ✅ Progress - Completed
            if (progressNotifier != null) {
                progressNotifier.completed(batchId, filename, 
                    result.textEmbeddings(), result.imageEmbeddings());
            }

            log.info("✅ [{}] DOCX traité: text={} images={} durée={}ms",
                getName(), result.textEmbeddings(), 
                result.imageEmbeddings(), duration);
            
            return result;
            
        } catch (DuplicateFileException e) {
            metrics.endProcessing();
            throw e;
        } catch (Exception e) {
            if (progressNotifier != null) {
                progressNotifier.error(batchId, filename, e.getMessage());
            }

            long duration = System.currentTimeMillis() - startTime;
            metrics.recordError(getName(), e.getClass().getSimpleName(), duration);
            metrics.endProcessing();
            throw e;
        } finally {
            metrics.endProcessing();
        }
    }
    
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
            // ✅ AJOUT : Progress - Streaming
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "STREAMING", 18, 
                    "Chargement DOCX en streaming...");
            }
            
            log.debug("💾 [{}] Création fichier temporaire...", getName());
            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                if (bytesWritten % (50 * 1024 * 1024) == 0) {
                    log.info("📊 [{}] Sauvegarde: {} MB", 
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
                throw new TimeoutException("Timeout ouverture DOCX: " + filename);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                throw new IOException("Erreur ouverture DOCX", cause);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interruption ouverture DOCX", e);
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
                log.warn("⚠️ Erreur fermeture document", e);
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
    
    private IngestionResult processDocxWithImages(XWPFDocument document, String filename, 
                                                   String batchId) throws Exception {
        
        int textEmbeddings = 0;
        int imageEmbeddings = 0;
        int totalImages = 0;
        int duplicates = 0;
        
        StringBuilder fullText = new StringBuilder();
        String baseFilename = FileUtils.sanitizeFilename(FileUtils.removeExtension(filename));
        String batchShort = batchId.substring(0, Math.min(8, batchId.length()));

        // ✅ AJOUT : Progress - Extraction
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "EXTRACTION", 20, "Extraction du texte...");
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

                            // ✅ AJOUT : Progress - Images
                            if (progressNotifier != null) {
                                progressNotifier.imageProgress(batchId, filename, totalImages, maxImagesPerFile);
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
                            
                            String embeddingId = analyzeAndIndexImageWithRetry(
                                image, imageName, metadata
                            );
                            
                            tracker.addImageEmbeddingId(batchId, embeddingId);
                            imageEmbeddings++;
                        } catch (Exception e) {
                            log.warn("⚠️ Erreur extraction image", e);
                        }
                    }
                }
            }
        }
        
        // ✅ AJOUT : Progress - Chunking
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "CHUNKING", 30, "Découpage du texte...");
        }       
            
        // INDEXER TEXTE
        if (fullText.length() > 0) {
            var chunkResult = chunkAndIndexText(fullText.toString(), filename, batchId);
            textEmbeddings = chunkResult.indexed();
            duplicates = chunkResult.duplicates();
        }
        
        if (duplicates > 0) {
            log.info("⏭️ [Dedup] {} duplicates skip, {} nouveaux indexés", 
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
            throw new IllegalArgumentException("DOCX vide: " + filename);
        }
        
        var chunkResult = chunkAndIndexText(fullText.toString(), filename, batchId);
        int textEmbeddings = chunkResult.indexed();
        int duplicates = chunkResult.duplicates();
        
        if (duplicates > 0) {
            log.info("⏭️ [Dedup] {} duplicates skip, {} nouveaux indexés", 
                duplicates, textEmbeddings);
        }
        
        Map<String, Object> resultMetadata = new HashMap<>();
        resultMetadata.put("strategy", getName());
        resultMetadata.put("filename", filename);
        resultMetadata.put("hasImages", false);
        
        return new IngestionResult(textEmbeddings, 0, resultMetadata);
    }
    
    @Retryable(
        value = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private String analyzeAndIndexImageWithRetry(BufferedImage image, String imageName,
                                                  Map<String, Object> additionalMetadata) 
            throws IOException {
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
            
            Embedding embedding = embeddingCache.getOrCompute(
                description, 
                () -> embeddingModel.embed(description).content()
            );
            
            return imageStore.add(embedding, segment);
        } catch (Exception e) {
            if (e instanceof IOException || e instanceof TimeoutException) {
                throw e;
            }
            throw new IOException("Vision API error", e);
        }
    }
    
    // ========================================================================
    // CHUNKING AVEC DÉDUPLICATION + PROGRESS
    // ========================================================================
    
    private record ChunkResult(int indexed, int duplicates) {}

    private ChunkResult chunkAndIndexText(String text, String filename, String batchId) {
        int chunkSize = 1000;
        int overlap = 100;
        int indexed = 0;
        int duplicates = 0;
        int chunkIndex = 0;
        
        // ✅ Estimer le nombre total de chunks
        int estimatedChunks = text.length() <= chunkSize ? 1 : 
            (int) Math.ceil(text.length() / (double)(chunkSize - overlap));
        
        // ✅ Si texte plus court que chunkSize, indexer tel quel
        if (text.length() <= chunkSize) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("source", filename);
            meta.put("type", "docx_text");
            meta.put("chunkIndex", 0);
            meta.put("batchId", batchId);
            
            Metadata metadata = Metadata.from(sanitizer.sanitize(meta));

            // ✅ Progress - Début embedding
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "EMBEDDING", 50, "Création embedding...");
            }

            String embeddingId = indexText(text.trim(), metadata, batchId);
            
            if (embeddingId != null) {
                tracker.addTextEmbeddingId(batchId, embeddingId);

                // ✅ Progress - Embedding terminé
                if (progressNotifier != null) {
                    progressNotifier.embeddingProgress(batchId, filename, 1, 1);
                }

                return new ChunkResult(1, 0);
            }
            return new ChunkResult(0, 1);
        }
        
        // ✅ Sinon, chunking avec overlap
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

                    // ✅ Progress - Tous les 10 chunks OU au dernier chunk
                    if (indexed % 10 == 0 || indexed == estimatedChunks) {
                        if (progressNotifier != null) {
                            progressNotifier.embeddingProgress(batchId, filename, indexed, estimatedChunks);
                        }
                    }

                } else {
                    duplicates++;
                }
                
                chunkIndex++;
            }
            
            // ✅ Avancer de (chunkSize - overlap), minimum 1
            start += Math.max(1, chunkSize - overlap);
        }
        
        // ✅ Log final avec stats
        if (duplicates > 0) {
            log.info("✅ [{}] {} chunks indexés ({} duplicates skip)", 
                getName(), indexed, duplicates);
        } else {
            log.info("✅ [{}] {} chunks indexés", getName(), indexed);
        }
        
        return new ChunkResult(indexed, duplicates);
    }
    
    private String indexText(String text, Metadata metadata, String batchId) {
        
        if (!textDeduplicationService.checkAndMark(text, batchId)) {
            log.debug("⏭️ [Dedup] Texte dupliqué, skip insertion: {}", 
                truncate(text, 50));
            return null;
        }
        
        log.debug("✅ [Dedup] Nouveau texte, indexation: {}", 
            truncate(text, 50));
        
        TextSegment segment = TextSegment.from(text, metadata);
        
        Embedding embedding = embeddingCache.getOrCompute(
            text, 
            () -> embeddingModel.embed(text).content()
        );
        
        return textStore.add(embedding, segment);
    }
    
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
/*
    ## 🎯 Étapes du progress pour DOCX
    ```
    5% - Upload started
    8% - Validation du fichier
    10% - Vérification des duplicates
    15% - Processing started
    18% - Streaming (si >100MB)
    20% - Extraction du texte
    25% - Analyse images (si présent)
    30% - Chunking du texte
    50-90% - Création embeddings (progress détaillé)
    100% - Completed

*/

