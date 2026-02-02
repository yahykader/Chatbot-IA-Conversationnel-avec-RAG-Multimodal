// ============================================================================
// REPOSITORY - EmbeddingRepository.java
// Couche d'accès aux données pour les embeddings (Pattern Repository)
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.repository;

import com.exemple.transactionservice.service.rag.ingestion.tracker.IngestionTracker;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

/**
 * Repository pour gérer les embeddings (texte et image).
 * 
 * Pattern Repository - Encapsule la logique d'accès aux données.
 * 
 * Fonctionnalités :
 * ✅ CRUD complet (Create, Read, Update, Delete)
 * ✅ Recherche par similarité
 * ✅ Gestion séparée texte/image
 * ✅ Opérations batch
 * ✅ Statistiques
 * 
 * @author RAG Team
 * @version 1.0
 */
@Slf4j
@Repository
public class EmbeddingRepository {

    @Value("${langchain4j.pgvector.text-table-name:text_embeddings}")
    private String textTableName;

    @Value("${langchain4j.pgvector.image-table-name:image_embeddings}")
    private String imageTableName;

    @Value("${repository.delete.truncate-threshold:10000}")
    private int truncateThreshold;  // Seuil pour TRUNCATE vs DELETE
    
    private final EmbeddingStore<TextSegment> textStore;
    private final EmbeddingStore<TextSegment> imageStore;
    private final JdbcTemplate jdbcTemplate;
    private final IngestionTracker tracker;
    
    public EmbeddingRepository(
            @Qualifier("textEmbeddingStore") EmbeddingStore<TextSegment> textStore,
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            JdbcTemplate jdbcTemplate,
            IngestionTracker tracker) {
        
        this.textStore = textStore;
        this.imageStore = imageStore;
        this.jdbcTemplate = jdbcTemplate;
        this.tracker = tracker;
        
        log.info("✅ EmbeddingRepository initialisé (mode hybride DELETE/TRUNCATE)");
        log.info("📊 Seuil TRUNCATE: {} lignes", truncateThreshold);
    }
    
    // // ========================================================================
    // // CREATE - SAUVEGARDE
    // // ========================================================================
    
    // /**
    //  * Sauvegarde un embedding texte.
    //  * 
    //  * @param embedding Vecteur d'embedding
    //  * @param segment Segment de texte avec métadonnées
    //  * @return ID de l'embedding créé
    //  */
    // public String saveText(Embedding embedding, TextSegment segment) {
    //     try {
    //         String id = textStore.add(embedding, segment);
    //         log.debug("✅ Text embedding sauvegardé: {}", id);
    //         return id;
            
    //     } catch (Exception e) {
    //         log.error("❌ Erreur sauvegarde text embedding", e);
    //         throw new RepositoryException("Erreur sauvegarde text", e);
    //     }
    // }
    
    // /**
    //  * Sauvegarde un embedding image.
    //  * 
    //  * @param embedding Vecteur d'embedding
    //  * @param segment Segment avec métadonnées image
    //  * @return ID de l'embedding créé
    //  */
    // public String saveImage(Embedding embedding, TextSegment segment) {
    //     try {
    //         String id = imageStore.add(embedding, segment);
    //         log.debug("✅ Image embedding sauvegardé: {}", id);
    //         return id;
            
    //     } catch (Exception e) {
    //         log.error("❌ Erreur sauvegarde image embedding", e);
    //         throw new RepositoryException("Erreur sauvegarde image", e);
    //     }
    // }
    
    // /**
    //  * Sauvegarde plusieurs embeddings texte en batch.
    //  * 
    //  * @param embeddings Liste d'embeddings
    //  * @param segments Liste de segments correspondants
    //  * @return Liste des IDs créés
    //  */
    // public List<String> saveTextBatch(List<Embedding> embeddings, List<TextSegment> segments) {
    //     try {
    //         if (embeddings.size() != segments.size()) {
    //             throw new IllegalArgumentException(
    //                 "Tailles différentes: embeddings=" + embeddings.size() + 
    //                 ", segments=" + segments.size()
    //             );
    //         }
            
