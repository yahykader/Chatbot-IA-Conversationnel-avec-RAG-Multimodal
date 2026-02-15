package com.exemple.transactionservice.service.rag.ingestion.repository;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Slf4j
@Repository
public class EmbeddingRepository {
    
    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingStore<TextSegment> imageStore;
    private final JdbcTemplate jdbcTemplate;
    
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
            JdbcTemplate jdbcTemplate) {
        
        this.textStore = textStore;
        this.imageStore = imageStore;
        this.jdbcTemplate = jdbcTemplate;
        
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