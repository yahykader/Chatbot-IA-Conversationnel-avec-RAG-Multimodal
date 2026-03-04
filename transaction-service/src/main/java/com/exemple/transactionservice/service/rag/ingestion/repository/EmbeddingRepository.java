package com.exemple.transactionservice.service.rag.ingestion.repository;

import com.exemple.transactionservice.service.rag.ingestion.deduplication.DeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.deduplication.TextDeduplicationService;
import com.exemple.transactionservice.service.rag.ingestion.tracker.IngestionTracker;
import com.exemple.transactionservice.service.rag.ingestion.cache.EmbeddingCache;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.data.redis.core.RedisTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Slf4j
@Repository
public class EmbeddingRepository {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingStore<TextSegment> imageStore;
    private final JdbcTemplate jdbcTemplate;
    private final IngestionTracker tracker;
    private final EmbeddingCache embeddingCache;
    private final DeduplicationService deduplicationService;
    private final TextDeduplicationService textDeduplicationService;

    
    @Value("${embedding.text.dimension:1536}")
    private int textEmbeddingDimension;
    
    @Value("${embedding.image.dimension:512}")
    private int imageEmbeddingDimension;
    
    @Value("${pgvector.text.table:text_embeddings}")
    private String textTableName;
    
    @Value("${pgvector.image.table:image_embeddings}")
    private String imageTableName;
    
    
    public EmbeddingRepository(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            JdbcTemplate jdbcTemplate,
            RedisTemplate<String, String> redisTemplate,
            EmbeddingCache embeddingCache,
            DeduplicationService deduplicationService,
            TextDeduplicationService textDeduplicationService,
            IngestionTracker tracker) {
        
        this.textStore = textStore;
        this.imageStore = imageStore;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.tracker = tracker;
        this.embeddingCache = embeddingCache;
        this.deduplicationService = deduplicationService;
        this.textDeduplicationService = textDeduplicationService;
 
        
        log.info("✅ EmbeddingRepository initialisé (SQL direct mode)");
    }
    
    // ========================================================================
    // DUMMY EMBEDDINGS (gardés pour compatibilité mais non utilisés)
    // ========================================================================
    
    private Embedding createDummyTextEmbedding() {
        float[] zeros = new float[textEmbeddingDimension];
        Arrays.fill(zeros, 0.0f);
        return new Embedding(zeros);
    }
    
    private Embedding createDummyImageEmbedding() {
        float[] zeros = new float[imageEmbeddingDimension];
        Arrays.fill(zeros, 0.0f);
        return new Embedding(zeros);
    }
    
    // ========================================================================
    // MÉTHODES DE SUPPRESSION INDIVIDUELLES
    // ========================================================================
    
    public boolean deleteText(String embeddingId) {
        try {
            log.info("🗑️ Suppression embedding texte: {}", embeddingId);
            textStore.remove(embeddingId);
            log.info("✅ Embedding texte supprimé: {}", embeddingId);
            return true;
        } catch (Exception e) {
            log.error("❌ Erreur suppression embedding texte: {}", embeddingId, e);
            return false;
        }
    }
    
    public boolean deleteImage(String embeddingId) {
        try {
            log.info("🗑️ Suppression embedding image: {}", embeddingId);
            imageStore.remove(embeddingId);
            log.info("✅ Embedding image supprimé: {}", embeddingId);
            return true;
        } catch (Exception e) {
            log.error("❌ Erreur suppression embedding image: {}", embeddingId, e);
            return false;
        }
    }
    
    // ========================================================================
    // SUPPRESSION PAR BATCH ID
    // ========================================================================
    
    public int deleteTextByBatchId(String batchId) {
        try {
            log.info("🗑️ Suppression embeddings texte du batch: {}", batchId);
            
            List<String> idsToDelete = findTextIdsByBatchId(batchId);
            
            if (idsToDelete.isEmpty()) {
                log.warn("⚠️ Aucun embedding texte trouvé pour batch: {}", batchId);
                return 0;
            }
            
            int deleted = 0;
            for (String id : idsToDelete) {
                if (deleteText(id)) {
                    deleted++;
                }
            }
            
            log.info("✅ {} embeddings texte supprimés du batch: {}", deleted, batchId);
            return deleted;
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression embeddings texte du batch: {}", batchId, e);
            return 0;
        }
    }
    