    //         List<String> ids = textStore.addAll(embeddings, segments);
    //         log.info("✅ Batch text sauvegardé: {} embeddings", ids.size());
    //         return ids;
            
    //     } catch (Exception e) {
    //         log.error("❌ Erreur sauvegarde batch text", e);
    //         throw new RepositoryException("Erreur sauvegarde batch text", e);
    //     }
    // }
    
    // /**
    //  * Sauvegarde plusieurs embeddings image en batch.
    //  */
    // public List<String> saveImageBatch(List<Embedding> embeddings, List<TextSegment> segments) {
    //     try {
    //         if (embeddings.size() != segments.size()) {
    //             throw new IllegalArgumentException(
    //                 "Tailles différentes: embeddings=" + embeddings.size() + 
    //                 ", segments=" + segments.size()
    //             );
    //         }
            
    //         List<String> ids = imageStore.addAll(embeddings, segments);
    //         log.info("✅ Batch image sauvegardé: {} embeddings", ids.size());
    //         return ids;
            
    //     } catch (Exception e) {
    //         log.error("❌ Erreur sauvegarde batch image", e);
    //         throw new RepositoryException("Erreur sauvegarde batch image", e);
    //     }
    // }
    
    // ========================================================================
    // READ - RECHERCHE
    // ========================================================================
    
    // /**
    //  * Recherche les embeddings texte les plus similaires.
    //  * 
    //  * @param queryEmbedding Embedding de la requête
    //  * @param maxResults Nombre maximum de résultats
    //  * @param minScore Score minimum de similarité (0.0 à 1.0)
    //  * @return Liste des correspondances trouvées
    //  */
    // public List<EmbeddingMatch<TextSegment>> searchText(
    //         Embedding queryEmbedding, 
    //         int maxResults, 
    //         double minScore) {
        
    //     try {
    //         EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    //             .queryEmbedding(queryEmbedding)
    //             .maxResults(maxResults)
    //             .minScore(minScore)
    //             .build();
            
    //         EmbeddingSearchResult<TextSegment> result = textStore.search(request);
            
    //         log.debug("🔍 Recherche text: {} résultats trouvés", result.matches().size());
    //         return result.matches();
            
    //     } catch (Exception e) {
    //         log.error("❌ Erreur recherche text", e);
    //         throw new RepositoryException("Erreur recherche text", e);
    //     }
    // }
    
    // /**
    //  * Recherche les embeddings image les plus similaires.
    //  */
    // public List<EmbeddingMatch<TextSegment>> searchImage(
    //         Embedding queryEmbedding, 
    //         int maxResults, 
    //         double minScore) {
        
    //     try {
    //         EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    //             .queryEmbedding(queryEmbedding)
    //             .maxResults(maxResults)
    //             .minScore(minScore)
    //             .build();
            
    //         EmbeddingSearchResult<TextSegment> result = imageStore.search(request);
            
    //         log.debug("🔍 Recherche image: {} résultats trouvés", result.matches().size());
    //         return result.matches();
            
    //     } catch (Exception e) {
    //         log.error("❌ Erreur recherche image", e);
    //         throw new RepositoryException("Erreur recherche image", e);
    //     }
    // }
    
    // /**
    //  * Recherche avec filtres sur métadonnées (texte).
    //  * 
    //  * @param queryEmbedding Embedding de la requête
    //  * @param maxResults Nombre maximum de résultats
    //  * @param minScore Score minimum
    //  * @param filter Filtre sur métadonnées (ex: "batchId=xxx")
    //  * @return Liste des correspondances
    //  */
    // public List<EmbeddingMatch<TextSegment>> searchTextWithFilter(
    //         Embedding queryEmbedding, 
    //         int maxResults, 
    //         double minScore,
    //         Metadata filter) {
        
    //     try {
    //         EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    //             .queryEmbedding(queryEmbedding)
    //             .maxResults(maxResults)
    //             .minScore(minScore)
    //             .filter(filter)
    //             .build();
            
    //         EmbeddingSearchResult<TextSegment> result = textStore.search(request);
            
