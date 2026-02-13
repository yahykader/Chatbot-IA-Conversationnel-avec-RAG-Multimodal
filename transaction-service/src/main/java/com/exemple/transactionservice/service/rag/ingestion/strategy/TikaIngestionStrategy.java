// ============================================================================
// STRATEGY - TikaIngestionStrategy.java
// Stratégie d'ingestion universelle Apache Tika + RAGMetrics unifié
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
import com.exemple.transactionservice.exception.DuplicateFileException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Stratégie d'ingestion universelle Apache Tika
 * 
 * ✅ ADAPTÉ AVEC RAGMetrics unifié
 * 
 * Fonctionnalités:
 * - Fallback universel 1000+ formats
 * - Streaming pour gros fichiers (>100MB)
 * - Extraction métadonnées enrichies (auteur, date, pages, etc.)
 * - Support Office legacy, OpenOffice, iWork, eBooks, archives
 * - Progress temps réel via ProgressNotifier
 * - Déduplication fichier + texte
 * - Métriques Prometheus via RAGMetrics
 * - Cache embeddings
 * - Retry automatique
 * 
    Upload Fichier
        ↓
    IngestionOrchestrator.ingestFileInternal()
        ├── Antivirus scan ✅
        ├── Select strategy ✅
        ├── Calculate hash ✅
        ├── isDuplicateAndRecord() ✅ ← UNIQUE vérification
        │   └── Si doublon: throw DuplicateFileException
        ├── registerFile() ✅
        └── Call strategy.ingest() ✅
            ↓
            Strategy (PDF/DOCX/IMAGE/TEXT/TIKA)
            ├── Validate ✅
            ├── Process ✅ (pas de vérification doublon)
            ├── Metrics ✅
            └── Return result ✅
 */
@Slf4j
@Component
public class TikaIngestionStrategy implements IngestionStrategy {
    
    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingModel embeddingModel;
    private final IngestionTracker tracker;
    private final MetadataSanitizer sanitizer;
    private final ApacheTikaDocumentParser tikaParser;
    private final RAGMetrics ragMetrics;
    private final DeduplicationService deduplicationService;
    private final TextDeduplicationService textDeduplicationService;
    private final EmbeddingCache embeddingCache;
    
    @Autowired(required = false)
    private ProgressNotifier progressNotifier;
    
    public TikaIngestionStrategy(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            EmbeddingModel embeddingModel,
            IngestionTracker tracker,
            MetadataSanitizer sanitizer,
            RAGMetrics ragMetrics,  // ✅ Injection RAGMetrics
            DeduplicationService deduplicationService,
            TextDeduplicationService textDeduplicationService,
            EmbeddingCache embeddingCache) {
        
        this.textStore = textStore;
        this.embeddingModel = embeddingModel;
        this.tracker = tracker;
        this.sanitizer = sanitizer;
        this.ragMetrics = ragMetrics;  // ✅ Utilisation metrics unifié
        this.deduplicationService = deduplicationService;
        this.textDeduplicationService = textDeduplicationService;
        this.embeddingCache = embeddingCache;
        
        this.tikaParser = new ApacheTikaDocumentParser();
        
        log.info("✅ [{}] Strategy initialisée avec streaming + RAGMetrics (fallback 1000+ formats)", 
            getName());
    }
    
    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        return true;  // Fallback universel
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
            
            log.info("🔧 [{}] Processing TIKA (universal fallback): {} ({} MB, ext: {})", 
                getName(), filename, fileSize / 1_000_000, extension.toUpperCase());
            
            // ========================================================================
            // VALIDATION - Empty file check
            // ========================================================================
            if (file.isEmpty() || fileSize == 0) {
                if (progressNotifier != null) {
                    progressNotifier.error(batchId, filename, "Empty file");
                }
                throw new IOException("Empty file: " + filename);
            }
            
