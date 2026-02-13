// ============================================================================
// SERVICE - DeduplicationService.java (VERSION UUID COMPLÈTE)
// Service de détection de doublons avec Redis + support UUID
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.deduplication;

import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Service de déduplication de fichiers basé sur le hash SHA-256.
 * Utilise Redis pour stocker les hash des fichiers déjà ingérés.
 * 
 * ✅ VERSION AVEC SUPPORT UUID STRING
 * 
 * Fonctionnalités:
 * - Calcul hash SHA-256 du contenu
 * - Détection doublons via Redis
 * - TTL configurable (30 jours par défaut)
 * - Support metadata associées
 * - Support UUID String ET Long pour batchId
 * - Health check Redis
 * 
 * @author RAG Team
 * @version 2.0 (UUID Support)
 */
@Slf4j
@Service
public class DeduplicationService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RAGMetrics ragMetrics;
    
    private static final String REDIS_KEY_PREFIX = "ingestion:hash:";
    private static final int DEFAULT_TTL_DAYS = 30;
    
    public DeduplicationService(
            RedisTemplate<String, String> redisTemplate, 
            RAGMetrics ragMetrics) {
        this.redisTemplate = redisTemplate;
        this.ragMetrics = ragMetrics;
        log.info("✅ DeduplicationService initialisé - Redis activé (UUID support)");
    }
    
    // ========================================================================
    // HASH CALCULATION
    // ========================================================================
    
    /**
     * Calcule le hash SHA-256 d'un fichier
     */
    public String computeHash(MultipartFile file) throws IOException {
        try {
            byte[] fileBytes = file.getBytes();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(fileBytes);
            
            String hash = Base64.getEncoder().encodeToString(hashBytes);
            
            log.debug("🔐 [Dedup] Hash calculé: {} (file: {}, size: {} KB)", 
                hash.substring(0, 16) + "...", 
                file.getOriginalFilename(),
                String.format("%.2f", file.getSize() / 1024.0));
            
            return hash;
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Calcule le hash SHA-256 de bytes
     */
    public String computeHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Alias pour compatibilité
     */
    public String calculateHash(byte[] fileBytes) {
        return computeHash(fileBytes);
    }
    
    // ========================================================================
    // DUPLICATE DETECTION
    // ========================================================================
    
    /**
     * Vérifie si un fichier a déjà été ingéré
     */
    public boolean isDuplicate(MultipartFile file) throws IOException {
        String hash = computeHash(file);
        return isDuplicateByHash(hash);
    }
    
    /**
     * Vérifie si un hash existe déjà dans Redis
     */
    public boolean isDuplicateByHash(String hash) {
        String key = REDIS_KEY_PREFIX + hash;
        Boolean exists = redisTemplate.hasKey(key);
        
        if (Boolean.TRUE.equals(exists)) {
            log.info("⚠️ [Dedup] Doublon détecté: hash={}", hash.substring(0, 16) + "...");
            return true;
        }
        
        return false;
    }
    
    /**
     * Alias pour compatibilité
     */
    public boolean isDuplicate(String fileHash) {
        return isDuplicateByHash(fileHash);
    }
    
    /**
     * ✅ MISE À JOUR: Retourne le batchId en String (UUID ou numérique)
     */
    public String getDuplicateMetadata(String hash) {
        String key = REDIS_KEY_PREFIX + hash;
        return redisTemplate.opsForValue().get(key);
    }
    
    /**
     * ✅ MISE À JOUR: Récupère le batchId existant (String)
     * 
     * @param fileHash Hash du fichier
     * @return batchId du fichier existant (UUID String), ou null si pas trouvé
     */
    public String getExistingBatchId(String fileHash) {
        try {
            String key = REDIS_KEY_PREFIX + fileHash;
            String batchId = redisTemplate.opsForValue().get(key);
            
            if (batchId != null && !batchId.isBlank()) {
                log.debug("🔍 [Dedup] BatchId récupéré: {} pour hash: {}...", 
                    batchId, fileHash.substring(0, 16));
                return batchId;
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("❌ [Dedup] Erreur récupération batchId pour hash: {}...", 
                fileHash.substring(0, 16), e);
            return null;
        }
    }
    
    /**
     * ✅ OBSOLÈTE: Gardé pour compatibilité mais déprécié
     * Utilisez getExistingBatchId() qui retourne String
     * 
     * @deprecated Utilisez getExistingBatchId() à la place
     */
    @Deprecated
    public Long getExistingBatchIdAsLong(String fileHash) {
        try {
            String batchIdStr = getExistingBatchId(fileHash);
            
            if (batchIdStr != null) {
                try {
                    return Long.parseLong(batchIdStr);
                } catch (NumberFormatException e) {
                    log.warn("⚠️ [Dedup] BatchId non numérique: {}", batchIdStr);
                    return null;
                }
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("❌ [Dedup] Erreur récupération batchId Long", e);
            return null;
        }
    }
    
    // ========================================================================
    // DUPLICATE DETECTION + METRICS
    // ========================================================================
    
    /**
     * ✅ Check duplication + enregistre la métrique
     */
    public boolean isDuplicateAndRecord(String fileHash, String strategyName) {
        boolean dup = isDuplicateByHash(fileHash);
        if (dup) {
            String strategy = (strategyName == null || strategyName.isBlank()) 
                ? "unknown" 
                : strategyName;
            ragMetrics.recordDuplicate(strategy);
        }
        return dup;
    }
    
    /**
     * ✅ MISE À JOUR: Retourne String batchId dans DuplicationInfo
     */
    public DuplicationInfo checkDuplicationAndRecord(
            MultipartFile file, 
            String strategyName) throws IOException {
        
        String hash = computeHash(file);
        
        if (isDuplicateAndRecord(hash, strategyName)) {
            String batchId = getDuplicateMetadata(hash);
            return new DuplicationInfo(true, hash, batchId);
        }
        
        return new DuplicationInfo(false, hash, null);
    }
    
    // ========================================================================
    // MARKING AS INGESTED
    // ========================================================================
    
    /**
     * ✅ MISE À JOUR: Accepte batchId String (UUID)
     */
    public void markAsIngested(MultipartFile file, String batchId) throws IOException {
        String hash = computeHash(file);
        markAsIngestedByHash(hash, batchId);
    }
    
    /**
     * ✅ MISE À JOUR: batchId en String
     */
    public void markAsIngestedByHash(String hash, String batchId) {
        markAsIngestedByHash(hash, batchId, DEFAULT_TTL_DAYS, TimeUnit.DAYS);
    }
    
    /**
     * ✅ MISE À JOUR: batchId en String avec TTL custom
     */
    public void markAsIngestedByHash(
            String hash, 
            String batchId, 
            long ttl, 
            TimeUnit timeUnit) {
        
        String key = REDIS_KEY_PREFIX + hash;
        
        // Stocker batchId comme valeur (String: UUID ou numérique)
        redisTemplate.opsForValue().set(key, batchId, ttl, timeUnit);
        
        log.debug("✅ [Dedup] Fichier marqué comme ingéré: hash={} batchId={} ttl={}{}",
            hash.substring(0, 16) + "...", 
            batchId,
            ttl,
            timeUnit.toString().toLowerCase());
    }
    
    /**
     * ✅ SURCHARGE: Accepte Long batchId (convertit en String)
     * Pour compatibilité avec ancien code
     */
    public void registerFile(String fileHash, Long batchId, String filename) {
        markAsIngestedByHash(fileHash, String.valueOf(batchId));
        log.debug("✅ [Dedup] Fichier enregistré: {} -> batch {}", 
            filename != null ? filename : "unknown", batchId);
    }
    
    /**
     * ✅ NOUVEAU: Accepte String batchId directement
     */
    public void registerFile(String fileHash, String batchId, String filename) {
        markAsIngestedByHash(fileHash, batchId);
        log.debug("✅ [Dedup] Fichier enregistré: {} -> batch {}", 
            filename != null ? filename : "unknown", batchId);
    }
    
    // ========================================================================
    // CLEANUP
    // ========================================================================
    
    /**
     * Supprime un hash de Redis
     */
    public void removeHash(String hash) {
        String key = REDIS_KEY_PREFIX + hash;
        Boolean deleted = redisTemplate.delete(key);
        
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("🗑️ [Dedup] Hash supprimé: {}", hash.substring(0, 16) + "...");
        }
    }
    
    /**
     * Supprime le marquage d'un fichier
     */
    public void removeFile(MultipartFile file) throws IOException {
        String hash = computeHash(file);
        removeHash(hash);
    }
    
    // ========================================================================
    // STATISTICS
    // ========================================================================
    
    /**
     * Compte le nombre de hash stockés
     */
    public long countTrackedFiles() {
        try {
            var keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.warn("⚠️ [Dedup] Erreur comptage: {}", e.getMessage());
            return -1;
        }
    }
    
    /**
     * Vérifie si Redis est accessible
     */
    public boolean isRedisAvailable() {
        try {
            redisTemplate.opsForValue().get("ping");
            return true;
        } catch (Exception e) {
            log.error("❌ [Dedup] Redis inaccessible: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Health check complet
     */
    public boolean performHealthCheck() {
        try {
            String testKey = "health:check:" + System.currentTimeMillis();
            String testValue = "OK";
            
            redisTemplate.opsForValue().set(testKey, testValue, 5, TimeUnit.SECONDS);
            String result = redisTemplate.opsForValue().get(testKey);
            redisTemplate.delete(testKey);
            
            boolean healthy = testValue.equals(result);
            
            if (healthy) {
                log.debug("✅ [Dedup] Health check OK");
            } else {
                log.warn("⚠️ [Dedup] Health check FAILED");
            }
            
            return healthy;
            
        } catch (Exception e) {
            log.error("❌ [Dedup] Health check FAILED: {}", e.getMessage());
            return false;
        }
    }
    
    // ========================================================================
    // ADVANCED FEATURES
    // ========================================================================
    
    /**
     * Vérifie duplication et retourne infos complètes
     */
    public DuplicationInfo checkDuplication(MultipartFile file) throws IOException {
        String hash = computeHash(file);
        
        if (isDuplicateByHash(hash)) {
            String batchId = getDuplicateMetadata(hash);
            return new DuplicationInfo(true, hash, batchId);
        }
        
        return new DuplicationInfo(false, hash, null);
    }
    
    /**
     * ✅ MISE À JOUR: batchId en String
     */
    public record DuplicationInfo(
        boolean isDuplicate,
        String hash,
        String originalBatchId  // ✅ String (UUID ou numérique)
    ) {
        public String getShortHash() {
            return hash != null && hash.length() >= 16 
                ? hash.substring(0, 16) + "..." 
                : hash;
        }
    }
    
    /**
     * Récupère infos complètes d'un fichier dupliqué
     */
    public FileInfo getFileInfo(String fileHash) {
        try {
            if (isDuplicateByHash(fileHash)) {
                String batchId = getDuplicateMetadata(fileHash);
                Long ttl = redisTemplate.getExpire(
                    REDIS_KEY_PREFIX + fileHash, 
                    TimeUnit.SECONDS
                );
                
                return new FileInfo(
                    fileHash,
                    batchId,
                    ttl != null ? ttl : -1
                );
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("❌ [Dedup] Erreur récupération info fichier", e);
            return null;
        }
    }
    
    /**
     * ✅ MISE À JOUR: batchId en String
     */
    public record FileInfo(
        String hash,
        String batchId,  // ✅ String (UUID ou numérique)
        long ttlSeconds
    ) {
        public String getShortHash() {
            return hash != null && hash.length() >= 16 
                ? hash.substring(0, 16) + "..." 
                : hash;
        }
        
        public boolean isExpiringSoon() {
            return ttlSeconds > 0 && ttlSeconds < 86400; // < 24h
        }
    }
    
    /**
     * Rafraîchit le TTL d'un hash
     */
    public boolean refreshTTL(String fileHash, long ttl, TimeUnit timeUnit) {
        try {
            String key = REDIS_KEY_PREFIX + fileHash;
            
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                Boolean result = redisTemplate.expire(key, ttl, timeUnit);
                
                if (Boolean.TRUE.equals(result)) {
                    log.debug("🔄 [Dedup] TTL rafraîchi: {}... -> {}{}",
                        fileHash.substring(0, 16), ttl, 
                        timeUnit.toString().toLowerCase());
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("❌ [Dedup] Erreur rafraîchissement TTL", e);
            return false;
        }
    }
    
    /**
     * Statistiques du service
     */
    public DeduplicationStats getStats() {
        try {
            long trackedFiles = countTrackedFiles();
            boolean redisAvailable = isRedisAvailable();
            
            return new DeduplicationStats(
                trackedFiles,
                redisAvailable,
                REDIS_KEY_PREFIX,
                DEFAULT_TTL_DAYS
            );
            
        } catch (Exception e) {
            log.error("❌ [Dedup] Erreur récupération stats", e);
            return new DeduplicationStats(
                0, 
                false, 
                REDIS_KEY_PREFIX, 
                DEFAULT_TTL_DAYS
            );
        }
    }
    
    /**
     * Record pour statistiques
     */
    public record DeduplicationStats(
        long trackedFiles,
        boolean redisAvailable,
        String redisKeyPrefix,
        int defaultTtlDays
    ) {
        public boolean isHealthy() {
            return redisAvailable;
        }
    }
}