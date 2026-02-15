package com.exemple.transactionservice.service.rag.ingestion.tracker;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
     * ✅ Map pour rollback (utilise BatchEmbeddings)
     * Thread-safe pour ingestions concurrentes
     */
    private final Map<String, BatchEmbeddings> batchMap = new ConcurrentHashMap<>();
    
    /**
     * ✅ Map pour info batches (utilise BatchInfo)
     * Pour méthodes CRUD
     */
    private final Map<String, BatchInfo> batches = new ConcurrentHashMap<>();
    
    public IngestionTracker(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            @Qualifier("embeddingCacheRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        
        this.textStore = textStore;
        this.imageStore = imageStore;
        this.redisTemplate = redisTemplate;
        
        log.info("✅ IngestionTracker initialisé (rollback + CRUD support)");
    }
    
    // ========================================================================
    // TRACKING EMBEDDINGS (pour rollback)
    // ========================================================================
    
    /**
     * Ajoute un embedding texte au tracking
     */
    public void addTextEmbeddingId(String batchId, String embeddingId) {
        if (batchId == null || embeddingId == null) {
            return;
        }
        
        // Pour rollback
        BatchEmbeddings batch = batchMap.computeIfAbsent(batchId, k -> new BatchEmbeddings());
        batch.addTextEmbedding(embeddingId);
        
        // Pour CRUD
        BatchInfo info = batches.get(batchId);
        if (info != null) {
            info.textEmbeddings().add(embeddingId);
        }
        
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
        
        // Pour rollback
        BatchEmbeddings batch = batchMap.computeIfAbsent(batchId, k -> new BatchEmbeddings());
        batch.addImageEmbedding(embeddingId);
        
        // Pour CRUD
        BatchInfo info = batches.get(batchId);
        if (info != null) {
            info.imageEmbeddings().add(embeddingId);
        }
        
        log.debug("🖼️ [Tracker] Image embedding ajouté: batch={} id={} (total: {})",
            batchId, embeddingId, batch.getImageEmbeddingCount());
    }
    
    // ========================================================================
    // BATCH INFO (pour CRUD)
    // ========================================================================
    
    /**
     * Enregistre un nouveau batch
     */
    public void trackBatch(String batchId, String filename, String mimeType) {
        BatchInfo info = new BatchInfo(
            batchId,
            filename,
            mimeType,
            LocalDateTime.now(),
            new ArrayList<>(),
            new ArrayList<>()
        );
        
        batches.put(batchId, info);
        log.info("📊 Batch tracké: {} - {}", batchId, filename);
    }
    
    /**
     * Récupère les informations d'un batch
     */
    public Optional<BatchInfo> getBatchInfo(String batchId) {
        return Optional.ofNullable(batches.get(batchId));
    }
    
    /**
     * Récupère tous les batches
     */
    public Map<String, BatchInfo> getAllBatches() {
        return new HashMap<>(batches);
    }
    
    // ========================================================================
    // RÉCUPÉRATION (pour rollback)
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
     * Rollback complet d'un batch
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
        batches.clear();
        log.warn("⚠️ [Tracker] Tous les batches nettoyés: {} batches", count);
    }
    
    // ========================================================================
    // CRUD OPERATIONS
    // ========================================================================
    
    /**
     * Vérifie si un batch existe
     */
    public boolean batchExists(String batchId) {
        return batches.containsKey(batchId) || batchMap.containsKey(batchId);
    }
    
    /**
     * Supprime un batch du tracker
     */
    public void removeBatch(String batchId) {
        batches.remove(batchId);
        batchMap.remove(batchId);
        log.info("📊 Batch supprimé du tracker: {}", batchId);
    }
    
    /**
     * Nombre de batches trackés
     */
    public int getBatchCount() {
        return batches.size();
    }
    
    /**
     * Nombre total d'embeddings trackés (depuis BatchInfo)
     */
    public int getTotalEmbeddings() {
        return batches.values().stream()
            .mapToInt(batch -> batch.textEmbeddings().size() + batch.imageEmbeddings().size())
            .sum();
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
     * Retourne le nombre total d'embeddings trackés (depuis BatchEmbeddings)
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
     * Record pour stocker les informations d'un batch (CRUD)
     */
    public record BatchInfo(
        String batchId,
        String filename,
        String mimeType,
        LocalDateTime timestamp,
        List<String> textEmbeddings,
        List<String> imageEmbeddings
    ) {}
    
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