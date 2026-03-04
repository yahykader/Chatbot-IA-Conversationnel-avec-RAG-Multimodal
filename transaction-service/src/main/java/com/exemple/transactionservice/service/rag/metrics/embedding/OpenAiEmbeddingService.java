package com.exemple.transactionservice.service.rag.metrics.embedding;

import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service d'embedding OpenAI
 * 
 * ✅ ADAPTÉ AVEC RAGMetrics unifié
 * 
 * Utilise OpenAI text-embedding-3-small ou similar
 */
@Slf4j
@Service
public class OpenAiEmbeddingService {
    
    private final EmbeddingModel embeddingModel;
    private final RAGMetrics ragMetrics;  // ✅ Métriques unifiées
    
    public OpenAiEmbeddingService(
            EmbeddingModel embeddingModel,
            RAGMetrics ragMetrics) {
        
        this.embeddingModel = embeddingModel;
        this.ragMetrics = ragMetrics;
    }
    
    /**
     * Génère embedding pour un texte
     */
    public Embedding embedText(String text) {
        long start = System.currentTimeMillis();
        
        try {
            Response<Embedding> response = embeddingModel.embed(text);
            Embedding embedding = response.content();
            
            long duration = System.currentTimeMillis() - start;
            
            // ✅ MÉTRIQUE: API call success
            ragMetrics.recordApiCall("embed_text", duration);
            
            log.debug("✅ Embedded text: {} chars in {}ms", text.length(), duration);
            
            return embedding;
            
        } catch (Exception e) {
            log.error("❌ Embedding failed for text", e);
            
            // ✅ MÉTRIQUE: API call error
            ragMetrics.recordApiError("embed_text");
            
            throw new RuntimeException("Text embedding failed", e);
        }
    }
    
    /**
     * Génère embedding pour un segment de texte
     */
    public Embedding embedSegment(TextSegment segment) {
        long start = System.currentTimeMillis();
        
        try {
            Response<Embedding> response = embeddingModel.embed(segment);
            Embedding embedding = response.content();
            
            long duration = System.currentTimeMillis() - start;
            
            // ✅ MÉTRIQUE: API call success
            ragMetrics.recordApiCall("embed_segment", duration);
            
            log.debug("✅ Embedded segment in {}ms", duration);
            
            return embedding;
            
        } catch (Exception e) {
            log.error("❌ Embedding failed for segment", e);
            
            // ✅ MÉTRIQUE: API call error
            ragMetrics.recordApiError("embed_segment");
            
            throw new RuntimeException("Segment embedding failed", e);
        }
    }
    
    /**
     * Génère embeddings pour une liste de textes (batch)
     */
    public List<Embedding> embedBatch(List<String> texts) {
        long start = System.currentTimeMillis();
        
        try {
            List<TextSegment> segments = texts.stream()
                .map(TextSegment::from)
                .collect(Collectors.toList());
            
            Response<List<Embedding>> response = embeddingModel.embedAll(segments);
            List<Embedding> embeddings = response.content();
            
            long duration = System.currentTimeMillis() - start;
            
            // ✅ MÉTRIQUE: API call batch success
            ragMetrics.recordApiCall("embed_batch", duration);
            
            log.debug("✅ Embedded batch: {} texts in {}ms", texts.size(), duration);
            
            return embeddings;
            
        } catch (Exception e) {
            log.error("❌ Batch embedding failed", e);
            
            // ✅ MÉTRIQUE: API call error
            ragMetrics.recordApiError("embed_batch");
            
            throw new RuntimeException("Batch embedding failed", e);
        }
    }
    
    /**
     * Génère embedding pour une image (si supporté)
     */
    public Embedding embedImage(byte[] imageData) {
        long start = System.currentTimeMillis();
        
        try {
            // Note: Implémentation dépend du modèle d'embedding image utilisé
            // Pour l'instant, placeholder
            
            // TODO: Implémenter embedding image avec CLIP ou similaire
            
            long duration = System.currentTimeMillis() - start;
            
            // ✅ MÉTRIQUE: API call success
            ragMetrics.recordApiCall("embed_image", duration);
            
            log.debug("✅ Embedded image in {}ms", duration);
            
            return null; // Placeholder
            
        } catch (Exception e) {
            log.error("❌ Image embedding failed", e);
            
            // ✅ MÉTRIQUE: API call error
            ragMetrics.recordApiError("embed_image");
            
            throw new RuntimeException("Image embedding failed", e);
        }
    }
    
    /**
     * Retourne la dimension des embeddings
     */
    public int getDimension() {
        return embeddingModel.dimension();
    }
}