    public int deleteImageByBatchId(String batchId) {
        try {
            log.info("🗑️ Suppression embeddings image du batch: {}", batchId);
            
            List<String> idsToDelete = findImageIdsByBatchId(batchId);
            
            if (idsToDelete.isEmpty()) {
                log.warn("⚠️ Aucun embedding image trouvé pour batch: {}", batchId);
                return 0;
            }
            
            int deleted = 0;
            for (String id : idsToDelete) {
                if (deleteImage(id)) {
                    deleted++;
                }
            }
            
            log.info("✅ {} embeddings image supprimés du batch: {}", deleted, batchId);
            return deleted;
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression embeddings image du batch: {}", batchId, e);
            return 0;
        }
    }
    
    // ========================================================================
    // SUPPRESSION PAR LISTE D'IDS
    // ========================================================================
    
    public int deleteTextBatch(List<String> embeddingIds) {
        try {
            log.info("🗑️ Suppression batch de {} embeddings texte", embeddingIds.size());
            
            int deleted = 0;
            for (String id : embeddingIds) {
                if (deleteText(id)) {
                    deleted++;
                }
            }
            
            log.info("✅ {} embeddings texte supprimés", deleted);
            return deleted;
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression batch embeddings texte", e);
            return 0;
        }
    }
    
    public int deleteImageBatch(List<String> embeddingIds) {
        try {
            log.info("🗑️ Suppression batch de {} embeddings image", embeddingIds.size());
            
            int deleted = 0;
            for (String id : embeddingIds) {
                if (deleteImage(id)) {
                    deleted++;
                }
            }
            
            log.info("✅ {} embeddings image supprimés", deleted);
            return deleted;
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression batch embeddings image", e);
            return 0;
        }
    }



        /**
     * ✅ MODIFIÉ: Supprime batch + TOUS les caches Redis
     */
    public int deleteBatch(String batchId) {
        try {
            log.info("🗑️ Suppression batch: {}", batchId);
            
            int totalDeleted = 0;
            
            // 1. Supprimer les embeddings texte
            int textDeleted = this.deleteTextByBatchId(batchId);
            log.info("📝 Embeddings texte supprimés: {}", textDeleted);
            totalDeleted += textDeleted;
            
            // 2. Supprimer les embeddings image
            int imageDeleted = this.deleteImageByBatchId(batchId);
            log.info("🖼️ Embeddings image supprimés: {}", imageDeleted);
            totalDeleted += imageDeleted;
            
            // 3. Supprimer du tracker
            tracker.removeBatch(batchId);
            log.info("📊 Batch supprimé du tracker");
            
            // 4. ✅ AMÉLIORER: Nettoyer TOUS les caches Redis
            cleanupRedisCaches(batchId);
            
            log.info("✅ Batch supprimé: {} - Total: {} embeddings", 
                batchId, totalDeleted);
            
            return totalDeleted;
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression batch: {}", batchId, e);
            throw new RuntimeException("Erreur suppression batch: " + e.getMessage(), e);
        }
    }

    
    // ========================================================================
    // ✅ MÉTHODES CRUD - MODIFIÉES
    // ========================================================================
    
    /**
     * Vérifie si un batch existe (INCHANGÉE)
     */
    public boolean batchExists(String batchId) {
        try {
            boolean existsInTracker = tracker.batchExists(batchId);
            
            if (existsInTracker) {
                log.debug("✅ Batch trouvé dans tracker: {}", batchId);
                return true;
            }
            
            Map<String, Integer> stats = getBatchStats(batchId);
            boolean existsInQdrant = stats.get("textEmbeddings") > 0 || 
                                     stats.get("imageEmbeddings") > 0;
            
            if (existsInQdrant) {
                log.debug("✅ Batch trouvé dans Qdrant: {}", batchId);
                return true;
            }
            
            log.debug("⚠️ Batch non trouvé: {}", batchId);
            return false;
            
        } catch (Exception e) {
            log.error("❌ Erreur vérification existence batch: {}", batchId, e);
            return false;
        }
    }
    
