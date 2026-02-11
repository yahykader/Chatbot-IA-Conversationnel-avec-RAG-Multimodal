package com.exemple.transactionservice.service.rag.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import java.util.concurrent.TimeUnit;

/**
 * 📊 MÉTRIQUES UNIFIÉES RAG SYSTEM - Production Ready
 *
 * Service centralisé pour TOUTES les métriques du système RAG.
 *
 * Compatible: Prometheus + Grafana
 */
@Slf4j
@Component
public class RAGMetrics {

    private final MeterRegistry registry;

    // ========================================================================
    // GAUGES - Valeurs Actuelles
    // ========================================================================

    private final AtomicInteger activeIngestions = new AtomicInteger(0);
    private final AtomicInteger activeQueries = new AtomicInteger(0);
    private final AtomicLong totalFilesProcessed = new AtomicLong(0);
    private final AtomicLong totalQueriesProcessed = new AtomicLong(0);
    private final AtomicLong totalTokensGenerated = new AtomicLong(0);

    // ========================================================================
    // FIX: caches meters (évite register() à chaud)
    // ========================================================================

    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();

    // FIX: custom gauges stables
    private final Map<String, AtomicReference<Double>> customGaugeCache = new ConcurrentHashMap<>();

    // ========================================================================
    // CONSTRUCTEUR
    // ========================================================================

    public RAGMetrics(MeterRegistry registry) {
        System.out.println("🚀🚀🚀 RAGMetrics CONSTRUCTOR CALLED 🚀🚀🚀");
        System.out.println("Registry class: " + registry.getClass().getName());
        
        this.registry = registry;
        registerGauges();
        
        System.out.println("✅✅✅ RAGMetrics INITIALIZED ✅✅✅");
        System.out.println("Registered meters: " + registry.getMeters().size());
    
        log.info("✅ RAGMetrics initialisé - Monitoring complet actif");
    }

    private void registerGauges() {
        // Activités en cours
        Gauge.builder("rag_active_ingestions", activeIngestions, AtomicInteger::get)
            .description("Files currently being ingested")
            .tag("component", "ingestion")
            .register(registry);

        Gauge.builder("rag_active_queries", activeQueries, AtomicInteger::get)
            .description("Queries currently being processed")
            .tag("component", "retrieval")
            .register(registry);

        // Totaux cumulés
        Gauge.builder("rag_total_files", totalFilesProcessed, AtomicLong::get)
            .description("Total files processed since startup")
            .tag("component", "ingestion")
            .register(registry);

        Gauge.builder("rag_total_queries", totalQueriesProcessed, AtomicLong::get)
            .description("Total queries processed since startup")
            .tag("component", "retrieval")
            .register(registry);

        Gauge.builder("rag_total_tokens", totalTokensGenerated, AtomicLong::get)
            .description("Total tokens generated since startup")
            .tag("component", "generation")
            .register(registry);
    }

    // ========================================================================
    // Helpers cache meters
    // ========================================================================

    private static String key(String name, String... tags) {
        return name + "|" + String.join("|", tags);
    }

    private Counter counter(String name, String description, String... tags) {
        return counterCache.computeIfAbsent(key(name, tags), k ->
            Counter.builder(name)
                .description(description)
                .tags(tags)
                .register(registry)
        );
    }

    private Timer timer(String name, String description, String... tags) {
        return timerCache.computeIfAbsent(key(name, tags), k ->
            Timer.builder(name)
                .description(description)
                .tags(tags)
                // ✅ CRITIQUE: buckets pour Grafana (histogram_quantile)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry)
        );
    }

    private static double safeDouble(Double v) {
        if (v == null) return 0.0;
        if (v.isNaN() || v.isInfinite()) return 0.0;
        return v;
    }

    // ========================================================================
    // INGESTION METRICS
    // ========================================================================

    public void recordIngestionSuccess(String strategy, long durationMs, int embeddings) {
        counter("rag_ingestion_files_total",
            "Total files ingested",
            "strategy", strategy,
            "status", "success"
        ).increment();

        timer("rag_ingestion_duration_seconds",
            "File ingestion duration",
            "strategy", strategy
        ).record(Duration.ofMillis(durationMs));

        if (embeddings > 0) {
            counter("rag_embeddings_total",
                "Total embeddings created",
                "strategy", strategy
            ).increment(embeddings);
        }

        totalFilesProcessed.incrementAndGet();
    }

