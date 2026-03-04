package com.exemple.transactionservice.service.rag.retrieval.reranker;

import com.exemple.transactionservice.service.rag.retrieval.model.AggregatedContext.SelectedChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Interface pour reranking
 */
public interface Reranker {
    
    /**
     * Rerank chunks basé sur similarité sémantique profonde
     */
    List<SelectedChunk> rerank(String query, List<SelectedChunk> chunks);
}