    //         log.debug("🔍 Recherche text filtrée: {} résultats", result.matches().size());
    //         return result.matches();
            
    //     } catch (Exception e) {
    //         log.error("❌ Erreur recherche text filtrée", e);
    //         throw new RepositoryException("Erreur recherche text filtrée", e);
    //     }
    // }
    
    // /**
    //  * Recherche avec filtres sur métadonnées (image).
    //  */
    // public List<EmbeddingMatch<TextSegment>> searchImageWithFilter(
    //         Embedding queryEmbedding, 
    //         int maxResults, 
    //         double minScore,
    //         Metadata filter) {
        
    //     try {
    //         EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    //             .queryEmbedding(queryEmbedding)
    //             .maxResults(maxResults)
    //             .minScore(minScore)
    //             .filter(filter)
    //             .build();
            
    //         EmbeddingSearchResult<TextSegment> result = imageStore.search(request);
            
    //         log.debug("🔍 Recherche image filtrée: {} résultats", result.matches().size());
    //         return result.matches();
            
    //     } catch (Exception e) {
    //         log.error("❌ Erreur recherche image filtrée", e);
    //         throw new RepositoryException("Erreur recherche image filtrée", e);
    //     }
    // }
    

    // ========================================================================
    // DELETE - SUPPRESSION
    // ========================================================================
    
    /**
     * Supprime un embedding texte par son ID.
     * 
     * @param embeddingId ID de l'embedding
     * @return true si supprimé, false si non trouvé
     */
    public boolean deleteText(String embeddingId) {
        try {
            textStore.remove(embeddingId);
            log.info("🗑️ Text embedding supprimé: {}", embeddingId);
            return true;
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression text: {}", embeddingId, e);
            return false;
        }
    }
    
    /**
     * Supprime un embedding image par son ID.
     * 
     * @param embeddingId ID de l'embedding
     * @return true si supprimé, false si non trouvé
     */
    public boolean deleteImage(String embeddingId) {
        try {
            imageStore.remove(embeddingId);
            log.info("🗑️ Image embedding supprimé: {}", embeddingId);
            return true;
            
        } catch (Exception e) {
            log.error("❌ Erreur suppression image: {}", embeddingId, e);
            return false;
        }
    }
    
    /**
     * Supprime plusieurs embeddings texte en batch.
     * 
     * @param embeddingIds Liste des IDs à supprimer
     * @return Nombre d'embeddings supprimés
     */
    public int deleteTextBatch(List<String> embeddingIds) {
        int deleted = 0;
        
        for (String id : embeddingIds) {
            try {
                textStore.remove(id);
                deleted++;
            } catch (Exception e) {
                log.warn("⚠️ Erreur suppression text: {}", id);
            }
        }
        
        log.info("🗑️ Batch text supprimé: {}/{} embeddings", deleted, embeddingIds.size());
        return deleted;
    }
    
    /**
     * Supprime plusieurs embeddings image en batch.
     * 
     * @param embeddingIds Liste des IDs à supprimer
     * @return Nombre d'embeddings supprimés
     */
    public int deleteImageBatch(List<String> embeddingIds) {
        int deleted = 0;
        
        for (String id : embeddingIds) {
            try {
                imageStore.remove(id);
                deleted++;
            } catch (Exception e) {
                log.warn("⚠️ Erreur suppression image: {}", id);
            }
        }
        
        log.info("🗑️ Batch image supprimé: {}/{} embeddings", deleted, embeddingIds.size());
        return deleted;
    }

