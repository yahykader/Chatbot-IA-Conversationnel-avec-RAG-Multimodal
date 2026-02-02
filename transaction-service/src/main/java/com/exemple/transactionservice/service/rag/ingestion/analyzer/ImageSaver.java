// ============================================================================
// SERVICE - ImageSaver.java
// Service de sauvegarde d'images sur disque
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service de sauvegarde d'images sur disque.
 * 
 * Fonctionnalités :
 * - Sauvegarde images dans répertoire configurable
 * - Génération noms de fichiers uniques
 * - Création automatique des répertoires
 * - Support multiple formats (PNG, JPG)
 * - Gestion erreurs
 * 
 * Configuration :
 * document.images.storage-path : Chemin de sauvegarde (ex: D:/extracted-images)
 * 
 * Usage :
 * <pre>
 * BufferedImage image = ...;
 * String path = imageSaver.saveImage(image, "mon_image");
 * // Retourne: "D:/extracted-images/mon_image.png"
 * </pre>
 */
@Slf4j
@Service
public class ImageSaver {
    
    @Value("${document.images.storage-path:./images}")
    private String storagePath;
    
    private static final String DEFAULT_FORMAT = "png";
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    public ImageSaver() {
        log.info("✅ ImageSaver initialisé");
    }
    
    // ========================================================================
    // SAUVEGARDE IMAGE
    // ========================================================================
    
    /**
     * Sauvegarde une image sur disque au format PNG
     * 
     * @param image Image à sauvegarder
     * @param imageName Nom de base du fichier (sans extension)
     * @return Chemin complet du fichier sauvegardé
     * @throws IOException Si erreur sauvegarde
     */
    public String saveImage(BufferedImage image, String imageName) throws IOException {
        return saveImage(image, imageName, DEFAULT_FORMAT);
    }
    
    /**
     * Sauvegarde une image sur disque avec format spécifique
     * 
     * @param image Image à sauvegarder
     * @param imageName Nom de base du fichier (sans extension)
     * @param format Format (png, jpg, etc.)
     * @return Chemin complet du fichier sauvegardé
     * @throws IOException Si erreur sauvegarde
     */
    public String saveImage(BufferedImage image, String imageName, String format) 
            throws IOException {
        
        if (image == null) {
            throw new IllegalArgumentException("Image ne peut pas être null");
        }
        
        if (imageName == null || imageName.isBlank()) {
            throw new IllegalArgumentException("Nom d'image ne peut pas être vide");
        }
        
        try {
            // 1. Créer le répertoire si nécessaire
            ensureStorageDirectoryExists();
            
            // 2. Générer nom de fichier unique
            String sanitizedName = sanitizeFilename(imageName);
            String filename = sanitizedName + "." + format.toLowerCase();
            
            // 3. Créer le chemin complet
            Path imagePath = Paths.get(storagePath, filename);
            File imageFile = imagePath.toFile();
            
            // 4. Vérifier si fichier existe déjà
            if (imageFile.exists()) {
                // Ajouter timestamp pour unicité
                String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
                filename = sanitizedName + "_" + timestamp + "." + format.toLowerCase();
                imagePath = Paths.get(storagePath, filename);
                imageFile = imagePath.toFile();
            }
            
            // 5. Sauvegarder l'image
            boolean saved = ImageIO.write(image, format, imageFile);
            
            if (!saved) {
                throw new IOException("Impossible de sauvegarder l'image au format: " + format);
            }
            
            String absolutePath = imageFile.getAbsolutePath();
            
            log.debug("💾 [ImageSaver] Image sauvegardée: {} ({}x{} px, {} bytes)",
                filename, image.getWidth(), image.getHeight(), imageFile.length());
            
            return absolutePath;
            
        } catch (IOException e) {
            log.error("❌ [ImageSaver] Erreur sauvegarde image '{}': {}", 
                imageName, e.getMessage(), e);
            throw e;
        }
    }
    
    // ========================================================================
    // GESTION RÉPERTOIRE
    // ========================================================================
    
    /**
     * S'assure que le répertoire de stockage existe
     */
    private void ensureStorageDirectoryExists() throws IOException {
        Path path = Paths.get(storagePath);
        
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                log.info("📁 [ImageSaver] Répertoire créé: {}", storagePath);
            } catch (IOException e) {
                log.error("❌ [ImageSaver] Impossible de créer répertoire: {}", storagePath, e);
                throw new IOException("Impossible de créer le répertoire: " + storagePath, e);
            }
        }
        
        // Vérifier permissions écriture
        if (!Files.isWritable(path)) {
            throw new IOException("Répertoire non accessible en écriture: " + storagePath);
        }
    }
    
    // ========================================================================
    // UTILITAIRES
    // ========================================================================
    
    /**
     * Nettoie un nom de fichier (retire caractères invalides)
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "image";
        }
        
        // Retirer extension si présente
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            filename = filename.substring(0, lastDot);
        }
        
        // Remplacer caractères invalides par underscore
        String sanitized = filename
            .replaceAll("[\\\\/:*?\"<>|]", "_")  // Caractères interdits Windows
            .replaceAll("\\s+", "_")              // Espaces
            .replaceAll("_{2,}", "_")             // Underscores multiples
            .trim();
        
        // Limiter longueur
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200);
        }
        
        // Fallback si vide
        if (sanitized.isBlank()) {
            sanitized = "image";
        }
        
        return sanitized;
    }
    
    /**
     * Retourne le chemin de stockage configuré
     */
    public String getStoragePath() {
        return storagePath;
    }
    
    /**
     * Retourne les statistiques du répertoire
     */
    public StorageStats getStorageStats() throws IOException {
        Path path = Paths.get(storagePath);
        
        if (!Files.exists(path)) {
            return new StorageStats(0, 0, storagePath);
        }
        
        long fileCount = Files.list(path)
            .filter(Files::isRegularFile)
            .count();
        
        long totalSize = Files.walk(path)
            .filter(Files::isRegularFile)
            .mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            })
            .sum();
        
        return new StorageStats(fileCount, totalSize, storagePath);
    }
    
    /**
     * Record pour statistiques de stockage
     */
    public record StorageStats(
        long fileCount,
        long totalSizeBytes,
        String path
    ) {
        public double getTotalSizeMB() {
            return totalSizeBytes / (1024.0 * 1024.0);
        }
        
        public String getFormattedSize() {
            double mb = getTotalSizeMB();
            if (mb < 1024) {
                return String.format("%.2f MB", mb);
            } else {
                return String.format("%.2f GB", mb / 1024.0);
            }
        }
    }
}