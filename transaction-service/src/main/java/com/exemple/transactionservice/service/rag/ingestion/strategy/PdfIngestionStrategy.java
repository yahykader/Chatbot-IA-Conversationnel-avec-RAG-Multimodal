// ============================================================================
// STRATEGY - PdfIngestionStrategy.java (VERSION COMPLÈTE)
// Stratégie d'ingestion pour PDF avec streaming + RAGMetrics unifié
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.strategy;

import com.exemple.transactionservice.service.rag.ingestion.progress.ProgressNotifier;
import com.exemple.transactionservice.service.rag.ingestion.cache.EmbeddingCache;
import com.exemple.transactionservice.service.rag.ingestion.analyzer.ImageSaver;
import com.exemple.transactionservice.service.rag.ingestion.analyzer.VisionAnalyzer;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.TextDeduplicationService;
import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Stratégie d'ingestion pour fichiers PDF - VERSION COMPLÈTE
 * 
 * ✅ ADAPTÉ AVEC RAGMetrics unifié
 * 
 * Fonctionnalités:
 * - Streaming pour gros PDFs (>100MB)
 * - Progress temps réel via ProgressNotifier
 * - Extraction texte + images embedded + rendu pages
 * - Déduplication fichier + texte
 * - Vision API pour images
 * - Métriques Prometheus via RAGMetrics
 * - Cache embeddings
 * - Retry automatique
 * 
 * @author RAG Team
 * @version 3.0 - Adapté avec RAGMetrics unifié
 */
@Slf4j
@Component
public class PdfIngestionStrategy implements IngestionStrategy {
    
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
    
    @Value("${document.max-pages:100}")
    private int maxPages;
    
    @Value("${document.max-images-per-file:100}")
    private int maxImagesPerFile;
    
