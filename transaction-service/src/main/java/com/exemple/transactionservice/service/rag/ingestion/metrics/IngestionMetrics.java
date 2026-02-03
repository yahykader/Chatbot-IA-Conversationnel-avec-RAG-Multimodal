// ============================================================================
// SERVICE - IngestionMetrics.java (VERSION AMÉLIORÉE)
// Service de métriques pour monitoring avec Prometheus + Grafana
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service centralisé de métriques pour l'ingestion de documents.
 * 
 * ✅ AMÉLIORATIONS v2.0:
 * - Métriques compatibles avec dashboard Grafana
 * - Noms standardisés Prometheus (snake_case avec _total)
 * - Gauges pour valeurs cumulées
 * - Support métriques OpenAI et Cache
 * 
 * Expose des métriques Prometheus pour :
 * - Nombre de fichiers traités (succès/échec) par strategy
 * - Durée d'ingestion par strategy
 * - Nombre d'embeddings créés par type
 * - Taux d'erreur par type d'erreur
 * - Fichiers en cours de traitement
 * - Duplicates détectés
 * - Virus détectés
 * 
 * Visualisation : Grafana dashboard
 * 
 * Exemple de métriques exposées:
 * - ingestion_files_total{strategy="DOCX"} 1523
 * - ingestion_duration_seconds{strategy="DOCX",quantile="0.95"} 3.4
 * - ingestion_embeddings_total{type="text"} 45678
 */