    /**
     * Récupère les statistiques d'un batch (INCHANGÉE)
     */
    public Map<String, Integer> getBatchStats(String batchId) {
        Map<String, Integer> stats = new HashMap<>();
        
        try {
            int textCount = this.countTextByBatchId(batchId);
            int imageCount = this.countImageByBatchId(batchId);
            
            stats.put("textEmbeddings", textCount);
            stats.put("imageEmbeddings", imageCount);
            
            log.debug("📊 Stats batch {}: text={}, images={}", 
                batchId, textCount, imageCount);
            
            return stats;
            
        } catch (Exception e) {
            log.error("❌ Erreur récupération stats batch: {}", batchId, e);
            stats.put("textEmbeddings", 0);
            stats.put("imageEmbeddings", 0);
            return stats;
        }
    }
    

    
    /**
     * ✅ NOUVELLE MÉTHODE: Nettoie TOUS les caches Redis pour un batch
     */
    private void cleanupRedisCaches(String batchId) {
        try {
            log.info("🧹 [Redis] Nettoyage complet des caches pour batch: {}", batchId);
            
            // 1. DeduplicationService: ingestion:hash:*
            try {
                deduplicationService.removeBatch(batchId);
                log.info("🔍 Batch supprimé de la déduplication fichiers");
            } catch (Exception e) {
                log.error("❌ Erreur nettoyage DeduplicationService", e);
            }
            
            // 2. ✅ TextDeduplicationService: text:dedup:* + batch:text:*
            try {
                textDeduplicationService.removeBatch(batchId);
                log.info("📝 Batch supprimé du cache de déduplication texte");
            } catch (Exception e) {
                log.error("❌ Erreur nettoyage TextDeduplicationService", e);
            }
            
            // 3. ✅ EmbeddingCache: emb:* + batch:emb:*
            try {
                embeddingCache.removeBatch(batchId);
                log.info("💾 Batch supprimé du cache d'embeddings");
            } catch (Exception e) {
                log.error("❌ Erreur nettoyage EmbeddingCache", e);
            }
            
            log.info("✅ [Redis] Nettoyage complet terminé pour batch: {}", batchId);
            
        } catch (Exception e) {
            log.error("❌ [Redis] Erreur nettoyage caches pour batch: {}", batchId, e);
        }
    }
    
    // ========================================================================
    // ✅ SUPPRESSION GLOBALE - VERSION SQL DIRECT
    // ========================================================================
    
