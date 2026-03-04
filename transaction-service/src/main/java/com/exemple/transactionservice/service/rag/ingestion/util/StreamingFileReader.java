// ============================================================================
// UTIL - StreamingFileReader.java
// Utilitaire pour lecture streaming de gros fichiers (>100MB)
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * Utilitaire pour lecture streaming de gros fichiers sans charger en mémoire.
 * 
 * <h2>Problème</h2>
 * <p>Charger un fichier de 500MB en mémoire :
 * <ul>
 *   <li>file.getBytes() → <b>500MB RAM</b></li>
 *   <li>Parsing PDF/DOCX → <b>+500MB RAM</b></li>
 *   <li><b>Total : 1GB RAM pour 1 fichier</b></li>
 * </ul>
 * 
 * <h2>Solution</h2>
 * <p>Streaming par chunks :
 * <ul>
 *   <li>Lire par blocs de 10MB</li>
 *   <li>Traiter chunk par chunk</li>
 *   <li><b>Mémoire constante : ~20MB</b></li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>
 * // Au lieu de :
 * byte[] data = file.getBytes(); // ❌ Charge tout en RAM
 * 
 * // Faire :
 * StreamingFileReader.streamChunks(file, 10_000_000, chunk -> {
 *     // Traiter chunk (10MB max)
 *     processChunk(chunk);
 * }); // ✅ Mémoire constante
 * </pre>
 * 
 * @author RAG Team
 * @version 1.0
 * @since 2025-01
 */
@Slf4j
public class StreamingFileReader {
    
    private static final int DEFAULT_CHUNK_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int BUFFER_SIZE = 8192; // 8 KB
    
    /**
     * Seuil à partir duquel activer streaming (100MB)
     */
    public static final long STREAMING_THRESHOLD = 100 * 1024 * 1024L;
    
    // ========================================================================
    // MÉTHODES PRINCIPALES - STREAMING PAR CHUNKS
    // ========================================================================
    
    /**
     * Stream un fichier par chunks sans charger en mémoire.
     * 
     * <p><b>Usage optimal pour fichiers > 100MB</b>
     * 
     * @param file Fichier à streamer
     * @param chunkSize Taille d'un chunk (bytes)
     * @param processor Fonction à appliquer sur chaque chunk
     * @throws IOException Si erreur lecture
     */
    public static void streamChunks(MultipartFile file, int chunkSize, 
                                     Consumer<byte[]> processor) throws IOException {
        
        long fileSize = file.getSize();
        log.debug("📖 Streaming fichier: {} ({} MB)", 
            file.getOriginalFilename(), fileSize / 1_000_000);
        
        try (InputStream input = file.getInputStream()) {
            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            int chunkIndex = 0;
            
            while ((bytesRead = input.read(buffer)) != -1) {
                // Créer chunk de la taille exacte lue
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                
                log.debug("📦 Chunk {} : {} bytes", chunkIndex++, bytesRead);
                processor.accept(chunk);
            }
        }
    }
    
    /**
     * Stream avec chunk size par défaut (10MB).
     */
    public static void streamChunks(MultipartFile file, Consumer<byte[]> processor) 
            throws IOException {
        streamChunks(file, DEFAULT_CHUNK_SIZE, processor);
    }
    