    public PdfIngestionStrategy(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            EmbeddingModel embeddingModel,
            VisionAnalyzer visionAnalyzer,
            ImageSaver imageSaver,
            IngestionTracker tracker,
            MetadataSanitizer sanitizer,
            RAGMetrics ragMetrics,  // ✅ Injection RAGMetrics
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
        this.ragMetrics = ragMetrics;  // ✅ Utilisation metrics unifié
        this.deduplicationService = deduplicationService;
        this.textDeduplicationService = textDeduplicationService;
        this.signatureValidator = signatureValidator;
        this.embeddingCache = embeddingCache;
        
        log.info("✅ [{}] Strategy initialisée avec streaming + RAGMetrics", getName());
    }
    
    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return "pdf".equals(extension);
    }
    
    @Override
    public IngestionResult ingest(MultipartFile file, String batchId) throws Exception {
        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Progress - Upload started
            if (progressNotifier != null) {
                progressNotifier.uploadStarted(batchId, filename, fileSize);
            }
            
            log.info("📕 [{}] Processing PDF: {} ({} MB)", 
                getName(), filename, fileSize / 1_000_000);
            
            // Progress - Validation
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "VALIDATION", 8, 
                    "PDF validation...");
            }
            
            signatureValidator.validate(file, "pdf");
            
            // Progress - Deduplication
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "DEDUPLICATION", 10, 
                    "Checking duplicates...");
            }
            
            DeduplicationService.DuplicationInfo dupInfo = 
                deduplicationService.checkDuplication(file);
            
            if (dupInfo.isDuplicate()) {
                log.warn("⚠️ [{}] PDF duplicate: {}", getName(), filename);
                
                // Progress - Error
                if (progressNotifier != null) {
                    progressNotifier.error(batchId, filename, 
                        "Already processed (batch: " + dupInfo.originalBatchId() + ")");
                }
                
                // ✅ MÉTRIQUE: Duplicate
                ragMetrics.recordDuplicate(getName());
                
                throw new DuplicateFileException(
                    String.format("PDF already processed (batch: %s)", dupInfo.originalBatchId()),
                    dupInfo.originalBatchId()
                );
            }
            
            // Progress - Upload completed
            if (progressNotifier != null) {
                progressNotifier.uploadCompleted(batchId, filename);
            }
            
            IngestionResult result;
            
            if (StreamingFileReader.requiresStreaming(file)) {
                log.info("📖 [{}] STREAMING enabled: {} MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestWithStreaming(file, batchId);
            } else {
                log.debug("📄 [{}] Normal mode: {} MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestNormal(file, batchId);
            }
            
            deduplicationService.markAsIngested(file, batchId);
            
            long duration = System.currentTimeMillis() - startTime;
            int totalEmbeddings = result.textEmbeddings() + result.imageEmbeddings();
            
            // ✅ MÉTRIQUE: Strategy processing
            ragMetrics.recordStrategyProcessing(
                getName(),
                duration,
                totalEmbeddings
            );
            
            // Progress - Completed
            if (progressNotifier != null) {
                progressNotifier.completed(batchId, filename, 
                    result.textEmbeddings(), result.imageEmbeddings());
            }
            
            log.info("✅ [{}] PDF processed: {} - text={} images={} duration={}ms mode={}",
                getName(), filename, result.textEmbeddings(), 
                result.imageEmbeddings(), duration,
                StreamingFileReader.requiresStreaming(file) ? "STREAMING" : "NORMAL");
            
            return result;
            
        } catch (DuplicateFileException e) {
            throw e;
            
        } catch (Exception e) {
            // Progress - Error
            if (progressNotifier != null) {
                progressNotifier.error(batchId, filename, e.getMessage());
            }
            
            log.error("❌ [{}] PDF processing error: {}", getName(), filename, e);
            throw e;
        }
    }
    
    private IngestionResult ingestNormal(MultipartFile file, String batchId) throws Exception {
        
        // Progress - Processing
        if (progressNotifier != null) {
            progressNotifier.processingStarted(batchId, file.getOriginalFilename());
        }
        
        boolean hasImages = pdfHasImages(file);
        
        if (hasImages) {
            return ingestPdfWithImages(file, batchId);
        } else {
            return ingestPdfTextOnly(file, batchId);
        }
    }
    
    private IngestionResult ingestWithStreaming(MultipartFile file, String batchId) 
            throws Exception {
        
        String filename = file.getOriginalFilename();
        Path tempFile = null;
        
        try {
            // Progress - Streaming
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "STREAMING", 15, 
                    "Loading PDF in streaming...");
            }
            
            log.debug("💾 [{}] Creating temp file...", getName());
            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                if (bytesWritten % (50 * 1024 * 1024) == 0) {
                    log.info("📊 [{}] Saved: {} MB", 
                        getName(), bytesWritten / 1_000_000);
                    
                    // Progress streaming détaillé
                    if (progressNotifier != null) {
                        int percentage = 15 + (int)((bytesWritten / (double)file.getSize()) * 10);
                        progressNotifier.notifyProgress(batchId, filename, "STREAMING", percentage, 
                            String.format("Loading: %d MB", bytesWritten / 1_000_000));
                    }
                }
            });
            
            log.info("✅ [{}] Temp file created: {}", getName(), tempFile);
            
            // Progress - Processing
            if (progressNotifier != null) {
                progressNotifier.processingStarted(batchId, filename);
            }
            
            boolean hasImages = pdfHasImagesFromFile(tempFile.toFile());
            
            IngestionResult result;
            if (hasImages) {
                result = ingestPdfWithImagesFromFile(tempFile.toFile(), filename, batchId);
            } else {
                result = ingestPdfTextOnlyFromFile(tempFile.toFile(), filename, batchId);
            }
            
            return result;
            
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.debug("🗑️ [{}] Temp file deleted", getName());
                } catch (IOException e) {
                    log.warn("⚠️ [{}] Cannot delete temp file: {}", 
                        getName(), e.getMessage());
                }
            }
        }
    }
    
    private boolean pdfHasImages(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             RandomAccessReadBuffer rarBuffer = new RandomAccessReadBuffer(inputStream);
             PDDocument document = Loader.loadPDF(rarBuffer)) {
            
            return pdfHasImagesInternal(document);
            
        } catch (Exception e) {
            log.warn("⚠️ [{}] Cannot check images: {}", getName(), e.getMessage());
            return false;
        }
    }
    
    private boolean pdfHasImagesFromFile(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            return pdfHasImagesInternal(document);
        } catch (Exception e) {
            log.warn("⚠️ [{}] Cannot check images: {}", getName(), e.getMessage());
            return false;
        }
    }
    
    private boolean pdfHasImagesInternal(PDDocument document) throws IOException {
        int pagesToCheck = Math.min(3, document.getNumberOfPages());
        
        for (int i = 0; i < pagesToCheck; i++) {
            PDPage page = document.getPage(i);
            PDResources resources = page.getResources();
            
            if (resources.getXObjectNames().iterator().hasNext()) {
                log.debug("✓ [{}] PDF contains images (page {})", getName(), i + 1);
                return true;
            }
        }
        
        return false;
    }
    
    private IngestionResult ingestPdfWithImages(MultipartFile file, String batchId) 
            throws Exception {
        
        String filename = file.getOriginalFilename();
        log.info("📕🖼️ [{}] Processing PDF with images: {}", getName(), filename);
        
        try (InputStream inputStream = file.getInputStream();
             RandomAccessReadBuffer rarBuffer = new RandomAccessReadBuffer(inputStream);
             PDDocument document = Loader.loadPDF(rarBuffer)) {
            
            return processPdfWithImages(document, filename, batchId);
        }
    }
    
    private IngestionResult ingestPdfWithImagesFromFile(File file, String filename, 
                                                         String batchId) throws Exception {
        
        log.info("📕🖼️ [{}] Processing PDF with images (streaming): {}", 
            getName(), filename);
        
        try (PDDocument document = Loader.loadPDF(file)) {
            return processPdfWithImages(document, filename, batchId);
        }
    }
    
    private IngestionResult processPdfWithImages(PDDocument document, String filename, 
                                                  String batchId) throws Exception {
        
        int textEmbeddings = 0;
        int imageEmbeddings = 0;
        
        int totalPages = document.getNumberOfPages();
        
        if (totalPages > maxPages) {
            throw new IllegalArgumentException(
                String.format("PDF too large: %d pages (max: %d)", 
                    totalPages, maxPages)
            );
        }
        
        log.info("📄 [{}] PDF: {} pages", getName(), totalPages);
        
        // Progress - Extraction
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "EXTRACTION", 30, 
                "Content extraction...");
        }
        
        PDFTextStripper stripper = new PDFTextStripper();
        PDFRenderer renderer = new PDFRenderer(document);
        
        int totalImagesExtracted = 0;
        String baseFilename = FileUtils.sanitizeFilename(
            FileUtils.removeExtension(filename)
        );
        
        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            
            if (totalImagesExtracted >= maxImagesPerFile) {
                log.warn("⚠️ [{}] Image limit reached: {}", 
                    getName(), maxImagesPerFile);
                break;
            }
            
            int pageNum = pageIndex + 1;
            
            // Progress par page
            if (pageIndex % 5 == 0) {
                if (progressNotifier != null) {
                    int percentage = 30 + (int)((pageIndex / (double)totalPages) * 30);
                    progressNotifier.notifyProgress(batchId, filename, "PROCESSING", percentage, 
                        String.format("Processing page %d/%d", pageNum, totalPages));
                }
            }
            
            // 1. TEXT EXTRACTION
            stripper.setStartPage(pageNum);
            stripper.setEndPage(pageNum);
            String pageText = stripper.getText(document);
            
            if (pageText != null && !pageText.trim().isEmpty() && pageText.length() > 10) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("page", pageNum);
                meta.put("totalPages", totalPages);
                meta.put("source", filename);
                meta.put("type", "pdf_page_" + pageNum);
                meta.put("batchId", batchId);
                
                Metadata metadata = Metadata.from(sanitizer.sanitize(meta));
                String embeddingId = indexText(pageText, metadata, batchId);
                
                if (embeddingId != null) {
                    tracker.addTextEmbeddingId(batchId, embeddingId);
                    textEmbeddings++;
                }
            }
            
            // 2. EMBEDDED IMAGES EXTRACTION
            try {
                PDPage page = document.getPage(pageIndex);
                PDResources resources = page.getResources();
                
                int imageIndexOnPage = 0;
                
                for (COSName name : resources.getXObjectNames()) {
                    if (totalImagesExtracted >= maxImagesPerFile) break;
                    
                    PDXObject xObject = resources.getXObject(name);
                    
                    if (xObject instanceof PDImageXObject imageXObject) {
                        try {
                            BufferedImage bufferedImage = imageXObject.getImage();
                            
                            if (bufferedImage != null) {
                                totalImagesExtracted++;
                                imageIndexOnPage++;
                                
                                // Progress images
                                if (progressNotifier != null && totalImagesExtracted % 5 == 0) {
                                    progressNotifier.imageProgress(batchId, filename, 
                                        totalImagesExtracted, maxImagesPerFile);
                                }
                                
                                String imageName = FileUtils.generateImageName(
                                    baseFilename, batchId, 
                                    pageNum * 100 + imageIndexOnPage
                                );
                                
                                String savedImagePath = imageSaver.saveImage(
                                    bufferedImage, imageName
                                );
                                
                                Map<String, Object> metadata = new HashMap<>();
                                metadata.put("page", pageNum);
                                metadata.put("totalPages", totalPages);
                                metadata.put("source", "pdf_embedded");
                                metadata.put("filename", filename);
                                metadata.put("imageNumber", totalImagesExtracted);
                                metadata.put("savedPath", savedImagePath);
                                metadata.put("batchId", batchId);
                                
                                String embeddingId = analyzeAndIndexImageWithRetry(
                                    bufferedImage, imageName, metadata
                                );
                                
                                tracker.addImageEmbeddingId(batchId, embeddingId);
                                imageEmbeddings++;
                                
                                if (totalImagesExtracted % 10 == 0) {
                                    log.info("📊 [{}] Progress: {} images", 
                                        getName(), totalImagesExtracted);
                                }
                            }
                            
                        } catch (Exception e) {
                            log.warn("⚠️ [{}] Image extraction error page {}: {}", 
                                getName(), pageNum, e.getMessage());
                        }
                    }
                }
                
            } catch (Exception e) {
                log.warn("⚠️ [{}] Images extraction error page {}: {}", 
                    getName(), pageNum, e.getMessage());
            }
            
            // 3. FULL PAGE RENDERING
            if (totalImagesExtracted < maxImagesPerFile) {
                try {
                    BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, 150);
                    
                    String pageImageName = FileUtils.generatePageName(
                        baseFilename, batchId, pageNum
                    );
                    
                    String savedPageRenderPath = imageSaver.saveImage(
                        pageImage, pageImageName + "_render"
                    );
                    
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("page", pageNum);
                    metadata.put("totalPages", totalPages);
                    metadata.put("source", "pdf_rendered");
                    metadata.put("filename", filename);
                    metadata.put("savedPath", savedPageRenderPath);
                    metadata.put("batchId", batchId);
                    
                    String embeddingId = analyzeAndIndexImageWithRetry(
                        pageImage, pageImageName, metadata
                    );
                    
                    tracker.addImageEmbeddingId(batchId, embeddingId);
                    imageEmbeddings++;
                    totalImagesExtracted++;
                    
                } catch (Exception e) {
                    log.warn("⚠️ [{}] Page rendering error {}: {}", 
                        getName(), pageNum, e.getMessage());
                }
            }
            
            if (pageIndex % 10 == 0 && pageIndex > 0) {
                System.gc();
                Thread.sleep(50);
            }
        }
        
        // Progress - Indexing
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "INDEXING", 90, 
                "Finalizing indexing...");
        }
        
        log.info("✅ [{}] PDF processed: {} pages, {} texts, {} images", 
            getName(), totalPages, textEmbeddings, imageEmbeddings);
        
        Map<String, Object> resultMetadata = new HashMap<>();
        resultMetadata.put("strategy", getName());
        resultMetadata.put("filename", filename);
        resultMetadata.put("hasImages", true);
        
        return new IngestionResult(textEmbeddings, imageEmbeddings, resultMetadata);
    }
    
    private IngestionResult ingestPdfTextOnly(MultipartFile file, String batchId) 
            throws Exception {
        
        String filename = file.getOriginalFilename();
        
        try (InputStream inputStream = file.getInputStream();
             RandomAccessReadBuffer rarBuffer = new RandomAccessReadBuffer(inputStream);
             PDDocument document = Loader.loadPDF(rarBuffer)) {
            
            return processPdfTextOnly(document, filename, batchId);
        }
    }
    
    private IngestionResult ingestPdfTextOnlyFromFile(File file, String filename, 
                                                       String batchId) throws Exception {
        
        try (PDDocument document = Loader.loadPDF(file)) {
            return processPdfTextOnly(document, filename, batchId);
        }
    }
    
    private IngestionResult processPdfTextOnly(PDDocument document, String filename, 
                                                String batchId) throws Exception {
        
        log.info("📕 [{}] Processing PDF text: {}", getName(), filename);
        
        // Progress - Extraction
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "EXTRACTION", 30, 
                "Text extraction...");
        }
        
        PDFTextStripper stripper = new PDFTextStripper();
        String fullText = stripper.getText(document);
        
        if (fullText == null || fullText.isBlank()) {
            throw new IllegalArgumentException("PDF contains no text");
        }
        
        log.debug("📝 [{}] Text: {} characters", getName(), fullText.length());
        
        // Progress - Chunking
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "CHUNKING", 40, 
                "Text chunking...");
        }
        
        var chunkResult = chunkAndIndexText(fullText, filename, batchId);
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
    
    @Retryable(
        value = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private String analyzeAndIndexImageWithRetry(
            BufferedImage image,
            String imageName,
            Map<String, Object> additionalMetadata) throws IOException {
        
        long visionStart = System.currentTimeMillis();
        
        try {
            String description = visionAnalyzer.analyzeImage(image);
            long visionDuration = System.currentTimeMillis() - visionStart;
            
            // ✅ MÉTRIQUE: Vision API call
            ragMetrics.recordApiCall("vision_analyze", visionDuration);
            
            Map<String, Object> metadata = new HashMap<>(sanitizer.sanitize(additionalMetadata));
            metadata.put("imageName", imageName);
            metadata.put("type", "image");
            metadata.put("width", image.getWidth());
            metadata.put("height", image.getHeight());
            
            TextSegment segment = TextSegment.from(
                description,
                Metadata.from(metadata)
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
            
        } catch (Exception e) {
            log.warn("⚠️ [{}] Vision AI error: {}", getName(), e.getMessage());
            
            // ✅ MÉTRIQUE: Vision API error
            ragMetrics.recordApiError("vision_analyze");
            
            if (e instanceof IOException || e instanceof TimeoutException) {
                throw (IOException) e;
            }
            
            throw new IOException("Vision API error", e);
        }
    }
    
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
            meta.put("type", "pdf_text");
            meta.put("chunkIndex", 0);
            meta.put("batchId", batchId);
            
            Metadata metadata = Metadata.from(sanitizer.sanitize(meta));
            
            // Progress embedding
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "EMBEDDING", 50, 
                    "Creating embedding...");
            }
            
            String embeddingId = indexText(text.trim(), metadata, batchId);
            
            if (embeddingId != null) {
                tracker.addTextEmbeddingId(batchId, embeddingId);
                
                // Progress terminé
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
                meta.put("type", "pdf_text");
                meta.put("chunkIndex", chunkIndex);
                meta.put("batchId", batchId);
                
                Metadata metadata = Metadata.from(sanitizer.sanitize(meta));
                
                String embeddingId = indexText(chunk, metadata, batchId);
                
                if (embeddingId != null) {
                    tracker.addTextEmbeddingId(batchId, embeddingId);
                    indexed++;
                    
                    // Progress tous les 10 chunks
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
        
        log.info("✅ [{}] {} chunks indexed ({} duplicates skipped)", 
            getName(), indexed, duplicates);
        
        return new ChunkResult(indexed, duplicates);
    }
    
    private String indexText(String text, Metadata metadata, String batchId) {
        
        if (!textDeduplicationService.checkAndMark(text, batchId)) {
            log.debug("⏭️ [Dedup] Duplicate text, skip: {}", 
                truncate(text, 50));
            return null;
        }
        
        log.debug("✅ [Dedup] New text, indexing: {}", 
            truncate(text, 50));
        
        TextSegment segment = TextSegment.from(text, metadata);
        
        Embedding embedding = embeddingCache.getOrCompute(
            text, 
            () -> {
                long apiStart = System.currentTimeMillis();
                Embedding emb = embeddingModel.embed(text).content();
                long apiDuration = System.currentTimeMillis() - apiStart;
                
                // ✅ MÉTRIQUE: Embedding API call
                ragMetrics.recordApiCall("embed_text", apiDuration);
                
                return emb;
            }
        );
        
        long storeStart = System.currentTimeMillis();
        String embeddingId = textStore.add(embedding, segment);
        long storeDuration = System.currentTimeMillis() - storeStart;
        
        // ✅ MÉTRIQUE: Vector store operation
        ragMetrics.recordVectorStoreOperation("insert", storeDuration, 1);
        
        return embeddingId;
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    @Override
    public String getName() {
        return "PDF";
    }
    
    @Override
    public int getPriority() {
        return 1;
    }
}

/*
 * Progress Steps for PDF:
 * 5%   - Upload started
 * 8%   - PDF validation
 * 10%  - Duplicate check
 * 12%  - Upload completed
 * 15-25% - Streaming (if >100MB)
 * 30%  - Content extraction
 * 30-60% - Processing pages (every 5 pages)
 * 60-80% - Image analysis (every 5 images)
 * 40-90% - Embedding creation (text only)
 * 90%  - Final indexing
 * 100% - Completed
 */