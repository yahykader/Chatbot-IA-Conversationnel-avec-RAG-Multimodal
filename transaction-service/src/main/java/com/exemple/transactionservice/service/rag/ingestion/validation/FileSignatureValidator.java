// ============================================================================
// SERVICE - FileSignatureValidator.java
// Validation des signatures de fichiers (magic bytes) pour sécurité
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * Validateur de signatures de fichiers (magic bytes).
 * Vérifie que le contenu réel du fichier correspond à son extension.
 * 
 * Sécurité:
 * - Détecte fichiers malicieux déguisés (ex: .exe renommé en .pdf)
 * - Empêche injections de fichiers non autorisés
 * - Valide intégrité avant traitement
 * 
 * Fonctionnement:
 * - Lit les premiers bytes du fichier (magic bytes)
 * - Compare avec signatures connues
 * - Rejette si mismatch
 * 
 * Formats supportés:
 * - PDF, PNG, JPG, GIF, BMP, TIFF, WEBP
 * - DOCX, XLSX (ZIP OOXML)
 * - DOC, XLS, PPT (OLE)
 * - Et plus...
 */
@Slf4j
@Component
public class FileSignatureValidator {
    
    /**
     * Signatures de fichiers (magic bytes)
     * Format: extension -> signature bytes
     */
    private static final Map<String, byte[]> FILE_SIGNATURES = Map.ofEntries(
        // Documents
        Map.entry("pdf", new byte[]{0x25, 0x50, 0x44, 0x46}), // %PDF
        Map.entry("docx", new byte[]{0x50, 0x4B, 0x03, 0x04}), // ZIP (OOXML)
        Map.entry("xlsx", new byte[]{0x50, 0x4B, 0x03, 0x04}), // ZIP (OOXML)
        Map.entry("pptx", new byte[]{0x50, 0x4B, 0x03, 0x04}), // ZIP (OOXML)
        Map.entry("doc", new byte[]{(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0}), // OLE
        Map.entry("xls", new byte[]{(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0}), // OLE
        Map.entry("ppt", new byte[]{(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0}), // OLE
        
        // Images
        Map.entry("png", new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
        Map.entry("jpg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF}),
        Map.entry("jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF}),
        Map.entry("gif", new byte[]{0x47, 0x49, 0x46, 0x38}), // GIF8
        Map.entry("bmp", new byte[]{0x42, 0x4D}), // BM
        Map.entry("tiff", new byte[]{0x49, 0x49, 0x2A, 0x00}), // Little-endian
        Map.entry("tif", new byte[]{0x49, 0x49, 0x2A, 0x00}),
        Map.entry("webp", new byte[]{0x52, 0x49, 0x46, 0x46}), // RIFF
        
        // Archives
        Map.entry("zip", new byte[]{0x50, 0x4B, 0x03, 0x04}),
        Map.entry("rar", new byte[]{0x52, 0x61, 0x72, 0x21}), // Rar!
        Map.entry("7z", new byte[]{0x37, 0x7A, (byte)0xBC, (byte)0xAF}),
        Map.entry("gz", new byte[]{0x1F, (byte)0x8B}),
        
        // Texte/Code (pas de signature fiable, on skip)
        // txt, md, csv, json, xml, etc. n'ont pas de magic bytes
        
        // Exécutables (pour blocage)
        Map.entry("exe", new byte[]{0x4D, 0x5A}), // MZ
        Map.entry("dll", new byte[]{0x4D, 0x5A}), // MZ
        Map.entry("sh", new byte[]{0x23, 0x21}), // #! (shebang)
        Map.entry("bat", new byte[]{0x40, 0x65, 0x63, 0x68, 0x6F}) // @echo
    );
    
    /**
     * Extensions dangereuses à bloquer systématiquement
     */
    private static final String[] DANGEROUS_EXTENSIONS = {
        "exe", "dll", "bat", "sh", "cmd", "vbs", "ps1", "jar"
    };
    
    public FileSignatureValidator() {
        log.info("✅ FileSignatureValidator initialisé - {} signatures enregistrées", 
            FILE_SIGNATURES.size());
    }
    
    // ========================================================================
    // VALIDATION PRINCIPALE
    // ========================================================================
    
    /**
     * Valide la signature d'un fichier
     * 
     * @param file Fichier à valider
     * @param declaredExtension Extension déclarée (depuis nom fichier)
     * @throws SecurityException Si fichier malveillant détecté
     * @throws IOException Si erreur lecture
     */
    public void validate(MultipartFile file, String declaredExtension) 
            throws IOException, SecurityException {
        
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide ou null");
        }
        
        if (declaredExtension == null || declaredExtension.isBlank()) {
            throw new IllegalArgumentException("Extension non fournie");
        }
        
        String extension = declaredExtension.toLowerCase();
        
        // 1. Bloquer extensions dangereuses
        blockDangerousExtensions(extension);
        
        // 2. Valider signature si disponible
        validateSignature(file, extension);
        
        log.debug("✅ [Signature] Validation réussie: {}", file.getOriginalFilename());
    }
    
    // ========================================================================
    // VALIDATION SIGNATURE
    // ========================================================================
    
    /**
     * Valide que la signature du fichier correspond à l'extension
     */
    private void validateSignature(MultipartFile file, String extension) throws IOException {
        byte[] expectedSignature = FILE_SIGNATURES.get(extension);
        
        // Pas de signature connue pour ce format → skip validation
        if (expectedSignature == null) {
            log.debug("⚠️ [Signature] Pas de signature pour extension: {}", extension);
            return;
        }
        
        // Lire les bytes du fichier
        byte[] fileBytes = file.getBytes();
        
        if (fileBytes.length < expectedSignature.length) {
            throw new SecurityException(
                String.format("Fichier trop court (%d bytes) pour être un %s valide",
                    fileBytes.length, extension.toUpperCase())
            );
        }
        
        // Extraire signature actuelle
        byte[] actualSignature = Arrays.copyOf(fileBytes, expectedSignature.length);
        
        // Comparer
        if (!Arrays.equals(actualSignature, expectedSignature)) {
            throw new SecurityException(
                String.format("Signature invalide pour %s. " +
                    "Le fichier ne correspond pas à son extension (fichier déguisé ?)",
                    extension.toUpperCase())
            );
        }
        
        log.debug("✅ [Signature] Signature valide pour {}: {}", 
            extension, bytesToHex(actualSignature));
    }
    
    // ========================================================================
    // BLOCAGE FICHIERS DANGEREUX
    // ========================================================================
    
    /**
     * Bloque les extensions dangereuses (exécutables, scripts)
     */
    private void blockDangerousExtensions(String extension) throws SecurityException {
        for (String dangerous : DANGEROUS_EXTENSIONS) {
            if (dangerous.equals(extension)) {
                throw new SecurityException(
                    String.format("Extension dangereuse bloquée: %s " +
                        "(fichiers exécutables non autorisés)",
                        extension.toUpperCase())
                );
            }
        }
    }
    
    // ========================================================================
    // DÉTECTION TYPE RÉEL
    // ========================================================================
    
    /**
     * Détecte le type réel d'un fichier basé sur sa signature
     * (indépendamment de l'extension déclarée)
     * 
     * @param file Fichier à analyser
     * @return Extension détectée ou null si inconnue
     */
    public String detectRealType(MultipartFile file) throws IOException {
        byte[] fileBytes = file.getBytes();
        
        // Tester chaque signature connue
        for (Map.Entry<String, byte[]> entry : FILE_SIGNATURES.entrySet()) {
            String extension = entry.getKey();
            byte[] signature = entry.getValue();
            
            if (fileBytes.length >= signature.length) {
                byte[] fileSignature = Arrays.copyOf(fileBytes, signature.length);
                
                if (Arrays.equals(fileSignature, signature)) {
                    return extension;
                }
            }
        }
        
        return null; // Type inconnu
    }
    
    /**
     * Vérifie si l'extension déclarée correspond au type réel
     */
    public boolean isExtensionMatching(MultipartFile file, String declaredExtension) 
            throws IOException {
        
        String realType = detectRealType(file);
        
        if (realType == null) {
            // Type inconnu, on ne peut pas valider
            return true;
        }
        
        // Cas spéciaux: DOCX/XLSX/PPTX ont même signature (ZIP)
        if (realType.equals("zip") || realType.equals("docx") || 
            realType.equals("xlsx") || realType.equals("pptx")) {
            
            return declaredExtension.equals("docx") || 
                   declaredExtension.equals("xlsx") || 
                   declaredExtension.equals("pptx") ||
                   declaredExtension.equals("zip");
        }
        
        return realType.equals(declaredExtension.toLowerCase());
    }
    
    // ========================================================================
    // VALIDATION AVANCÉE
    // ========================================================================
    
    /**
     * Validation complète avec détection de mismatch
     */
    public ValidationResult validateComplete(MultipartFile file, String declaredExtension) 
            throws IOException {
        
        try {
            // 1. Validation standard
            validate(file, declaredExtension);
            
            // 2. Détection type réel
            String realType = detectRealType(file);
            
            // 3. Vérification correspondance
            boolean matching = isExtensionMatching(file, declaredExtension);
            
            return new ValidationResult(
                true,
                null,
                realType,
                matching
            );
            
        } catch (SecurityException e) {
            return new ValidationResult(
                false,
                e.getMessage(),
                null,
                false
            );
        }
    }
    
    /**
     * Record pour résultat de validation
     */
    public record ValidationResult(
        boolean isValid,
        String errorMessage,
        String detectedType,
        boolean extensionMatches
    ) {
        public boolean isSuspicious() {
            return !extensionMatches;
        }
    }
    
    // ========================================================================
    // UTILITAIRES
    // ========================================================================
    
    /**
     * Convertit bytes en hexadécimal pour affichage
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
    
    /**
     * Retourne la liste des extensions supportées pour validation
     */
    public static String[] getSupportedExtensions() {
        return FILE_SIGNATURES.keySet().toArray(new String[0]);
    }
    
    /**
     * Vérifie si une extension a une signature connue
     */
    public static boolean hasSignature(String extension) {
        return FILE_SIGNATURES.containsKey(extension.toLowerCase());
    }
}