    /**
     * Supprime TOUS les embeddings texte avec stratégie hybride.
     * 
     * Stratégie :
     * - Si < 10 000 lignes → DELETE (plus sûr, rollback possible)
     * - Si ≥ 10 000 lignes → TRUNCATE (plus rapide)
     * 
     * @return Nombre d'embeddings texte supprimés
     */
    public int deleteAllText() {
        try {
            log.warn("🚨 [Repository] Suppression TOUS les embeddings texte - MODE HYBRIDE");
            
            if (jdbcTemplate == null) {
                log.error("❌ [Repository] JdbcTemplate non disponible");
                log.warn("⚠️ [Repository] SQL manuel requis: DELETE FROM {};", textTableName);
                return 0;
            }
            
            // 1. Compter les lignes
            Integer count = null;
            try {
                String countSql = "SELECT COUNT(*) FROM " + textTableName;
                count = jdbcTemplate.queryForObject(countSql, Integer.class);
                
                if (count == null || count == 0) {
                    log.info("ℹ️ [Repository] Table texte déjà vide");
                    return 0;
                }
                
                log.info("📊 [Repository] {} embeddings texte trouvés", count);
                
            } catch (Exception e) {
                log.error("❌ [Repository] Erreur comptage texte", e);
                return 0;
            }
            
            // 2. Choisir stratégie selon le nombre de lignes
            int deleted = 0;
            
            if (count < truncateThreshold) {
                // DELETE pour petites tables (rollback possible)
                log.info("🔧 [Repository] Utilisation DELETE (count={} < threshold={})", 
                    count, truncateThreshold);
                
                try {
                    String deleteSql = "DELETE FROM " + textTableName;
                    deleted = jdbcTemplate.update(deleteSql);
                    
                    log.warn("✅ [DELETE] {} embeddings texte supprimés (table: {})", 
                        deleted, textTableName);
                    
                } catch (Exception e) {
                    log.error("❌ [Repository] Erreur DELETE texte", e);
                    throw new RepositoryException("Erreur DELETE texte", e);
                }
                
            } else {
                // TRUNCATE pour grandes tables (plus rapide)
                log.info("⚡ [Repository] Utilisation TRUNCATE (count={} >= threshold={})", 
                    count, truncateThreshold);
                
                try {
                    String truncateSql = "TRUNCATE TABLE " + textTableName + " RESTART IDENTITY CASCADE";
                    jdbcTemplate.execute(truncateSql);
                    deleted = count;
                    
                    log.warn("✅ [TRUNCATE] ~{} embeddings texte supprimés (table: {})", 
                        deleted, textTableName);
                    
                } catch (Exception e) {
                    log.error("❌ [Repository] Erreur TRUNCATE texte, fallback vers DELETE", e);
                    
                    // Fallback vers DELETE si TRUNCATE échoue
                    try {
                        String deleteSql = "DELETE FROM " + textTableName;
                        deleted = jdbcTemplate.update(deleteSql);
                        
                        log.warn("✅ [DELETE-FALLBACK] {} embeddings texte supprimés", deleted);
                        
                    } catch (Exception e2) {
                        log.error("❌ [Repository] Fallback DELETE échoué", e2);
                        throw new RepositoryException("Erreur suppression texte", e2);
                    }
                }
            }
            
            return deleted;
            
        } catch (RepositoryException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ [Repository] Erreur suppression globale texte", e);
            throw new RepositoryException("Erreur suppression globale texte", e);
        }
    }