    /**
     * Stream un fichier ligne par ligne (pour fichiers texte).
     * 
     * @param file Fichier texte
     * @param processor Fonction à appliquer sur chaque ligne
     * @throws IOException Si erreur lecture
     */
    public static void streamLines(MultipartFile file, Consumer<String> processor) 
            throws IOException {
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {
            
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                processor.accept(line);
                lineNumber++;
                
                if (lineNumber % 10000 == 0) {
                    log.debug("📄 Lignes traitées: {}", lineNumber);
                }
            }
            
            log.debug("✅ Total lignes: {}", lineNumber);
        }
    }
    
    // ========================================================================
    // MÉTHODES UTILITAIRES - FICHIERS TEMPORAIRES
    // ========================================================================
    
    /**
     * Sauvegarde MultipartFile en fichier temporaire pour streaming.
     * 
     * <p>Utile pour bibliothèques nécessitant un File (Apache POI, PDFBox).
     * 
     * @param file Fichier à sauvegarder
     * @return Path du fichier temporaire
     * @throws IOException Si erreur écriture
     */
    public static Path saveToTempFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        
        Path tempFile = Files.createTempFile("upload-", "." + extension);
        
        // Streamer vers fichier temp (évite de charger en RAM)
        try (InputStream input = file.getInputStream();
             FileChannel channel = FileChannel.open(tempFile, 
                 StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            byte[] bytes = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = input.read(bytes)) != -1) {
                buffer.clear();
                buffer.put(bytes, 0, bytesRead);
                buffer.flip();
                channel.write(buffer);
            }
        }
        
        log.debug("💾 Fichier temporaire créé: {}", tempFile);
        return tempFile;
    }
    
    /**
     * Sauvegarde avec callback de progression.
     */
    public static Path saveToTempFileWithProgress(MultipartFile file, 
                                                   Consumer<Long> progressCallback) 
            throws IOException {
        
        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        
        Path tempFile = Files.createTempFile("upload-", "." + extension);
        long totalSize = file.getSize();
        long bytesWritten = 0;
        
        try (InputStream input = file.getInputStream();
             FileChannel channel = FileChannel.open(tempFile, 
                 StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            byte[] bytes = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = input.read(bytes)) != -1) {
                buffer.clear();
                buffer.put(bytes, 0, bytesRead);
                buffer.flip();
                channel.write(buffer);
                
                bytesWritten += bytesRead;
                progressCallback.accept(bytesWritten);
                
                // Log progression tous les 10MB
                if (bytesWritten % (10 * 1024 * 1024) == 0) {
                    double percent = (bytesWritten * 100.0) / totalSize;
                    log.debug("📊 Progression: {:.1f}% ({} MB / {} MB)", 
                        percent, bytesWritten / 1_000_000, totalSize / 1_000_000);
                }
            }
        }
        
        log.info("✅ Fichier sauvegardé: {} ({} MB)", 
            tempFile, bytesWritten / 1_000_000);
        
        return tempFile;
    }
    
    // ========================================================================
    // MÉTHODES UTILITAIRES - DÉTECTION & VALIDATION
    // ========================================================================
    
    /**
     * Vérifie si fichier nécessite streaming (>100MB).
     */
    public static boolean requiresStreaming(MultipartFile file) {
        return file.getSize() > STREAMING_THRESHOLD;
    }
    
    /**
     * Détermine la taille de chunk optimale selon taille fichier.
     */
    public static int getOptimalChunkSize(long fileSize) {
        if (fileSize < 50 * 1024 * 1024) {          // < 50MB
            return 5 * 1024 * 1024;                  // 5MB chunks
        } else if (fileSize < 200 * 1024 * 1024) {  // < 200MB
            return 10 * 1024 * 1024;                 // 10MB chunks
        } else if (fileSize < 500 * 1024 * 1024) {  // < 500MB
            return 20 * 1024 * 1024;                 // 20MB chunks
        } else {                                     // >= 500MB
            return 50 * 1024 * 1024;                 // 50MB chunks
        }
    }
    
    /**
     * Calcule nombre de chunks nécessaires.
     */
    public static int getChunkCount(long fileSize, int chunkSize) {
        return (int) Math.ceil((double) fileSize / chunkSize);
    }
    
    /**
     * Valide si fichier peut être streamé.
     */
    public static boolean canStream(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        try {
            // Vérifier que stream est accessible
            try (InputStream ignored = file.getInputStream()) {
                return true;
            }
        } catch (IOException e) {
            log.warn("⚠️ Fichier non streamable: {}", file.getOriginalFilename(), e);
            return false;
        }
    }
    
    // ========================================================================
    // CLASSE UTILITAIRE - ITERATOR PAR CHUNKS
    // ========================================================================
    
    /**
     * Itérateur pour lire fichier chunk par chunk.
     * 
     * <p>Permet usage dans for-each :
     * <pre>
     * for (byte[] chunk : StreamingFileReader.chunks(file, 10_000_000)) {
     *     processChunk(chunk);
     * }
     * </pre>
     */
    public static Iterable<byte[]> chunks(MultipartFile file, int chunkSize) {
        return () -> new ChunkIterator(file, chunkSize);
    }
    
    /**
     * Itérateur interne pour chunks.
     */
    private static class ChunkIterator implements Iterator<byte[]> {
        private final InputStream input;
        private final int chunkSize;
        private byte[] nextChunk;
        private boolean hasNext;
        
        public ChunkIterator(MultipartFile file, int chunkSize) {
            try {
                this.input = file.getInputStream();
                this.chunkSize = chunkSize;
                this.hasNext = true;
                advance();
            } catch (IOException e) {
                throw new RuntimeException("Erreur ouverture stream", e);
            }
        }
        
        private void advance() {
            try {
                byte[] buffer = new byte[chunkSize];
                int bytesRead = input.read(buffer);
                
                if (bytesRead == -1) {
                    hasNext = false;
                    input.close();
                } else {
                    nextChunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, nextChunk, 0, bytesRead);
                }
            } catch (IOException e) {
                hasNext = false;
                try { input.close(); } catch (IOException ignored) {}
                throw new RuntimeException("Erreur lecture chunk", e);
            }
        }
        
        @Override
        public boolean hasNext() {
            return hasNext;
        }
        
        @Override
        public byte[] next() {
            if (!hasNext) {
                throw new NoSuchElementException();
            }
            
            byte[] current = nextChunk;
            advance();
            return current;
        }
    }
    
    // ========================================================================
    // MÉTHODES PRIVÉES
    // ========================================================================
    
    /**
     * Extrait extension d'un nom de fichier.
     */
    private static String getExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "tmp";
        }
        
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "tmp";
        }
        
        return filename.substring(lastDot + 1);
    }
    
    // ========================================================================
    // RECORDS UTILITAIRES
    // ========================================================================
    
    /**
     * Informations sur un chunk.
     */
    public record ChunkInfo(
        int index,        // Index du chunk (0-based)
        int size,         // Taille du chunk (bytes)
        long offset,      // Offset dans le fichier
        boolean isLast    // Dernier chunk ?
    ) {}
    
    /**
     * Statistiques de streaming.
     */
    public record StreamingStats(
        long totalBytes,     // Bytes totaux lus
        int chunksProcessed, // Nombre de chunks traités
        long durationMs,     // Durée totale (ms)
        double throughputMBps // Débit (MB/s)
    ) {
        @Override
        public String toString() {
            return String.format(
                "StreamingStats{bytes=%d, chunks=%d, duration=%dms, throughput=%.2f MB/s}",
                totalBytes, chunksProcessed, durationMs, throughputMBps
            );
        }
    }
}