    public void recordIngestionError(String strategy, String errorType) {
        counter("rag_ingestion_files_total",
            "Total files ingested",
            "strategy", strategy,
            "status", "error"
        ).increment();

        counter("rag_ingestion_errors_total",
            "Total ingestion errors",
            "strategy", strategy,
            "error_type", errorType
        ).increment();
    }

    public void startIngestion() {
        activeIngestions.incrementAndGet();
    }

    public void endIngestion() {
        activeIngestions.decrementAndGet();
    }

    public void recordDuplicate(String strategy) {
        counter("rag_duplicates_total",
            "Total duplicate files detected",
            "strategy", strategy
        ).increment();
    }

    public void recordVirusDetected(String virusName) {
        counter("rag_viruses_detected_total",
            "Total viruses detected",
            "virus", (virusName != null ? virusName : "unknown")
        ).increment();
    }

    public void recordStrategyProcessing(String strategy, long durationMs, int chunks) {
        timer("rag_strategy_processing_duration_seconds",
            "Strategy processing duration",
            "strategy", strategy
        ).record(Duration.ofMillis(durationMs));

        counter("rag_strategy_chunks_total",
            "Total chunks extracted by strategy",
            "strategy", strategy
        ).increment(chunks);
    }

    public void recordVectorStoreOperation(String operation, long durationMs, int count) {
        timer("rag_vectorstore_operation_duration_seconds",
            "Vector store operation duration",
            "operation", operation
        ).record(Duration.ofMillis(durationMs));

        counter("rag_vectorstore_operations_total",
            "Total vector store operations",
            "operation", operation
        ).increment(count);
    }

    public void recordAntivirusScan(long durationMs, boolean clean) {
        timer("rag_antivirus_scan_duration_seconds",
            "Antivirus scan duration",
            "result", (clean ? "clean" : "infected")
        ).record(Duration.ofMillis(durationMs));

        counter("rag_antivirus_scans_total",
            "Total antivirus scans",
            "result", (clean ? "clean" : "infected")
        ).increment();
    }

    // ========================================================================
    // RETRIEVAL METRICS
    // ========================================================================

    public void recordQueryTransformation(long durationMs, int variants) {
        timer("rag_query_transformation_duration_seconds",
            "Query transformation duration"
        ).record(Duration.ofMillis(durationMs));

        counter("rag_query_variants_total",
            "Total query variants generated"
        ).increment(variants);
    }

    public void recordRoutingDecision(String strategy, double confidence) {
        // confidence conservé (signature inchangée) - pas de suppression
        counter("rag_routing_decisions_total",
            "Total routing decisions",
            "strategy", strategy
        ).increment();
    }

    public void recordRetrieval(String retriever, long durationMs, int chunks) {
        timer("rag_retrieval_duration_seconds",
            "Retrieval duration",
            "retriever", retriever
        ).record(Duration.ofMillis(durationMs));

        counter("rag_chunks_retrieved_total",
            "Total chunks retrieved",
            "retriever", retriever
        ).increment(chunks);
    }

    public void recordAggregation(long durationMs, int inputChunks, int outputChunks) {
        // inputChunks/outputChunks conservés (signature inchangée)
        timer("rag_aggregation_duration_seconds",
            "Content aggregation duration"
        ).record(Duration.ofMillis(durationMs));
    }

    // ========================================================================
    // 🎖️ RERANKING METRICS
    // ========================================================================

    /**
     * Enregistre la durée du reranking
     * 
     * @param durationMs Durée du reranking en millisecondes
     * @param chunksReranked Nombre de chunks rerankés
     */
    public void recordReranking(long durationMs, int chunksReranked) {
        // Durée du reranking (histogram pour P50/P95/P99)
        timer("rag_reranking_duration",
            "Reranking duration"
        ).record(durationMs, TimeUnit.MILLISECONDS);
        
        // Nombre total de chunks rerankés
        counter("rag_reranking_chunks_total",
            "Total chunks reranked"
        ).increment(chunksReranked);
        
        log.debug("🎖️ Recorded reranking: {} chunks, {}ms", chunksReranked, durationMs);
    }

