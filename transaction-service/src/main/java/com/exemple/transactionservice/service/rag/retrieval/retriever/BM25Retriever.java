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
 * Retriever BM25 (full-text search PostgreSQL)
 */
@Slf4j
@Component
public class BM25Retriever implements Retriever {
    
    private final JdbcTemplate jdbcTemplate;
    
    public BM25Retriever(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public CompletableFuture<RetrievalResult> retrieveAsync(List<String> queries, int topK) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                // Combine queries into one search
                String combinedQuery = String.join(" ", queries);
                
                String sql = """
                    SELECT 
                        id,
                        content,
                        metadata,
                        ts_rank(to_tsvector('french', content), query) as score
                    FROM text_embeddings,
                         plainto_tsquery('french', ?) query
                    WHERE to_tsvector('french', content) @@ query
                    ORDER BY score DESC
                    LIMIT ?
                    """;
                
                List<ScoredChunk> chunks = jdbcTemplate.query(
                    sql,
                    new Object[]{combinedQuery, topK},
                    (rs, rowNum) -> ScoredChunk.builder()
                        .id(rs.getString("id"))
                        .content(rs.getString("content"))
                        .metadata(parseJsonb(rs.getString("metadata")))
                        .score(rs.getDouble("score"))
                        .retrieverName("bm25")
                        .rank(rowNum)
                        .build()
                );
                
                double topScore = chunks.isEmpty() ? 0.0 : chunks.get(0).getScore();
                long duration = System.currentTimeMillis() - startTime;
                
                log.debug("📊 [BM25] {} queries → {} chunks (top={}, {}ms)", 
                    queries.size(), chunks.size(), 
                    String.format("%.3f", topScore), duration);
                
                return RetrievalResult.builder()
                    .retrieverName("bm25")
                    .chunks(chunks)
                    .totalFound(chunks.size())
                    .topScore(topScore)
                    .durationMs(duration)
                    .cacheHits(0)
                    .cacheMisses(1)
                    .build();
                
            } catch (Exception e) {
                log.error("❌ [BM25] Erreur retrieval", e);
                throw new RuntimeException("BM25 retrieval failed", e);
            }
        });
    }
    
    @Override
    public String getName() {
        return "bm25";
    }
    
    private Map<String, Object> parseJsonb(String jsonb) {
        // Simple JSONB parsing (use Jackson in production)
        if (jsonb == null || jsonb.isEmpty()) {
            return new HashMap<>();
        }
        // TODO: Use ObjectMapper for proper parsing
        return new HashMap<>();
    }
}









































