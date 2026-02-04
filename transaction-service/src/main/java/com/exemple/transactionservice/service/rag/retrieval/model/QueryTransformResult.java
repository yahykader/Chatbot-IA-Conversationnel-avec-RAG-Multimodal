package com.exemple.transactionservice.service.rag.retrieval.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Résultat de la transformation de query
 */
@Data
@Builder
public class QueryTransformResult {
    private String originalQuery;
    private List<String> variants;
    private String method; // "llm" | "rule-based"
    private long durationMs;
    private double confidence;
}