    /**
     * Enregistre la durée du reranking avec stratégie
     * 
     * @param durationMs Durée du reranking en millisecondes
     * @param chunksReranked Nombre de chunks rerankés
     * @param strategy Stratégie de reranking (ex: "cross-encoder", "semantic")
     */
    public void recordRerankingWithStrategy(long durationMs, int chunksReranked, String strategy) {
        // Durée du reranking par stratégie
        timer("rag_reranking_duration",
            "Reranking duration",
            "strategy", strategy
        ).record(durationMs, TimeUnit.MILLISECONDS);
        
        // Nombre de chunks rerankés par stratégie
        counter("rag_reranking_chunks_total",
            "Total chunks reranked",
            "strategy", strategy
        ).increment(chunksReranked);
        
        log.debug("🎖️ Recorded reranking ({}): {} chunks, {}ms", strategy, chunksReranked, durationMs);
    }

    public void startQuery() {
        activeQueries.incrementAndGet();
    }

    public void endQuery() {
        activeQueries.decrementAndGet();
        totalQueriesProcessed.incrementAndGet();
    }

    // ========================================================================
    // GENERATION METRICS
    // ========================================================================

    public void recordGeneration(long durationMs, int tokens) {
        timer("rag_generation_duration_seconds",
            "Text generation duration"
        ).record(Duration.ofMillis(durationMs));

        counter("rag_tokens_generated_total",
            "Total tokens generated"
        ).increment(tokens);

        totalTokensGenerated.addAndGet(tokens);
    }

    public void recordCitation() {
        counter("rag_citations_total",
            "Total citations detected"
        ).increment();
    }

    public void recordConversationMessage(String role, int tokens) {
        counter("rag_conversation_messages_total",
            "Total conversation messages",
            "role", role
        ).increment();

        counter("rag_conversation_tokens_total",
            "Total conversation tokens",
            "role", role
        ).increment(tokens);
    }

    // ========================================================================
    // API METRICS
    // ========================================================================

    public void recordApiCall(String operation, long durationMs) {
        counter("rag_api_calls_total",
            "Total API calls",
            "service", "openai",
            "operation", operation
        ).increment();

        timer("rag_api_duration_seconds",
            "API call duration",
            "service", "openai",
            "operation", operation
        ).record(Duration.ofMillis(durationMs));
    }

    public void recordApiError(String operation) {
        counter("rag_api_errors_total",
            "Total API errors",
            "service", "openai",
            "operation", operation
        ).increment();
    }

    // ========================================================================
    // CACHE METRICS
    // ========================================================================

    public void recordCacheHit(String cacheType) {
        counter("rag_cache_hits_total",
            "Total cache hits",
            "cache", cacheType
        ).increment();
    }

    public void recordCacheMiss(String cacheType) {
        counter("rag_cache_misses_total",
            "Total cache misses",
            "cache", cacheType
        ).increment();
    }

    // ========================================================================
    // PIPELINE
    // ========================================================================

    public void recordPipeline(long totalDuration, long retrievalDuration, long generationDuration) {
        // paramètres conservés (signature inchangée)
        timer("rag_pipeline_duration_seconds",
            "Complete RAG pipeline duration"
        ).record(Duration.ofMillis(totalDuration));
    }

    // ========================================================================
    // CUSTOM METRICS
    // ========================================================================

    /**
     * Enregistre métrique custom
     * ✅ FIX: gauge stable (mis à jour), pas re-créé à chaque appel
     */
    public void recordCustomMetric(String name, String tag, double value) {
        String cacheKey = name + "|service=" + tag;

        AtomicReference<Double> ref = customGaugeCache.computeIfAbsent(cacheKey, k -> {
            AtomicReference<Double> ar = new AtomicReference<>(0.0);
            Gauge.builder(name, ar, r -> safeDouble(r.get()))
                .tags(Tags.of("service", tag))
                .register(registry);
            return ar;
        });

        ref.set(value);
    }