    public int deleteAllFilesPlusCache() {
        try {
            log.warn("🚨 SUPPRESSION GLOBALE COMPLÈTE DEMANDÉE");
            
            int totalDeleted = 0;
            
            // 1. Supprimer PostgreSQL (embeddings texte + images)
            log.info("🗑️ Suppression PostgreSQL...");
            totalDeleted = this.deleteAllFiles();
            log.info("✅ {} embeddings supprimés de PostgreSQL", totalDeleted);
            
            // 2. Nettoyer TOUS les caches Redis
            log.info("🗑️ Nettoyage complet Redis...");
            cleanupAllRedisCaches();

            // 3. ✅ AJOUT: Nettoyer rate-limits
            cleanupAllRateLimits();
            
            // 4. Nettoyer le tracker (mémoire)
            log.info("🗑️ Nettoyage tracker (mémoire)...");
            tracker.clearAll();
            log.info("✅ Tracker nettoyé");
            
            log.warn("✅ SUPPRESSION GLOBALE TERMINÉE: {} embeddings + tous les caches", 
                totalDeleted);
            
            return totalDeleted;
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression globale complète", e);
            throw new RuntimeException("Erreur suppression globale: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NOUVEAU: Nettoie TOUS les caches Redis (tous les batches)
     */
    private void cleanupAllRedisCaches() {
        try {
            log.info("🧹 [Redis] Nettoyage GLOBAL du tracker et de tous les caches");
            
            // 1. Tracker mémoire
            tracker.clearAll();
            log.info("✅ Tracker nettoyé");
            
            // 1. DeduplicationService: ingestion:hash:*
            try {
                deduplicationService.clearAll();
                log.info("✅ Cache déduplication fichiers nettoyé (ingestion:hash:*)");
            } catch (Exception e) {
                log.error("❌ Erreur nettoyage DeduplicationService", e);
            }
            
            // 2. TextDeduplicationService: text:dedup:* + batch:text:*
            try {
                textDeduplicationService.clearAll();
                log.info("✅ Cache déduplication texte nettoyé (text:dedup:* + batch:text:*)");
            } catch (Exception e) {
                log.error("❌ Erreur nettoyage TextDeduplicationService", e);
            }
            
            // 3. EmbeddingCache: emb:* + batch:emb:*
            try {
                embeddingCache.clearAll();
                log.info("✅ Cache embeddings nettoyé (emb:* + batch:emb:*)");
            } catch (Exception e) {
                log.error("❌ Erreur nettoyage EmbeddingCache", e);
            }
            
            log.info("✅ [Redis] Nettoyage GLOBAL terminé");
            
        } catch (Exception e) {
            log.error("❌ [Redis] Erreur nettoyage global caches", e);
        }
    }

    // Puis ajouter la méthode cleanupAllRateLimits()
    private void cleanupAllRateLimits() {
        try {
            log.info("🚦 [Redis] Nettoyage rate-limits...");
            
            Set<String> rateLimitKeys = redisTemplate.keys("rate-limit:*");
            if (rateLimitKeys != null && !rateLimitKeys.isEmpty()) {
                Long deleted = redisTemplate.delete(rateLimitKeys);
                log.info("✅ {} rate-limits supprimés", deleted);
            } else {
                log.info("ℹ️ Aucun rate-limit à supprimer");
            }
            
        } catch (Exception e) {
            log.warn("⚠️ Erreur nettoyage rate-limits (non bloquant): {}", e.getMessage());
        }
    }


        /**
     * ✅ AMÉLIORER: Nettoie tout le tracking + TOUS les caches Redis
     */
    public void clearAllTracking() {
        try {
            log.warn("🗑️ Nettoyage complet du tracker et des caches");
            
            // 1. Tracker mémoire
            tracker.clearAll();
            log.info("✅ Tracker nettoyé");
            
            // 2. ✅ Tous les caches Redis
            try {
                deduplicationService.clearAll();
                log.info("✅ DeduplicationService nettoyé");
            } catch (Exception e) {
                log.error("❌ Erreur clearAll DeduplicationService", e);
            }
            
            try {
                textDeduplicationService.clearAll();
                log.info("✅ TextDeduplicationService nettoyé");
            } catch (Exception e) {
                log.error("❌ Erreur clearAll TextDeduplicationService", e);
            }
            
            try {
                embeddingCache.clear();
                log.info("✅ EmbeddingCache nettoyé");
            } catch (Exception e) {
                log.error("❌ Erreur clear EmbeddingCache", e);
            }
            
        } catch (Exception e) {
            log.error("❌ Erreur nettoyage complet", e);
        }
    }

     // ========================================================================
    // ✅ SUPPRESSION GLOBALE - VERSION SQL DIRECT
    // ========================================================================
    
    public int deleteAllFiles() {
        try {
            log.warn("🚨 SUPPRESSION GLOBALE DEMANDÉE");
            
            int totalDeleted = 0;
            
            // 1. Supprimer tous les embeddings texte
            log.info("🗑️ Suppression embeddings texte...");
            int textDeleted = deleteAllTextEmbeddingsViaSql();
            log.info("✅ {} embeddings texte supprimés", textDeleted);
            totalDeleted += textDeleted;
            
            // 2. Supprimer tous les embeddings image
            log.info("🗑️ Suppression embeddings image...");
            int imageDeleted = deleteAllImageEmbeddingsViaSql();
            log.info("✅ {} embeddings image supprimés", imageDeleted);
            totalDeleted += imageDeleted;
            
            log.warn("✅ SUPPRESSION GLOBALE TERMINÉE: {} embeddings supprimés", totalDeleted);
            
            return totalDeleted;
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression globale", e);
            return 0;
        }
    }
    
    private int deleteAllTextEmbeddingsViaSql() {
        try {
            String selectSql = String.format("SELECT embedding_id FROM %s", textTableName);
            List<String> allIds = jdbcTemplate.queryForList(selectSql, String.class);
            
            if (allIds.isEmpty()) {
                log.info("ℹ️ Aucun embedding texte à supprimer");
                return 0;
            }
            
            log.info("📊 {} embeddings texte trouvés", allIds.size());
            
            int deleted = 0;
            int batchSize = 100;
            
            for (int i = 0; i < allIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allIds.size());
                List<String> batch = allIds.subList(i, end);
                
                for (String id : batch) {
                    try {
                        textStore.remove(id);
                        deleted++;
                    } catch (Exception e) {
                        log.warn("⚠️ Erreur suppression texte {}: {}", id, e.getMessage());
                    }
                }
                
                if (deleted % 500 == 0 && deleted > 0) {
                    log.info("⏳ Progression texte: {}/{}", deleted, allIds.size());
                }
            }
            
            return deleted;
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression tous embeddings texte", e);
            return 0;
        }
    }
    
    private int deleteAllImageEmbeddingsViaSql() {
        try {
            String selectSql = String.format("SELECT embedding_id FROM %s", imageTableName);
            List<String> allIds = jdbcTemplate.queryForList(selectSql, String.class);
            
            if (allIds.isEmpty()) {
                log.info("ℹ️ Aucun embedding image à supprimer");
                return 0;
            }
            
            log.info("📊 {} embeddings image trouvés", allIds.size());
            
            int deleted = 0;
            int batchSize = 100;
            
            for (int i = 0; i < allIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allIds.size());
                List<String> batch = allIds.subList(i, end);
                
                for (String id : batch) {
                    try {
                        imageStore.remove(id);
                        deleted++;
                    } catch (Exception e) {
                        log.warn("⚠️ Erreur suppression image {}: {}", id, e.getMessage());
                    }
                }
                
                if (deleted % 500 == 0 && deleted > 0) {
                    log.info("⏳ Progression image: {}/{}", deleted, allIds.size());
                }
            }
            
            return deleted;
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression tous embeddings image", e);
            return 0;
        }
    }
    
    // ========================================================================
    // MÉTHODES UTILITAIRES - SQL DIRECT
    // ========================================================================
    
    private List<String> findTextIdsByBatchId(String batchId) {
        try {
            String sql = String.format(
                "SELECT embedding_id FROM %s WHERE metadata->>'batchId' = ?",
                textTableName
            );
            
            List<String> ids = jdbcTemplate.queryForList(sql, String.class, batchId);
            log.debug("📊 {} IDs texte trouvés pour batch: {}", ids.size(), batchId);
            return ids;
            
        } catch (Exception e) {
            log.error("❌ Erreur recherche IDs texte pour batch: {}", batchId, e);
            return Collections.emptyList();
        }
    }
    
    private List<String> findImageIdsByBatchId(String batchId) {
        try {
            String sql = String.format(
                "SELECT embedding_id FROM %s WHERE metadata->>'batchId' = ?",
                imageTableName
            );
            
            List<String> ids = jdbcTemplate.queryForList(sql, String.class, batchId);
            log.debug("📊 {} IDs image trouvés pour batch: {}", ids.size(), batchId);
            return ids;
            
        } catch (Exception e) {
            log.error("❌ Erreur recherche IDs image pour batch: {}", batchId, e);
            return Collections.emptyList();
        }
    }
    
    public int countTextByBatchId(String batchId) {
        try {
            String sql = String.format(
                "SELECT COUNT(*) FROM %s WHERE metadata->>'batchId' = ?",
                textTableName
            );
            
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, batchId);
            log.debug("📊 Count texte pour batch {}: {}", batchId, count);
            return count != null ? count : 0;
            
        } catch (Exception e) {
            log.error("❌ Erreur comptage texte pour batch: {}", batchId, e);
            return 0;
        }
    }
    
    public int countImageByBatchId(String batchId) {
        try {
            String sql = String.format(
                "SELECT COUNT(*) FROM %s WHERE metadata->>'batchId' = ?",
                imageTableName
            );
            
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, batchId);
            log.debug("📊 Count image pour batch {}: {}", batchId, count);
            return count != null ? count : 0;
            
        } catch (Exception e) {
            log.error("❌ Erreur comptage image pour batch: {}", batchId, e);
            return 0;
        }
    }
    
    public int countAllText() {
        try {
            String sql = String.format("SELECT COUNT(*) FROM %s", textTableName);
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("❌ Erreur comptage total embeddings texte", e);
            return 0;
        }
    }
    
    public int countAllImage() {
        try {
            String sql = String.format("SELECT COUNT(*) FROM %s", imageTableName);
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("❌ Erreur comptage total embeddings image", e);
            return 0;
        }
    }
}