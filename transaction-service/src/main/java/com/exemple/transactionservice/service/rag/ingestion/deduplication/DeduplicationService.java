// ============================================================================
// SERVICE - DeduplicationService.java (VERSION COMPLÈTE MODIFIÉE)
// Service de détection de doublons avec Redis + nouvelles méthodes
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
 * Fonctionnalités:
 * - Calcul hash SHA-256 du contenu
 * - Détection doublons via Redis
 * - TTL configurable (30 jours par défaut)
 * - Support metadata associées
 * - ✅ Récupération du batchId existant (NOUVEAU)
 * - ✅ Health check Redis (NOUVEAU)
 * 
 * Cas d'usage:
 * - Éviter ingestion multiple du même fichier
 * - Économie ressources (CPU, API calls, stockage)
 * - Cohérence base de données
 * 
 * Exemple:
 * if (dedup.isDuplicate(file)) {
 *     throw new DuplicateFileException("Fichier déjà traité");
 * }
 * dedup.markAsIngested(file, batchId);
 */
@Slf4j
@Service
public class DeduplicationService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RAGMetrics ragMetrics;
    
    private static final String REDIS_KEY_PREFIX = "ingestion:hash:";
    private static final int DEFAULT_TTL_DAYS = 30;
    
    public DeduplicationService(RedisTemplate<String, String> redisTemplate, RAGMetrics ragMetrics) {
        this.redisTemplate = redisTemplate;
        this.ragMetrics = ragMetrics;
        log.info("✅ DeduplicationService initialisé - Redis activé");
    }
    
    // ========================================================================
    // HASH CALCULATION
    // ========================================================================
    
    /**
     * Calcule le hash SHA-256 d'un fichier
     * 
     * @param file Fichier à hasher
     * @return Hash SHA-256 encodé en Base64
     * @throws IOException Si erreur lecture fichier
     */
    public String computeHash(MultipartFile file) throws IOException {
        try {
            byte[] fileBytes = file.getBytes();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(fileBytes);
            
            // Encoder en Base64 pour clé Redis lisible
            String hash = Base64.getEncoder().encodeToString(hashBytes);
            
            log.debug("🔐 [Dedup] Hash calculé: {} (file: {}, size: {} KB)", 
                hash.substring(0, 16) + "...", 
                file.getOriginalFilename(),
                String.format("%.2f", file.getSize() / 1024.0));
            
            return hash;
            
        } catch (NoSuchAlgorithmException e) {
            // Ne devrait jamais arriver (SHA-256 toujours disponible)
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
    
    // ✅ NOUVELLE MÉTHODE AJOUTÉE : Alias pour compatibilité avec les strategies
    /**
     * Calcule le hash SHA-256 d'un tableau de bytes.
     * Alias de computeHash(byte[]) pour compatibilité avec PdfIngestionStrategy.
     * 
     * @param fileBytes Bytes du fichier
     * @return Hash SHA-256 encodé en Base64
     */
    public String calculateHash(byte[] fileBytes) {
        return computeHash(fileBytes);
    }
    
    // ========================================================================
    // DUPLICATE DETECTION
    // ========================================================================
    
    /**
     * Vérifie si un fichier a déjà été ingéré
     * 
     * @param file Fichier à vérifier
     * @return true si le fichier est un doublon
     */
    public boolean isDuplicate(MultipartFile file) throws IOException {
        String hash = computeHash(file);
        return isDuplicateByHash(hash);
    }
    
    /**
     * Vérifie si un hash existe déjà dans Redis
     * 
     * @param hash Hash à vérifier
     * @return true si le hash existe (= fichier déjà ingéré)
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
    
    // ✅ NOUVELLE MÉTHODE AJOUTÉE : Alias pour compatibilité avec les strategies
    /**
     * Vérifie si un hash existe déjà (doublon).
     * Alias de isDuplicateByHash() pour compatibilité avec PdfIngestionStrategy.
     * 
     * @param fileHash Hash du fichier à vérifier
     * @return true si le hash existe déjà
     */
    public boolean isDuplicate(String fileHash) {
        return isDuplicateByHash(fileHash);
    }
    
    /**
     * Vérifie et retourne les metadata du fichier déjà ingéré
     * 
     * @param hash Hash du fichier
     * @return Metadata (batchId) ou null si pas de doublon
     */
    public String getDuplicateMetadata(String hash) {
        String key = REDIS_KEY_PREFIX + hash;
        return redisTemplate.opsForValue().get(key);
    }
    
    // ✅ NOUVELLE MÉTHODE AJOUTÉE : Récupérer le batchId existant
    /**
     * Récupère le batchId du fichier déjà ingéré.
     * Cette méthode est utilisée par le Service et Controller pour informer l'utilisateur
     * du batch existant lors d'un doublon.
     * 
     * @param fileHash Hash du fichier
     * @return batchId du fichier existant, ou null si pas trouvé
     */
    public Long getExistingBatchId(String fileHash) {
        try {
            String key = REDIS_KEY_PREFIX + fileHash;
            String batchIdStr = redisTemplate.opsForValue().get(key);
            
            if (batchIdStr != null && !batchIdStr.isBlank()) {
                try {
                    Long batchId = Long.parseLong(batchIdStr);
                    log.debug("🔍 [Dedup] BatchId récupéré: {} pour hash: {}...", 
                        batchId, fileHash.substring(0, 16));
                    return batchId;
                } catch (NumberFormatException e) {
                    // Si ce n'est pas un Long, retourner null
                    log.warn("⚠️ [Dedup] BatchId non numérique: {}", batchIdStr);
                    return null;
                }
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("❌ [Dedup] Erreur récupération batchId pour hash: {}...", 
                fileHash.substring(0, 16), e);
            return null;
        }
    }




    // ========================================================================
    // ✅ NEW: DUPLICATE DETECTION + METRICS
    // ========================================================================

    /**
     * ✅ NOUVEAU: Check duplication + enregistre la métrique rag_duplicates_total.
     * Utilise un tag "strategy" pour Grafana (sinon "unknown").
     *
     * @param fileHash hash calculé
     * @param strategyName nom stratégie (DOCX/PDF/IMAGE/TEXT...) ou null
     * @return true si duplicate
     */
    public boolean isDuplicateAndRecord(String fileHash, String strategyName) {
        boolean dup = isDuplicateByHash(fileHash);
        if (dup) {
            String strategy = (strategyName == null || strategyName.isBlank()) ? "unknown" : strategyName;
            ragMetrics.recordDuplicate(strategy);
        }
        return dup;
    }

    /**
     * ✅ NOUVEAU: checkDuplication avec metrics + batchId.
     * Très utile pour l'orchestrator (retourne hash + batch existant).
     */
    public DuplicationInfo checkDuplicationAndRecord(MultipartFile file, String strategyName) throws IOException {
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
     * Marque un fichier comme ingéré
     * 
     * @param file Fichier ingéré
     * @param batchId ID du batch d'ingestion
     */
    public void markAsIngested(MultipartFile file, String batchId) throws IOException {
        String hash = computeHash(file);
        markAsIngestedByHash(hash, batchId);
    }
    
    /**
     * Marque un hash comme ingéré avec TTL par défaut (30 jours)
     * 
     * @param hash Hash du fichier
     * @param batchId ID du batch d'ingestion
     */
    public void markAsIngestedByHash(String hash, String batchId) {
        markAsIngestedByHash(hash, batchId, DEFAULT_TTL_DAYS, TimeUnit.DAYS);
    }
    
    /**
     * Marque un hash comme ingéré avec TTL custom
     * 
     * @param hash Hash du fichier
     * @param batchId ID du batch d'ingestion
     * @param ttl Durée de vie
     * @param timeUnit Unité de temps
     */
    public void markAsIngestedByHash(
            String hash, 
            String batchId, 
            long ttl, 
            TimeUnit timeUnit) {
        
        String key = REDIS_KEY_PREFIX + hash;
        
        // Stocker batchId comme valeur (utile pour traçabilité)
        redisTemplate.opsForValue().set(key, batchId, ttl, timeUnit);
        
        log.debug("✅ [Dedup] Fichier marqué comme ingéré: hash={} batchId={} ttl={}{}",
            hash.substring(0, 16) + "...", 
            batchId,
            ttl,
            timeUnit.toString().toLowerCase());
    }
    
    // ✅ NOUVELLE MÉTHODE AJOUTÉE : Alias pour compatibilité avec les strategies
    /**
     * Enregistre un fichier comme traité.
     * Alias de markAsIngestedByHash() pour compatibilité avec PdfIngestionStrategy.
     * 
     * @param fileHash Hash du fichier
     * @param batchId ID du batch
     * @param filename Nom du fichier (pour logs, optionnel)
     */
    public void registerFile(String fileHash, Long batchId, String filename) {
        markAsIngestedByHash(fileHash, String.valueOf(batchId));
        log.debug("✅ [Dedup] Fichier enregistré: {} -> batch {}", 
            filename != null ? filename : "unknown", batchId);
    }
    
    // ========================================================================
    // CLEANUP
    // ========================================================================
    
    /**
     * Supprime un hash de Redis (annule marquage "ingéré")
     * Utile en cas d'échec d'ingestion après marquage
     * 
     * @param hash Hash à supprimer
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
     * Compte le nombre de hash stockés (approximatif)
     * Attention: scan complet peut être lent sur gros volumes
     * 
     * @return Nombre approximatif de fichiers trackés
     */
    public long countTrackedFiles() {
        try {
            // Scan avec pattern (limité aux 1000 premiers)
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
    
    // ✅ NOUVELLE MÉTHODE AJOUTÉE : Health check détaillé
    /**
     * Effectue un health check complet de Redis.
     * Teste l'écriture et la lecture pour vérifier que Redis fonctionne correctement.
     * 
     * @return true si Redis est accessible et fonctionnel
     */
    public boolean performHealthCheck() {
        try {
            String testKey = "health:check:" + System.currentTimeMillis();
            String testValue = "OK";
            
            // Test écriture
            redisTemplate.opsForValue().set(testKey, testValue, 5, TimeUnit.SECONDS);
            
            // Test lecture
            String result = redisTemplate.opsForValue().get(testKey);
            
            // Nettoyage
            redisTemplate.delete(testKey);
            
            boolean healthy = testValue.equals(result);
            
            if (healthy) {
                log.debug("✅ [Dedup] Health check OK");
            } else {
                log.warn("⚠️ [Dedup] Health check FAILED - valeur incorrecte");
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
     * Vérifie si un fichier est un doublon ET retourne les infos
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
     * Record pour les informations de duplication
     */
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
    
    // ✅ NOUVELLE SECTION AJOUTÉE : Méthodes utilitaires supplémentaires
    // ========================================================================
    // UTILITY METHODS (NOUVEAUX)
    // ========================================================================
    
    /**
     * ✅ NOUVEAU : Récupère les informations complètes d'un fichier dupliqué.
     * Utile pour fournir des détails à l'utilisateur lors d'un doublon.
     * 
     * @param fileHash Hash du fichier
     * @return Informations du fichier ou null si pas trouvé
     */
    public FileInfo getFileInfo(String fileHash) {
        try {
            if (isDuplicateByHash(fileHash)) {
                String batchId = getDuplicateMetadata(fileHash);
                Long ttl = redisTemplate.getExpire(REDIS_KEY_PREFIX + fileHash, TimeUnit.SECONDS);
                
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
     * ✅ NOUVEAU : Record pour les informations d'un fichier.
     */
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
            return ttlSeconds > 0 && ttlSeconds < 86400; // < 24h
        }
    }
    
    /**
     * ✅ NOUVEAU : Rafraîchit le TTL d'un hash existant.
     * Utile si on veut prolonger la durée de vie d'un fichier déjà traité.
     * 
     * @param fileHash Hash du fichier
     * @param ttl Nouvelle durée de vie
     * @param timeUnit Unité de temps
     * @return true si le TTL a été rafraîchi, false si le hash n'existe pas
     */
    public boolean refreshTTL(String fileHash, long ttl, TimeUnit timeUnit) {
        try {
            String key = REDIS_KEY_PREFIX + fileHash;
            
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                Boolean result = redisTemplate.expire(key, ttl, timeUnit);
                
                if (Boolean.TRUE.equals(result)) {
                    log.debug("🔄 [Dedup] TTL rafraîchi: {}... -> {}{}",
                        fileHash.substring(0, 16), ttl, timeUnit.toString().toLowerCase());
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
     * ✅ NOUVEAU : Obtient des statistiques détaillées sur le service.
     * 
     * @return Statistiques du service de déduplication
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
            return new DeduplicationStats(0, false, REDIS_KEY_PREFIX, DEFAULT_TTL_DAYS);
        }
    }
    
    /**
     * ✅ NOUVEAU : Record pour les statistiques du service.
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