package com.exemple.transactionservice.service.rag.ingestion.util;

public class FileUtils {
    
    private FileUtils() {}
    
    public static String getExtension(String filename) {
        if (filename == null || filename.isBlank()) return "";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) return "";
        return filename.substring(lastDot + 1).toLowerCase();
    }
    
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "unknown";
        return filename.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
    
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.2f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.2f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

     /**
     * Retire l'extension d'un nom de fichier
     */
    public static String removeExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return filename;
        }
        
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return filename;
        }
        
        return filename.substring(0, lastDot);
    }
    
    /**
     * Génère un nom d'image unique
     */
    public static String generateImageName(String baseName, String extension, int index) {
        return String.format("%s_img_%d.%s", baseName, index, extension);
    }
    
    /**
     * Génère un nom de page unique
     */
    public static String generatePageName(String baseName, String extension, int pageNumber) {
        return String.format("%s_page_%d.%s", baseName, pageNumber, extension);
    }
}
