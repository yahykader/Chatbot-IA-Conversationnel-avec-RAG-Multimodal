package com.exemple.transactionservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * Configuration pour le Retrieval Augmentor
 */
@Configuration
@ConfigurationProperties(prefix = "retrieval")
@Data
public class RetrievalConfig {
    
    // ========================================================================
    // QUERY TRANSFORMER
    // ========================================================================
    
    private QueryTransformer queryTransformer = new QueryTransformer();
    
    @Data
    public static class QueryTransformer {
        private boolean enabled = true;
        private String method = "llm"; // "llm" | "rule-based" | "hybrid"
        private String model = "gpt-4o-mini";
        private int maxVariants = 5;
        private int timeoutMs = 2000;
        private boolean enableSynonyms = true;
        private boolean enableTemporalContext = true;
    }
    
    // ========================================================================
    // QUERY ROUTER
    // ========================================================================
    
    private QueryRouter queryRouter = new QueryRouter();
    
    @Data
    public static class QueryRouter {
        private boolean enabled = true;
        private String defaultStrategy = "HYBRID";
        private double confidenceThreshold = 0.7;
    }
    
    // ========================================================================
    // RETRIEVERS
    // ========================================================================
    
    private Retrievers retrievers = new Retrievers();
    
    @Data
    public static class Retrievers {
        private TextRetriever text = new TextRetriever();
        private ImageRetriever image = new ImageRetriever();
        private BM25Retriever bm25 = new BM25Retriever();
        private int parallelTimeout = 5000;
    }
    
    @Data
    public static class TextRetriever {
        private boolean enabled = true;
        private int topK = 20;
        private double similarityThreshold = 0.7;
    }
    
    @Data
    public static class ImageRetriever {
        private boolean enabled = true;
        private int topK = 5;
        private double similarityThreshold = 0.6;
    }
    
    @Data
    public static class BM25Retriever {
        private boolean enabled = true;
        private int topK = 10;
        private String language = "french";
    }
    
    // ========================================================================
    // AGGREGATOR
    // ========================================================================
    
    private Aggregator aggregator = new Aggregator();
    
    @Data
    public static class Aggregator {
        private String fusionMethod = "rrf"; // "rrf" | "weighted"
        private int rrfK = 60;
        private double textWeight = 0.5;
        private double imageWeight = 0.3;
        private double bm25Weight = 0.2;
        private int maxCandidates = 30;
        private int finalTopK = 10;
    }
    
    // ========================================================================
    // RERANKER
    // ========================================================================
    
    private Reranker reranker = new Reranker();
    
    @Data
    public static class Reranker {
        private boolean enabled = false;
        private String model = "cross-encoder/ms-marco-MiniLM-L-6-v2";
        private int topK = 10;
        private int batchSize = 32;
    }
    
    // ========================================================================
    // CONTENT INJECTOR
    // ========================================================================
    
    private ContentInjector contentInjector = new ContentInjector();
    
    @Data
    public static class ContentInjector {
        private int maxTokens = 200000;
        private int systemPromptTokens = 200;
        private int instructionsTokens = 250;
        private boolean enableCitations = true;
        private String citationFormat = "<cite index=\"{index}\">{content}</cite>";
    }
}