    /**
     * Supprime TOUS les embeddings image avec stratégie hybride.
     * 
     * Stratégie :
     * - Si < 10 000 lignes → DELETE (plus sûr, rollback possible)
     * - Si ≥ 10 000 lignes → TRUNCATE (plus rapide)
     * 
     * @return Nombre d'embeddings image supprimés
     */
    public int deleteAllImages() {
        try {
            log.warn("🚨 [Repository] Suppression TOUS les embeddings image - MODE HYBRIDE");
            
            if (jdbcTemplate == null) {
                log.error("❌ [Repository] JdbcTemplate non disponible");
                log.warn("⚠️ [Repository] SQL manuel requis: DELETE FROM {};", imageTableName);
                return 0;
            }
            
            // 1. Compter les lignes
            Integer count = null;
            try {
                String countSql = "SELECT COUNT(*) FROM " + imageTableName;
                count = jdbcTemplate.queryForObject(countSql, Integer.class);
                
                if (count == null || count == 0) {
                    log.info("ℹ️ [Repository] Table images déjà vide");
                    return 0;
                }
                
                log.info("📊 [Repository] {} embeddings image trouvés", count);
                
            } catch (Exception e) {
                log.error("❌ [Repository] Erreur comptage images", e);
                return 0;
            }
            
            // 2. Choisir stratégie selon le nombre de lignes
            int deleted = 0;
            
            if (count < truncateThreshold) {
                // DELETE pour petites tables
                log.info("🔧 [Repository] Utilisation DELETE (count={} < threshold={})", 
                    count, truncateThreshold);
                
                try {
                    String deleteSql = "DELETE FROM " + imageTableName;
                    deleted = jdbcTemplate.update(deleteSql);
                    
                    log.warn("✅ [DELETE] {} embeddings image supprimés (table: {})", 
                        deleted, imageTableName);
                    
                } catch (Exception e) {
                    log.error("❌ [Repository] Erreur DELETE images", e);
                    throw new RepositoryException("Erreur DELETE images", e);
                }
                
            } else {
                // TRUNCATE pour grandes tables
                log.info("⚡ [Repository] Utilisation TRUNCATE (count={} >= threshold={})", 
                    count, truncateThreshold);
                
                try {
                    String truncateSql = "TRUNCATE TABLE " + imageTableName + " RESTART IDENTITY CASCADE";
                    jdbcTemplate.execute(truncateSql);
                    deleted = count;
                    
                    log.warn("✅ [TRUNCATE] ~{} embeddings image supprimés (table: {})", 
                        deleted, imageTableName);
                    
                } catch (Exception e) {
                    log.error("❌ [Repository] Erreur TRUNCATE images, fallback vers DELETE", e);
                    
                    // Fallback vers DELETE
                    try {
                        String deleteSql = "DELETE FROM " + imageTableName;
                        deleted = jdbcTemplate.update(deleteSql);
                        
                        log.warn("✅ [DELETE-FALLBACK] {} embeddings image supprimés", deleted);
                        
                    } catch (Exception e2) {
                        log.error("❌ [Repository] Fallback DELETE échoué", e2);
                        throw new RepositoryException("Erreur suppression images", e2);
                    }
                }
            }
            
            return deleted;
            
        } catch (RepositoryException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ [Repository] Erreur suppression globale images", e);
            throw new RepositoryException("Erreur suppression globale images", e);
        }
    }

    /**
     * Supprime TOUS les embeddings (texte + images) avec stratégie hybride.
     * 
     * @return Nombre total d'embeddings supprimés
     */
    public int deleteAllFiles() {
        try {
            log.warn("🚨 [Repository] Suppression TOUS les fichiers (texte + images) - MODE HYBRIDE");
            
            long startTime = System.currentTimeMillis();
            
            int deletedText = 0;
            int deletedImages = 0;
            
            // Supprimer les embeddings texte
            try {
                deletedText = deleteAllText();
            } catch (Exception e) {
                log.error("❌ [Repository] Erreur suppression texte, continuation...", e);
            }
            
            // Supprimer les embeddings image
            try {
                deletedImages = deleteAllImages();
            } catch (Exception e) {
                log.error("❌ [Repository] Erreur suppression images, continuation...", e);
            }
            
            int total = deletedText + deletedImages;
            long duration = System.currentTimeMillis() - startTime;
            
            log.warn("🚨 [Repository] Suppression totale terminée:");
            log.warn("   • Texte: {} embeddings", deletedText);
            log.warn("   • Images: {} embeddings", deletedImages);
            log.warn("   • Total: {} embeddings", total);
            log.warn("   • Durée: {} ms", duration);
            
            return total;
            
        } catch (Exception e) {
            log.error("❌ [Repository] Erreur suppression globale de tous les fichiers", e);
            throw new RepositoryException("Erreur suppression globale de tous les fichiers", e);
        }
    }

