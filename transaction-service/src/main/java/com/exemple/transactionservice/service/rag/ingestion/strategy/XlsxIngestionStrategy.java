// ============================================================================
// STRATEGY - XlsxIngestionStrategy.java (VERSION COMPLÈTE AVEC VISION AI + PROGRESS)
// Fusion + Déduplication + Vision AI sur PDF + Progress temps réel
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
import com.exemple.transactionservice.service.rag.ingestion.util.InMemoryMultipartFile;
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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
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
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ✨ STRATÉGIE D'INGESTION XLSX - VERSION COMPLÈTE AVEC PROGRESS
 * 
 * ✅ ROBUSTESSE :
 *    - Détection charts robuste (3 méthodes)
 *    - Détection drawings complète
 *    - resolveDrawing() pour compatibilité POI
 *    - Extraction images via relations + fallback
 *    - Fallback LibreOffice amélioré
 * 
 * ✅ PERFORMANCE :
 *    - Streaming automatique >100MB
 *    - Retry Vision AI (3 tentatives)
 *    - Métriques Prometheus
 *    - EmbeddingCache Redis
 * 
 * ✅ DÉDUPLICATION :
 *    - TextDeduplicationService (évite duplicates PgVector)
 *    - Fix race condition (opération atomique)
 * 
 * ✅ VISION AI SUR PDF :
 *    - Sauvegarde PDF généré
 *    - Conversion PDF → Images
 *    - Analyse Vision AI de chaque page
 *    - Indexation dans imageEmbeddings
 * 
 * ✅ PROGRESS TEMPS RÉEL :
 *    - WebSocket notifications
 *    - Suivi granulaire par étape
 *    - Progress streaming, LibreOffice, Vision AI
 * 
 * @author System
 * @version 5.0.0
 */
@Slf4j
@Component
public class XlsxIngestionStrategy implements IngestionStrategy {
    
    // ========================================================================
    // DÉPENDANCES
    // ========================================================================
    
    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingStore<TextSegment> imageStore;
    private final EmbeddingModel embeddingModel;
    private final VisionAnalyzer visionAnalyzer;
    private final ImageSaver imageSaver;
    private final IngestionTracker tracker;
    private final MetadataSanitizer sanitizer;
    private final PdfIngestionStrategy pdfIngestionStrategy;
    private final RAGMetrics ragMetrics;
    private final DeduplicationService deduplicationService;
    private final TextDeduplicationService textDeduplicationService;
    private final FileSignatureValidator signatureValidator;
    private final EmbeddingCache embeddingCache;
    
    // ✅ AJOUT : ProgressNotifier (injection optionnelle)
    @Autowired(required = false)
    private ProgressNotifier progressNotifier;
    
    // ========================================================================
    // CONFIGURATION
    // ========================================================================
    
    @Value("${document.max-images-per-file:100}")
    private int maxImagesPerFile;
    
    @Value("${app.libreoffice.enabled:true}")
    private boolean libreofficeEnabled;
    
    @Value("${app.libreoffice.sofficePath:}")
    private String sofficePath;
    
    @Value("${app.libreoffice.timeoutSeconds:60}")
    private int libreofficeTimeoutSeconds;
    
    @Value("${document.max-pdf-pages-to-analyze:20}")
    private int maxPdfPagesToAnalyze;
    
    @Value("${app.pdf.save-generated:true}")
    private boolean savePdfGenerated;
    
    @Value("${app.pdf.generated-pdf-dir:uploads/generated-pdfs}")
    private String generatedPdfDir;
    
    @Value("${app.pdf.analyze-with-vision:true}")
    private boolean analyzePdfWithVision;
    
    @Value("${app.pdf.render-dpi:300}")
    private int pdfRenderDpi;
    
    // ========================================================================
    // CONSTRUCTEUR
    // ========================================================================
    
