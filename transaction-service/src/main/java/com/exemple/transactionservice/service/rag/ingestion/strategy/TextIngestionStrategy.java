// ============================================================================
// STRATEGY - TextIngestionStrategy.java (VERSION AVEC STREAMING + PROGRESS)
// Stratégie d'ingestion pour fichiers texte avec streaming gros fichiers (>100MB)
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.strategy;

import com.exemple.transactionservice.service.rag.ingestion.progress.ProgressNotifier;
import com.exemple.transactionservice.service.rag.ingestion.cache.EmbeddingCache;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.TextDeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.metrics.IngestionMetrics;
import com.exemple.transactionservice.service.rag.ingestion.model.IngestionResult;
import com.exemple.transactionservice.service.rag.ingestion.tracker.IngestionTracker;
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
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stratégie d'ingestion pour fichiers texte - VERSION AVEC STREAMING + PROGRESS.
 */
@Slf4j
@Component
public class TextIngestionStrategy implements IngestionStrategy {
    
    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingModel embeddingModel;
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
    
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        // Texte basique
        "txt", "text", "log",
        // Markdown
        "md", "markdown",
        // Data
        "csv", "tsv", "json", "xml", "yaml", "yml",
        // Web
        "html", "htm", "css",
        // Code
        "java", "py", "js", "ts", "jsx", "tsx",
        "c", "cpp", "h", "hpp",
        "go", "rs", "rb", "php",
        "swift", "kt", "kts",
        "sql", "sh", "bash", "zsh",
        // Config
        "properties", "conf", "config", "ini", "env",
        // Documentation
        "rst", "adoc", "tex"
    );
    
    private static final Set<String> CODE_EXTENSIONS = Set.of(
        "java", "py", "js", "ts", "jsx", "tsx",
        "c", "cpp", "h", "hpp", "go", "rs", "rb",
        "php", "swift", "kt", "kts", "sql"
    );
    
    public TextIngestionStrategy(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            EmbeddingModel embeddingModel,
            IngestionTracker tracker,
            MetadataSanitizer sanitizer,
            IngestionMetrics metrics,
            DeduplicationService deduplicationService,
            TextDeduplicationService textDeduplicationService,
            FileSignatureValidator signatureValidator,
            EmbeddingCache embeddingCache) {
        
        this.textStore = textStore;
        this.embeddingModel = embeddingModel;
        this.tracker = tracker;
        this.sanitizer = sanitizer;
        this.metrics = metrics;
        this.deduplicationService = deduplicationService;
        this.textDeduplicationService = textDeduplicationService;
        this.signatureValidator = signatureValidator;
        this.embeddingCache = embeddingCache;
        
        log.info("✅ [{}] Strategy initialisée avec streaming support (40+ formats)", 
            getName());
    }
    
    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }
    
    @Override
    public IngestionResult ingest(MultipartFile file, String batchId) throws Exception {
        String filename = file.getOriginalFilename();
        String extension = getExtension(filename);
        long fileSize = file.getSize();
        
        long startTime = System.currentTimeMillis();
        metrics.startProcessing();
        
        try {
            // ✅ AJOUT : Progress - Upload started
            if (progressNotifier != null) {
                progressNotifier.uploadStarted(batchId, filename, fileSize);
            }
            
            log.info("📄 [{}] Traitement fichier texte: {} ({} MB, ext: {})", 
                getName(), filename, fileSize / 1_000_000, extension.toUpperCase());
            
            if (file.isEmpty() || fileSize == 0) {
                // ✅ AJOUT : Progress - Error
                if (progressNotifier != null) {
                    progressNotifier.error(batchId, filename, "Fichier vide");
                }
                throw new IOException("Fichier texte vide: " + filename);
            }
            
            // ✅ AJOUT : Progress - Déduplication
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "DEDUPLICATION", 10, 
                    "Vérification des duplicates...");
            }
            
            DeduplicationService.DuplicationInfo dupInfo = 
                deduplicationService.checkDuplication(file);
            
            if (dupInfo.isDuplicate()) {
                // ✅ AJOUT : Progress - Error
                if (progressNotifier != null) {
                    progressNotifier.error(batchId, filename, 
                        "Fichier déjà traité (batch: " + dupInfo.originalBatchId() + ")");
                }
                
                metrics.recordDuplicate(getName());
                throw new DuplicateFileException(
                    String.format("Fichier texte déjà traité (batch: %s)", 
                        dupInfo.originalBatchId()),
                    dupInfo.originalBatchId()
                );
            }
            
            // ✅ AJOUT : Progress - Upload completed
            if (progressNotifier != null) {
                progressNotifier.uploadCompleted(batchId, filename);
            }
            
            IngestionResult result;
            
            if (StreamingFileReader.requiresStreaming(file)) {
                log.info("📖 [{}] STREAMING activé: {} MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestWithStreaming(file, filename, extension, batchId, fileSize);
                
            } else {
                log.debug("📄 [{}] Mode normal: {} MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestNormal(file, filename, extension, batchId, fileSize);
            }
            
            deduplicationService.markAsIngested(file, batchId);
            
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordSuccess(getName(), duration, result.textEmbeddings(), 0);
            metrics.recordFileSize(getName(), fileSize);
            
            // ✅ AJOUT : Progress - Completed
            if (progressNotifier != null) {
                progressNotifier.completed(batchId, filename, result.textEmbeddings(), 0);
            }
            
            log.info("✅ [{}] Fichier texte traité: {} - {} chunks, durée={}ms mode={}",
                getName(), filename, result.textEmbeddings(), duration,
                StreamingFileReader.requiresStreaming(file) ? "STREAMING" : "NORMAL");
            
            return result;
            
        } catch (DuplicateFileException e) {
            metrics.endProcessing();
            throw e;
            
        } catch (Exception e) {
            // ✅ AJOUT : Progress - Error
            if (progressNotifier != null) {
                progressNotifier.error(batchId, filename, e.getMessage());
            }
            
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordError(getName(), e.getClass().getSimpleName(), duration);
            metrics.endProcessing();
            
            log.error("❌ [{}] Erreur traitement: {}", getName(), filename, e);
            throw e;
            
        } finally {
            metrics.endProcessing();
        }
    }
    
    private IngestionResult ingestNormal(MultipartFile file, String filename,
                                          String extension, String batchId,
                                          long fileSize) throws Exception {
        
        // ✅ AJOUT : Progress - Processing
        if (progressNotifier != null) {
            progressNotifier.processingStarted(batchId, filename);
        }
        
        // ✅ AJOUT : Progress - Reading
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "READING", 20, 
                "Lecture du contenu...");
        }
        
        String content = readTextWithEncodingDetection(file.getBytes(), filename);
        
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Fichier sans contenu: " + filename);
        }
        
        return processContent(content, filename, extension, batchId, fileSize);
    }
    
    private IngestionResult ingestWithStreaming(MultipartFile file, String filename,
                                                 String extension, String batchId,
                                                 long fileSize) throws Exception {
        
        Path tempFile = null;
        
        try {
            // ✅ AJOUT : Progress - Streaming
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "STREAMING", 15, 
                    "Chargement en streaming...");
            }
            
            log.debug("💾 [{}] Création fichier temporaire...", getName());
            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                if (bytesWritten % (50 * 1024 * 1024) == 0) {
                    log.info("📊 [{}] Sauvegarde: {} MB", 
                        getName(), bytesWritten / 1_000_000);
                    
                    // ✅ AJOUT : Progress streaming détaillé
                    if (progressNotifier != null) {
                        int percentage = 15 + (int)((bytesWritten / (double)fileSize) * 10);
                        progressNotifier.notifyProgress(batchId, filename, "STREAMING", percentage, 
                            String.format("Chargement: %d MB", bytesWritten / 1_000_000));
                    }
                }
            });
            
            log.info("✅ [{}] Fichier temporaire créé: {}", getName(), tempFile);
            
            // ✅ AJOUT : Progress - Reading
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "READING", 25, 
                    "Lecture du contenu...");
            }
            
            byte[] bytes = Files.readAllBytes(tempFile);
            String content = readTextWithEncodingDetection(bytes, filename);
            
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("Fichier sans contenu: " + filename);
            }
            
            return processContent(content, filename, extension, batchId, fileSize);
            
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
    
    private IngestionResult processContent(String content, String filename,
                                            String extension, String batchId,
                                            long fileSize) throws Exception {
        
        log.debug("📝 [{}] Contenu extrait: {} caractères", getName(), content.length());
        
        // ✅ AJOUT : Progress - Content analysis
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "ANALYSIS", 30, 
                "Analyse du contenu...");
        }
        
        String contentType = detectContentType(content, extension);
        
        log.info("🔍 [{}] Type détecté: {}", getName(), contentType);
        
        // ✅ AJOUT : Progress - Chunking
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "CHUNKING", 40, 
                "Découpage du texte...");
        }

        var chunkResult = chunkAndIndexText(content, filename, extension, contentType, batchId);
        int textEmbeddings = chunkResult.indexed();
        int duplicates = chunkResult.duplicates();
        
        if (duplicates > 0) {
            log.info("⏭️ [Dedup] {} duplicates skip, {} nouveaux indexés", 
                duplicates, textEmbeddings);
        }
        
        Map<String, Object> resultMetadata = new HashMap<>();
        resultMetadata.put("strategy", getName());
        resultMetadata.put("filename", filename);
        resultMetadata.put("extension", extension);
        resultMetadata.put("contentType", contentType);
        resultMetadata.put("characters", content.length());
        
        return new IngestionResult(textEmbeddings, 0, resultMetadata);
    }
    
    private String readTextWithEncodingDetection(byte[] bytes, String filename) 
            throws IOException {
        
        try {
            String content = new String(bytes, StandardCharsets.UTF_8);
            
            if (!content.contains("\uFFFD")) {
                log.debug("✓ [{}] Encodage détecté: UTF-8", getName());
                return content;
            }
            
        } catch (Exception e) {
            log.debug("⚠️ [{}] Échec lecture UTF-8", getName());
        }
        
        try {
            String content = new String(bytes, "ISO-8859-1");
            log.debug("✓ [{}] Encodage détecté: ISO-8859-1 (fallback)", getName());
            return content;
            
        } catch (Exception e) {
            throw new IOException("Impossible de lire le fichier: " + filename, e);
        }
    }
    
    private String detectContentType(String content, String extension) {
        
        if ("json".equals(extension) || content.trim().startsWith("{") || 
            content.trim().startsWith("[")) {
            return "json";
        }
        
        if ("xml".equals(extension) || "html".equals(extension) || "htm".equals(extension) ||
            content.trim().startsWith("<")) {
            return extension.equals("html") || extension.equals("htm") ? "html" : "xml";
        }
        
        if ("csv".equals(extension) || "tsv".equals(extension)) {
            return "csv";
        }
        
        if ("md".equals(extension) || "markdown".equals(extension)) {
            return "markdown";
        }
        
        if (CODE_EXTENSIONS.contains(extension)) {
            return "code_" + extension;
        }
        
        if ("yaml".equals(extension) || "yml".equals(extension)) {
            return "yaml";
        }
        
        if ("properties".equals(extension) || "conf".equals(extension) || 
            "config".equals(extension) || "ini".equals(extension) || "env".equals(extension)) {
            return "config";
        }
        
        if ("rst".equals(extension) || "adoc".equals(extension) || "tex".equals(extension)) {
            return "documentation";
        }
        
        return "text";
    }
    
    private record ChunkResult(int indexed, int duplicates) {}

    private ChunkResult chunkAndIndexText(String content, String filename, String extension,
                                    String contentType, String batchId) {
        int chunkSize = 1000;
        int overlap = 100;
        int indexed = 0;
        int duplicates = 0;
        int chunkIndex = 0;
        
        ChunkConfig config = getChunkConfig(contentType);
        
        log.debug("📏 [{}] Config chunking: size={} overlap={} (type: {})",
            getName(), config.size, config.overlap, contentType);
        
        // ✅ Estimer nombre de chunks
        int estimatedChunks = content.length() <= config.size ? 1 : 
            (int) Math.ceil(content.length() / (double)(config.size - config.overlap));
    
        if (content.length() <= config.size) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("source", filename);
            meta.put("extension", extension);
            meta.put("type", contentType);
            meta.put("chunkIndex", chunkIndex);
            meta.put("batchId", batchId);
            
            addTypeSpecificMetadata(meta, content.trim(), contentType);
            
            Metadata metadata = Metadata.from(sanitizer.sanitize(meta));
            
            // ✅ AJOUT : Progress embedding
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "EMBEDDING", 50, 
                    "Création embedding...");
            }

            String embeddingId = indexText(content.trim(), metadata, batchId);

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
        while (start < content.length()) {
            int end = Math.min(start + config.size, content.length());
            String chunk = content.substring(start, end).trim();
            
            if (chunk.length() > 10) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("source", filename);
                meta.put("extension", extension);
                meta.put("type", contentType);
                meta.put("chunkIndex", chunkIndex);
                meta.put("batchId", batchId);
                
                addTypeSpecificMetadata(meta, chunk, contentType);
                
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
            
            start += Math.max(1, config.size - config.overlap);
        }
        
        log.info("✅ [{}] {} chunks indexés ({} duplicates skip)", 
            getName(), indexed, duplicates);
        
        return new ChunkResult(indexed, duplicates);
    }
    
    private ChunkConfig getChunkConfig(String contentType) {
        
        if (contentType.startsWith("code_")) {
            return new ChunkConfig(1500, 200);
        }
        
        if (contentType.equals("json") || contentType.equals("xml")) {
            return new ChunkConfig(800, 100);
        }
        
        if (contentType.equals("csv")) {
            return new ChunkConfig(1200, 50);
        }
        
        if (contentType.equals("markdown")) {
            return new ChunkConfig(1000, 100);
        }
        
        return new ChunkConfig(1000, 100);
    }
    
    private void addTypeSpecificMetadata(Map<String, Object> meta, String chunk, 
                                          String contentType) {
        
        if (contentType.startsWith("code_")) {
            String language = contentType.substring(5);
            meta.put("language", language);
            
            int lines = chunk.split("\n").length;
            meta.put("linesOfCode", lines);
        }
        
        if (contentType.equals("csv")) {
            int rows = chunk.split("\n").length;
            meta.put("rows", rows);
            meta.put("format", "csv");
        }
        
        if (contentType.equals("json")) {
            meta.put("format", "json");
        }
        
        if (contentType.equals("markdown")) {
            boolean hasHeaders = chunk.contains("#");
            meta.put("hasHeaders", hasHeaders);
        }
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

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    @Override
    public String getName() {
        return "TEXT";
    }
    
    @Override
    public int getPriority() {
        return 8;
    }
    
    private record ChunkConfig(int size, int overlap) {}
    
    public static Set<String> getSupportedExtensions() {
        return Set.copyOf(SUPPORTED_EXTENSIONS);
    }
    
    public static boolean isSupported(String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }
}
/*

    ## 🎯 Étapes du progress pour TEXT
    ```
    5% - Upload started
    10% - Vérification des duplicates
    12% - Upload completed
    15-25% - Streaming (si >100MB)
    20-25% - Lecture du contenu
    30% - Analyse du contenu
    40% - Découpage du texte
    50-90% - Création embeddings (progress détaillé)
    100% - Completed

*/
