// ============================================================================
// UTIL - MetadataSanitizer.java
// Utilitaire pour nettoyer et valider les metadata
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilitaire pour nettoyer et valider les metadata avant stockage.
 * 
 * Fonctionnalités :
 * - Suppression valeurs null
 * - Conversion types non supportés vers String
 * - Validation clés
 * - Limitation taille
 * 
 * Usage :
 * <pre>
 * Map<String, Object> metadata = new HashMap<>();
 * metadata.put("key", value);
 * 
 * Map<String, Object> clean = sanitizer.sanitize(metadata);
 * </pre>
 */
@Slf4j
@Component
public class MetadataSanitizer {
    
    private static final int MAX_KEY_LENGTH = 100;
    private static final int MAX_STRING_VALUE_LENGTH = 10000;
    
    /**
     * Nettoie une map de metadata
     * 
     * @param metadata Metadata à nettoyer
     * @return Metadata nettoyée (nouvelle map)
     */
    public Map<String, Object> sanitize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String, Object> cleaned = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Ignorer si clé ou valeur null
            if (key == null || value == null) {
                continue;
            }
            
            // Nettoyer la clé
            String cleanKey = sanitizeKey(key);
            
            // Nettoyer la valeur
            Object cleanValue = sanitizeValue(value);
            
            if (cleanKey != null && cleanValue != null) {
                cleaned.put(cleanKey, cleanValue);
            }
        }
        
        return cleaned;
    }
    
    /**
     * Nettoie une clé
     */
    private String sanitizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        
        // Trim et limiter longueur
        String clean = key.trim();
        
        if (clean.length() > MAX_KEY_LENGTH) {
            clean = clean.substring(0, MAX_KEY_LENGTH);
            log.debug("⚠️ [Sanitizer] Clé tronquée: {} → {}", key, clean);
        }
        
        return clean;
    }
    
    /**
     * Nettoie une valeur
     */
    private Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        
        // Types supportés directement
        if (value instanceof String ||
            value instanceof Number ||
            value instanceof Boolean) {
            
            // Limiter taille des strings
            if (value instanceof String str) {
                if (str.length() > MAX_STRING_VALUE_LENGTH) {
                    log.debug("⚠️ [Sanitizer] String tronquée: {} chars → {}",
                        str.length(), MAX_STRING_VALUE_LENGTH);
                    return str.substring(0, MAX_STRING_VALUE_LENGTH);
                }
            }
            
            return value;
        }
        
        // Convertir autres types en String
        return value.toString();
    }
}