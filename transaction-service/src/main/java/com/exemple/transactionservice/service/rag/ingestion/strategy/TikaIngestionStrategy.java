// ============================================================================
// STRATEGY - TikaIngestionStrategy.java (VERSION AVEC STREAMING)
// Stratégie d'ingestion universelle avec Apache Tika - Fallback 1000+ formats
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.strategy;

import com.exemple.transactionservice.service.rag.ingestion.cache.EmbeddingCache;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.TextDeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.metrics.IngestionMetrics;
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
 * Stratégie d'ingestion universelle avec Apache Tika - VERSION AVEC STREAMING.
 * 
 * ✨ NOUVEAU dans cette version :
 * ✅ Streaming automatique pour fichiers >100MB
 * ✅ Mémoire optimisée pour gros fichiers
 * ✅ Support fichiers jusqu'à 1GB+ (formats exotiques)
 * ✅ Détection automatique du mode
 * 
 * Fallback ULTIME pour tous les formats non gérés par les strategies spécialisées.
 * Apache Tika supporte 1000+ formats de fichiers.
 * 
 * Améliorations précédentes :
 * ✅ Métriques Prometheus intégrées
 * ✅ Deduplication avec Redis
 * ✅ Retry automatique (3 tentatives)
 * ✅ Extraction metadata enrichie (MIME, titre, auteur, dates)
 * ✅ Détection automatique format
 * 
 * Formats supportés (exemples) :
 * - Office legacy : DOC, PPT, XLS, VSD
 * - OpenOffice : ODT, ODS, ODP, ODG
 * - Apple iWork : Pages, Numbers, Keynote
 * - eBooks : EPUB, MOBI, AZW
 * - Archives : ZIP, RAR, 7z, TAR, GZ
 * - Scientific : LaTeX, BibTeX
 * - CAD : DWG, DXF
 * - Audio/Video : MP3, MP4 (metadata)
 * - Et 1000+ autres formats...
 * 
 * Priorité : 10 (la plus basse - fallback absolu)
 */
@Slf4j
@Component
public class TikaIngestionStrategy implements IngestionStrategy {
    
    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingModel embeddingModel;
    private final IngestionTracker tracker;
    private final MetadataSanitizer sanitizer;
    private final ApacheTikaDocumentParser tikaParser;
    private final IngestionMetrics metrics;
    private final DeduplicationService deduplicationService;
    private final TextDeduplicationService textDeduplicationService;
    private final EmbeddingCache embeddingCache;
    
    public TikaIngestionStrategy(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            EmbeddingModel embeddingModel,
            IngestionTracker tracker,
            MetadataSanitizer sanitizer,
            IngestionMetrics metrics,
            DeduplicationService deduplicationService,
            TextDeduplicationService textDeduplicationService,
            EmbeddingCache embeddingCache) {
        
        this.textStore = textStore;
        this.embeddingModel = embeddingModel;
        this.tracker = tracker;
        this.sanitizer = sanitizer;
        this.metrics = metrics;
        this.deduplicationService = deduplicationService;
        this.textDeduplicationService =  textDeduplicationService;
        this.embeddingCache = embeddingCache;
        
        // Initialiser Tika parser
        this.tikaParser = new ApacheTikaDocumentParser();
        
        log.info("✅ [{}] Strategy initialisée avec streaming support (fallback 1000+ formats)", 
            getName());
    }
    
    @Override
    public boolean canHandle(MultipartFile file, String extension) {
        // TOUJOURS retourner true - c'est le fallback universel
        return true;
    }
    
    // ========================================================================
    // ✨ MÉTHODE PRINCIPALE AVEC STREAMING
    // ========================================================================
    
