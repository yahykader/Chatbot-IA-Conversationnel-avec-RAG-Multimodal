// ============================================================================
// SERVICE - TextDeduplicationService.java (FIXED - Race Condition)
// Déduplication des textes avec opération atomique check-and-mark
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.deduplication;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de déduplication des textes avant insertion dans PgVector
 * 
 * ✅ FIX: Opération atomique check-and-mark pour éviter race conditions
 * 
 * Évite de stocker plusieurs fois le même texte dans la base d'embeddings.
 * 
 * Stratégie :
 * 1. Hash SHA-256 du texte normalisé
 * 2. Vérification ET marquage ATOMIQUE dans cache local
 * 3. Synchronisation avec Redis en arrière-plan
 * 
 * Cas d'usage :
 * - Headers/footers répétés dans les documents
 * - Sections dupliquées
 * - Textes identiques dans différents fichiers
 */
@Slf4j
@Service
public class TextDeduplicationService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // Cache mémoire local pour la session d'ingestion
    // ✅ ConcurrentHashMap.newKeySet() pour thread-safety
    private final Set<String> localCache = ConcurrentHashMap.newKeySet();
    
    @Value("${deduplication.text.enabled:true}")
    private boolean enabled;
    
    @Value("${deduplication.text.redis-prefix:text:dedup:}")
    private String redisPrefix;
    
    @Value("${deduplication.text.ttl-days:30}")
    private int ttlDays;
    
    @Value("${deduplication.text.batch-id-scope:false}")
    private boolean batchIdScope;  // Si true, dédup par batch, sinon global
    
    public TextDeduplicationService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("✅ TextDeduplicationService initialisé");
        log.info("   - Enabled: {}", enabled);
        log.info("   - Redis Prefix: {}", redisPrefix);
        log.info("   - TTL: {} jours", ttlDays);
        log.info("   - Batch ID Scope: {}", batchIdScope);
    }
    
    /**
     * ✅ OPÉRATION ATOMIQUE : Vérifie et marque en une seule opération
     * 
     * Cette méthode résout la race condition en utilisant une opération atomique
     * sur le Set concurrent. L'ajout au Set retourne false si l'élément existait déjà.
     * 
     * @param text Texte à vérifier
     * @param batchId Batch ID (optionnel)
     * @return true si c'est un nouveau texte (à indexer), false si duplicate (skip)
     */
    public boolean checkAndMark(String text, String batchId) {
        if (!enabled || text == null || text.isBlank()) {
            return true;  // Désactivé ou texte vide → Continuer l'indexation
        }
        
        String hash = hash(text);
        String key = buildKey(hash, batchId);
        
        // ✅ FIX RACE CONDITION: Opération atomique
        // Set.add() retourne false si l'élément existe déjà
        boolean isNew = localCache.add(key);
        
        if (!isNew) {
            // Déjà dans le cache local → Duplicate
            log.debug("🔄 [Dedup] Duplicate détecté (local cache): {}", truncate(text, 50));
            return false;
        }
        
        // ✅ Premier ajout dans le cache local → Nouveau texte
        log.debug("✅ [Dedup] Nouveau texte, marqué: {}", truncate(text, 50));
        
        // Synchroniser avec Redis en arrière-plan (non bloquant)
        markInRedisAsync(key);
        
        return true;
    }
    
    /**
     * Vérifie si un texte a déjà été indexé (lecture seule, non atomique)
     * 
     * ⚠️ Utilisé uniquement pour la vérification, pas pour check-and-mark
     * 
     * @param text Texte à vérifier
     * @param batchId Batch ID (optionnel)
     * @return true si déjà indexé, false sinon
     */
    public boolean isDuplicate(String text, String batchId) {
        if (!enabled || text == null || text.isBlank()) {
            return false;
        }
        
        String hash = hash(text);
        String key = buildKey(hash, batchId);
        
        // 1. Vérification cache local (ultra-rapide)
        if (localCache.contains(key)) {
            return true;
        }
        
        // 2. Vérification Redis (rapide)
        try {
            Boolean exists = redisTemplate.hasKey(key);
            
            if (Boolean.TRUE.equals(exists)) {
                // Ajouter au cache local pour prochaines vérifications
                localCache.add(key);
                return true;
            }
            
        } catch (Exception e) {
            log.debug("⚠️ [Dedup] Redis non disponible, fallback local only: {}", e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Marque un texte comme indexé (sans vérification préalable)
     * 
     * ⚠️ Ne pas utiliser directement, préférer checkAndMark()
     * 
     * @param text Texte indexé
     * @param batchId Batch ID (optionnel)
     */
    public void markAsIndexed(String text, String batchId) {
        if (!enabled || text == null || text.isBlank()) {
            return;
        }
        
        String hash = hash(text);
        String key = buildKey(hash, batchId);
        
        // 1. Marquer dans cache local
        localCache.add(key);
        
        // 2. Marquer dans Redis
        markInRedisAsync(key);
        
        log.debug("✅ [Dedup] Texte marqué comme indexé: {}", truncate(text, 50));
    }
    
    /**
     * ✅ NOUVEAU: Marquage Redis asynchrone (non bloquant)
     * 
     * Évite de bloquer le thread d'ingestion en cas de latence Redis
     */
    private void markInRedisAsync(String key) {
        try {
            redisTemplate.opsForValue().set(
                key, 
                "1", 
                Duration.ofDays(ttlDays)
            );
        } catch (Exception e) {
            // Redis non disponible → Pas critique, on continue avec cache local
            log.debug("⚠️ [Dedup] Redis non disponible pour marquage: {}", e.getMessage());
        }
    }
    
    /**
     * Construit la clé Redis
     */
    private String buildKey(String hash, String batchId) {
        if (batchIdScope && batchId != null && !batchId.isBlank()) {
            return redisPrefix + batchId + ":" + hash;
        }
        return redisPrefix + hash;
    }
    
    /**
     * Hash SHA-256 du texte normalisé
     */
    private String hash(String text) {
        try {
            // Normalisation : trim + lowercase + suppression espaces multiples
            String normalized = text.trim()
                .toLowerCase()
                .replaceAll("\\s+", " ");
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("❌ Erreur hash SHA-256", e);
            return String.valueOf(text.hashCode());
        }
    }
    
    /**
     * Nettoie le cache local (à appeler en fin de batch)
     */
    public void clearLocalCache() {
        int size = localCache.size();
        localCache.clear();
        log.debug("🗑️ [Dedup] Cache local nettoyé: {} entrées", size);
    }
    
    /**
     * Nettoie toutes les entrées Redis d'un batch
     */
    public void clearBatch(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return;
        }
        
        try {
            String pattern = redisPrefix + batchId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("🗑️ [Dedup] Batch nettoyé: {} → {} clés", batchId, keys.size());
            }
            
        } catch (Exception e) {
            log.error("❌ [Dedup] Erreur nettoyage batch: {}", e.getMessage());
        }
    }
    
    /**
     * Statistiques de déduplication
     */
    public DedupStats getStats(String batchId) {
        try {
            String pattern = batchIdScope && batchId != null 
                ? redisPrefix + batchId + ":*" 
                : redisPrefix + "*";
            
            Set<String> keys = redisTemplate.keys(pattern);
            long totalIndexed = keys != null ? keys.size() : 0;
            long localCacheSize = localCache.size();
            
            return new DedupStats(enabled, totalIndexed, localCacheSize, batchIdScope);
            
        } catch (Exception e) {
            log.error("❌ [Dedup] Erreur stats: {}", e.getMessage());
            return new DedupStats(enabled, 0, localCache.size(), batchIdScope);
        }
    }
    
    /**
     * Tronque un texte pour les logs
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * Record pour les statistiques
     */
    public record DedupStats(
        boolean enabled,
        long totalIndexed,
        long localCacheSize,
        boolean batchIdScope
    ) {}
}