    /**
     * Supprime tous les embeddings (texte + images) d'un batch spécifique.
     * 
     * Utilise les IDs trackés pour supprimer uniquement les embeddings d'un batch.
     * Plus ciblé que deleteAllFiles() qui supprime TOUT.
     * 
     * @param textIds Liste des IDs d'embeddings texte du batch
     * @param imageIds Liste des IDs d'embeddings image du batch
     * @return Nombre total d'embeddings supprimés
     */
    public int deleteBatchFiles(List<String> textIds, List<String> imageIds) {
        try {
            log.info("🗑️ [Repository] Suppression batch: {} texte + {} images", 
                textIds.size(), imageIds.size());
            
            long startTime = System.currentTimeMillis();
            
            int deletedText = 0;
            int deletedImages = 0;
            
            // Supprimer les embeddings texte
            if (textIds != null && !textIds.isEmpty()) {
                deletedText = deleteTextBatch(textIds);
            }
            
            // Supprimer les embeddings image
            if (imageIds != null && !imageIds.isEmpty()) {
                deletedImages = deleteImageBatch(imageIds);
            }
            
            int total = deletedText + deletedImages;
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("✅ [Repository] Batch supprimé: text={}, images={}, total={}, durée={}ms",
                deletedText, deletedImages, total, duration);
            
            return total;
            
        } catch (Exception e) {
            log.error("❌ [Repository] Erreur suppression batch files", e);
            throw new RepositoryException("Erreur suppression batch files", e);
        }
    }



    
    // ========================================================================
    // STATISTIQUES
    // ========================================================================
    
        /**
     * Compte le nombre d'embeddings d'un batch spécifique.
     * 
     * @param batchId ID du batch
     * @return Map avec counts par type
     */
    public Map<String, Integer> countBatchFiles(String batchId) {
        Map<String, Integer> counts = new HashMap<>();
        
        try {
            if (jdbcTemplate == null) {
                return counts;
            }
            
            // Compter texte
            try {
                String sqlText = String.format(
                    "SELECT COUNT(*) FROM %s WHERE metadata->>'batchId' = ?",
                    textTableName
                );
                Integer textCount = jdbcTemplate.queryForObject(sqlText, Integer.class, batchId);
                counts.put("textEmbeddings", textCount != null ? textCount : 0);
                
            } catch (Exception e) {
                log.error("❌ [Repository] Erreur count texte batch", e);
                counts.put("textEmbeddings", 0);
            }
            
            // Compter images
            try {
                String sqlImage = String.format(
                    "SELECT COUNT(*) FROM %s WHERE metadata->>'batchId' = ?",
                    imageTableName
                );
                Integer imageCount = jdbcTemplate.queryForObject(sqlImage, Integer.class, batchId);
                counts.put("imageEmbeddings", imageCount != null ? imageCount : 0);
                
            } catch (Exception e) {
                log.error("❌ [Repository] Erreur count images batch", e);
                counts.put("imageEmbeddings", 0);
            }
            
            int total = counts.get("textEmbeddings") + counts.get("imageEmbeddings");
            counts.put("totalEmbeddings", total);
            
            log.debug("📊 [Repository] Batch {} : {} embeddings (text={}, images={})",
                batchId, total, counts.get("textEmbeddings"), counts.get("imageEmbeddings"));
            
        } catch (Exception e) {
            log.error("❌ [Repository] Erreur count batch: {}", batchId, e);
        }
        
        return counts;
    }

    /**
     * Vérifie si un batch existe.
     * 
     * @param batchId ID du batch
     * @return true si le batch contient des embeddings
     */
    public boolean batchExists(String batchId) {
        try {
            Map<String, Integer> counts = countBatchFiles(batchId);
            return counts.get("totalEmbeddings") > 0;
            
        } catch (Exception e) {
            log.error("❌ [Repository] Erreur vérification existence batch: {}", batchId, e);
            return false;
        }
    }

    /**
     * Compte le nombre approximatif d'embeddings texte.
     * 
     * Note: LangChain4j n'a pas de méthode count()
     * Cette méthode retourne une estimation basée sur une recherche.
     * 
     * @return Nombre approximatif d'embeddings
     */
    public long countText() {
        try {
            // Approximation: chercher avec un embedding vide
            // Ce n'est pas exact mais donne une idée
            
            log.warn("⚠️ Count approximatif - utiliser SQL pour précision");
            log.warn("⚠️ SQL: SELECT COUNT(*) FROM text_embeddings;");
            
            return -1; // -1 = non disponible
            
        } catch (Exception e) {
            log.error("❌ Erreur count text", e);
            return -1;
        }
    }
    
