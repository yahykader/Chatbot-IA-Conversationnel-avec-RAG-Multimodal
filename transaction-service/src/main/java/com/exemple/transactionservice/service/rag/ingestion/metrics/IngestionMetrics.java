// ============================================================================
// SERVICE - IngestionMetrics.java
// Service de métriques pour monitoring avec Prometheus
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service centralisé de métriques pour l'ingestion de documents.
 * 
 * Expose des métriques Prometheus pour :
 * - Nombre de fichiers traités (succès/échec) par strategy
 * - Durée d'ingestion par strategy
 * - Nombre d'embeddings créés par type
 * - Taux d'erreur par type d'erreur
 * - Fichiers en cours de traitement
 * 
 * Visualisation : Grafana dashboard
 * 
 * Exemple de métriques exposées:
 * - ingestion_success_total{strategy="PDF"} 1523
 * - ingestion_duration_seconds{strategy="PDF",quantile="0.95"} 3.4
 * - ingestion_embeddings_created_total{strategy="PDF",type="text"} 45678
 */
@Slf4j
@Component
public class IngestionMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // Compteurs par strategy
    private final Map<String, Counter> successCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    
    // Timers par strategy
    private final Map<String, Timer> durationTimers = new ConcurrentHashMap<>();
    
    // Gauge pour fichiers en cours
    private final AtomicInteger filesInProgress = new AtomicInteger(0);
    
    public IngestionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Enregistrer gauge pour fichiers en cours
        meterRegistry.gauge("ingestion.files.in_progress", filesInProgress);
        
        log.info("✅ IngestionMetrics initialisé - Métriques Prometheus activées");
    }
    
    // ========================================================================
    // ENREGISTREMENT MÉTRIQUES
    // ========================================================================
    
    /**
     * Enregistre un succès d'ingestion
     * 
     * @param strategyName Nom de la strategy (ex: "PDF", "DOCX")
     * @param durationMs Durée en millisecondes
     * @param textEmbeddings Nombre d'embeddings texte créés
     * @param imageEmbeddings Nombre d'embeddings image créés
     */
    public void recordSuccess(
            String strategyName, 
            long durationMs,
            int textEmbeddings,
            int imageEmbeddings) {
        
        // Incrémenter compteur succès
        getSuccessCounter(strategyName).increment();
        
        // Enregistrer durée
        getDurationTimer(strategyName).record(durationMs, TimeUnit.MILLISECONDS);
        
        // Enregistrer embeddings créés
        if (textEmbeddings > 0) {
            meterRegistry.counter("ingestion.embeddings.created",
                "strategy", strategyName,
                "type", "text").increment(textEmbeddings);
        }
        
        if (imageEmbeddings > 0) {
            meterRegistry.counter("ingestion.embeddings.created",
                "strategy", strategyName,
                "type", "image").increment(imageEmbeddings);
        }
        
        log.debug("📊 [Metrics] Success: strategy={} duration={}ms text={} images={}", 
            strategyName, durationMs, textEmbeddings, imageEmbeddings);
    }
    
    /**
     * Enregistre un échec d'ingestion
     * 
     * @param strategyName Nom de la strategy
     * @param errorType Type d'erreur (classe de l'exception)
     * @param durationMs Durée avant échec
     */
    public void recordError(
            String strategyName, 
            String errorType,
            long durationMs) {
        
        // Incrémenter compteur erreur
        getErrorCounter(strategyName).increment();
        
        // Compteur par type d'erreur
        meterRegistry.counter("ingestion.error.by_type",
            "strategy", strategyName,
            "error", errorType).increment();
        
        // Durée avant échec (utile pour détecter timeouts)
        getDurationTimer(strategyName).record(durationMs, TimeUnit.MILLISECONDS);
        
        log.debug("📊 [Metrics] Error: strategy={} type={} duration={}ms", 
            strategyName, errorType, durationMs);
    }
    
    /**
     * Enregistre une taille de fichier traité
     */
    public void recordFileSize(String strategyName, long sizeBytes) {
        meterRegistry.summary("ingestion.file.size",
            "strategy", strategyName).record(sizeBytes);
    }
    
    /**
     * Enregistre qu'un fichier a été dédupliqué
     */
    public void recordDuplicate(String strategyName) {
        meterRegistry.counter("ingestion.duplicate",
            "strategy", strategyName).increment();
        
        log.debug("📊 [Metrics] Duplicate: strategy={}", strategyName);
    }
    
    /**
     * Enregistre un retry (tentative supplémentaire)
     */
    public void recordRetry(String strategyName, int attemptNumber) {
        meterRegistry.counter("ingestion.retry",
            "strategy", strategyName,
            "attempt", String.valueOf(attemptNumber)).increment();
        
        log.debug("📊 [Metrics] Retry: strategy={} attempt={}", 
            strategyName, attemptNumber);
    }
    
    // ========================================================================
    // GESTION FICHIERS EN COURS
    // ========================================================================
    
    /**
     * Marque le début du traitement d'un fichier
     */
    public void startProcessing() {
        int current = filesInProgress.incrementAndGet();
        log.debug("📊 [Metrics] Fichiers en cours: {}", current);
    }
    
    /**
     * Marque la fin du traitement d'un fichier
     */
    public void endProcessing() {
        int current = filesInProgress.decrementAndGet();
        log.debug("📊 [Metrics] Fichiers en cours: {}", current);
    }
    
    /**
     * Retourne le nombre de fichiers en cours de traitement
     */
    public int getFilesInProgress() {
        return filesInProgress.get();
    }
    
    // ========================================================================
    // MÉTRIQUES CUSTOM
    // ========================================================================
    
    /**
     * Enregistre une métrique custom
     */
    public void recordCustomMetric(String metricName, String strategyName, double value) {
        meterRegistry.gauge(metricName, 
            io.micrometer.core.instrument.Tags.of("strategy", strategyName),
            value);
    }
    
    /**
     * Incrémente un compteur custom
     */
    public void incrementCustomCounter(String counterName, String... tags) {
        meterRegistry.counter(counterName, tags).increment();
    }
    
    // ========================================================================
    // HELPERS PRIVÉS
    // ========================================================================
    
    /**
     * Obtient ou crée un compteur de succès pour une strategy
     */
    private Counter getSuccessCounter(String strategyName) {
        return successCounters.computeIfAbsent(strategyName, name ->
            Counter.builder("ingestion.success")
                .tag("strategy", name)
                .description("Nombre de fichiers traités avec succès")
                .register(meterRegistry)
        );
    }
    
    /**
     * Obtient ou crée un compteur d'erreur pour une strategy
     */
    private Counter getErrorCounter(String strategyName) {
        return errorCounters.computeIfAbsent(strategyName, name ->
            Counter.builder("ingestion.error")
                .tag("strategy", name)
                .description("Nombre d'échecs d'ingestion")
                .register(meterRegistry)
        );
    }
    
    /**
     * Obtient ou crée un timer pour une strategy
     */
    private Timer getDurationTimer(String strategyName) {
        return durationTimers.computeIfAbsent(strategyName, name ->
            Timer.builder("ingestion.duration")
                .tag("strategy", name)
                .description("Durée d'ingestion par strategy")
                .publishPercentiles(0.5, 0.95, 0.99) // Median, P95, P99
                .register(meterRegistry)
        );
    }
    
    // ========================================================================
    // STATISTIQUES (pour logs/debug)
    // ========================================================================
    
    /**
     * Retourne un résumé des métriques actuelles
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Résumé Métriques Ingestion:\n");
        sb.append("  - Fichiers en cours: ").append(filesInProgress.get()).append("\n");
        
        successCounters.forEach((strategy, counter) -> {
            double count = counter.count();
            sb.append("  - ").append(strategy).append(" succès: ").append((int)count).append("\n");
        });
        
        errorCounters.forEach((strategy, counter) -> {
            double count = counter.count();
            if (count > 0) {
                sb.append("  - ").append(strategy).append(" erreurs: ").append((int)count).append("\n");
            }
        });
        
        return sb.toString();
    }
    
    /**
     * Log le résumé des métriques (utile pour monitoring manuel)
     */
    public void logSummary() {
        log.info(getSummary());
    }

    public void recordVirusDetected(String virusName) {
    log.warn("🦠 Virus: {}", virusName);
    }
    
}