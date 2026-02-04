package com.exemple.transactionservice.service.rag.retrieval.retriever;

import com.exemple.transactionservice.service.rag.retrieval.model.RetrievalResult;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Interface de base pour tous les retrievers
 */
public interface Retriever {
    
    /**
     * Recherche asynchrone
     */
    CompletableFuture<RetrievalResult> retrieveAsync(List<String> queries, int topK);
    
    /**
     * Nom du retriever
     */
    String getName();
}