@Slf4j
@Component
public class IngestionMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // ========================================================================
    // COMPTEURS GLOBAUX (pour dashboard Grafana)
    // ========================================================================
    
    // Compteur total de fichiers traités
    private final Counter filesTotalCounter;
    
    // Compteur total d'embeddings créés
    private final Counter embeddingsTotalCounter;
    
    // Compteur total d'erreurs
    private final Counter errorsTotalCounter;
    
    // Compteur total de duplicates
    private final Counter duplicatesTotalCounter;
    
    // ========================================================================
    // COMPTEURS PAR STRATÉGIE
    // ========================================================================
    
    // Compteurs par strategy
    private final Map<String, Counter> successCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    
    // Timers par strategy
    private final Map<String, Timer> durationTimers = new ConcurrentHashMap<>();
    
    // ========================================================================
    // GAUGES (valeurs actuelles/cumulées)
    // ========================================================================
    
    // Gauge pour fichiers en cours
    private final AtomicInteger filesInProgress = new AtomicInteger(0);
    
    // Gauges pour totaux cumulés (depuis démarrage)
    private final AtomicLong totalFilesProcessed = new AtomicLong(0);
    private final AtomicLong totalEmbeddingsCreated = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalDuplicates = new AtomicLong(0);
    
    public IngestionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // ====================================================================
        // COMPTEURS GLOBAUX (noms compatibles Grafana)
        // ====================================================================
        
        this.filesTotalCounter = Counter.builder("ingestion_files_total")
            .description("Total files processed successfully")
            .tag("application", "rag-system")
            .register(meterRegistry);
        
        this.embeddingsTotalCounter = Counter.builder("ingestion_embeddings_total")
            .description("Total embeddings created")
            .tag("application", "rag-system")
            .register(meterRegistry);
        
        this.errorsTotalCounter = Counter.builder("ingestion_errors_total")
            .description("Total ingestion errors")
            .tag("application", "rag-system")
            .register(meterRegistry);
        
        this.duplicatesTotalCounter = Counter.builder("ingestion_duplicates_total")
            .description("Total duplicate files detected")
            .tag("application", "rag-system")
            .register(meterRegistry);
        
        // ====================================================================
        // GAUGES (valeurs actuelles)
        // ====================================================================
        
        // Fichiers en cours de traitement
        Gauge.builder("ingestion_active_processing", filesInProgress, AtomicInteger::get)
            .description("Number of files currently being processed")
            .tag("application", "rag-system")
            .register(meterRegistry);
        
        // Total fichiers traités (cumulé depuis démarrage)
        Gauge.builder("ingestion_total_processed", totalFilesProcessed, AtomicLong::get)
            .description("Total files processed since startup")
            .tag("application", "rag-system")
            .register(meterRegistry);
        
        // Total embeddings créés (cumulé depuis démarrage)
        Gauge.builder("ingestion_total_embeddings", totalEmbeddingsCreated, AtomicLong::get)
            .description("Total embeddings created since startup")
            .tag("application", "rag-system")
            .register(meterRegistry);
        
        // Total erreurs (cumulé depuis démarrage)
        Gauge.builder("ingestion_total_errors", totalErrors, AtomicLong::get)
            .description("Total errors since startup")
            .tag("application", "rag-system")
            .register(meterRegistry);
        
        log.info("✅ IngestionMetrics initialisé - Métriques Prometheus activées");
        log.info("   - Compteurs globaux: files, embeddings, errors, duplicates");
        log.info("   - Gauges: active_processing, total_processed, total_embeddings");
        log.info("   - Timers: duration par strategy avec percentiles (P50, P95, P99)");
    }
    
    // ========================================================================
    // ENREGISTREMENT MÉTRIQUES - SUCCÈS
    // ========================================================================
    
    /**
     * Enregistre un succès d'ingestion
     * 
     * ✅ AMÉLIORATION: Incrémente à la fois les compteurs globaux ET par strategy
     * 
     * @param strategyName Nom de la strategy (ex: "DOCX", "PDF", "XLSX")
     * @param durationMs Durée en millisecondes
     * @param textEmbeddings Nombre d'embeddings texte créés
     * @param imageEmbeddings Nombre d'embeddings image créés
     */
    public void recordSuccess(
            String strategyName, 
            long durationMs,
            int textEmbeddings,
            int imageEmbeddings) {
        
        // ✅ Incrémenter compteur global
        filesTotalCounter.increment();
        totalFilesProcessed.incrementAndGet();
        
        // ✅ Incrémenter compteur par strategy
        getSuccessCounter(strategyName).increment();
        
        // ✅ Enregistrer durée (avec percentiles pour Grafana)
        getDurationTimer(strategyName).record(durationMs, TimeUnit.MILLISECONDS);
        
        // ✅ Enregistrer embeddings créés
        int totalEmbeddings = textEmbeddings + imageEmbeddings;
        
        if (totalEmbeddings > 0) {
            embeddingsTotalCounter.increment(totalEmbeddings);
            totalEmbeddingsCreated.addAndGet(totalEmbeddings);
        }
        
        if (textEmbeddings > 0) {
            meterRegistry.counter("ingestion_embeddings_total",
                "strategy", strategyName,
                "type", "text").increment(textEmbeddings);
        }
        
        if (imageEmbeddings > 0) {
            meterRegistry.counter("ingestion_embeddings_total",
                "strategy", strategyName,
                "type", "image").increment(imageEmbeddings);
        }
        
        log.debug("📊 [Metrics] Success: strategy={} duration={}ms text={} images={} total={}", 
            strategyName, durationMs, textEmbeddings, imageEmbeddings, totalEmbeddings);
    }
    
    // ========================================================================
    // ENREGISTREMENT MÉTRIQUES - ERREURS
    // ========================================================================
    
    /**
     * Enregistre un échec d'ingestion
     * 
     * ✅ AMÉLIORATION: Incrémente compteurs globaux + par strategy + par type d'erreur
     * 
     * @param strategyName Nom de la strategy
     * @param errorType Type d'erreur (classe de l'exception)
     * @param durationMs Durée avant échec
     */
    public void recordError(
            String strategyName, 
            String errorType,
            long durationMs) {
        
        // ✅ Incrémenter compteur global
        errorsTotalCounter.increment();
        totalErrors.incrementAndGet();
        
        // ✅ Incrémenter compteur par strategy
        getErrorCounter(strategyName).increment();
        
        // ✅ Compteur par type d'erreur (pour Grafana)
        meterRegistry.counter("ingestion_errors_total",
            "strategy", strategyName,
            "error_type", errorType).increment();
        
        // ✅ Durée avant échec (utile pour détecter timeouts)
        getDurationTimer(strategyName).record(durationMs, TimeUnit.MILLISECONDS);
        
        log.debug("📊 [Metrics] Error: strategy={} type={} duration={}ms", 
            strategyName, errorType, durationMs);
    }
    
    // ========================================================================
    // AUTRES MÉTRIQUES
    // ========================================================================
    
    /**
     * Enregistre une taille de fichier traité
     */
    public void recordFileSize(String strategyName, long sizeBytes) {
        meterRegistry.summary("ingestion_file_size_bytes",
            "strategy", strategyName).record(sizeBytes);
        
        log.debug("📊 [Metrics] FileSize: strategy={} bytes={}", strategyName, sizeBytes);
    }
    
    /**
     * Enregistre qu'un fichier a été dédupliqué
     * 
     * ✅ AMÉLIORATION: Incrémente compteur global + par strategy
     */
    public void recordDuplicate(String strategyName) {
        duplicatesTotalCounter.increment();
        totalDuplicates.incrementAndGet();
        
        meterRegistry.counter("ingestion_duplicates_total",
            "strategy", strategyName).increment();
        
        log.debug("📊 [Metrics] Duplicate: strategy={}", strategyName);
    }
    
    /**
     * Enregistre un retry (tentative supplémentaire)
     */
    public void recordRetry(String strategyName, int attemptNumber) {
        meterRegistry.counter("ingestion_retry_total",
            "strategy", strategyName,
            "attempt", String.valueOf(attemptNumber)).increment();
        
        log.debug("📊 [Metrics] Retry: strategy={} attempt={}", 
            strategyName, attemptNumber);
    }
    
    /**
     * Enregistre un virus détecté
     * 
     * ✅ AMÉLIORATION: Métrique dédiée pour antivirus
     */
    public void recordVirusDetected(String virusName) {
        meterRegistry.counter("ingestion_virus_detected_total",
            "virus_name", virusName).increment();
        
        log.warn("🦠 [Metrics] Virus détecté: {}", virusName);
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
     * 
     * ✅ AMÉLIORATION: Nom standardisé pour Grafana
     */
    private Counter getSuccessCounter(String strategyName) {
        return successCounters.computeIfAbsent(strategyName, name ->
            Counter.builder("ingestion_files_total")
                .tag("strategy", name)
                .tag("status", "success")
                .description("Nombre de fichiers traités avec succès par strategy")
                .register(meterRegistry)
        );
    }
    
    /**
     * Obtient ou crée un compteur d'erreur pour une strategy
     * 
     * ✅ AMÉLIORATION: Nom standardisé pour Grafana
     */
    private Counter getErrorCounter(String strategyName) {
        return errorCounters.computeIfAbsent(strategyName, name ->
            Counter.builder("ingestion_files_total")
                .tag("strategy", name)
                .tag("status", "error")
                .description("Nombre d'échecs d'ingestion par strategy")
                .register(meterRegistry)
        );
    }
    
    /**
     * Obtient ou crée un timer pour une strategy
     * 
     * ✅ AMÉLIORATION: 
     * - Nom en secondes (convention Prometheus)
     * - Percentiles P50, P95, P99 pour Grafana
     */
    private Timer getDurationTimer(String strategyName) {
        return durationTimers.computeIfAbsent(strategyName, name ->
            Timer.builder("ingestion_duration_seconds")
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
     * 
     * ✅ AMÉLIORATION: Affiche les totaux cumulés
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔════════════════════════════════════════════════════════╗\n");
        sb.append("║         📊 RÉSUMÉ MÉTRIQUES INGESTION                 ║\n");
        sb.append("╠════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Fichiers en cours         : %-25d ║\n", filesInProgress.get()));
        sb.append(String.format("║ Total traités (démarrage) : %-25d ║\n", totalFilesProcessed.get()));
        sb.append(String.format("║ Total embeddings          : %-25d ║\n", totalEmbeddingsCreated.get()));
        sb.append(String.format("║ Total erreurs             : %-25d ║\n", totalErrors.get()));
        sb.append(String.format("║ Total duplicates          : %-25d ║\n", totalDuplicates.get()));
        sb.append("╠════════════════════════════════════════════════════════╣\n");
        
        if (!successCounters.isEmpty()) {
            sb.append("║ PAR STRATÉGIE:                                         ║\n");
            successCounters.forEach((strategy, counter) -> {
                double count = counter.count();
                sb.append(String.format("║   %-15s : %8d succès               ║\n", 
                    strategy, (int)count));
            });
        }
        
        if (!errorCounters.isEmpty() && totalErrors.get() > 0) {
            sb.append("║ ERREURS PAR STRATÉGIE:                                 ║\n");
            errorCounters.forEach((strategy, counter) -> {
                double count = counter.count();
                if (count > 0) {
                    sb.append(String.format("║   %-15s : %8d erreurs              ║\n", 
                        strategy, (int)count));
                }
            });
        }
        
        sb.append("╚════════════════════════════════════════════════════════╝");
        
        return sb.toString();
    }
    
    /**
     * Log le résumé des métriques (utile pour monitoring manuel)
     */
    public void logSummary() {
        log.info(getSummary());
    }
    
    /**
     * Réinitialise les compteurs (utile pour les tests)
     * ⚠️ À utiliser uniquement en développement
     */
    public void reset() {
        filesInProgress.set(0);
        totalFilesProcessed.set(0);
        totalEmbeddingsCreated.set(0);
        totalErrors.set(0);
        totalDuplicates.set(0);
        
        log.warn("⚠️ Métriques réinitialisées (dev only)");
    }
}
/*

## 🎯 Principales améliorations

| Amélioration | Avant | Après |
|--------------|-------|-------|
| **Noms métriques** | `ingestion.success` | `ingestion_files_total` ✅ |
| **Durée** | Millisecondes | Secondes (convention Prometheus) ✅ |
| **Gauges cumulées** | ❌ Manquant | `ingestion_total_processed`, `ingestion_total_embeddings` ✅ |
| **Compteurs globaux** | ❌ Manquant | `ingestion_files_total`, `ingestion_embeddings_total` ✅ |
| **Percentiles** | P50, P95, P99 | P50, P95, P99 ✅ (déjà bon) |
| **Tags standardisés** | Mixte | `strategy`, `type`, `error_type` ✅ |
| **Résumé visuel** | Basique | Tableau formaté ✅ |

---

## 🚀 Avantages de cette version

    1. ✅ **Compatible Grafana** - Les noms de métriques correspondent exactement au dashboard
    2. ✅ **Compteurs globaux** - Permet d'avoir des totaux dans Grafana
    3. ✅ **Gauges cumulées** - Affiche les totaux depuis le démarrage
    4. ✅ **Noms standardisés** - Convention Prometheus (`snake_case` + `_total` pour compteurs)
    5. ✅ **Meilleur debug** - Résumé formaté en tableau

---

## 📊 Métriques exposées (compatibles dashboard)
```
    # Compteurs
        ingestion_files_total{strategy="DOCX"} 42
        ingestion_embeddings_total{type="text"} 1536
        ingestion_errors_total{strategy="PDF",error_type="IOException"} 2
        ingestion_duplicates_total{strategy="DOCX"} 3

    # Gauges
        ingestion_active_processing 2
        ingestion_total_processed 42
        ingestion_total_embeddings 15360

    # Timers (avec percentiles)
        ingestion_duration_seconds{strategy="DOCX",quantile="0.5"} 2.5
        ingestion_duration_seconds{strategy="DOCX",quantile="0.95"} 5.2
        ingestion_duration_seconds{strategy="DOCX",quantile="0.99"} 8.7

*/

