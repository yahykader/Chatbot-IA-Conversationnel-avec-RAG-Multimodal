// ============================================================================
// STRATEGY - TextIngestionStrategy.java
// Stratégie d'ingestion pour fichiers texte avec streaming + RAGMetrics unifié
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.strategy;

import com.exemple.transactionservice.service.rag.ingestion.progress.ProgressNotifier;
import com.exemple.transactionservice.service.rag.ingestion.cache.EmbeddingCache;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.TextDeduplicationService;
import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
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
 * Stratégie d'ingestion pour fichiers texte
 * 
 * ✅ ADAPTÉ AVEC RAGMetrics unifié
 * 
 * Fonctionnalités:
 * - Streaming pour gros fichiers (>100MB)
 * - Support 40+ formats texte/code/data
 * - Détection automatique d'encodage (UTF-8, ISO-8859-1)
 * - Détection type de contenu
 * - Chunking adaptatif selon le type
 * - Progress temps réel via ProgressNotifier
 * - Déduplication fichier + texte
 * - Métriques Prometheus via RAGMetrics
 * - Cache embeddings
 * 
 * @author RAG Team
 * @version 3.0 - Adapté avec RAGMetrics unifié
 */
@Slf4j
@Component
public class TextIngestionStrategy implements IngestionStrategy {
    
    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingModel embeddingModel;
    private final IngestionTracker tracker;
    private final MetadataSanitizer sanitizer;
    private final RAGMetrics ragMetrics;  // ✅ Métriques unifiées
    private final DeduplicationService deduplicationService;
    private final TextDeduplicationService textDeduplicationService;
    private final FileSignatureValidator signatureValidator;
    private final EmbeddingCache embeddingCache;
    
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
            RAGMetrics ragMetrics,  // ✅ Injection RAGMetrics
            DeduplicationService deduplicationService,
            TextDeduplicationService textDeduplicationService,
            FileSignatureValidator signatureValidator,
            EmbeddingCache embeddingCache) {
        
        this.textStore = textStore;
        this.embeddingModel = embeddingModel;
        this.tracker = tracker;
        this.sanitizer = sanitizer;
        this.ragMetrics = ragMetrics;  // ✅ Utilisation metrics unifié
        this.deduplicationService = deduplicationService;
        this.textDeduplicationService = textDeduplicationService;
        this.signatureValidator = signatureValidator;
        this.embeddingCache = embeddingCache;
        
        log.info("✅ [{}] Strategy initialisée avec streaming + RAGMetrics (40+ formats)", 
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
        
        try {
            // ========================================================================
            // PROGRESS - Upload started
            // ========================================================================
            if (progressNotifier != null) {
                progressNotifier.uploadStarted(batchId, filename, fileSize);
            }
            
            log.info("📄 [{}] Processing text file: {} ({} MB, ext: {})", 
                getName(), filename, fileSize / 1_000_000, extension.toUpperCase());
            
            // ========================================================================
            // VALIDATION - Empty file check
            // ========================================================================
            if (file.isEmpty() || fileSize == 0) {
                if (progressNotifier != null) {
                    progressNotifier.error(batchId, filename, "Empty file");
                }
                throw new IOException("Empty text file: " + filename);
            }
            
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
                    getName(), fileSize / 1_000_000);
                result = ingestWithStreaming(file, filename, extension, batchId, fileSize);
            } else {
                log.debug("📄 [{}] Normal mode: {} MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestNormal(file, filename, extension, batchId, fileSize);
            }
            
            // ========================================================================
            // METRICS
            // ========================================================================
            long duration = System.currentTimeMillis() - startTime;
            
            ragMetrics.recordStrategyProcessing(
                getName(),
                duration,
                result.textEmbeddings()
            );
            
            // ========================================================================
            // PROGRESS - Completed
            // ========================================================================
            if (progressNotifier != null) {
                progressNotifier.completed(batchId, filename, result.textEmbeddings(), 0);
            }
            
            log.info("✅ [{}] Text file processed: {} - {} chunks, duration={}ms mode={}",
                getName(), filename, result.textEmbeddings(), duration,
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
            
            log.error("❌ [{}] Processing error: {}", getName(), filename, e);
            
            // Re-throw exception
            throw e;
        }
    }    
    private IngestionResult ingestNormal(MultipartFile file, String filename,
                                          String extension, String batchId,
                                          long fileSize) throws Exception {
        
        // Progress - Processing
        if (progressNotifier != null) {
            progressNotifier.processingStarted(batchId, filename);
        }
        
        // Progress - Reading
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "READING", 20, 
                "Reading content...");
        }
        
        String content = readTextWithEncodingDetection(file.getBytes(), filename);
        
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("File without content: " + filename);
        }
        
        return processContent(content, filename, extension, batchId, fileSize);
    }
    
    private IngestionResult ingestWithStreaming(MultipartFile file, String filename,
                                                 String extension, String batchId,
                                                 long fileSize) throws Exception {
        
        Path tempFile = null;
        
        try {
            // Progress - Streaming
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "STREAMING", 15, 
                    "Loading in streaming...");
            }
            
            log.debug("💾 [{}] Creating temp file...", getName());
            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                if (bytesWritten % (50 * 1024 * 1024) == 0) {
                    log.info("📊 [{}] Saved: {} MB", getName(), bytesWritten / 1_000_000);
                    
                    if (progressNotifier != null) {
                        int percentage = 15 + (int)((bytesWritten / (double)fileSize) * 10);
                        progressNotifier.notifyProgress(batchId, filename, "STREAMING", percentage, 
                            String.format("Loading: %d MB", bytesWritten / 1_000_000));
                    }
                }
            });
            
            log.info("✅ [{}] Temp file created: {}", getName(), tempFile);
            
            // Progress - Reading
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "READING", 25, 
                    "Reading content...");
            }
            
            byte[] bytes = Files.readAllBytes(tempFile);
            String content = readTextWithEncodingDetection(bytes, filename);
            
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("File without content: " + filename);
            }
            
            return processContent(content, filename, extension, batchId, fileSize);
            
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.debug("🗑️ [{}] Temp file deleted", getName());
                } catch (IOException e) {
                    log.warn("⚠️ [{}] Cannot delete temp file: {}", getName(), e.getMessage());
                }
            }
        }
    }
    
    private IngestionResult processContent(String content, String filename,
                                            String extension, String batchId,
                                            long fileSize) throws Exception {
        
        log.debug("📝 [{}] Content extracted: {} characters", getName(), content.length());
        
        // Progress - Content analysis
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "ANALYSIS", 30, 
                "Content analysis...");
        }
        
        String contentType = detectContentType(content, extension);
        
        log.info("🔍 [{}] Detected type: {}", getName(), contentType);
        
        // Progress - Chunking
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "CHUNKING", 40, 
                "Text chunking...");
        }

        var chunkResult = chunkAndIndexText(content, filename, extension, contentType, batchId);
        int textEmbeddings = chunkResult.indexed();
        int duplicates = chunkResult.duplicates();
        
        if (duplicates > 0) {
            log.info("⏭️ [Dedup] {} duplicates skipped, {} new indexed", 
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
                log.debug("✓ [{}] Encoding detected: UTF-8", getName());
                return content;
            }
            
        } catch (Exception e) {
            log.debug("⚠️ [{}] UTF-8 read failed", getName());
        }
        
        try {
            String content = new String(bytes, "ISO-8859-1");
            log.debug("✓ [{}] Encoding detected: ISO-8859-1 (fallback)", getName());
            return content;
            
        } catch (Exception e) {
            throw new IOException("Cannot read file: " + filename, e);
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
        int indexed = 0;
        int duplicates = 0;
        int chunkIndex = 0;
        
        ChunkConfig config = getChunkConfig(contentType);
        
        log.debug("📏 [{}] Chunking config: size={} overlap={} (type: {})",
            getName(), config.size, config.overlap, contentType);
        
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
            
            // Progress embedding
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "EMBEDDING", 50, 
                    "Creating embedding...");
            }

            String embeddingId = indexText(content.trim(), metadata, batchId);

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
            
            start += Math.max(1, config.size - config.overlap);
        }
        
        log.info("✅ [{}] {} chunks indexed ({} duplicates skipped)", 
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
            log.debug("⏭️ [Dedup] Duplicate text, skip: {}", truncate(text, 50));
            return null;
        }
        
        log.debug("✅ [Dedup] New text, indexing: {}", truncate(text, 50));
        
        TextSegment segment = TextSegment.from(text, metadata);
        
        // ✅ MODIFIÉ: Utiliser getAndTrack + put avec batchId
        Embedding embedding = embeddingCache.getAndTrack(text, batchId);
        
        if (embedding == null) {
            // Cache miss - Créer l'embedding
            long apiStart = System.currentTimeMillis();
            embedding = embeddingModel.embed(text).content();
            long apiDuration = System.currentTimeMillis() - apiStart;
            
            // ✅ MÉTRIQUE: Embedding API call (INCHANGÉE)
            ragMetrics.recordApiCall("embed_text", apiDuration);
            
            // ✅ NOUVEAU: Stocker avec tracking batch
            embeddingCache.put(text, embedding, batchId);
        }
        
        long storeStart = System.currentTimeMillis();
        String embeddingId = textStore.add(embedding, segment);
        long storeDuration = System.currentTimeMillis() - storeStart;
        
        // ✅ MÉTRIQUE: Vector store operation
        ragMetrics.recordVectorStoreOperation("insert", storeDuration, 1);
        
        return embeddingId;
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
 * Progress Steps for TEXT:
 * 5%   - Upload started
 * 10%  - Duplicate check
 * 12%  - Upload completed
 * 15-25% - Streaming (if >100MB)
 * 20-25% - Reading content
 * 30%  - Content analysis
 * 40%  - Text chunking
 * 50-90% - Embedding creation (detailed progress)
 * 100% - Completed
 */