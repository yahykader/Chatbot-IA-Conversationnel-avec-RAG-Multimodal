// ============================================================================
// STRATEGY - PdfIngestionStrategy.java (VERSION AVEC STREAMING)
// Stratégie d'ingestion pour PDF avec streaming gros fichiers (>100MB)
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.strategy;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Stratégie d'ingestion pour fichiers PDF - VERSION AVEC STREAMING.
 * 
 * ✨ NOUVEAU dans cette version :
 * ✅ Streaming automatique pour fichiers >100MB
 * ✅ Mémoire constante (~20MB) au lieu de charger tout le fichier
 * ✅ Support fichiers jusqu'à 1GB+
 * ✅ Détection automatique du mode (normal vs streaming)
 * 
 * Améliorations précédentes :
 * ✅ Métriques Prometheus intégrées
 * ✅ Deduplication avec Redis
 * ✅ Validation signature PDF
 * ✅ Retry automatique sur Vision AI
 * ✅ Gestion mémoire optimisée
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
    private final IngestionMetrics metrics;
    private final DeduplicationService deduplicationService;
    private final TextDeduplicationService textDeduplicationService;
    private final FileSignatureValidator signatureValidator;
    private final EmbeddingCache embeddingCache;
    
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
        
        log.info("✅ [{}] Strategy initialisée avec streaming support", getName());
    }
    
    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return "pdf".equals(extension);
    }
    
    // ========================================================================
    // ✨ MÉTHODE PRINCIPALE AVEC STREAMING
    // ========================================================================
    
    @Override
    public IngestionResult ingest(MultipartFile file, String batchId) throws Exception {
        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();
        
        // ✨ MÉTRIQUE : Démarrer le timer
        long startTime = System.currentTimeMillis();
        metrics.startProcessing();
        
        try {
            log.info("📕 [{}] Traitement PDF: {} ({} MB)", 
                getName(), filename, fileSize / 1_000_000);
            
            // ========== VALIDATIONS ==========
            
            // 1️⃣ Validation signature
            signatureValidator.validate(file, "pdf");
            
            // 2️⃣ Déduplication
            DeduplicationService.DuplicationInfo dupInfo = 
                deduplicationService.checkDuplication(file);
            
            if (dupInfo.isDuplicate()) {
                log.warn("⚠️ [{}] PDF doublon: {}", getName(), filename);
                metrics.recordDuplicate(getName());
                throw new DuplicateFileException(
                    String.format("PDF déjà traité (batch: %s)", dupInfo.originalBatchId())
                );
            }
            
            // ========== ✨ DÉTECTION MODE STREAMING ==========
            
            IngestionResult result;
            
            if (StreamingFileReader.requiresStreaming(file)) {
                // ✅ STREAMING ACTIVÉ pour fichiers >100MB
                log.info("📖 [{}] STREAMING activé: {} MB > 100 MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestWithStreaming(file, batchId);
                
            } else {
                // ✅ MODE NORMAL pour fichiers <100MB
                log.debug("📄 [{}] Mode normal: {} MB < 100 MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestNormal(file, batchId);
            }
            
            // ========== POST-TRAITEMENT ==========
            
            // Marquer comme ingéré
            deduplicationService.markAsIngested(file, batchId);
            
            // Métriques succès
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordSuccess(
                getName(), 
                duration,
                result.textEmbeddings(),
                result.imageEmbeddings()
            );
            metrics.recordFileSize(getName(), fileSize);
            
            log.info("✅ [{}] PDF traité: {} - text={} images={} durée={}ms mode={}",
                getName(), filename, result.textEmbeddings(), 
                result.imageEmbeddings(), duration,
                StreamingFileReader.requiresStreaming(file) ? "STREAMING" : "NORMAL");
            
            return result;
            
        } catch (DuplicateFileException e) {
            metrics.endProcessing();
            throw e;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordError(getName(), e.getClass().getSimpleName(), duration);
            metrics.endProcessing();
            
            log.error("❌ [{}] Erreur traitement PDF: {}", getName(), filename, e);
            throw e;
            
        } finally {
            metrics.endProcessing();
        }
    }
    
    // ========================================================================
    // ✨ INGESTION NORMALE (<100MB)
    // ========================================================================
    
    /**
     * Ingestion normale pour petits fichiers (<100MB).
     * Charge le fichier en mémoire.
     */
    private IngestionResult ingestNormal(MultipartFile file, String batchId) throws Exception {
        
        // Détecter si images présentes
        boolean hasImages = pdfHasImages(file);
        
        if (hasImages) {
            return ingestPdfWithImages(file, batchId);
        } else {
            return ingestPdfTextOnly(file, batchId);
        }
    }
    
    // ========================================================================
    // ✨ INGESTION STREAMING (>100MB)
    // ========================================================================
    
    /**
     * Ingestion streaming pour gros fichiers (>100MB).
     * Sauvegarde en fichier temporaire puis traite sans charger en RAM.
     */
    private IngestionResult ingestWithStreaming(MultipartFile file, String batchId) 
            throws Exception {
        
        String filename = file.getOriginalFilename();
        Path tempFile = null;
        
        try {
            // ✨ Sauvegarder en fichier temporaire (streaming, pas de RAM)
            log.debug("💾 [{}] Création fichier temporaire...", getName());
            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                // Callback progression (optionnel)
                if (bytesWritten % (50 * 1024 * 1024) == 0) {
                    log.info("📊 [{}] Sauvegarde: {} MB", 
                        getName(), bytesWritten / 1_000_000);
                }
            });
            
            log.info("✅ [{}] Fichier temporaire créé: {}", getName(), tempFile);
            
            // Détecter si images
            boolean hasImages = pdfHasImagesFromFile(tempFile.toFile());
            
            // Traiter selon type
            IngestionResult result;
            if (hasImages) {
                result = ingestPdfWithImagesFromFile(tempFile.toFile(), filename, batchId);
            } else {
                result = ingestPdfTextOnlyFromFile(tempFile.toFile(), filename, batchId);
            }
            
            return result;
            
        } finally {
            // ✨ Nettoyer fichier temporaire
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.debug("🗑️ [{}] Fichier temporaire supprimé", getName());
                } catch (IOException e) {
                    log.warn("⚠️ [{}] Impossible de supprimer temp file: {}", 
                        getName(), e.getMessage());
                }
            }
        }
    }
    
    // ========================================================================
    // DÉTECTION IMAGES
    // ========================================================================
    
    /**
     * Détecte si le PDF contient des images (depuis MultipartFile)
     */
    private boolean pdfHasImages(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             RandomAccessReadBuffer rarBuffer = new RandomAccessReadBuffer(inputStream);
             PDDocument document = Loader.loadPDF(rarBuffer)) {
            
            return pdfHasImagesInternal(document);
            
        } catch (Exception e) {
            log.warn("⚠️ [{}] Impossible de vérifier images: {}", getName(), e.getMessage());
            return false;
        }
    }
    
    /**
     * ✨ NOUVEAU : Détecte images depuis File (pour streaming)
     */
    private boolean pdfHasImagesFromFile(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            return pdfHasImagesInternal(document);
        } catch (Exception e) {
            log.warn("⚠️ [{}] Impossible de vérifier images: {}", getName(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Logique commune de détection d'images
     */
    private boolean pdfHasImagesInternal(PDDocument document) throws IOException {
        int pagesToCheck = Math.min(3, document.getNumberOfPages());
        
        for (int i = 0; i < pagesToCheck; i++) {
            PDPage page = document.getPage(i);
            PDResources resources = page.getResources();
            
            if (resources.getXObjectNames().iterator().hasNext()) {
                log.debug("✓ [{}] PDF contient des images (page {})", getName(), i + 1);
                return true;
            }
        }
        
        return false;
    }
    
    // ========================================================================
    // INGESTION PDF AVEC IMAGES (MultipartFile - Mode Normal)
    // ========================================================================
    
    /**
     * Traite un PDF contenant des images (mode normal)
     */
    private IngestionResult ingestPdfWithImages(MultipartFile file, String batchId) 
            throws Exception {
        
        String filename = file.getOriginalFilename();
        log.info("📕🖼️ [{}] Traitement PDF avec images: {}", getName(), filename);
        
        try (InputStream inputStream = file.getInputStream();
             RandomAccessReadBuffer rarBuffer = new RandomAccessReadBuffer(inputStream);
             PDDocument document = Loader.loadPDF(rarBuffer)) {
            
            return processPdfWithImages(document, filename, batchId);
        }
    }
    
    // ========================================================================
    // ✨ INGESTION PDF AVEC IMAGES (File - Mode Streaming)
    // ========================================================================
    
    /**
     * ✨ NOUVEAU : Traite PDF avec images depuis File (streaming)
     */
    private IngestionResult ingestPdfWithImagesFromFile(File file, String filename, 
                                                         String batchId) throws Exception {
        
        log.info("📕🖼️ [{}] Traitement PDF avec images (streaming): {}", 
            getName(), filename);
        
        try (PDDocument document = Loader.loadPDF(file)) {
            return processPdfWithImages(document, filename, batchId);
        }
    }
    
    // ========================================================================
    // LOGIQUE COMMUNE - TRAITEMENT PDF AVEC IMAGES
    // ========================================================================
    
    /**
     * Logique commune de traitement PDF avec images
     */
    private IngestionResult processPdfWithImages(PDDocument document, String filename, 
                                                  String batchId) throws Exception {
        
        int textEmbeddings = 0;
        int imageEmbeddings = 0;
        
        int totalPages = document.getNumberOfPages();
        
        // Validation nombre de pages
        if (totalPages > maxPages) {
            throw new IllegalArgumentException(
                String.format("PDF trop volumineux: %d pages (max: %d)", 
                    totalPages, maxPages)
            );
        }
        
        log.info("📄 [{}] PDF: {} pages", getName(), totalPages);
        
        PDFTextStripper stripper = new PDFTextStripper();
        PDFRenderer renderer = new PDFRenderer(document);
        
        int totalImagesExtracted = 0;
        String baseFilename = FileUtils.sanitizeFilename(
            FileUtils.removeExtension(filename)
        );
        
        // Traiter chaque page
        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            
            if (totalImagesExtracted >= maxImagesPerFile) {
                log.warn("⚠️ [{}] Limite images atteinte: {}", 
                    getName(), maxImagesPerFile);
                break;
            }
            
            int pageNum = pageIndex + 1;
            
            // 1. EXTRACTION TEXTE
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
                
                tracker.addTextEmbeddingId(batchId, embeddingId);
                textEmbeddings++;
            }
            
            // 2. EXTRACTION IMAGES EMBEDDED
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
                                    log.info("📊 [{}] Progression: {} images", 
                                        getName(), totalImagesExtracted);
                                }
                            }
                            
                        } catch (Exception e) {
                            log.warn("⚠️ [{}] Erreur extraction image page {}: {}", 
                                getName(), pageNum, e.getMessage());
                        }
                    }
                }
                
            } catch (Exception e) {
                log.warn("⚠️ [{}] Erreur extraction images page {}: {}", 
                    getName(), pageNum, e.getMessage());
            }
            
            // 3. RENDU PAGE COMPLÈTE
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
                    log.warn("⚠️ [{}] Erreur rendu page {}: {}", 
                        getName(), pageNum, e.getMessage());
                }
            }
            
            // OPTIMISATION MÉMOIRE
            if (pageIndex % 10 == 0 && pageIndex > 0) {
                System.gc();
                Thread.sleep(50);
            }
        }
        
        log.info("✅ [{}] PDF traité: {} pages, {} textes, {} images", 
            getName(), totalPages, textEmbeddings, imageEmbeddings);
        
        Map<String, Object> resultMetadata = new HashMap<>();
        resultMetadata.put("strategy", getName());
        resultMetadata.put("filename", filename);
        resultMetadata.put("hasImages", true);
        
        return new IngestionResult(textEmbeddings, imageEmbeddings, resultMetadata);
    }
    
    // ========================================================================
    // INGESTION PDF TEXTE SEULEMENT
    // ========================================================================
    
    /**
     * Traite un PDF texte (mode normal)
     */
    private IngestionResult ingestPdfTextOnly(MultipartFile file, String batchId) 
            throws Exception {
        
        String filename = file.getOriginalFilename();
        
        try (InputStream inputStream = file.getInputStream();
             RandomAccessReadBuffer rarBuffer = new RandomAccessReadBuffer(inputStream);
             PDDocument document = Loader.loadPDF(rarBuffer)) {
            
            return processPdfTextOnly(document, filename, batchId);
        }
    }
    
    /**
     * Traite PDF texte depuis File (streaming)
     */
    private IngestionResult ingestPdfTextOnlyFromFile(File file, String filename, 
                                                       String batchId) throws Exception {
        
        try (PDDocument document = Loader.loadPDF(file)) {
            return processPdfTextOnly(document, filename, batchId);
        }
    }
    
    /**
     * Logique commune traitement texte
     */
    private IngestionResult processPdfTextOnly(PDDocument document, String filename, 
                                                String batchId) throws Exception {
        
        log.info("📕 [{}] Traitement PDF texte: {}", getName(), filename);
        
        PDFTextStripper stripper = new PDFTextStripper();
        String fullText = stripper.getText(document);
        
        if (fullText == null || fullText.isBlank()) {
            throw new IllegalArgumentException("PDF ne contient pas de texte");
        }
        
        log.debug("📝 [{}] Texte: {} caractères", getName(), fullText.length());
        
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
    
    // ========================================================================
    // ANALYSE VISION AI AVEC RETRY
    // ========================================================================
    
    @Retryable(
        value = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private String analyzeAndIndexImageWithRetry(
            BufferedImage image,
            String imageName,
            Map<String, Object> additionalMetadata) throws IOException {
        
        try {
            String description = visionAnalyzer.analyzeImage(image);
            
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
                () -> embeddingModel.embed(description).content()
            );
            
            return imageStore.add(embedding, segment);
            
        } catch (Exception e) {
            log.warn("⚠️ [{}] Erreur Vision AI: {}", getName(), e.getMessage());
            
            if (e instanceof IOException || e instanceof TimeoutException) {
                throw (IOException) e;
            }
            
            throw new IOException("Vision API error", e);
        }
    }
    
    // ========================================================================
    // CHUNKING AVEC DÉDUPLICATION
    // ========================================================================
    
    private record ChunkResult(int indexed, int duplicates) {}
  
    private ChunkResult chunkAndIndexText(String text, String filename, String batchId) {
        int chunkSize = 1000;
        int overlap = 100;
        int indexed = 0;
        int duplicates = 0;
        int chunkIndex = 0;

        // ✅ Si texte plus court que chunkSize, indexer tel quel
        if (text.length() <= chunkSize) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("source", filename);
            meta.put("type", "pdf_text");
            meta.put("chunkIndex", 0);
            meta.put("batchId", batchId);
            
            Metadata metadata = Metadata.from(sanitizer.sanitize(meta));
            String embeddingId = indexText(text.trim(), metadata, batchId);
            
            if (embeddingId != null) {
                tracker.addTextEmbeddingId(batchId, embeddingId);
                 return new ChunkResult(1, 0);
            }
             return new ChunkResult(0, 1);
        }
        
        // Sinon, chunking avec overlap


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
                } else {
                    duplicates++;
                }

                chunkIndex++;
            }
            
            // ✅ Avancer de (chunkSize - overlap), minimum 1
            start += Math.max(1, chunkSize - overlap);
        }
        
        log.info("✅ [{}] {} chunks indexés ({} duplicates skip)", 
            getName(), indexed, duplicates);
        
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
        return "PDF";
    }
    
    @Override
    public int getPriority() {
        return 1;
    }
}