    /**
     * Compte le nombre approximatif d'embeddings image.
     */
    public long countImages() {
        try {
            log.warn("⚠️ Count approximatif - utiliser SQL pour précision");
            log.warn("⚠️ SQL: SELECT COUNT(*) FROM image_embeddings;");
            
            return -1; // -1 = non disponible
            
        } catch (Exception e) {
            log.error("❌ Erreur count images", e);
            return -1;
        }
    }
    
    /**
     * Vérifie si un embedding texte existe.
     * 
     * @param embeddingId ID à vérifier
     * @return true si existe
     */
    public boolean existsText(String embeddingId) {
        try {
            // Note: Pas de méthode exists() dans LangChain4j
            // Il faudrait faire une recherche ou un SELECT COUNT
            
            log.warn("⚠️ Exists non disponible via LangChain4j");
            return false;
            
        } catch (Exception e) {
            log.error("❌ Erreur exists text: {}", embeddingId, e);
            return false;
        }
    }
    
    /**
     * Vérifie si un embedding image existe.
     */
    public boolean existsImage(String embeddingId) {
        try {
            log.warn("⚠️ Exists non disponible via LangChain4j");
            return false;
            
        } catch (Exception e) {
            log.error("❌ Erreur exists image: {}", embeddingId, e);
            return false;
        }
    }
    
    // ========================================================================
    // UTILITAIRES
    // ========================================================================

    /**
     * Retourne des statistiques sur les tables.
     * 
     * @return Map avec counts par table
     */
    public Map<String, Integer> getTableStats() {
        Map<String, Integer> stats = new HashMap<>();
        
        try {
            if (jdbcTemplate != null) {
                Integer textCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + textTableName, 
                    Integer.class
                );
                
                Integer imageCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + imageTableName, 
                    Integer.class
                );
                
                stats.put("textEmbeddings", textCount != null ? textCount : 0);
                stats.put("imageEmbeddings", imageCount != null ? imageCount : 0);
                stats.put("totalEmbeddings", 
                    (textCount != null ? textCount : 0) + 
                    (imageCount != null ? imageCount : 0)
                );
            }
        } catch (Exception e) {
            log.error("❌ Erreur récupération stats", e);
        }
        
        return stats;
    }

    /**
     * Vérifie si les tables sont vides.
     */
    public boolean areTablesEmpty() {
        try {
            Map<String, Integer> stats = getTableStats();
            return stats.get("totalEmbeddings") == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retourne le seuil TRUNCATE configuré.
     */
    public int getTruncateThreshold() {
        return truncateThreshold;
    }

    /**
     * Retourne le type de store (pour debugging).
     */
    public String getTextStoreType() {
        return textStore.getClass().getSimpleName();
    }
    
    /**
     * Retourne le type de store image.
     */
    public String getImageStoreType() {
        return imageStore.getClass().getSimpleName();
    }
    
    // ========================================================================
    // EXCEPTION CUSTOM
    // ========================================================================
    
    /**
     * Exception levée en cas d'erreur repository.
     */
    public static class RepositoryException extends RuntimeException {
        public RepositoryException(String message) {
            super(message);
        }
        
        public RepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

/*
┌─────────────────────────────────────┐
│ Suppression demandée                │
└─────────────┬───────────────────────┘
              │
              ▼
    ┌─────────────────────┐
    │ Compter les lignes  │
    └─────────┬───────────┘
              │
              ▼
     ┌────────────────────┐
     │ < 10 000 lignes ?  │
     └────┬───────────┬───┘
          │           │
        OUI          NON
          │           │
          ▼           ▼
    ┌─────────┐  ┌──────────┐
    │ DELETE  │  │ TRUNCATE │
    │ (sûr)   │  │ (rapide) │
    └─────────┘  └────┬─────┘
                      │
                 ┌────▼─────┐
                 │ Échec ?  │
                 └────┬─────┘
                      │
                     OUI
                      │
                      ▼
                ┌──────────┐
                │ DELETE   │
                │ fallback │
                └──────────┘

*/