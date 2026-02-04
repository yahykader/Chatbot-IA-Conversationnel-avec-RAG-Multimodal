package com.exemple.transactionservice.service.rag.retrieval.retriever;

import com.exemple.transactionservice.service.rag.retrieval.retriever.Retriever;
import com.exemple.transactionservice.service.rag.retrieval.model.RetrievalResult;
import com.exemple.transactionservice.service.rag.retrieval.model.RetrievalResult.ScoredChunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Retriever pour recherche vectorielle images
 */
@Slf4j
@Component
public class ImageVectorRetriever implements Retriever {
    
    private final EmbeddingStore<TextSegment> imageStore;
    private final EmbeddingModel embeddingModel;
    
    public ImageVectorRetriever(
            @Qualifier("imageEmbeddingStore") EmbeddingStore<TextSegment> imageStore,
            EmbeddingModel embeddingModel) {
        this.imageStore = imageStore;
        this.embeddingModel = embeddingModel;
    }
    
    @Override
    public CompletableFuture<RetrievalResult> retrieveAsync(List<String> queries, int topK) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                List<ScoredChunk> allChunks = new ArrayList<>();
                
                for (String query : queries) {
                    Embedding queryEmbedding = embeddingModel.embed(query).content();
                    
                    List<EmbeddingMatch<TextSegment>> matches = 
                        imageStore.findRelevant(queryEmbedding, topK);
                    
                    for (int i = 0; i < matches.size(); i++) {
                        EmbeddingMatch<TextSegment> match = matches.get(i);
                        
                        allChunks.add(ScoredChunk.builder()
                            .id(match.embeddingId())
                            .content(match.embedded().text())
                            .metadata(match.embedded().metadata().toMap())
                            .score(match.score())
                            .retrieverName("image")
                            .rank(i)
                            .build());
                    }
                }
                
                // Deduplicate
                Map<String, ScoredChunk> uniqueChunks = allChunks.stream()
                    .collect(Collectors.toMap(
                        ScoredChunk::getId,
                        chunk -> chunk,
                        (existing, replacement) -> 
                            existing.getScore() > replacement.getScore() ? existing : replacement
                    ));
                
                List<ScoredChunk> finalChunks = new ArrayList<>(uniqueChunks.values());
                finalChunks.sort(Comparator.comparingDouble(ScoredChunk::getScore).reversed());
                
                double topScore = finalChunks.isEmpty() ? 0.0 : finalChunks.get(0).getScore();
                long duration = System.currentTimeMillis() - startTime;
                
                log.debug("🖼️ [IMAGE] {} queries → {} chunks (top={}, {}ms)", 
                    queries.size(), finalChunks.size(), 
                    String.format("%.3f", topScore), duration);
                
                return RetrievalResult.builder()
                    .retrieverName("image")
                    .chunks(finalChunks)
                    .totalFound(finalChunks.size())
                    .topScore(topScore)
                    .durationMs(duration)
                    .cacheHits(0)
                    .cacheMisses(queries.size())
                    .build();
                
            } catch (Exception e) {
                log.error("❌ [IMAGE] Erreur retrieval", e);
                throw new RuntimeException("Image retrieval failed", e);
            }
        });
    }
    
    @Override
    public String getName() {
        return "image";
    }
}