    public void incrementCustomCounter(String name, String... tags) {
        Counter.builder(name)
            .tags(tags)
            .register(registry)
            .increment();
    }

    // ========================================================================
    // 💰 COST METRICS - À AJOUTER DANS RAGMetrics.java
    // ========================================================================

    /**
     * Enregistre le coût LLM
     * 
     * @param costUSD Coût total en USD
     * @param inputTokens Nombre de tokens d'input
     * @param outputTokens Nombre de tokens d'output
     */
    public void recordLLMCost(double costUSD, int inputTokens, int outputTokens) {
        // Coût total
        counter("rag_cost_llm_usd",
            "Total LLM cost in USD"
        ).increment(costUSD);

        // Tokens d'input
        counter("rag_cost_input_tokens_total",
            "Total input tokens"
        ).increment(inputTokens);

        // Tokens d'output
        counter("rag_cost_output_tokens_total",
            "Total output tokens"
        ).increment(outputTokens);
        
        log.debug("💰 Recorded LLM cost: ${} (input: {}, output: {})", 
            String.format("%.6f", costUSD), inputTokens, outputTokens);
    }

    /**
     * Enregistre le coût par modèle (optionnel, plus détaillé)
     * 
     * @param model Nom du modèle (ex: "gpt-4o-mini")
     * @param costUSD Coût total en USD
     * @param inputTokens Nombre de tokens d'input
     * @param outputTokens Nombre de tokens d'output
     */
    public void recordLLMCostByModel(String model, double costUSD, int inputTokens, int outputTokens) {
        // Coût total par modèle
        counter("rag_cost_llm_total_usd",
            "Total LLM cost in USD",
            "model", model
        ).increment(costUSD);

        // Tokens d'input par modèle
        counter("rag_cost_input_tokens_total",
            "Total input tokens",
            "model", model
        ).increment(inputTokens);

        // Tokens d'output par modèle
        counter("rag_cost_output_tokens_total",
            "Total output tokens",
            "model", model
        ).increment(outputTokens);
        
        log.debug("💰 Recorded LLM cost for {}: ${} (input: {}, output: {})", 
            model, String.format("%.6f", costUSD), inputTokens, outputTokens);
    }

    /**
     * Enregistre le coût d'une opération spécifique (embedding, vision, etc.)
     * 
     * @param operation Type d'opération (ex: "embedding", "vision", "generation")
     * @param costUSD Coût en USD
     */
    public void recordOperationCost(String operation, double costUSD) {
        counter("rag_cost_operation_usd",
            "Cost by operation type",
            "operation", operation
        ).increment(costUSD);
    }



    // ========================================================================
    // GETTERS
    // ========================================================================

    public int getActiveIngestions() {
        return activeIngestions.get();
    }

    public int getActiveQueries() {
        return activeQueries.get();
    }

    public long getTotalFilesProcessed() {
        return totalFilesProcessed.get();
    }

    public long getTotalQueriesProcessed() {
        return totalQueriesProcessed.get();
    }

    public long getTotalTokensGenerated() {
        return totalTokensGenerated.get();
    }

    public String getSummary() {
        return String.format("""
            
            ╔════════════════════════════════════════════════════════╗
            ║         📊 MÉTRIQUES RAG SYSTEM                       ║
            ╠════════════════════════════════════════════════════════╣
            ║ Ingestion                                              ║
            ║   Files processed      : %-25d       ║
            ║   Active ingestions    : %-25d       ║
            ╠════════════════════════════════════════════════════════╣
            ║ Retrieval                                              ║
            ║   Queries processed    : %-25d       ║
            ║   Active queries       : %-25d       ║
            ╠════════════════════════════════════════════════════════╣
            ║ Generation                                             ║
            ║   Tokens generated     : %-25d       ║
            ╚════════════════════════════════════════════════════════╝
            """,
            totalFilesProcessed.get(),
            activeIngestions.get(),
            totalQueriesProcessed.get(),
            activeQueries.get(),
            totalTokensGenerated.get()
        );
    }

    public void logSummary() {
        log.info(getSummary());
    }
}
