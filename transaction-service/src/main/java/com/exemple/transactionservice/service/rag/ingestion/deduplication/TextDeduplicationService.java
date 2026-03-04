// ============================================================================
// SERVICE - TextDeduplicationService.java (FIXED - Race Condition + Batch Cleanup)
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
 * ✅ NEW: Nettoyage sélectif par batch (removeBatch, clearAll)
 * 
 * Évite de stocker plusieurs fois le même texte dans la base d'embeddings.
 * 
 * Stratégie :
 * 1. Hash SHA-256 du texte normalisé
 * 2. Vérification ET marquage ATOMIQUE dans cache local
 * 3. Synchronisation avec Redis en arrière-plan
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
    private boolean batchIdScope;
    
    // ✅ NOUVEAU: Prefix pour le tracking par batch
    private static final String BATCH_TEXT_PREFIX = "batch:text:";
    
    public TextDeduplicationService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("✅ TextDeduplicationService initialisé");
        log.info("   - Enabled: {}", enabled);
        log.info("   - Redis Prefix: {}", redisPrefix);
        log.info("   - TTL: {} jours", ttlDays);
        log.info("   - Batch ID Scope: {}", batchIdScope);
    }
    
    // ========================================================================
    // MÉTHODES EXISTANTES (TOUTES INCHANGÉES)
    // ========================================================================
    
    /**
     * ✅ OPÉRATION ATOMIQUE : Vérifie et marque en une seule opération
     */
    public boolean checkAndMark(String text, String batchId) {
        if (!enabled || text == null || text.isBlank()) {
            return true;
        }
        
        String hash = hash(text);
        String key = buildKey(hash, batchId);
        
        // ✅ FIX RACE CONDITION: Opération atomique
        boolean isNew = localCache.add(key);
        
        if (!isNew) {
            log.debug("🔄 [Dedup] Duplicate détecté (local cache): {}", truncate(text, 50));
            return false;
        }
        
        log.debug("✅ [Dedup] Nouveau texte, marqué: {}", truncate(text, 50));
        
        // ✅ AMÉLIORER: Associer au batch pour nettoyage sélectif
        if (batchId != null && !batchId.isBlank()) {
            trackBatchAssociation(batchId, hash);
        }
        
        markInRedisAsync(key);
        
        return true;
    }
    
    /**
     * Vérifie si un texte a déjà été indexé (lecture seule, non atomique)
     */
    public boolean isDuplicate(String text, String batchId) {
        if (!enabled || text == null || text.isBlank()) {
            return false;
        }
        
        String hash = hash(text);
        String key = buildKey(hash, batchId);
        
        // 1. Vérification cache local
        if (localCache.contains(key)) {
            return true;
        }
        
        // 2. Vérification Redis
        try {
            Boolean exists = redisTemplate.hasKey(key);
            
            if (Boolean.TRUE.equals(exists)) {
                localCache.add(key);
                
                // ✅ AMÉLIORER: Associer au batch si détecté
                if (batchId != null && !batchId.isBlank()) {
                    trackBatchAssociation(batchId, hash);
                }
                
                return true;
            }
            
        } catch (Exception e) {
            log.debug("⚠️ [Dedup] Redis non disponible, fallback local only: {}", e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Marque un texte comme indexé (sans vérification préalable)
     */
    public void markAsIndexed(String text, String batchId) {
        if (!enabled || text == null || text.isBlank()) {
            return;
        }
        
        String hash = hash(text);
        String key = buildKey(hash, batchId);
        
        localCache.add(key);
        
        // ✅ AMÉLIORER: Associer au batch
        if (batchId != null && !batchId.isBlank()) {
            trackBatchAssociation(batchId, hash);
        }
        
        markInRedisAsync(key);
        
        log.debug("✅ [Dedup] Texte marqué comme indexé: {}", truncate(text, 50));
    }
    
    /**
     * Marquage Redis asynchrone (non bloquant)
     */
    private void markInRedisAsync(String key) {
        try {
            redisTemplate.opsForValue().set(
                key, 
                "1", 
                Duration.ofDays(ttlDays)
            );
        } catch (Exception e) {
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
    
    // ========================================================================
    // ✅ NOUVELLES MÉTHODES - NETTOYAGE SÉLECTIF
    // ========================================================================
    
    /**
     * ✅ NOUVEAU: Associe un hash de texte à un batch pour tracking
     */
    private void trackBatchAssociation(String batchId, String hash) {
        try {
            String batchKey = BATCH_TEXT_PREFIX + batchId;
            redisTemplate.opsForSet().add(batchKey, hash);
            redisTemplate.expire(batchKey, Duration.ofDays(ttlDays));
        } catch (Exception e) {
            log.debug("⚠️ [Dedup] Erreur tracking batch: {}", e.getMessage());
        }
    }
    
    /**
     * ✅ NOUVEAU: Supprime UNIQUEMENT les hashs de texte d'un batch spécifique
     */
    public void removeBatch(String batchId) {
        if (!enabled || batchId == null || batchId.isBlank()) {
            return;
        }
        
        try {
            String batchKey = BATCH_TEXT_PREFIX + batchId;
            
            // Récupérer tous les hashs associés à ce batch
            Set<String> hashes = redisTemplate.opsForSet().members(batchKey);
            
            if (hashes == null || hashes.isEmpty()) {
                log.debug("ℹ️ [Dedup] Aucun hash texte trouvé pour batch: {}", batchId);
                return;
            }
            
            int deleted = 0;
            
            // Supprimer chaque hash de texte
            for (String hash : hashes) {
                String key = redisPrefix + hash;
                Boolean success = redisTemplate.delete(key);
                if (Boolean.TRUE.equals(success)) {
                    deleted++;
                    log.debug("🗑️ [Dedup] Hash texte supprimé: {}", key);
                }
            }
            
            // Supprimer la clé de mapping batch
            redisTemplate.delete(batchKey);
            
            log.info("✅ [Dedup] Batch text supprimé: {} ({} hashs)", batchId, deleted);
            
        } catch (Exception e) {
            log.error("❌ [Dedup] Erreur suppression batch texte: {}", batchId, e);
        }
    }
    
    /**
     * ✅ NOUVEAU: Nettoie TOUS les hashs de texte (tous les batches)
     */
    public void clearAll() {
        if (!enabled) {
            return;
        }
        
        try {
            log.warn("🚨 [Dedup] SUPPRESSION GLOBALE des hashs texte demandée");
            
            int totalDeleted = 0;
            
            // 1. Supprimer tous les text:dedup:*
            Set<String> dedupKeys = redisTemplate.keys(redisPrefix + "*");
            if (dedupKeys != null && !dedupKeys.isEmpty()) {
                Long deleted = redisTemplate.delete(dedupKeys);
                totalDeleted += (deleted != null ? deleted.intValue() : 0);
                log.info("✅ [Dedup] text:dedup:* → {} clés supprimées", deleted);
            }
            
            // 2. Supprimer tous les batch:text:*
            Set<String> batchKeys = redisTemplate.keys(BATCH_TEXT_PREFIX + "*");
            if (batchKeys != null && !batchKeys.isEmpty()) {
                Long deleted = redisTemplate.delete(batchKeys);
                totalDeleted += (deleted != null ? deleted.intValue() : 0);
                log.info("✅ [Dedup] batch:text:* → {} clés supprimées", deleted);
            }
            
            // 3. Vider le cache local
            clearLocalCache();
            
            log.warn("✅ [Dedup] SUPPRESSION GLOBALE terminée: {} clés Redis supprimées", totalDeleted);
            
        } catch (Exception e) {
            log.error("❌ [Dedup] Erreur clearAll", e);
        }
    }
    
    /**
     * ✅ NOUVEAU: Compte le nombre de hashs texte pour un batch
     */
    public long countBatchHashes(String batchId) {
        if (!enabled || batchId == null || batchId.isBlank()) {
            return 0;
        }
        
        try {
            String batchKey = BATCH_TEXT_PREFIX + batchId;
            Long size = redisTemplate.opsForSet().size(batchKey);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.error("❌ [Dedup] Erreur comptage batch: {}", batchId, e);
            return 0;
        }
    }
    
    // ========================================================================
    // RECORD (INCHANGÉ)
    // ========================================================================
    
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