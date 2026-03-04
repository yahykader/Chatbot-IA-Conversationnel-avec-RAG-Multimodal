package com.exemple.transactionservice.monitoring;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service de métriques pour RAG Assistant
 *
 * Tracks:
 * - Query latency
 * - Retrieval performance
 * - Generation metrics
 * - Cache hit rates
 * - Error rates
 * - Active connections
 */
@Slf4j
@Service
public class MetricsService {

    private final MeterRegistry registry;

    // Counters
    private final Counter queriesTotal;
    private final Counter queriesSuccess;
    private final Counter queriesError;      // "unknown" baseline conservé
    private final Counter queriesCancelled;

    private final Counter retrievalChunksTotal;
    private final Counter generationTokensTotal;
    private final Counter cacheHitsTotal;
    private final Counter cacheMissesTotal;

    // Timers
    private final Timer queryDuration;       // timer global conservé
    private final Timer retrievalDuration;
    private final Timer generationDuration;
    private final Timer ttftTimer;

    // Gauges (Atomic)
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger activeQueries = new AtomicInteger(0);

    // Gauges dynamiques (⚠️ enregistrés UNE seule fois)
    private final AtomicReference<Double> cacheHitRateRef = new AtomicReference<>(0.0);
    private final AtomicReference<Double> avgRelevanceRef = new AtomicReference<>(0.0);

    // Top score par retriever (gauge par retriever, valeur mise à jour)
    private final Map<String, AtomicReference<Double>> topScoreByRetriever = new ConcurrentHashMap<>();

    // Histograms (DistributionSummary)
    private final DistributionSummary chunksFoundDistribution;
    private final DistributionSummary chunksSelectedDistribution;
    private final DistributionSummary tokensGeneratedDistribution;

    // Caches meters taggés (pour éviter register() à chaque appel)
    private final Map<String, Counter> queryErrorsByType = new ConcurrentHashMap<>();
    private final Map<String, Timer> queryDurationByStrategySuccess = new ConcurrentHashMap<>();
    private final Map<String, Timer> queryDurationByErrorType = new ConcurrentHashMap<>();
    private final Map<String, Counter> retrievalExecutionsByStrategy = new ConcurrentHashMap<>();
    private final Map<String, Timer> retrievalDurationByStrategy = new ConcurrentHashMap<>();
    private final Map<String, Counter> retrieverChunksByName = new ConcurrentHashMap<>();
    private final Map<String, Timer> retrieverDurationByName = new ConcurrentHashMap<>();
    private final Map<String, Counter> cacheHitsByType = new ConcurrentHashMap<>();
    private final Map<String, Counter> cacheMissesByType = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;


            System.out.println("🚀🚀🚀 MetricsService CONSTRUCTOR CALLED 🚀🚀🚀");
            System.out.println("Registry class: " + registry.getClass().getName());
                

            


        // ========================================================================
        // COUNTERS
        // ========================================================================

        this.queriesTotal = Counter.builder("rag.queries.total")
            .description("Total number of queries")
            .register(registry);

        this.queriesSuccess = Counter.builder("rag.queries.success")
            .description("Number of successful queries")
            .register(registry);

        // Baseline conservé (type=unknown) + on ajoutera aussi des counters par type via cache
        this.queriesError = Counter.builder("rag.queries.error")
            .description("Number of failed queries")
            .tag("type", "unknown")
            .register(registry);

        this.queriesCancelled = Counter.builder("rag.queries.cancelled")
            .description("Number of cancelled queries")
            .register(registry);

        this.retrievalChunksTotal = Counter.builder("rag.retrieval.chunks.total")
            .description("Total number of chunks retrieved")
            .register(registry);

        this.generationTokensTotal = Counter.builder("rag.generation.tokens.total")
            .description("Total number of tokens generated")
            .register(registry);

        this.cacheHitsTotal = Counter.builder("rag.cache.hits")
            .description("Number of cache hits")
            .register(registry);

        this.cacheMissesTotal = Counter.builder("rag.cache.misses")
            .description("Number of cache misses")
            .register(registry);

        // ========================================================================
        // TIMERS (✅ ajout histogram buckets pour Prometheus/Grafana)
        // ========================================================================

