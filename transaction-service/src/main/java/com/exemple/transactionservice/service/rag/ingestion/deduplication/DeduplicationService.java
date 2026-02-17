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
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service de déduplication de fichiers basé sur le hash SHA-256.
 * Utilise Redis pour stocker les hash des fichiers déjà ingérés.
 * 
 * VERSION AVEC SUPPORT UUID STRING + CRUD + NETTOYAGE SÉLECTIF
 */
@Slf4j
@Service
public class DeduplicationService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RAGMetrics ragMetrics;
    
    private static final String REDIS_KEY_PREFIX = "ingestion:hash:";
    private static final int DEFAULT_TTL_DAYS = 30;
    private static final String FILE_HASH_PREFIX = REDIS_KEY_PREFIX;
    
    public DeduplicationService(
            RedisTemplate<String, String> redisTemplate, 
            RAGMetrics ragMetrics) {
        this.redisTemplate = redisTemplate;
        this.ragMetrics = ragMetrics;
        log.info("✅ DeduplicationService initialisé - Redis activé (UUID support + Nettoyage sélectif)");
    }
    
    // ========================================================================
    // HASH CALCULATION (INCHANGÉ)
    // ========================================================================
    
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
    
    public String computeHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    public String calculateHash(byte[] fileBytes) {
        return computeHash(fileBytes);
    }
    
    // ========================================================================
    // DUPLICATE DETECTION (INCHANGÉ)
    // ========================================================================
    
    public boolean isDuplicate(MultipartFile file) throws IOException {
        String hash = computeHash(file);
        return isDuplicateByHash(hash);
    }
    
    public boolean isDuplicateByHash(String hash) {
        String key = REDIS_KEY_PREFIX + hash;
        Boolean exists = redisTemplate.hasKey(key);
        
        if (Boolean.TRUE.equals(exists)) {
            log.info("⚠️ [Dedup] Doublon détecté: hash={}", hash.substring(0, 16) + "...");
            return true;
        }
        
        return false;
    }
    
    public boolean isDuplicate(String fileHash) {
        return isDuplicateByHash(fileHash);
    }
    
    public String getDuplicateMetadata(String hash) {
        String key = REDIS_KEY_PREFIX + hash;
        return redisTemplate.opsForValue().get(key);
    }
    
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
    // DUPLICATE DETECTION + METRICS (INCHANGÉ)
    // ========================================================================
    
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
    // MARKING AS INGESTED (INCHANGÉ)
    // ========================================================================
    
    public void markAsIngested(MultipartFile file, String batchId) throws IOException {
        String hash = computeHash(file);
        markAsIngestedByHash(hash, batchId);
    }
    
    public void markAsIngestedByHash(String hash, String batchId) {
        markAsIngestedByHash(hash, batchId, DEFAULT_TTL_DAYS, TimeUnit.DAYS);
    }
    
    public void markAsIngestedByHash(
            String hash, 
            String batchId, 
            long ttl, 
            TimeUnit timeUnit) {
        
        String key = REDIS_KEY_PREFIX + hash;
        redisTemplate.opsForValue().set(key, batchId, ttl, timeUnit);
        
        log.debug("✅ [Dedup] Fichier marqué comme ingéré: hash={} batchId={} ttl={}{}",
            hash.substring(0, 16) + "...", 
            batchId,
            ttl,
            timeUnit.toString().toLowerCase());
    }
    
    public void registerFile(String fileHash, Long batchId, String filename) {
        markAsIngestedByHash(fileHash, String.valueOf(batchId));
        log.debug("✅ [Dedup] Fichier enregistré: {} -> batch {}", 
            filename != null ? filename : "unknown", batchId);
    }
    
    public void registerFile(String fileHash, String batchId, String filename) {
        markAsIngestedByHash(fileHash, batchId);
        log.debug("✅ [Dedup] Fichier enregistré: {} -> batch {}", 
            filename != null ? filename : "unknown", batchId);
    }
    
    // ========================================================================
    // CLEANUP - ✅ VERSION AMÉLIORÉE
    // ========================================================================
    
    /**
     * ✅ MODIFIÉ: Nettoie TOUT SAUF rate-limit
     */
    public void clearAll() {
        try {
            log.warn("🚨 [Redis] SUPPRESSION GLOBALE des caches demandée");
            
            int totalDeleted = 0;
            
            String[] patterns = {
                FILE_HASH_PREFIX + "*",
                "text:dedup:*",
                "emb:*",
                "text:*"
            };
            
            for (String pattern : patterns) {
                Set<String> keys = redisTemplate.keys(pattern);
                
                if (keys != null && !keys.isEmpty()) {
                    keys.removeIf(key -> key.startsWith("rate-limit:"));
                    
                    if (!keys.isEmpty()) {
                        Long deleted = redisTemplate.delete(keys);
                        totalDeleted += (deleted != null ? deleted.intValue() : 0);
                        log.info("✅ [Redis] Pattern '{}': {} clés supprimées", pattern, deleted);
                    }
                }
            }
            
            log.warn("✅ [Redis] SUPPRESSION GLOBALE terminée: {} clés supprimées", totalDeleted);
            
        } catch (Exception e) {
            log.error("❌ [Redis] Erreur suppression globale", e);
        }
    }
    
    public void removeHash(String hash) {
        String key = REDIS_KEY_PREFIX + hash;
        Boolean deleted = redisTemplate.delete(key);
        
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("🗑️ [Dedup] Hash supprimé: {}", hash.substring(0, 16) + "...");
        }
    }
    
    public void removeFile(MultipartFile file) throws IOException {
        String hash = computeHash(file);
        removeHash(hash);
    }
    
    /**
     * ✅ MODIFIÉ: Nettoyage sélectif (seulement ingestion:hash:* du batch)
     * Les caches text:dedup:* et emb:* sont gérés par leurs services respectifs
     */
    public void removeBatch(String batchId) {
        try {
            log.info("🗑️ [Redis] Nettoyage sélectif pour batch: {}", batchId);
            
            // Supprimer SEULEMENT ingestion:hash:* qui contient ce batchId
            int deleted = deleteHashKeysForBatch(batchId);
            
            log.info("✅ [Redis] Batch supprimé: {} ({} fichier(s) hash supprimé(s))", batchId, deleted);
            
        } catch (Exception e) {
            log.error("❌ [Redis] Erreur suppression batch: {}", batchId, e);
        }
    }
    
    /**
     * ✅ NOUVELLE MÉTHODE: Supprime uniquement ingestion:hash:* du batch
     */
    private int deleteHashKeysForBatch(String batchId) {
        try {
            Set<String> keys = redisTemplate.keys(FILE_HASH_PREFIX + "*");
            
            if (keys == null || keys.isEmpty()) {
                log.debug("ℹ️ [Redis] Aucune clé ingestion:hash:* trouvée");
                return 0;
            }
            
            int deleted = 0;
            
            for (String key : keys) {
                String value = redisTemplate.opsForValue().get(key);
                
                // ✅ Supprimer SEULEMENT si la valeur correspond exactement au batchId
                if (batchId.equals(value)) {
                    Boolean success = redisTemplate.delete(key);
                    if (Boolean.TRUE.equals(success)) {
                        deleted++;
                        log.debug("🗑️ [Redis] Clé supprimée: {}", key);
                    }
                }
            }
            
            if (deleted > 0) {
                log.info("✅ [Redis] Pattern 'ingestion:hash:*': {} clés supprimées", deleted);
            }
            
            return deleted;
            
        } catch (Exception e) {
            log.error("❌ [Redis] Erreur suppression hashs batch: {}", e.getMessage());
            return 0;
        }
    }
    
    // ========================================================================
    // STATISTICS (INCHANGÉ)
    // ========================================================================
    
    public long countTrackedFiles() {
        try {
            var keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.warn("⚠️ [Dedup] Erreur comptage: {}", e.getMessage());
            return -1;
        }
    }
    
    public boolean isRedisAvailable() {
        try {
            redisTemplate.opsForValue().get("ping");
            return true;
        } catch (Exception e) {
            log.error("❌ [Dedup] Redis inaccessible: {}", e.getMessage());
            return false;
        }
    }
    
    public boolean isHealthy() {
        return isRedisAvailable();
    }
    
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
    // ADVANCED FEATURES (INCHANGÉ)
    // ========================================================================
    
    public DuplicationInfo checkDuplication(MultipartFile file) throws IOException {
        String hash = computeHash(file);
        
        if (isDuplicateByHash(hash)) {
            String batchId = getDuplicateMetadata(hash);
            return new DuplicationInfo(true, hash, batchId);
        }
        
        return new DuplicationInfo(false, hash, null);
    }
    
    public record DuplicationInfo(
        boolean isDuplicate,
        String hash,
        String originalBatchId
    ) {
        public String getShortHash() {
            return hash != null && hash.length() >= 16 
                ? hash.substring(0, 16) + "..." 
                : hash;
        }
    }
    
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
    
    public record FileInfo(
        String hash,
        String batchId,
        long ttlSeconds
    ) {
        public String getShortHash() {
            return hash != null && hash.length() >= 16 
                ? hash.substring(0, 16) + "..." 
                : hash;
        }
        
        public boolean isExpiringSoon() {
            return ttlSeconds > 0 && ttlSeconds < 86400;
        }
    }
    
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