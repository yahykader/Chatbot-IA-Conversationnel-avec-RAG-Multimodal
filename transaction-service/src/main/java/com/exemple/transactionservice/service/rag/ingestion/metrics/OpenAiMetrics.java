package com.exemple.transactionservice.service.rag.ingestion.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Duration;

/**
 * Métriques OpenAI
 * 
 * ✅ CORRECTION: Tags cohérents pour tous les counters
 */
@Slf4j
@Component
public class OpenAiMetrics {

    private final MeterRegistry registry;
    
    public OpenAiMetrics(MeterRegistry registry) {
        this.registry = registry;
        log.info("✅ OpenAiMetrics initialisé");
    }

    /**
     * Enregistre un appel API OpenAI
     * 
     * @param operation Type d'opération (embed_text, completion, etc.)
     * @param durationMs Durée en millisecondes
     */
    public void recordCall(String operation, long durationMs) {
        // ✅ CORRECTION: Toujours inclure le tag "operation"
        Counter.builder("openai_api_calls_total")
            .description("Total OpenAI API calls")
            .tag("service", "openai")
            .tag("operation", operation != null ? operation : "unknown")  // ✅ Tag cohérent
            .register(registry)
            .increment();
        
        // Enregistrer durée
        Timer.builder("openai_api_duration_seconds")
            .description("OpenAI API call duration")
            .tag("service", "openai")
            .tag("operation", operation != null ? operation : "unknown")  // ✅ Tag cohérent
            .register(registry)
            .record(Duration.ofMillis(durationMs));
    }
    
    /**
     * Enregistre une erreur API OpenAI
     * 
     * @param operation Type d'opération qui a échoué
     */
    public void recordError(String operation) {
        // ✅ CORRECTION: Tag cohérent
        Counter.builder("openai_api_errors_total")
            .description("Total OpenAI API errors")
            .tag("service", "openai")
            .tag("operation", operation != null ? operation : "unknown")  // ✅ Tag cohérent
            .register(registry)
            .increment();
    }
    
    /**
     * Enregistre un appel avec succès/échec
     * 
     * @param operation Type d'opération
     * @param success true si succès, false si erreur
     * @param durationMs Durée
     */
    public void recordCallWithStatus(String operation, boolean success, long durationMs) {
        recordCall(operation, durationMs);
        
        if (!success) {
            recordError(operation);
        }
    }
}