    @Override
    public IngestionResult ingest(MultipartFile file, String batchId) throws Exception {
        String filename = file.getOriginalFilename();
        String extension = getExtension(filename);
        long fileSize = file.getSize();
        
        // Métrique : Démarrer timer
        long startTime = System.currentTimeMillis();
        metrics.startProcessing();
        
        try {
            log.info("🔧 [{}] Traitement TIKA (fallback universel): {} ({} MB, ext: {})", 
                getName(), filename, fileSize / 1_000_000, extension.toUpperCase());
            
            // ========== VALIDATIONS ==========
            
            if (file.isEmpty() || fileSize == 0) {
                throw new IOException("Fichier vide: " + filename);
            }
            
            // Déduplication
            DeduplicationService.DuplicationInfo dupInfo = 
                deduplicationService.checkDuplication(file);
            
            if (dupInfo.isDuplicate()) {
                metrics.recordDuplicate(getName());
                throw new DuplicateFileException(
                    String.format("Fichier déjà traité (batch: %s)", 
                        dupInfo.originalBatchId())
                );
            }
            
            // ========== ✨ DÉTECTION MODE STREAMING ==========
            
            log.info("🔄 [{}] Extraction avec Apache Tika...", getName());
            
            IngestionResult result;
            
            if (StreamingFileReader.requiresStreaming(file)) {
                // ✅ STREAMING pour >100MB
                log.info("📖 [{}] STREAMING activé: {} MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestWithStreaming(file, filename, extension, batchId, fileSize);
                
            } else {
                // ✅ MODE NORMAL pour <100MB
                log.debug("📄 [{}] Mode normal: {} MB", 
                    getName(), fileSize / 1_000_000);
                result = ingestNormal(file, filename, extension, batchId, fileSize);
            }
            
            // ========== POST-TRAITEMENT ==========
            
            deduplicationService.markAsIngested(file, batchId);
            
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordSuccess(getName(), duration, result.textEmbeddings(), 0);
            metrics.recordFileSize(getName(), fileSize);
            
            log.info("✅ [{}] Fichier traité via Tika: {} - {} chunks, durée={}ms mode={}",
                getName(), filename, result.textEmbeddings(), duration,
                StreamingFileReader.requiresStreaming(file) ? "STREAMING" : "NORMAL");
            
            return result;
            
        } catch (DuplicateFileException e) {
            metrics.endProcessing();
            throw e;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordError(getName(), e.getClass().getSimpleName(), duration);
            metrics.endProcessing();
            
            log.error("❌ [{}] Erreur traitement Tika: {}", getName(), filename, e);
            throw e;
            
        } finally {
            metrics.endProcessing();
        }
    }
    
    // ========================================================================
    // ✨ INGESTION NORMALE (<100MB)
    // ========================================================================
    
    /**
     * Ingestion normale pour petits fichiers
     */
    private IngestionResult ingestNormal(MultipartFile file, String filename,
                                          String extension, String batchId,
                                          long fileSize) throws Exception {
        
        // Parsing avec retry depuis InputStream
        Document document = parseDocumentWithRetry(file);
        
        return processDocument(document, filename, extension, batchId, fileSize);
    }
    
    // ========================================================================
    // ✨ INGESTION STREAMING (>100MB)
    // ========================================================================
    
    /**
     * Ingestion streaming pour gros fichiers (>100MB).
     * Sauvegarde en fichier temporaire puis parse depuis fichier.
     */
    private IngestionResult ingestWithStreaming(MultipartFile file, String filename,
                                                 String extension, String batchId,
                                                 long fileSize) throws Exception {
        
        Path tempFile = null;
        
        try {
            // ✨ Sauvegarder en fichier temporaire (streaming)
            log.debug("💾 [{}] Création fichier temporaire...", getName());
            tempFile = StreamingFileReader.saveToTempFileWithProgress(file, bytesWritten -> {
                if (bytesWritten % (50 * 1024 * 1024) == 0) {
                    log.info("📊 [{}] Sauvegarde: {} MB", 
                        getName(), bytesWritten / 1_000_000);
                }
            });
            
            log.info("✅ [{}] Fichier temporaire créé: {}", getName(), tempFile);
            
            // ✨ Parser depuis fichier (streaming)
            Document document = parseDocumentFromFileWithRetry(tempFile);
            
            return processDocument(document, filename, extension, batchId, fileSize);
            
        } finally {
            // ✨ Nettoyer fichier temporaire
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
    // TRAITEMENT DOCUMENT (LOGIQUE COMMUNE)
    // ========================================================================
    
    /**
     * Traite un document Tika (logique commune normal/streaming)
     */
    private IngestionResult processDocument(Document document, String filename,
                                             String extension, String batchId,
                                             long fileSize) throws Exception {
        
        if (document == null) {
            throw new IOException("Tika n'a pas pu extraire de contenu: " + filename);
        }
        
        String content = document.text();
        
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                "Aucun contenu textuel extractible: " + filename);
        }
        
        log.debug("📝 [{}] Contenu extrait: {} caractères", getName(), content.length());
        
        // Extraction metadata Tika
        Metadata tikaMetadata = document.metadata();
        Map<String, Object> enrichedMetadata = extractTikaMetadata(
            tikaMetadata, filename, extension, batchId
        );
        
        // Log metadata intéressantes
        if (enrichedMetadata.containsKey("mimeType")) {
            log.info("🔍 [{}] Type MIME détecté: {}", 
                getName(), enrichedMetadata.get("mimeType"));
        }
        
        // Chunking et indexation
        var chunkResult = chunkAndIndexText(content, enrichedMetadata, batchId);
        int textEmbeddings = chunkResult.indexed();
        int duplicates = chunkResult.duplicates();
        
        if (duplicates > 0) {
            log.info("⏭️ [Dedup] {} duplicates skip, {} nouveaux indexés", 
                duplicates, textEmbeddings);
        }
        
        // Résultat
        Map<String, Object> resultMetadata = new HashMap<>(enrichedMetadata);
        resultMetadata.put("strategy", getName());
        resultMetadata.put("parser", "apache-tika");
        resultMetadata.put("characters", content.length());
        
        return new IngestionResult(textEmbeddings, 0, resultMetadata);
    }
    
    // ========================================================================
    // PARSING AVEC RETRY
    // ========================================================================
    
    /**
     * Parse document avec retry depuis MultipartFile
     */
    @Retryable(
        value = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private Document parseDocumentWithRetry(MultipartFile file) throws IOException {
        try {
            return tikaParser.parse(file.getInputStream());
            
        } catch (Exception e) {
            log.warn("⚠️ [{}] Erreur parsing Tika (retry si possible): {}",
                getName(), e.getMessage());
            
            if (e instanceof IOException || e instanceof TimeoutException) {
                throw e;
            }
            
            throw new IOException("Tika parsing error", e);
        }
    }
    
    /**
     * ✨ NOUVEAU : Parse document avec retry depuis File (streaming)
     */
    @Retryable(
        value = {IOException.class, TimeoutException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private Document parseDocumentFromFileWithRetry(Path filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            return tikaParser.parse(fis);
            
        } catch (Exception e) {
            log.warn("⚠️ [{}] Erreur parsing Tika depuis fichier (retry si possible): {}",
                getName(), e.getMessage());
            
            if (e instanceof IOException || e instanceof TimeoutException) {
                throw e;
            }
            
            throw new IOException("Tika parsing error from file", e);
        }
    }
    
    // ========================================================================
    // EXTRACTION METADATA TIKA
    // ========================================================================
    
    /**
     * Extrait et enrichit les metadata Tika
     */
    private Map<String, Object> extractTikaMetadata(
            Metadata tikaMetadata,
            String filename,
            String extension,
            String batchId) {
        
        Map<String, Object> enriched = new HashMap<>();
        
        // Metadata de base
        enriched.put("filename", filename);
        enriched.put("extension", extension);
        enriched.put("batchId", batchId);
        enriched.put("source", "tika");
        
        if (tikaMetadata == null) {
            return enriched;
        }
        
        // Type MIME
        String mimeType = getMetadataValue(tikaMetadata, "Content-Type");
        if (mimeType != null) {
            enriched.put("mimeType", mimeType);
        }
        
        // Titre
        String title = getMetadataValue(tikaMetadata, "title", "dc:title");
        if (title != null) {
            enriched.put("title", title);
        }
        
        // Auteur
        String author = getMetadataValue(tikaMetadata, 
            "author", "dc:creator", "Author", "creator");
        if (author != null) {
            enriched.put("author", author);
        }
        
        // Créateur/Application
        String creator = getMetadataValue(tikaMetadata, "Creator", "Application-Name");
        if (creator != null) {
            enriched.put("creator", creator);
        }
        
        // Date création
        String creationDate = getMetadataValue(tikaMetadata, 
            "Creation-Date", "dcterms:created", "meta:creation-date");
        if (creationDate != null) {
            enriched.put("creationDate", creationDate);
        }
        
        // Date modification
        String modifiedDate = getMetadataValue(tikaMetadata, 
            "Last-Modified", "dcterms:modified", "Last-Save-Date");
        if (modifiedDate != null) {
            enriched.put("modifiedDate", modifiedDate);
        }
        
        // Nombre de pages
        String pageCount = getMetadataValue(tikaMetadata, 
            "xmpTPg:NPages", "Page-Count", "meta:page-count");
        if (pageCount != null) {
            try {
                enriched.put("pageCount", Integer.parseInt(pageCount));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        // Nombre de mots
        String wordCount = getMetadataValue(tikaMetadata, 
            "meta:word-count", "Word-Count");
        if (wordCount != null) {
            try {
                enriched.put("wordCount", Integer.parseInt(wordCount));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        // Langue
        String language = getMetadataValue(tikaMetadata, 
            "language", "dc:language", "meta:language");
        if (language != null) {
            enriched.put("language", language);
        }
        
        // Mots-clés
        String keywords = getMetadataValue(tikaMetadata, 
            "Keywords", "dc:subject", "meta:keyword");
        if (keywords != null) {
            enriched.put("keywords", keywords);
        }
        
        log.debug("📋 [{}] Metadata Tika extraites: {}", getName(), enriched.size());
        
        return enriched;
    }
    
    /**
     * Obtient une valeur de metadata (essaie plusieurs clés)
     */
    private String getMetadataValue(Metadata metadata, String... keys) {
        for (String key : keys) {
            String value = metadata.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
    
    // ========================================================================
    // CHUNKING AVEC DÉDUPLICATION
    // ========================================================================
    
    private record ChunkResult(int indexed, int duplicates) {}

    private ChunkResult chunkAndIndexText(String text, Map<String, Object> baseMetadata,
                                    String batchId) {
        
        int chunkSize = 1000;
        int overlap = 100;
        int indexed = 0;
        int duplicates = 0;
        int chunkIndex = 0;

        // ✅ Si texte plus court que chunkSize, indexer tel quel
        if (text.length() <= chunkSize) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("type", "tika_text");
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
        
        // ✅ Sinon, chunking avec overlap
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
    
    /**
     * Indexe du texte
     */
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
    /**
     * Extrait l'extension
     */
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
        return 10; // Priorité la plus basse - fallback absolu
    }
    
    // ========================================================================
    // INFORMATIONS FORMATS SUPPORTÉS
    // ========================================================================
    
    /**
     * Retourne des exemples de formats supportés
     */
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
    
    /**
     * Retourne une description des capacités Tika
     */
    public static String getCapabilities() {
        return "Apache Tika fallback strategy - Supporte 1000+ formats de fichiers " +
               "incluant Office legacy, OpenOffice, iWork, eBooks, archives, formats " +
               "scientifiques, CAD, et extraction de metadata pour audio/video.";
    }
}
/*

## 🎯 **ARCHITECTURE FINALE COMPLÈTE**
```
┌──────────────────────────────────────┐
│    Upload Fichier (n'importe quel   │
│         format, jusqu'à 5GB)         │
└──────────────┬───────────────────────┘
               │
               ▼
    ┌──────────────────────┐
    │ MultimodalIngestion  │
    │      Service         │
    └──────────┬───────────┘
               │
      ┌────────┴─────────┐
      │  Strategy Router │
      │  (par priorité)  │
      └────────┬─────────┘
               │
    ┌──────────┴──────────────────┐
    │                             │
    ▼                             ▼
Priorité 1-4              Priorité 10
(Spécialisées)            (Fallback)
    │                             │
    ▼                             ▼
PDF, DOCX, XLSX,            TIKA
Image, Text              (1000+ formats)
    │                             │
    │         ┌───────────────────┘
    │         │
    ▼         ▼
┌──────────────────┐
│ StreamingReader  │
│   >100MB ?       │
└────────┬─────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
STREAMING  NORMAL
  (20MB)   (100MB)
    │         │
    └────┬────┘
         │
         ▼
   Indexation
   (pgvector)

*/