    public XlsxIngestionStrategy(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            EmbeddingModel embeddingModel,
            VisionAnalyzer visionAnalyzer,
            ImageSaver imageSaver,
            IngestionTracker tracker,
            MetadataSanitizer sanitizer,
            PdfIngestionStrategy pdfIngestionStrategy,
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
        this.pdfIngestionStrategy = pdfIngestionStrategy;
        this.ragMetrics = ragMetrics;
        this.deduplicationService = deduplicationService;
        this.textDeduplicationService = textDeduplicationService;
        this.signatureValidator = signatureValidator;
        this.embeddingCache = embeddingCache;
        
        log.info("✅ [{}] Strategy initialisée (streaming + déduplication + Vision AI + progress)", getName());
    }
    
    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return "xlsx".equals(extension);
    }
    
    // ========================================================================
    // MÉTHODE PRINCIPALE
    // ========================================================================
    
    @Override
    public IngestionResult ingest(MultipartFile file, String batchId) throws Exception {
        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();
        
        long startTime = System.currentTimeMillis();
        
        try {
            // ========================================================================
            // PROGRESS - Upload started
            // ========================================================================
            if (progressNotifier != null) {
                progressNotifier.uploadStarted(batchId, filename, fileSize);
            }
            
            log.info("📗 [{}] Traitement XLSX: {} ({} MB)", 
                getName(), filename, fileSize / 1_000_000);
            
            // ========================================================================
            // VALIDATIONS
            // ========================================================================
            
            // Empty file check
            if (file.isEmpty() || fileSize == 0) {
                if (progressNotifier != null) {
                    progressNotifier.error(batchId, filename, "Fichier vide");
                }
                throw new IOException("Fichier XLSX vide: " + filename);
            }
            
            // Signature validation
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "VALIDATION", 8, 
                    "Validation du fichier...");
            }
            
            signatureValidator.validate(file, "xlsx");
            
            // ========================================================================
            // ✅ SUPPRESSION: Vérification doublon (déjà faite par orchestrator)
            // ========================================================================
            // ❌ ANCIEN CODE SUPPRIMÉ:
            /*
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "DEDUPLICATION", 10, 
                    "Vérification des duplicates...");
            }
            
            DeduplicationService.DuplicationInfo dupInfo = 
                deduplicationService.checkDuplication(file);
            
            if (dupInfo.isDuplicate()) {
                if (progressNotifier != null) {
                    progressNotifier.error(batchId, filename, 
                        "Fichier déjà traité (batch: " + dupInfo.originalBatchId() + ")");
                }
                
                ragMetrics.recordDuplicate(getName());
                log.warn("⚠️ [{}] XLSX doublon: {}", getName(), filename);
                throw new DuplicateFileException(
                    String.format("XLSX déjà traité (batch: %s)", dupInfo.originalBatchId()),
                    dupInfo.originalBatchId()
                );
            }
            */
            // ✅ FIN SUPPRESSION
            
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
                log.info("📖 [{}] STREAMING activé: {} MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestWithStreaming(file, batchId);
            } else {
                log.debug("📄 [{}] Mode normal: {} MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestNormal(file, batchId);
            }
            
            // ========================================================================
            // POST-TRAITEMENT
            // ========================================================================
            
            // ✅ OPTIONNEL: Enregistrement (déjà fait par orchestrator)
            // deduplicationService.markAsIngested(file, batchId);
            
            // Text deduplication cleanup
            textDeduplicationService.clearLocalCache();
            
            var dedupStats = textDeduplicationService.getStats(batchId);
            log.info("📊 [Dedup] Stats - Total indexés: {}, Cache local: {}", 
                dedupStats.totalIndexed(), dedupStats.localCacheSize());
            
            // ========================================================================
            // METRICS
            // ========================================================================
            long duration = System.currentTimeMillis() - startTime;
            int totalEmbeddings = result.textEmbeddings() + result.imageEmbeddings();
            
            ragMetrics.recordStrategyProcessing(
                getName(),
                duration,
                totalEmbeddings
            );
            
            // ========================================================================
            // PROGRESS - Completed
            // ========================================================================
            if (progressNotifier != null) {
                progressNotifier.completed(batchId, filename, 
                    result.textEmbeddings(), result.imageEmbeddings());
            }
            
            log.info("✅ [{}] XLSX traité: {} - text={} images={} durée={}ms mode={}",
                getName(), filename, result.textEmbeddings(), 
                result.imageEmbeddings(), duration,
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
            
            log.error("❌ [{}] Erreur traitement XLSX: {}", getName(), filename, e);
            
            // Re-throw exception
            throw e;
            
        } finally {
            // Cleanup si nécessaire
            // Par exemple: fermer des ressources, nettoyer temp files, etc.
        }
    }
    
    // ========================================================================
    // INGESTION NORMALE (<100MB)
    // ========================================================================
    
    private IngestionResult ingestNormal(MultipartFile file, String batchId) throws Exception {
        
        String filename = file.getOriginalFilename();
        
        // ✅ AJOUT : Progress - Processing
        if (progressNotifier != null) {
            progressNotifier.processingStarted(batchId, filename);
        }
        
        byte[] bytes = file.getBytes();
        
        if (bytes.length < 2 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new IOException("Fichier XLSX invalide (pas ZIP OOXML): " + filename);
        }
        
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            return processWorkbook(workbook, file, filename, batchId, bytes);
        }
    }
    
    // ========================================================================
    // INGESTION STREAMING (>100MB)
    // ========================================================================
    
    private IngestionResult ingestWithStreaming(MultipartFile file, String batchId) 
            throws Exception {
        
        String filename = file.getOriginalFilename();
        Path tempFile = null;
        
        try {
            // ✅ AJOUT : Progress - Streaming
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "STREAMING", 15, 
                    "Chargement XLSX en streaming...");
            }
            
            log.debug("💾 [{}] Création fichier temporaire...", getName());
            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                if (bytesWritten % (50 * 1024 * 1024) == 0) {
                    log.info("📊 [{}] Sauvegarde: {} MB", 
                        getName(), bytesWritten / 1_000_000);
                    
                    // ✅ AJOUT : Progress streaming détaillé
                    if (progressNotifier != null) {
                        int percentage = 15 + (int)((bytesWritten / (double)file.getSize()) * 10);
                        progressNotifier.notifyProgress(batchId, filename, "STREAMING", percentage, 
                            String.format("Chargement: %d MB", bytesWritten / 1_000_000));
                    }
                }
            });
            
            log.info("✅ [{}] Fichier temporaire créé: {}", getName(), tempFile);
            
            // ✅ AJOUT : Progress - Processing
            if (progressNotifier != null) {
                progressNotifier.processingStarted(batchId, filename);
            }
            
            byte[] bytes = Files.readAllBytes(tempFile);
            
            try (FileInputStream fis = new FileInputStream(tempFile.toFile());
                 XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
                
                return processWorkbook(workbook, file, filename, batchId, bytes);
            }
            
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.debug("🗑️ [{}] Fichier temporaire supprimé", getName());
                } catch (IOException e) {
                    log.warn("⚠️ [{}] Impossible de supprimer temp: {}", 
                        getName(), e.getMessage());
                }
            }
        }
    }
    
    // ========================================================================
    // TRAITEMENT WORKBOOK
    // ========================================================================
    
    private IngestionResult processWorkbook(
            XSSFWorkbook workbook, 
            MultipartFile file,
            String filename, 
            String batchId,
            byte[] xlsxBytes) throws Exception {
        
        // ✅ AJOUT : Progress - Analysis
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "ANALYSIS", 25, 
                "Analyse du contenu XLSX...");
        }
        
        int sheetCount = workbook.getNumberOfSheets();
        int chartCount = countChartsRobust(workbook);
        boolean hasImages = hasImagesInXlsx(workbook);
        boolean hasDrawings = hasAnyDrawingInXlsx(workbook);
        
        log.info("🔍 [{}] XLSX analysé: sheets={} charts={} images={} drawings={}",
            getName(), sheetCount, chartCount, hasImages, hasDrawings);
        
        log.info("🖼️ [{}] getAllPictures()={}", getName(), workbook.getAllPictures().size());
        
        IngestionResult result;
        
        if (chartCount > 0 && !hasImages) {
            log.info("📊 [{}] Charts détectés, pas d'images → Conversion PDF", getName());
            result = processWithLibreOfficeFallback(xlsxBytes, filename, batchId);
        }
        else if (hasDrawings && !hasImages && chartCount == 0) {
            log.info("🎨 [{}] Drawings détectés, pas d'images → Conversion PDF", getName());
            result = processWithLibreOfficeFallback(xlsxBytes, filename, batchId);
        }
        else if (hasImages) {
            log.info("🖼️ [{}] Images détectées → Extraction XLSX", getName());
            result = processXlsxWithImages(workbook, filename, batchId);
        }
        else {
            log.info("📝 [{}] Texte uniquement → Extraction XLSX", getName());
            result = processXlsxTextOnly(workbook, filename, batchId);
        }
        
        return result;
    }
    
    // ========================================================================
    // DÉTECTION CHARTS ROBUSTE
    // ========================================================================
    
    private int countChartsRobust(XSSFWorkbook workbook) {
        int charts = 0;
        
        try {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                
                if (sheet instanceof XSSFChartSheet) {
                    charts++;
                    continue;
                }
                
                if (!(sheet instanceof XSSFSheet xssfSheet)) continue;
                
                XSSFDrawing drawing = resolveDrawing(xssfSheet);
                if (drawing == null) continue;
                
                try {
                    List<XSSFChart> embeddedCharts = drawing.getCharts();
                    if (embeddedCharts != null) {
                        charts += embeddedCharts.size();
                        continue;
                    }
                } catch (NoSuchMethodError | Exception ignored) {
                }
                
                for (POIXMLDocumentPart rel : drawing.getRelations()) {
                    if (rel instanceof XSSFChart) {
                        charts++;
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("⚠️ [{}] Erreur comptage charts: {}", getName(), e.getMessage());
        }
        
        return charts;
    }
    
    private boolean hasImagesInXlsx(XSSFWorkbook workbook) {
        try {
            if (!workbook.getAllPictures().isEmpty()) {
                return true;
            }
            
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (!(sheet instanceof XSSFSheet xssfSheet)) continue;
                
                XSSFDrawing drawing = resolveDrawing(xssfSheet);
                if (drawing == null) continue;
                
                for (XSSFShape shape : drawing.getShapes()) {
                    if (shape instanceof XSSFPicture) {
                        return true;
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("⚠️ [{}] Erreur détection images: {}", getName(), e.getMessage());
        }
        
        return false;
    }
    
    private boolean hasAnyDrawingInXlsx(XSSFWorkbook workbook) {
        try {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                
                if (sheet instanceof XSSFChartSheet) {
                    return true;
                }
                
                if (!(sheet instanceof XSSFSheet xssfSheet)) continue;
                
                XSSFDrawing drawing = resolveDrawing(xssfSheet);
                if (drawing == null) continue;
                
                try {
                    if (!drawing.getCharts().isEmpty()) {
                        return true;
                    }
                } catch (NoSuchMethodError | Exception ignored) {
                }
            }
            
        } catch (Exception e) {
            log.warn("⚠️ [{}] Erreur détection drawings: {}", getName(), e.getMessage());
        }
        
        return false;
    }
    
    private XSSFDrawing resolveDrawing(XSSFSheet sheet) {
        XSSFDrawing drawing = sheet.getDrawingPatriarch();
        if (drawing != null) {
            return drawing;
        }
        
        for (POIXMLDocumentPart rel : sheet.getRelations()) {
            if (rel instanceof XSSFDrawing xssfDrawing) {
                return xssfDrawing;
            }
        }
        
        return null;
    }
    
    // ========================================================================
    // ✨ FALLBACK LIBREOFFICE AVEC VISION AI
    // ========================================================================
    
    private IngestionResult processWithLibreOfficeFallback(
            byte[] xlsxBytes,
            String filename,
            String batchId) throws Exception {
        
        if (!libreofficeEnabled) {
            log.warn("⚠️ [{}] LibreOffice désactivé, texte uniquement", getName());
            
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
                return processXlsxTextOnly(workbook, filename, batchId);
            }
        }
        
        log.info("🔄 [{}] Conversion XLSX → PDF via LibreOffice", getName());
        
        // ✅ AJOUT : Progress - LibreOffice conversion
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "LIBREOFFICE", 30, 
                "Conversion en PDF via LibreOffice...");
        }
        
        String sofficeBinary = resolveSofficeExecutable();
        String baseFilename = FileUtils.sanitizeFilename(FileUtils.removeExtension(filename));
        Path tempDir = Files.createTempDirectory("xlsx2pdf_");
        Path inputXlsx = tempDir.resolve(baseFilename + ".xlsx");
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);
        
        try {
            // ========== ÉTAPE 1 : CONVERSION XLSX → PDF ==========
            
            Files.write(inputXlsx, xlsxBytes, 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            List<String> cmd = List.of(
                sofficeBinary,
                "--headless",
                "--nologo",
                "--nofirststartwizard",
                "--norestore",
                "--convert-to", "pdf",
                "--outdir", outDir.toAbsolutePath().toString(),
                inputXlsx.toAbsolutePath().toString()
            );

            // ✅ AJOUTER après lancement processus LibreOffice:
            long libreofficeStart = System.currentTimeMillis();
            
            Process process;
            try {
                process = new ProcessBuilder(cmd)
                        .redirectErrorStream(true)
                        .start();
            } catch (IOException e) {
                throw new IOException(
                    "LibreOffice introuvable. Installez LibreOffice ou configurez " +
                    "app.libreoffice.sofficePath. Commande=" + sofficeBinary, e);
            }
            
            String output = readAll(process.getInputStream());
            boolean finished = process.waitFor(libreofficeTimeoutSeconds, TimeUnit.SECONDS);

            long librefficeDuration = System.currentTimeMillis() - libreofficeStart;
    
            // ✅ MÉTRIQUE: LibreOffice conversion
            ragMetrics.recordApiCall("libreoffice_convert", librefficeDuration);
            
            if (!finished) {
                process.destroyForcibly();

                // ✅ MÉTRIQUE: LibreOffice error
                ragMetrics.recordApiError("libreoffice_convert");

                throw new IOException(
                    "Timeout conversion LibreOffice (" + libreofficeTimeoutSeconds + "s). " +
                    "Output=" + output);
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {

                // ✅ MÉTRIQUE: LibreOffice error  
                ragMetrics.recordApiError("libreoffice_convert");
                throw new IOException(
                    "Échec conversion LibreOffice (exit=" + exitCode + "). Output=" + output);
            }
            
            Path pdfPath = outDir.resolve(baseFilename + ".pdf");
            if (!Files.exists(pdfPath)) {
                try (var stream = Files.list(outDir)) {
                    Optional<Path> anyPdf = stream
                        .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                        .findFirst();
                    
                    if (anyPdf.isPresent()) {
                        pdfPath = anyPdf.get();
                    } else {
                        throw new IOException("PDF non généré par LibreOffice. Output=" + output);
                    }
                }
            }
            
            log.info("✅ [{}] PDF généré: {} ({} KB)", 
                getName(), pdfPath.getFileName(), Files.size(pdfPath) / 1024);
            
            // ✅ AJOUT : Progress - PDF generated
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "PDF_GENERATED", 50, 
                    "PDF généré, analyse en cours...");
            }
            
            byte[] pdfBytes = Files.readAllBytes(pdfPath);
            
            // ========== ÉTAPE 2 : SAUVEGARDER PDF ==========
            
            String savedPdfPath = savePdfToDisk(pdfBytes, baseFilename, batchId);
            if (savedPdfPath != null) {
                log.info("💾 [{}] PDF sauvegardé: {}", getName(), savedPdfPath);
            }
            
            // ========== ÉTAPE 3 : VISION AI SUR PDF ==========
            
            int visionImageEmbeddings = 0;
            
            if (analyzePdfWithVision && visionAnalyzer != null) {
                log.info("🎨 [{}] Conversion PDF → Images pour Vision AI", getName());
                
                visionImageEmbeddings = convertPdfToImagesAndAnalyze(
                    pdfBytes, 
                    baseFilename, 
                    batchId, 
                    savedPdfPath
                );
                
                log.info("✅ [{}] {} pages analysées avec Vision AI", 
                    getName(), visionImageEmbeddings);
            }
            
            // ========== ÉTAPE 4 : INGESTION TEXTE PDF ==========
            
            MultipartFile pdfFile = new InMemoryMultipartFile(
                "file",
                baseFilename + ".pdf",
                "application/pdf",
                pdfBytes
            );
            
            log.info("📕 [{}] Traitement PDF généré (texte)", getName());
            IngestionResult pdfResult = pdfIngestionStrategy.ingest(pdfFile, batchId);
            
            // ========== ÉTAPE 5 : COMBINER RÉSULTATS ==========
            
            Map<String, Object> resultMetadata = new HashMap<>(pdfResult.metadata());
            resultMetadata.put("originalFormat", "xlsx");
            resultMetadata.put("conversionMethod", "libreoffice");
            resultMetadata.put("xlsxFilename", filename);
            resultMetadata.put("pdfSavedPath", savedPdfPath);
            resultMetadata.put("pdfPagesAnalyzed", visionImageEmbeddings);
            
            return new IngestionResult(
                pdfResult.textEmbeddings(),
                pdfResult.imageEmbeddings() + visionImageEmbeddings,
                resultMetadata
            );
            
        } finally {
            try {
                Files.deleteIfExists(inputXlsx);
                if (Files.exists(outDir)) {
                    try (var stream = Files.list(outDir)) {
                        stream.forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {}
                        });
                    }
                    Files.deleteIfExists(outDir);
                }
                Files.deleteIfExists(tempDir);
            } catch (Exception e) {
                log.warn("⚠️ [{}] Erreur cleanup: {}", getName(), e.getMessage());
            }
        }
    }
    
    // ========================================================================
    // ✨ SAUVEGARDER PDF SUR DISQUE
    // ========================================================================
    
    private String savePdfToDisk(byte[] pdfBytes, String baseFilename, String batchId) 
            throws IOException {
        
        if (!savePdfGenerated) {
            log.debug("⏭️ [{}] Sauvegarde PDF désactivée", getName());
            return null;
        }
        
        Path pdfDir = Paths.get(generatedPdfDir);
        Files.createDirectories(pdfDir);
        
        String timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        String pdfFilename = String.format("%s_batch%s_%s.pdf",
            baseFilename,
            batchId.substring(0, Math.min(8, batchId.length())),
            timestamp
        );
        
        Path pdfPath = pdfDir.resolve(pdfFilename);
        Files.write(pdfPath, pdfBytes);
        
        log.debug("💾 [{}] PDF sauvegardé: {} ({} KB)", 
            getName(), pdfPath.getFileName(), pdfBytes.length / 1024);
        
        return pdfPath.toAbsolutePath().toString();
    }
    
    // ========================================================================
    // ✨ CONVERTIR PDF EN IMAGES + VISION AI
    // ========================================================================
    
    private int convertPdfToImagesAndAnalyze(
            byte[] pdfBytes,
            String baseFilename,
            String batchId,
            String pdfSavedPath) throws Exception {
        
        if (!analyzePdfWithVision) {
            log.debug("⏭️ [{}] Analyse Vision AI désactivée", getName());
            return 0;
        }
        
        int pagesAnalyzed = 0;
        String batchShort = batchId.substring(0, Math.min(8, batchId.length()));
        
        Path tempPdf = Files.createTempFile("xlsx_pdf_", ".pdf");
        
        try {
            Files.write(tempPdf, pdfBytes);
            
            try (PDDocument document = Loader.loadPDF(tempPdf.toFile())) {
                
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                int totalPages = document.getNumberOfPages();
                
                log.info("📄 [{}] PDF contient {} pages", getName(), totalPages);
                
                int maxPages = Math.min(totalPages, maxPdfPagesToAnalyze);
                
                for (int pageIndex = 0; pageIndex < maxPages; pageIndex++) {
                    
                    try {
                        BufferedImage pageImage = pdfRenderer.renderImageWithDPI(
                            pageIndex, 
                            pdfRenderDpi
                        );
                        
                        log.debug("🎨 [{}] Page {} rendue: {}x{} pixels ({}DPI)", 
                            getName(), pageIndex + 1, 
                            pageImage.getWidth(), pageImage.getHeight(),
                            pdfRenderDpi);
                        
                        String imageName = String.format("%s_batch%s_page%d",
                            baseFilename,
                            batchShort,
                            pageIndex + 1
                        );
                        
                        String savedImagePath = imageSaver.saveImage(pageImage, imageName);
                        
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("source", "xlsx_to_pdf");
                        metadata.put("pdfPath", pdfSavedPath);
                        metadata.put("pageNumber", pageIndex + 1);
                        metadata.put("totalPages", totalPages);
                        metadata.put("imageNumber", pageIndex + 1);
                        metadata.put("savedPath", savedImagePath);
                        metadata.put("batchId", batchId);
                        metadata.put("type", "pdf_page_chart");
                        metadata.put("conversionMethod", "libreoffice");
                        metadata.put("renderDpi", pdfRenderDpi);
                        metadata.put("width", pageImage.getWidth());
                        metadata.put("height", pageImage.getHeight());
                        
                        String embeddingId = analyzeAndIndexImageWithRetry(
                            pageImage,
                            imageName,
                            metadata
                        );
                        
                        tracker.addImageEmbeddingId(batchId, embeddingId);
                        pagesAnalyzed++;
                        
                        // ✅ AJOUT : Progress tous les 3 pages
                        if ((pageIndex + 1) % 3 == 0 || (pageIndex + 1) == maxPages) {
                            if (progressNotifier != null) {
                                int percentage = 50 + (int)(((pageIndex + 1) / (double)maxPages) * 30);
                                progressNotifier.notifyProgress(batchId, baseFilename + ".xlsx", 
                                    "PDF_VISION", percentage, 
                                    String.format("Analyse Vision AI: page %d/%d", pageIndex + 1, maxPages));
                            }
                            
                            log.info("📊 [{}] Progress: {}/{} pages analysées", 
                                getName(), pageIndex + 1, maxPages);
                        }
                        
                    } catch (Exception e) {
                        log.warn("⚠️ [{}] Erreur analyse page {}: {}", 
                            getName(), pageIndex + 1, e.getMessage());
                    }
                }
                
                if (totalPages > maxPages) {
                    log.warn("⚠️ [{}] PDF contient {} pages, limité à {} (config: max-pdf-pages-to-analyze)", 
                        getName(), totalPages, maxPages);
                }
            }
            
        } catch (Exception e) {
            log.error("❌ [{}] Erreur conversion PDF→Images: {}", getName(), e.getMessage());
            throw e;
            
        } finally {
            try {
                Files.deleteIfExists(tempPdf);
            } catch (IOException ignored) {
            }
        }
        
        return pagesAnalyzed;
    }
    
    // ========================================================================
    // EXTRACTION XLSX AVEC IMAGES
    // ========================================================================
    
    private IngestionResult processXlsxWithImages(
            XSSFWorkbook workbook,
            String filename,
            String batchId) throws Exception {
        
        log.info("📗🖼️ [{}] Extraction texte + images XLSX", getName());
        
        // ✅ AJOUT : Progress - Extraction
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "EXTRACTION", 30, 
                "Extraction des données...");
        }
        
        int textEmbeddings = 0;
        int imageEmbeddings = 0;
        int totalImagesExtracted = 0;
        int duplicates = 0;
        
        StringBuilder fullText = new StringBuilder();
        DataFormatter dataFormatter = new DataFormatter();
        FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
        
        String baseFilename = FileUtils.sanitizeFilename(FileUtils.removeExtension(filename));
        String batchShort = batchId.length() >= 8 ? batchId.substring(0, 8) : batchId;
        
        long nonEmptyCells = 0;
        
        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            
            if (totalImagesExtracted >= maxImagesPerFile) {
                log.warn("⚠️ [{}] Limite images atteinte: {}", getName(), maxImagesPerFile);
                break;
            }
            
            XSSFSheet sheet = workbook.getSheetAt(sheetIndex);
            String sheetName = sheet.getSheetName();
            
            fullText.append("\n=== Sheet: ").append(sheetName).append(" ===\n");
            
            // EXTRACTION TEXTE
            for (Row row : sheet) {
                boolean anyInRow = false;
                
                for (Cell cell : row) {
                    try {
                        String cellValue = dataFormatter.formatCellValue(cell, formulaEvaluator);
                        
                        if (cellValue != null) {
                            cellValue = cellValue.trim();
                            if (!cellValue.isEmpty()) {
                                if (anyInRow) fullText.append(" | ");
                                fullText.append(cellValue);
                                anyInRow = true;
                                nonEmptyCells++;
                            }
                        }
                        
                    } catch (Exception e) {
                        log.warn("⚠️ [{}] Erreur lecture cellule: {}", 
                            getName(), e.getMessage());
                    }
                }
                
                if (anyInRow) fullText.append('\n');
            }
            
            fullText.append('\n');
            
            // EXTRACTION IMAGES
            XSSFDrawing drawing = resolveDrawing(sheet);
            if (drawing != null) {
                int imageIndexInSheet = 0;
                
                for (XSSFShape shape : drawing.getShapes()) {
                    if (totalImagesExtracted >= maxImagesPerFile) break;
                    
                    if (shape instanceof XSSFPicture picture) {
                        try {
                            XSSFPictureData pictureData = picture.getPictureData();
                            if (pictureData == null) continue;
                            
                            byte[] imageBytes = pictureData.getData();
                            if (imageBytes == null || imageBytes.length == 0) continue;
                            
                            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
                            if (image == null) continue;
                            
                            totalImagesExtracted++;
                            imageIndexInSheet++;
                            
                            // ✅ AJOUT : Progress images tous les 5
                            if (totalImagesExtracted % 5 == 0 && progressNotifier != null) {
                                progressNotifier.imageProgress(batchId, filename, 
                                    totalImagesExtracted, maxImagesPerFile);
                            }
                            
                            String imageName = String.format("%s_batch%s_sheet%d_img%d",
                                baseFilename, batchShort, sheetIndex + 1, imageIndexInSheet);
                            
                            String savedImagePath = imageSaver.saveImage(image, imageName);
                            
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("sheetName", sheetName);
                            metadata.put("sheetIndex", sheetIndex + 1);
                            metadata.put("imageNumber", totalImagesExtracted);
                            metadata.put("imageIndexInSheet", imageIndexInSheet);
                            metadata.put("source", "xlsx");
                            metadata.put("filename", filename);
                            metadata.put("savedPath", savedImagePath);
                            metadata.put("batchId", batchId);
                            
                            String embeddingId = analyzeAndIndexImageWithRetry(
                                image, imageName, metadata
                            );
                            
                            tracker.addImageEmbeddingId(batchId, embeddingId);
                            imageEmbeddings++;
                            
                            if (totalImagesExtracted % 10 == 0) {
                                log.info("📊 [{}] {} images extraites", 
                                    getName(), totalImagesExtracted);
                            }
                            
                        } catch (Exception e) {
                            log.warn("⚠️ [{}] Erreur extraction image sheet {}: {}",
                                getName(), sheetName, e.getMessage());
                        }
                    }
                }
            }
        }
        
        // INDEXER TEXTE
        // ✅ AJOUT : Progress - Chunking
        if (fullText.length() > 0 && progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "CHUNKING", 70, 
                "Découpage du texte...");
        }
        
        if (fullText.length() > 0) {
            var chunkResult = chunkAndIndexText(fullText.toString(), filename, batchId);
            textEmbeddings = chunkResult.indexed();
            duplicates = chunkResult.duplicates();
        }
        
        if (duplicates > 0) {
            log.info("⏭️ [Dedup] {} duplicates skip, {} nouveaux indexés", 
                duplicates, textEmbeddings);
        }
        
        log.info("✅ [{}] XLSX traité: {} sheets, {} cellules, {} images",
            getName(), workbook.getNumberOfSheets(), nonEmptyCells, totalImagesExtracted);
        
        Map<String, Object> resultMetadata = new HashMap<>();
        resultMetadata.put("strategy", getName());
        resultMetadata.put("filename", filename);
        resultMetadata.put("hasImages", true);
        resultMetadata.put("sheets", workbook.getNumberOfSheets());
        resultMetadata.put("nonEmptyCells", nonEmptyCells);
        resultMetadata.put("duplicatesSkipped", duplicates);
        
        return new IngestionResult(textEmbeddings, imageEmbeddings, resultMetadata);
    }
    
    // ========================================================================
    // EXTRACTION XLSX TEXTE SEULEMENT
    // ========================================================================
    
    private IngestionResult processXlsxTextOnly(
            XSSFWorkbook workbook,
            String filename,
            String batchId) throws Exception {
        
        log.info("📝 [{}] Extraction texte XLSX", getName());
        
        // ✅ AJOUT : Progress - Extraction
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "EXTRACTION", 30, 
                "Extraction du texte...");
        }
        
        StringBuilder fullText = new StringBuilder();
        DataFormatter dataFormatter = new DataFormatter();
        FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
        
        long nonEmptyCells = 0;
        
        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            String sheetName = sheet.getSheetName();
            
            fullText.append("\n=== Sheet: ").append(sheetName).append(" ===\n");
            
            for (Row row : sheet) {
                boolean anyInRow = false;
                
                for (Cell cell : row) {
                    try {
                        String cellValue = dataFormatter.formatCellValue(cell, formulaEvaluator);
                        
                        if (cellValue != null) {
                            cellValue = cellValue.trim();
                            if (!cellValue.isEmpty()) {
                                if (anyInRow) fullText.append(" | ");
                                fullText.append(cellValue);
                                anyInRow = true;
                                nonEmptyCells++;
                            }
                        }
                        
                    } catch (Exception e) {
                        log.warn("⚠️ [{}] Erreur cellule: {}", getName(), e.getMessage());
                    }
                }
                
                if (anyInRow) fullText.append('\n');
            }
            
            fullText.append('\n');
        }
        
        if (fullText.length() == 0) {
            throw new IllegalArgumentException("XLSX vide: " + filename);
        }
        
        // ✅ AJOUT : Progress - Chunking
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "CHUNKING", 50, 
                "Découpage du texte...");
        }
        
        var chunkResult = chunkAndIndexText(fullText.toString(), filename, batchId);
        int textEmbeddings = chunkResult.indexed();
        int duplicates = chunkResult.duplicates();
        
        if (duplicates > 0) {
            log.info("⏭️ [Dedup] {} duplicates skip, {} nouveaux indexés", 
                duplicates, textEmbeddings);
        }
        
        log.info("✅ [{}] XLSX texte traité: {} sheets, {} cellules",
            getName(), workbook.getNumberOfSheets(), nonEmptyCells);
        
        Map<String, Object> resultMetadata = new HashMap<>();
        resultMetadata.put("strategy", getName());
        resultMetadata.put("filename", filename);
        resultMetadata.put("hasImages", false);
        resultMetadata.put("sheets", workbook.getNumberOfSheets());
        resultMetadata.put("nonEmptyCells", nonEmptyCells);
        resultMetadata.put("duplicatesSkipped", duplicates);
        
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
        
        // ✅ Tracking Vision API
        long visionStart = System.currentTimeMillis();
        
        try {
            String description = visionAnalyzer.analyzeImage(image);
            long visionDuration = System.currentTimeMillis() - visionStart;
            
            // ✅ MÉTRIQUE: Vision API call
            ragMetrics.recordApiCall("vision_analyze", visionDuration);
            
            Map<String, Object> metadata = new HashMap<>(sanitizer.sanitize(additionalMetadata));
            metadata.put("imageName", imageName);
            metadata.put("type", metadata.getOrDefault("type", "image"));
            metadata.put("width", image.getWidth());
            metadata.put("height", image.getHeight());
            
            TextSegment segment = TextSegment.from(
                description,
                Metadata.from(metadata)
            );
            
            // ✅ Tracking Embedding avec callback
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
            
            // ✅ Tracking Vector Store
            long storeStart = System.currentTimeMillis();
            String embeddingId = imageStore.add(embedding, segment);
            long storeDuration = System.currentTimeMillis() - storeStart;
            
            // ✅ MÉTRIQUE: Vector store operation
            ragMetrics.recordVectorStoreOperation("insert", storeDuration, 1);
            
            return embeddingId;
            
        } catch (Exception e) {
            // ✅ MÉTRIQUE: Vision API error
            ragMetrics.recordApiError("vision_analyze");
            
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
        
        // ✅ Estimer nombre de chunks
        int estimatedChunks = text.length() <= chunkSize ? 1 : 
            (int) Math.ceil(text.length() / (double)(chunkSize - overlap));

        if (text.length() <= chunkSize) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("source", filename);
            meta.put("type", "xlsx_text");
            meta.put("chunkIndex", 0);
            meta.put("batchId", batchId);
            
            Metadata metadata = Metadata.from(sanitizer.sanitize(meta));
            
            // ✅ AJOUT : Progress embedding
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "EMBEDDING", 80, 
                    "Création embedding...");
            }
            
            String embeddingId = indexText(text.trim(), metadata, batchId);
            
            if (embeddingId != null) {
                tracker.addTextEmbeddingId(batchId, embeddingId);
                
                // ✅ AJOUT : Progress terminé
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
                meta.put("type", "xlsx_text");
                meta.put("chunkIndex", chunkIndex);
                meta.put("batchId", batchId);
                
                Metadata metadata = Metadata.from(sanitizer.sanitize(meta));
                String embeddingId = indexText(chunk, metadata, batchId);
                
                if (embeddingId != null) {
                    tracker.addTextEmbeddingId(batchId, embeddingId);
                    indexed++;
                    
                    // ✅ AJOUT : Progress tous les 10 chunks
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
        
        log.info("✅ [{}] {} chunks indexés ({} duplicates skip)", 
            getName(), indexed, duplicates);
        
        return new ChunkResult(indexed, duplicates);
    }
    
    private String indexText(String text, Metadata metadata, String batchId) {
        
        if (!textDeduplicationService.checkAndMark(text, batchId)) {
            log.debug("⏭️ [Dedup] Duplicate text, skip: {}", truncate(text, 50));
            return null;
        }
        
        log.debug("✅ [Dedup] New text, indexing: {}", truncate(text, 50));
        
        TextSegment segment = TextSegment.from(text, metadata);
        
        // ✅ MODIFIER: Ajouter tracking embedding
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
        
        // ✅ AJOUTER: Tracking vector store
        long storeStart = System.currentTimeMillis();
        String embeddingId = textStore.add(embedding, segment);
        long storeDuration = System.currentTimeMillis() - storeStart;
        
        // ✅ MÉTRIQUE: Vector store operation
        ragMetrics.recordVectorStoreOperation("insert", storeDuration, 1);
        
        return embeddingId;
    }
    // ========================================================================
    // UTILITAIRES
    // ========================================================================
    
    private String resolveSofficeExecutable() {
        if (sofficePath != null && !sofficePath.isBlank()) {
            Path p = Paths.get(sofficePath);
            if (Files.exists(p)) {
                return p.toAbsolutePath().toString();
            }
            throw new IllegalStateException(
                "LibreOffice sofficePath configuré mais introuvable: " + p);
        }

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            List<String> candidates = List.of(
                "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
                "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe"
            );
            
            for (String c : candidates) {
                if (Files.exists(Paths.get(c))) {
                    return c;
                }
            }
            
            return "soffice.exe";
        }

        return "soffice";
    }
    
    private String readAll(InputStream in) {
        try (in) {
            return new String(in.readAllBytes());
        } catch (Exception e) {
            return "";
        }
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    @Override
    public String getName() {
        return "XLSX";
    }
    
    @Override
    public int getPriority() {
        return 3;
    }
}