        this.queryDuration = Timer.builder("rag.query.duration")
            .description("Query end-to-end duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram() // ✅ buckets *_bucket
            .register(registry);

        this.retrievalDuration = Timer.builder("rag.retrieval.duration")
            .description("Retrieval duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        this.generationDuration = Timer.builder("rag.generation.duration")
            .description("Generation duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        this.ttftTimer = Timer.builder("rag.generation.ttft")
            .description("Time to first token")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        // ========================================================================
        // GAUGES (✅ enregistrés une fois)
        // ========================================================================

        Gauge.builder("rag.connections.active", activeConnections, AtomicInteger::get)
            .description("Number of active SSE/WebSocket connections")
            .register(registry);

        Gauge.builder("rag.queries.active", activeQueries, AtomicInteger::get)
            .description("Number of queries currently processing")
            .register(registry);

        Gauge.builder("rag.cache.hit_rate", cacheHitRateRef, ref -> safeDouble(ref.get()))
            .description("Cache hit rate (0-1)")
            .register(registry);

        Gauge.builder("rag.quality.avg_relevance", avgRelevanceRef, ref -> safeDouble(ref.get()))
            .description("Average relevance score of selected chunks")
            .register(registry);

        // ========================================================================
        // DISTRIBUTIONS (DistributionSummary) — inchangé + histogram buckets utiles
        // ========================================================================

        this.chunksFoundDistribution = DistributionSummary.builder("rag.retrieval.chunks.found")
            .description("Distribution of chunks found")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        this.chunksSelectedDistribution = DistributionSummary.builder("rag.retrieval.chunks.selected")
            .description("Distribution of chunks selected")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        this.tokensGeneratedDistribution = DistributionSummary.builder("rag.generation.tokens.generated")
            .description("Distribution of tokens generated")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

                System.out.println("✅✅✅ MetricsService INITIALIZED ✅✅✅");
            System.out.println("Registered meters: " + registry.getMeters().size());
    }

    private static double safeDouble(Double v) {
        return (v == null || v.isNaN() || v.isInfinite()) ? 0.0 : v;
    }

    // ========================================================================
    // QUERY METRICS
    // ========================================================================

    public Timer.Sample startQuery() {
        queriesTotal.increment();
        activeQueries.incrementAndGet();
        return Timer.start(registry);
    }

    public void recordQuerySuccess(Timer.Sample sample, String strategy) {
        queriesSuccess.increment();
        activeQueries.decrementAndGet();

        // ✅ stop global timer (utile pour dashboards simples)
        long nanos = sample.stop(queryDuration);

        // ✅ timer taggé (sans recréer à chaque fois)
        Timer tagged = queryDurationByStrategySuccess.computeIfAbsent(strategy, s ->
            Timer.builder("rag.query.duration")
                .description("Query end-to-end duration")
                .tag("status", "success")
                .tag("strategy", s)
                .publishPercentileHistogram()
                .register(registry)
        );

        // On ne peut pas stopper 2 fois le même sample -> on re-record la durée mesurée
        tagged.record(nanos, TimeUnit.NANOSECONDS);
    }

    public void recordQueryError(Timer.Sample sample, String errorType) {
        // conserve le compteur baseline "unknown" (ne supprime rien)
        queriesError.increment();

        activeQueries.decrementAndGet();

        // ✅ counter par type (cache)
        queryErrorsByType.computeIfAbsent(errorType, et ->
            Counter.builder("rag.queries.error")
                .description("Number of failed queries")
                .tag("type", et)
                .register(registry)
        ).increment();

        long nanos = sample.stop(queryDuration);

        // ✅ timer taggé par type d’erreur (cache)
        Timer tagged = queryDurationByErrorType.computeIfAbsent(errorType, et ->
            Timer.builder("rag.query.duration")
                .description("Query end-to-end duration")
                .tag("status", "error")
                .tag("error_type", et)
                .publishPercentileHistogram()
                .register(registry)
        );

        tagged.record(nanos, TimeUnit.NANOSECONDS);
    }

    public void recordQueryCancelled(Timer.Sample sample) {
        queriesCancelled.increment();
        activeQueries.decrementAndGet();

        long nanos = sample.stop(queryDuration);

        // ✅ timer taggé cancelled (unique)
        Timer tagged = Timer.builder("rag.query.duration")
            .description("Query end-to-end duration")
            .tag("status", "cancelled")
            .publishPercentileHistogram()
            .register(registry);

        // Timer.register(...) renverra le même meter si déjà existant (tags identiques)
        tagged.record(nanos, TimeUnit.NANOSECONDS);
    }

    // ========================================================================
    // RETRIEVAL METRICS
    // ========================================================================

    public void recordRetrieval(long durationMs, int chunksFound, int chunksSelected, String strategy) {

        retrievalDuration.record(durationMs, TimeUnit.MILLISECONDS);
        retrievalChunksTotal.increment(chunksFound);

        chunksFoundDistribution.record(chunksFound);
        chunksSelectedDistribution.record(chunksSelected);

        // ✅ per-strategy counter (cache)
        retrievalExecutionsByStrategy.computeIfAbsent(strategy, s ->
            Counter.builder("rag.retrieval.executions")
                .tag("strategy", s)
                .register(registry)
        ).increment();

        // ✅ per-strategy timer (cache) + histogram
        retrievalDurationByStrategy.computeIfAbsent(strategy, s ->
            Timer.builder("rag.retrieval.duration")
                .description("Retrieval duration")
                .tag("strategy", s)
                .publishPercentileHistogram()
                .register(registry)
        ).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordRetrieverMetrics(String retrieverName, long durationMs, int chunksFound, double topScore) {

        retrieverChunksByName.computeIfAbsent(retrieverName, r ->
            Counter.builder("rag.retriever.chunks")
                .tag("retriever", r)
                .register(registry)
        ).increment(chunksFound);

        retrieverDurationByName.computeIfAbsent(retrieverName, r ->
            Timer.builder("rag.retriever.duration")
                .tag("retriever", r)
                .publishPercentileHistogram()
                .register(registry)
        ).record(durationMs, TimeUnit.MILLISECONDS);

        // ✅ Gauge stable (1 par retriever) avec valeur mise à jour
        AtomicReference<Double> ref = topScoreByRetriever.computeIfAbsent(retrieverName, r -> {
            AtomicReference<Double> ar = new AtomicReference<>(0.0);
            Gauge.builder("rag.retriever.top_score", ar, v -> safeDouble(v.get()))
                .tag("retriever", r)
                .register(registry);
            return ar;
        });
        ref.set(topScore);
    }

    // ========================================================================
    // GENERATION METRICS
    // ========================================================================

    public void recordTTFT(long milliseconds) {
        ttftTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }

    public void recordGeneration(long durationMs, int tokensGenerated, int citationsCount) {

        generationDuration.record(durationMs, TimeUnit.MILLISECONDS);
        generationTokensTotal.increment(tokensGenerated);
        tokensGeneratedDistribution.record(tokensGenerated);

        Counter.builder("rag.generation.citations")
            .register(registry)
            .increment(citationsCount);
    }

    // ========================================================================
    // CACHE METRICS
    // ========================================================================

    public void recordCacheHit(String cacheType) {
        cacheHitsTotal.increment();

        cacheHitsByType.computeIfAbsent(cacheType, ct ->
            Counter.builder("rag.cache.hits")
                .tag("type", ct)
                .register(registry)
        ).increment();
    }

    public void recordCacheMiss(String cacheType) {
        cacheMissesTotal.increment();

        cacheMissesByType.computeIfAbsent(cacheType, ct ->
            Counter.builder("rag.cache.misses")
                .tag("type", ct)
                .register(registry)
        ).increment();
    }

    public void updateCacheHitRate() {
        double hits = cacheHitsTotal.count();
        double misses = cacheMissesTotal.count();
        double total = hits + misses;

        if (total > 0) {
            double hitRate = hits / total;
            // ✅ mise à jour gauge (au lieu de re-register)
            cacheHitRateRef.set(hitRate);
        }
    }

    // ========================================================================
    // CONNECTION METRICS
    // ========================================================================

    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }

    // ========================================================================
    // QUALITY METRICS
    // ========================================================================

    public void recordAverageRelevance(double avgRelevance) {
        // ✅ mise à jour gauge (au lieu de re-register)
        avgRelevanceRef.set(avgRelevance);
    }

    public void recordEmptyResult() {
        Counter.builder("rag.quality.empty_results")
            .description("Number of queries with no results")
            .register(registry)
            .increment();
    }

    // ========================================================================
    // COST METRICS
    // ========================================================================

    public void recordLLMCost(double costUSD, int inputTokens, int outputTokens) {
        Counter.builder("rag.cost.llm_total_usd")
            .description("Total LLM cost in USD")
            .register(registry)
            .increment(costUSD);

        Counter.builder("rag.cost.input_tokens")
            .register(registry)
            .increment(inputTokens);

        Counter.builder("rag.cost.output_tokens")
            .register(registry)
            .increment(outputTokens);
    }
}
