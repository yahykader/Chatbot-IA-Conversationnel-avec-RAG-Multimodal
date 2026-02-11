package com.exemple.transactionservice.service.rag.retrieval.reranker;

import com.exemple.transactionservice.service.rag.retrieval.reranker.Reranker;
import com.exemple.transactionservice.config.RetrievalConfig;
import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
import com.exemple.transactionservice.service.rag.retrieval.model.AggregatedContext.SelectedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implémentation Cross-Encoder pour reranking
 * 
 * Utilise un modèle BERT cross-encoder pour scorer la pertinence
 * query-document avec plus de précision qu'un bi-encoder
 * 
 * Note: Cette implémentation est un stub
 * En production, intégrer avec HuggingFace Transformers ou API externe
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "retrieval.reranker.enabled", havingValue = "true")
public class CrossEncoderReranker implements Reranker {
    
    private final RetrievalConfig config;
    private final RAGMetrics ragMetrics;
    
    public CrossEncoderReranker(RetrievalConfig config, RAGMetrics ragMetrics) {
        this.config = config;
        this.ragMetrics = ragMetrics;
        log.info("✅ CrossEncoderReranker initialisé (model: {})", 
            config.getReranker().getModel());
    }
    
    @Override
    public List<SelectedChunk> rerank(String query, List<SelectedChunk> chunks) {
        long startTime = System.currentTimeMillis();
        
        log.debug("🔝 Reranking {} chunks...", chunks.size());
        
        // TODO: Implémenter vraie logique cross-encoder
        // Pour l'instant: simulation avec scores existants
        
        List<RerankResult> results = new ArrayList<>();
        
        for (SelectedChunk chunk : chunks) {
            // STUB: En production, appeler vrai modèle cross-encoder
            // Example: rerankScore = crossEncoderModel.score(query, chunk.content)
            
            // Pour l'instant: combiner score RRF avec heuristiques
            double rerankScore = calculateSimulatedRerankScore(query, chunk);
            
            results.add(new RerankResult(chunk, rerankScore));
        }
        
        // Trier par nouveau score
        List<SelectedChunk> reranked = results.stream()
            .sorted(Comparator.comparingDouble(r -> -r.score))
            .map(r -> {
                // Mettre à jour le score final
                return SelectedChunk.builder()
                    .id(r.chunk.getId())
                    .content(r.chunk.getContent())
                    .metadata(r.chunk.getMetadata())
                    .finalScore(r.score)
                    .scoresByRetriever(r.chunk.getScoresByRetriever())
                    .retrieversUsed(r.chunk.getRetrieversUsed())
                    .build();
            })
            .collect(Collectors.toList());
        
        long duration = System.currentTimeMillis() - startTime;
        
        log.debug("✅ Reranking complete: {} chunks, {}ms", 
            reranked.size(), duration);
        
        // ⬅️ MÉTRIQUE AJOUTÉE
        ragMetrics.recordReranking(duration, reranked.size());
        
        return reranked;
    }
    
    /**
     * STUB: Score simulé pour démonstration
     * 
     * En production: remplacer par vrai cross-encoder
     */
    private double calculateSimulatedRerankScore(String query, SelectedChunk chunk) {
        double baseScore = chunk.getFinalScore();
        
        // Bonus si query keywords présents dans content
        String queryLower = query.toLowerCase();
        String contentLower = chunk.getContent().toLowerCase();
        
        String[] queryWords = queryLower.split("\\s+");
        long matchingWords = 0;
        
        for (String word : queryWords) {
            if (word.length() > 3 && contentLower.contains(word)) {
                matchingWords++;
            }
        }
        
        double matchBonus = (matchingWords / (double) queryWords.length) * 0.2;
        
        // Bonus si chunk vient de plusieurs retrievers (consensus)
        double consensusBonus = chunk.getRetrieversUsed().size() > 1 ? 0.1 : 0.0;
        
        return baseScore + matchBonus + consensusBonus;
    }
    
    private record RerankResult(SelectedChunk chunk, double score) {}
}

/**
 * TODO: Pour une vraie implémentation cross-encoder, utiliser:
 * 
 * Option 1: HuggingFace Transformers (Python via REST API)
 * 
 * from sentence_transformers import CrossEncoder
 * model = CrossEncoder('cross-encoder/ms-marco-MiniLM-L-6-v2')
 * scores = model.predict([(query, doc1), (query, doc2), ...])
 * 
 * 
 * Option 2: ONNX Runtime (Java)
 * 
 * import ai.onnxruntime.*;
 * OrtSession session = env.createSession("cross-encoder.onnx");
 * // Tokenize et inférence
 * 
 * 
 * Option 3: API externe (Cohere Rerank, Jina Reranker)
 * 
 * POST https://api.cohere.ai/v1/rerank
 * {
 *   "query": "...",
 *   "documents": ["doc1", "doc2", ...],
 *   "top_n": 10
 * }
 */