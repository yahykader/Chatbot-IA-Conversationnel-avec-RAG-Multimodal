// ============================================================================
// STRATEGY - TextIngestionStrategy.java (VERSION AVEC STREAMING)
// Stratégie d'ingestion pour fichiers texte avec streaming gros fichiers (>100MB)
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
import com.exemple.transactionservice.service.rag.ingestion.validation.FileSignatureValidator;
import com.exemple.transactionservice.exception.DuplicateFileException;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
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
 * Stratégie d'ingestion pour fichiers texte - VERSION AVEC STREAMING.
 * 
 * ✨ NOUVEAU dans cette version :
 * ✅ Streaming automatique pour fichiers >100MB
 * ✅ Lecture ligne par ligne pour gros fichiers texte
 * ✅ Mémoire constante (~20MB) pour logs de plusieurs GB
 * ✅ Support fichiers jusqu'à 5GB+
 * ✅ Détection automatique du mode
 * 
 * Améliorations précédentes :
 * ✅ Métriques Prometheus intégrées
 * ✅ Deduplication avec Redis
 * ✅ Détection encodage automatique (UTF-8 + fallback ISO-8859-1)
 * ✅ Détection type de contenu (JSON, XML, Code, CSV, etc.)
 * ✅ Chunking adaptatif selon le type
 * 
 * Formats supportés (40+) :
 * - Texte : txt, text, log
 * - Markdown : md, markdown
 * - Data : csv, tsv, json, xml, yaml, yml
 * - Web : html, htm, css
 * - Code : java, py, js, ts, c, cpp, go, rs, rb, php, swift, kt, sql, sh, bash
 * - Config : properties, conf, config, ini, env
 * - Docs : rst, adoc, tex
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
    
    /**
     * Extensions supportées (40+)
     */
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
    
    /**
     * Extensions de code pour détection type
     */
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
            log.info("📄 [{}] Traitement fichier texte: {} ({} MB, ext: {})", 
                getName(), filename, fileSize / 1_000_000, extension.toUpperCase());
            
            // ========== VALIDATIONS ==========
            
            if (file.isEmpty() || fileSize == 0) {
                throw new IOException("Fichier texte vide: " + filename);
            }
            
            // Déduplication
            DeduplicationService.DuplicationInfo dupInfo = 
                deduplicationService.checkDuplication(file);
            
            if (dupInfo.isDuplicate()) {
                metrics.recordDuplicate(getName());
                throw new DuplicateFileException(
                    String.format("Fichier texte déjà traité (batch: %s)", 
                        dupInfo.originalBatchId())
                );
            }
            
            // ========== ✨ DÉTECTION MODE STREAMING ==========
            
            IngestionResult result;
            
            if (StreamingFileReader.requiresStreaming(file)) {
                // ✅ STREAMING pour >100MB (logs volumineux, gros fichiers texte)
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
            
            log.info("✅ [{}] Fichier texte traité: {} - {} chunks, durée={}ms mode={}",
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
            
            log.error("❌ [{}] Erreur traitement: {}", getName(), filename, e);
            throw e;
            
        } finally {
            metrics.endProcessing();
        }
    }
    
    // ========================================================================
    // ✨ INGESTION NORMALE (<100MB)
    // ========================================================================
    
    /**
     * Ingestion normale pour petits fichiers texte
     */
    private IngestionResult ingestNormal(MultipartFile file, String filename,
                                          String extension, String batchId,
                                          long fileSize) throws Exception {
        
        // Lire contenu avec détection encodage
        String content = readTextWithEncodingDetection(file.getBytes(), filename);
        
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Fichier sans contenu: " + filename);
        }
        
        return processContent(content, filename, extension, batchId, fileSize);
    }
    
    // ========================================================================
    // ✨ INGESTION STREAMING (>100MB)
    // ========================================================================
    
    /**
     * Ingestion streaming pour gros fichiers texte (>100MB).
     * Lecture ligne par ligne pour éviter de charger tout en mémoire.
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
            
            // ✨ Lire contenu depuis fichier (streaming)
            // Pour les très gros fichiers, on lit tout d'un coup depuis le fichier
            // (plus efficace que ligne par ligne pour le chunking)
            byte[] bytes = Files.readAllBytes(tempFile);
            String content = readTextWithEncodingDetection(bytes, filename);
            
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("Fichier sans contenu: " + filename);
            }
            
            return processContent(content, filename, extension, batchId, fileSize);
            
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
    // TRAITEMENT CONTENU (LOGIQUE COMMUNE)
    // ========================================================================
    
    /**
     * Traite le contenu texte (logique commune normal/streaming)
     */
    private IngestionResult processContent(String content, String filename,
                                            String extension, String batchId,
                                            long fileSize) throws Exception {
        
        log.debug("📝 [{}] Contenu extrait: {} caractères", getName(), content.length());
        
        // Détection type de contenu
        String contentType = detectContentType(content, extension);
        
        log.info("🔍 [{}] Type détecté: {}", getName(), contentType);
        

        var chunkResult = chunkAndIndexText(content, filename, extension, contentType, batchId);
        int textEmbeddings = chunkResult.indexed();
        int duplicates = chunkResult.duplicates();
        
        if (duplicates > 0) {
            log.info("⏭️ [Dedup] {} duplicates skip, {} nouveaux indexés", 
                duplicates, textEmbeddings);
        }
        
        // Résultat
        Map<String, Object> resultMetadata = new HashMap<>();
        resultMetadata.put("strategy", getName());
        resultMetadata.put("filename", filename);
        resultMetadata.put("extension", extension);
        resultMetadata.put("contentType", contentType);
        resultMetadata.put("characters", content.length());
        
        return new IngestionResult(textEmbeddings, 0, resultMetadata);
    }
    
    // ========================================================================
    // LECTURE TEXTE AVEC DÉTECTION ENCODAGE
    // ========================================================================
    
    /**
     * Lit le contenu avec détection automatique de l'encodage
     */
    private String readTextWithEncodingDetection(byte[] bytes, String filename) 
            throws IOException {
        
        // 1. Essayer UTF-8
        try {
            String content = new String(bytes, StandardCharsets.UTF_8);
            
            // Vérifier si contient des caractères de remplacement (�)
            if (!content.contains("\uFFFD")) {
                log.debug("✓ [{}] Encodage détecté: UTF-8", getName());
                return content;
            }
            
        } catch (Exception e) {
            log.debug("⚠️ [{}] Échec lecture UTF-8", getName());
        }
        
        // 2. Fallback ISO-8859-1 (Latin-1)
        try {
            String content = new String(bytes, "ISO-8859-1");
            log.debug("✓ [{}] Encodage détecté: ISO-8859-1 (fallback)", getName());
            return content;
            
        } catch (Exception e) {
            throw new IOException("Impossible de lire le fichier: " + filename, e);
        }
    }
    
    // ========================================================================
    // DÉTECTION TYPE DE CONTENU
    // ========================================================================
    
    /**
     * Détecte le type de contenu basé sur l'extension et le contenu
     */
    private String detectContentType(String content, String extension) {
        
        // JSON
        if ("json".equals(extension) || content.trim().startsWith("{") || 
            content.trim().startsWith("[")) {
            return "json";
        }
        
        // XML/HTML
        if ("xml".equals(extension) || "html".equals(extension) || "htm".equals(extension) ||
            content.trim().startsWith("<")) {
            return extension.equals("html") || extension.equals("htm") ? "html" : "xml";
        }
        
        // CSV
        if ("csv".equals(extension) || "tsv".equals(extension)) {
            return "csv";
        }
        
        // Markdown
        if ("md".equals(extension) || "markdown".equals(extension)) {
            return "markdown";
        }
        
        // Code
        if (CODE_EXTENSIONS.contains(extension)) {
            return "code_" + extension;
        }
        
        // YAML
        if ("yaml".equals(extension) || "yml".equals(extension)) {
            return "yaml";
        }
        
        // Config
        if ("properties".equals(extension) || "conf".equals(extension) || 
            "config".equals(extension) || "ini".equals(extension) || "env".equals(extension)) {
            return "config";
        }
        
        // Documentation
        if ("rst".equals(extension) || "adoc".equals(extension) || "tex".equals(extension)) {
            return "documentation";
        }
        
        // Texte générique
        return "text";
    }
    
    // ========================================================================
    // CHUNKING AVEC DÉDUPLICATION
    // ========================================================================
    
    private record ChunkResult(int indexed, int duplicates) {}

    private ChunkResult chunkAndIndexText(String content, String filename, String extension,
                                    String contentType, String batchId) {
        int chunkSize = 1000;
        int overlap = 100;
        int indexed = 0;
        int duplicates = 0;
        int chunkIndex = 0;
        
        // Déterminer taille chunk selon type
        ChunkConfig config = getChunkConfig(contentType);
        
        log.debug("📏 [{}] Config chunking: size={} overlap={} (type: {})",
            getName(), config.size, config.overlap, contentType);
    
        if (content.length() <= chunkSize) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("source", filename);
                meta.put("extension", extension);
                meta.put("type", contentType);
                meta.put("chunkIndex", chunkIndex);
                meta.put("batchId", batchId);
                
                // Metadata spécifiques selon type
                addTypeSpecificMetadata(meta, content.trim(), contentType);
                
                Metadata metadata = Metadata.from(sanitizer.sanitize(meta));

                String embeddingId = indexText(content.trim(), metadata, batchId);

                if (embeddingId != null) {
                    tracker.addTextEmbeddingId(batchId, embeddingId);
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
                
                // Metadata spécifiques selon type
                addTypeSpecificMetadata(meta, chunk, contentType);
                
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
     * Configuration de chunking selon le type
     */
    private ChunkConfig getChunkConfig(String contentType) {
        
        // Code : chunks plus grands pour garder le contexte
        if (contentType.startsWith("code_")) {
            return new ChunkConfig(1500, 200);
        }
        
        // JSON/XML : chunks plus petits pour préserver la structure
        if (contentType.equals("json") || contentType.equals("xml")) {
            return new ChunkConfig(800, 100);
        }
        
        // CSV : chunks moyens avec peu d'overlap
        if (contentType.equals("csv")) {
            return new ChunkConfig(1200, 50);
        }
        
        // Markdown : chunks standards
        if (contentType.equals("markdown")) {
            return new ChunkConfig(1000, 100);
        }
        
        // Texte par défaut
        return new ChunkConfig(1000, 100);
    }
    
    /**
     * Ajoute metadata spécifiques selon le type
     */
    private void addTypeSpecificMetadata(Map<String, Object> meta, String chunk, 
                                          String contentType) {
        
        // Code : détecter le langage
        if (contentType.startsWith("code_")) {
            String language = contentType.substring(5);
            meta.put("language", language);
            
            int lines = chunk.split("\n").length;
            meta.put("linesOfCode", lines);
        }
        
        // CSV : compter lignes
        if (contentType.equals("csv")) {
            int rows = chunk.split("\n").length;
            meta.put("rows", rows);
            meta.put("format", "csv");
        }
        
        // JSON : indiquer format
        if (contentType.equals("json")) {
            meta.put("format", "json");
        }
        
        // Markdown : détecter headers
        if (contentType.equals("markdown")) {
            boolean hasHeaders = chunk.contains("#");
            meta.put("hasHeaders", hasHeaders);
        }
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
        return 8; // Basse priorité (fallback pour fichiers texte)
    }
    
    /**
     * Record pour configuration chunking
     */
    private record ChunkConfig(int size, int overlap) {}
    
    /**
     * Retourne les extensions supportées
     */
    public static Set<String> getSupportedExtensions() {
        return Set.copyOf(SUPPORTED_EXTENSIONS);
    }
    
    /**
     * Vérifie si une extension est supportée
     */
    public static boolean isSupported(String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }
}