            // ========================================================================
            // ✅ SUPPRESSION: Vérification doublon (déjà faite par orchestrator)
            // ========================================================================
            // ❌ ANCIEN CODE SUPPRIMÉ:
            /*
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "DEDUPLICATION", 10, 
                    "Checking duplicates...");
            }
            
            DeduplicationService.DuplicationInfo dupInfo = 
                deduplicationService.checkDuplication(file);
            
            if (dupInfo.isDuplicate()) {
                if (progressNotifier != null) {
                    progressNotifier.error(batchId, filename, 
                        "Already processed (batch: " + dupInfo.originalBatchId() + ")");
                }
                
                ragMetrics.recordDuplicate(getName());
                
                throw new DuplicateFileException(
                    String.format("File already processed (batch: %s)", 
                        dupInfo.originalBatchId()),
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
            // PROCESSING - Apache Tika Extraction
            // ========================================================================
            if (progressNotifier != null) {
                progressNotifier.processingStarted(batchId, filename);
            }
            
            log.info("🔄 [{}] Extracting with Apache Tika...", getName());
            
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
            // ✅ OPTIONNEL: Enregistrement (déjà fait par orchestrator)
            // ========================================================================
            // Note: L'orchestrator enregistre déjà le fichier AVANT l'appel strategy
            // Cette ligne est redondante mais sans danger
            // deduplicationService.markAsIngested(file, batchId);
            
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
            
            log.info("✅ [{}] File processed via Tika: {} - {} chunks, duration={}ms mode={}",
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
            
            log.error("❌ [{}] Tika processing error: {}", getName(), filename, e);
            
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
        
        // Progress - Parsing
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "TIKA_PARSING", 20, 
                "Extracting with Apache Tika...");
        }
        
        long parseStart = System.currentTimeMillis();
        Document document = parseDocumentWithRetry(file);
        long parseDuration = System.currentTimeMillis() - parseStart;
        
        // ✅ MÉTRIQUE: Tika parsing (API-like operation)
        ragMetrics.recordApiCall("tika_parse", parseDuration);
        
        return processDocument(document, filename, extension, batchId, fileSize);
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
                    log.info("📊 [{}] Saved: {} MB", 
                        getName(), bytesWritten / 1_000_000);
                    
                    if (progressNotifier != null) {
                        int percentage = 15 + (int)((bytesWritten / (double)fileSize) * 10);
                        progressNotifier.notifyProgress(batchId, filename, "STREAMING", percentage, 
                            String.format("Loading: %d MB", bytesWritten / 1_000_000));
                    }
                }
            });
            
            log.info("✅ [{}] Temp file created: {}", getName(), tempFile);
            
            // Progress - Parsing
            if (progressNotifier != null) {
                progressNotifier.notifyProgress(batchId, filename, "TIKA_PARSING", 25, 
                    "Extracting with Apache Tika...");
            }
            
            long parseStart = System.currentTimeMillis();
            Document document = parseDocumentFromFileWithRetry(tempFile);
            long parseDuration = System.currentTimeMillis() - parseStart;
            
            // ✅ MÉTRIQUE: Tika parsing
            ragMetrics.recordApiCall("tika_parse", parseDuration);
            
            return processDocument(document, filename, extension, batchId, fileSize);
            
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
    
    private IngestionResult processDocument(Document document, String filename,
                                             String extension, String batchId,
                                             long fileSize) throws Exception {
        
        if (document == null) {
            throw new IOException("Tika could not extract content: " + filename);
        }
        
        String content = document.text();
        
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                "No extractable text content: " + filename);
        }
        
        log.debug("📝 [{}] Content extracted: {} characters", getName(), content.length());
        
        // Progress - Metadata extraction
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "METADATA", 35, 
                "Extracting metadata...");
        }
        
        Metadata tikaMetadata = document.metadata();
        Map<String, Object> enrichedMetadata = extractTikaMetadata(
            tikaMetadata, filename, extension, batchId
        );
        
        if (enrichedMetadata.containsKey("mimeType")) {
            log.info("🔍 [{}] MIME type detected: {}", 
                getName(), enrichedMetadata.get("mimeType"));
        }
        
        // Progress - Chunking
        if (progressNotifier != null) {
            progressNotifier.notifyProgress(batchId, filename, "CHUNKING", 40, 
                "Text chunking...");
        }
        
        var chunkResult = chunkAndIndexText(content, enrichedMetadata, batchId, filename);
        int textEmbeddings = chunkResult.indexed();
        int duplicates = chunkResult.duplicates();
        
        if (duplicates > 0) {
            log.info("⏭️ [Dedup] {} duplicates skipped, {} new indexed", 
                duplicates, textEmbeddings);
        }
        
        Map<String, Object> resultMetadata = new HashMap<>(enrichedMetadata);
        resultMetadata.put("strategy", getName());
        resultMetadata.put("parser", "apache-tika");
        resultMetadata.put("characters", content.length());
        
        return new IngestionResult(textEmbeddings, 0, resultMetadata);
    }
    
    @Retryable(
        value = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private Document parseDocumentWithRetry(MultipartFile file) throws IOException {
        try {
            return tikaParser.parse(file.getInputStream());
            
        } catch (Exception e) {
            log.warn("⚠️ [{}] Tika parsing error (retry if possible): {}",
                getName(), e.getMessage());
            
            // ✅ MÉTRIQUE: Tika parsing error
            ragMetrics.recordApiError("tika_parse");
            
            if (e instanceof IOException || e instanceof TimeoutException) {
                throw e;
            }
            
            throw new IOException("Tika parsing error", e);
        }
    }
    
    @Retryable(
        value = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private Document parseDocumentFromFileWithRetry(Path filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            return tikaParser.parse(fis);
            
        } catch (Exception e) {
            log.warn("⚠️ [{}] Tika parsing error from file (retry if possible): {}",
                getName(), e.getMessage());
            
            // ✅ MÉTRIQUE: Tika parsing error
            ragMetrics.recordApiError("tika_parse");
            
            if (e instanceof IOException || e instanceof TimeoutException) {
                throw e;
            }
            
            throw new IOException("Tika parsing error from file", e);
        }
    }
    
    private Map<String, Object> extractTikaMetadata(
            Metadata tikaMetadata,
            String filename,
            String extension,
            String batchId) {
        
        Map<String, Object> enriched = new HashMap<>();
        
        enriched.put("filename", filename);
        enriched.put("extension", extension);
        enriched.put("batchId", batchId);
        enriched.put("source", "tika");
        
        if (tikaMetadata == null) {
            return enriched;
        }
        
        String mimeType = getMetadataValue(tikaMetadata, "Content-Type");
        if (mimeType != null) {
            enriched.put("mimeType", mimeType);
        }
        
        String title = getMetadataValue(tikaMetadata, "title", "dc:title");
        if (title != null) {
            enriched.put("title", title);
        }
        
        String author = getMetadataValue(tikaMetadata, 
            "author", "dc:creator", "Author", "creator");
        if (author != null) {
            enriched.put("author", author);
        }
        
        String creator = getMetadataValue(tikaMetadata, "Creator", "Application-Name");
        if (creator != null) {
            enriched.put("creator", creator);
        }
        
        String creationDate = getMetadataValue(tikaMetadata, 
            "Creation-Date", "dcterms:created", "meta:creation-date");
        if (creationDate != null) {
            enriched.put("creationDate", creationDate);
        }
        
        String modifiedDate = getMetadataValue(tikaMetadata, 
            "Last-Modified", "dcterms:modified", "Last-Save-Date");
        if (modifiedDate != null) {
            enriched.put("modifiedDate", modifiedDate);
        }
        
        String pageCount = getMetadataValue(tikaMetadata, 
            "xmpTPg:NPages", "Page-Count", "meta:page-count");
        if (pageCount != null) {
            try {
                enriched.put("pageCount", Integer.parseInt(pageCount));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        String wordCount = getMetadataValue(tikaMetadata, 
            "meta:word-count", "Word-Count");
        if (wordCount != null) {
            try {
                enriched.put("wordCount", Integer.parseInt(wordCount));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        String language = getMetadataValue(tikaMetadata, 
            "language", "dc:language", "meta:language");
        if (language != null) {
            enriched.put("language", language);
        }
        
        String keywords = getMetadataValue(tikaMetadata, 
            "Keywords", "dc:subject", "meta:keyword");
        if (keywords != null) {
            enriched.put("keywords", keywords);
        }
        
        log.debug("📋 [{}] Tika metadata extracted: {}", getName(), enriched.size());
        
        return enriched;
    }
    
    private String getMetadataValue(Metadata metadata, String... keys) {
        for (String key : keys) {
            String value = metadata.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
    
    private record ChunkResult(int indexed, int duplicates) {}

    private ChunkResult chunkAndIndexText(String text, Map<String, Object> baseMetadata,
                                    String batchId, String filename) {
        
        int chunkSize = 1000;
        int overlap = 100;
        int indexed = 0;
        int duplicates = 0;
        int chunkIndex = 0;

        int estimatedChunks = text.length() <= chunkSize ? 1 : 
            (int) Math.ceil(text.length() / (double)(chunkSize - overlap));

        if (text.length() <= chunkSize) {
            Map<String, Object> meta = new HashMap<>(baseMetadata);
            meta.put("type", "tika_text");
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
                Map<String, Object> meta = new HashMap<>(baseMetadata);
                meta.put("chunkIndex", chunkIndex);
                meta.put("type", "tika_text");
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
            log.debug("⏭️ [Dedup] Duplicate text, skip: {}", truncate(text, 50));
            return null;
        }
        
        log.debug("✅ [Dedup] New text, indexing: {}", truncate(text, 50));
        
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
    
    @Override
    public String getName() {
        return "TIKA";
    }
    
    @Override
    public int getPriority() {
        return 10;  // Fallback - lowest priority
    }
    
    public static String[] getSupportedFormatExamples() {
        return new String[]{
            "doc", "ppt", "xls", "vsd",
            "odt", "ods", "odp", "odg",
            "pages", "numbers", "key",
            "epub", "mobi", "azw", "fb2",
            "zip", "rar", "7z", "tar", "gz", "bz2",
            "tex", "bib", "rtf",
            "dwg", "dxf",
            "psd", "ai", "eps",
            "mp3", "flac", "ogg", "wav",
            "mp4", "avi", "mkv", "mov",
            "msg", "eml", "mbox", "pst"
        };
    }
    
    public static String getCapabilities() {
        return "Apache Tika fallback strategy - Supports 1000+ file formats " +
               "including Office legacy, OpenOffice, iWork, eBooks, archives, " +
               "scientific formats, CAD, and metadata extraction for audio/video.";
    }
}

/*
 * Progress Steps for TIKA:
 * 5%   - Upload started
 * 10%  - Duplicate check
 * 12%  - Upload completed
 * 15-25% - Streaming (if >100MB)
 * 20-25% - Apache Tika extraction
 * 35%  - Metadata extraction
 * 40%  - Text chunking
 * 50-90% - Embedding creation (detailed progress)
 * 100% - Completed
 */