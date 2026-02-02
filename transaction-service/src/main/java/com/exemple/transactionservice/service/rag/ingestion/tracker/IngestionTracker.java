// ============================================================================
// SERVICE - IngestionTracker.java (VERSION CORRIGÉE)
// Service de tracking des embeddings pour rollback transactionnel
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.tracker;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de tracking des embeddings pour rollback transactionnel.
 */
@Slf4j
@Service
public class IngestionTracker {

    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingStore<TextSegment> imageStore;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * Map stockant les embeddings par batchId
     * Thread-safe pour ingestions concurrentes
     */
    private final Map<String, BatchEmbeddings> batchMap = new ConcurrentHashMap<>();
    
    public IngestionTracker(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            @Qualifier("embeddingCacheRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        
        this.textStore = textStore;
        this.imageStore = imageStore;
        this.redisTemplate = redisTemplate;
        
        log.info("✅ IngestionTracker initialisé (rollback support)");
    }
    
    // ========================================================================
    // TRACKING EMBEDDINGS
    // ========================================================================
    
    /**
     * Ajoute un embedding texte au tracking
     */
    public void addTextEmbeddingId(String batchId, String embeddingId) {
        if (batchId == null || embeddingId == null) {
            return;
        }
        
        BatchEmbeddings batch = batchMap.computeIfAbsent(batchId, k -> new BatchEmbeddings());
        batch.addTextEmbedding(embeddingId);
        
        log.debug("📝 [Tracker] Text embedding ajouté: batch={} id={} (total: {})",
            batchId, embeddingId, batch.getTextEmbeddingCount());
    }
    
    /**
     * Ajoute un embedding image au tracking
     */
    public void addImageEmbeddingId(String batchId, String embeddingId) {
        if (batchId == null || embeddingId == null) {
            return;
        }
        
        BatchEmbeddings batch = batchMap.computeIfAbsent(batchId, k -> new BatchEmbeddings());
        batch.addImageEmbedding(embeddingId);
        
        log.debug("🖼️ [Tracker] Image embedding ajouté: batch={} id={} (total: {})",
            batchId, embeddingId, batch.getImageEmbeddingCount());
    }
    
    // ========================================================================
    // RÉCUPÉRATION
    // ========================================================================
    
    /**
     * Récupère tous les embeddings d'un batch
     */
    public BatchEmbeddings getBatchEmbeddings(String batchId) {
        return batchMap.get(batchId);
    }
    
    /**
     * Récupère les IDs d'embeddings texte d'un batch
     */
    public List<String> getTextEmbeddingIds(String batchId) {
        BatchEmbeddings batch = batchMap.get(batchId);
        return batch != null ? batch.getTextEmbeddingIds() : new ArrayList<>();
    }
    
    /**
     * Récupère les IDs d'embeddings image d'un batch
     */
    public List<String> getImageEmbeddingIds(String batchId) {
        BatchEmbeddings batch = batchMap.get(batchId);
        return batch != null ? batch.getImageEmbeddingIds() : new ArrayList<>();
    }
    
    // ========================================================================
    // ROLLBACK
    // ========================================================================
    
    /**
     * ✅ MÉTHODE ROLLBACKBATCH CORRIGÉE
     */
    public int rollbackBatch(String batchId) {
        log.info("🔄 [ROLLBACK] Démarrage: {}", batchId);
        
        int deletedCount = 0;
        
        try {
            BatchEmbeddings batchData = getBatchEmbeddings(batchId);
            
            if (batchData == null) {
                log.warn("⚠️ [ROLLBACK] Batch non trouvé: {}", batchId);
                return 0;
            }
            
            // ✅ CORRECTION : Utiliser les méthodes getters existantes
            List<String> textIds = batchData.getTextEmbeddingIds();
            List<String> imageIds = batchData.getImageEmbeddingIds();
            
            // Supprimer les embeddings texte
            for (String embeddingId : textIds) {
                try {
                    textStore.remove(embeddingId);
                    deletedCount++;
                } catch (Exception e) {
                    log.warn("⚠️ [ROLLBACK] Erreur suppression text: {} - {}",
                        embeddingId, e.getMessage());
                }
            }
            
            // Supprimer les embeddings image
            for (String embeddingId : imageIds) {
                try {
                    imageStore.remove(embeddingId);
                    deletedCount++;
                } catch (Exception e) {
                    log.warn("⚠️ [ROLLBACK] Erreur suppression image: {} - {}",
                        embeddingId, e.getMessage());
                }
            }
            
            // Supprimer le batch du tracking
            removeBatch(batchId);
            
            log.info("✅ [ROLLBACK] Terminé: {} - {} embeddings supprimés",
                batchId, deletedCount);
            
            return deletedCount;
            
        } catch (Exception e) {
            log.error("❌ [ROLLBACK] Erreur: {}", batchId, e);
            throw new RuntimeException("Erreur rollback batch: " + batchId, e);
        }
    }

    /**
     * Supprime un batch du tracking (Redis)
     */
    private void removeBatch(String batchId) {
        try {
            redisTemplate.delete("batch:" + batchId + ":text");
            redisTemplate.delete("batch:" + batchId + ":images");
            
            // Supprimer aussi de la map locale
            batchMap.remove(batchId);
            
            log.debug("🗑️ [ROLLBACK] Batch supprimé du tracking: {}", batchId);
            
        } catch (Exception e) {
            log.warn("⚠️ [ROLLBACK] Erreur suppression tracking: {} - {}", 
                batchId, e.getMessage());
        }
    }
    
    // ========================================================================
    // NETTOYAGE
    // ========================================================================
    
    /**
     * Nettoie un batch après succès (libère mémoire)
     */
    public void clearBatch(String batchId) {
        BatchEmbeddings batch = batchMap.remove(batchId);
        
        if (batch != null) {
            log.debug("✅ [Tracker] Batch nettoyé: {} (text: {}, images: {})",
                batchId, batch.getTextEmbeddingCount(), batch.getImageEmbeddingCount());
        }
    }
    
    /**
     * Nettoie tous les batches (attention : perte tracking!)
     */
    public void clearAll() {
        int count = batchMap.size();
        batchMap.clear();
        log.warn("⚠️ [Tracker] Tous les batches nettoyés: {} batches", count);
    }
    
    // ========================================================================
    // STATISTIQUES
    // ========================================================================
    
    /**
     * Retourne le nombre de batches en cours de tracking
     */
    public int getActiveBatchCount() {
        return batchMap.size();
    }
    
    /**
     * Retourne le nombre total d'embeddings trackés
     */
    public int getTotalEmbeddingCount() {
        return batchMap.values().stream()
            .mapToInt(batch -> batch.getTextEmbeddingCount() + batch.getImageEmbeddingCount())
            .sum();
    }
    
    /**
     * Retourne des statistiques détaillées
     */
    public TrackerStats getStats() {
        int totalText = batchMap.values().stream()
            .mapToInt(BatchEmbeddings::getTextEmbeddingCount)
            .sum();
        
        int totalImages = batchMap.values().stream()
            .mapToInt(BatchEmbeddings::getImageEmbeddingCount)
            .sum();
        
        return new TrackerStats(
            batchMap.size(),
            totalText,
            totalImages,
            totalText + totalImages
        );
    }
    
    /**
     * Log les statistiques actuelles
     */
    public void logStats() {
        TrackerStats stats = getStats();
        log.info("📊 [Tracker] Stats: {} batches actifs, {} embeddings " +
                 "(text: {}, images: {})",
            stats.activeBatches, stats.totalEmbeddings, 
            stats.textEmbeddings, stats.imageEmbeddings);
    }
    
    // ========================================================================
    // CLASSES INTERNES
    // ========================================================================
    
    /**
     * ✨ CLASSE BATCHEMBEDDINGS (MANQUAIT DANS VOTRE CODE)
     * 
     * Contient les IDs d'embeddings d'un batch pour rollback
     */
    public static class BatchEmbeddings {
        private final Set<String> textEmbeddingIds = ConcurrentHashMap.newKeySet();
        private final Set<String> imageEmbeddingIds = ConcurrentHashMap.newKeySet();
        
        public void addTextEmbedding(String id) {
            textEmbeddingIds.add(id);
        }
        
        public void addImageEmbedding(String id) {
            imageEmbeddingIds.add(id);
        }
        
        public List<String> getTextEmbeddingIds() {
            return new ArrayList<>(textEmbeddingIds);
        }
        
        public List<String> getImageEmbeddingIds() {
            return new ArrayList<>(imageEmbeddingIds);
        }
        
        public int getTextEmbeddingCount() {
            return textEmbeddingIds.size();
        }
        
        public int getImageEmbeddingCount() {
            return imageEmbeddingIds.size();
        }
    }
    
    /**
     * Record pour statistiques tracker
     */
    public record TrackerStats(
        int activeBatches,
        int textEmbeddings,
        int imageEmbeddings,
        int totalEmbeddings
    ) {}
}