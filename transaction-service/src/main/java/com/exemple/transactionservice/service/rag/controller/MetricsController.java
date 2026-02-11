package com.exemple.transactionservice.service.rag.controller;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

/**
 * Controller pour métriques et health checks
 * 
 * Endpoints:
 * - GET /metrics - Prometheus format
 * - GET /health - Application health
 * - GET /metrics/summary - Human-readable summary
 */
@Slf4j
@RestController
@RequestMapping("/actuator")
public class MetricsController {
    
    private final PrometheusMeterRegistry prometheusRegistry;
    
    public MetricsController(PrometheusMeterRegistry prometheusRegistry) {
        log.info("════════════════════════════════════════════════════════════");
        log.info("🚀 MetricsController CONSTRUCTOR");  // ⬅️ Ajoutez ceci
        log.info("   PrometheusMeterRegistry: {}", prometheusRegistry.getClass().getName());
        log.info("   Meters count: {}", prometheusRegistry.getMeters().size());
        log.info("════════════════════════════════════════════════════════════");
        this.prometheusRegistry = prometheusRegistry;
    }
    
    /**
     * Endpoint Prometheus (scrape target)
     * 
     * GET /actuator/prometheus
     * 
     * Returns metrics in Prometheus format
     */
    @GetMapping(value = "/prometheus", produces = "text/plain")
    public String prometheus() {
        log.info("📊 /actuator/prometheus appelé");  // ⬅️ Ajoutez ceci
        String scrape = prometheusRegistry.scrape();
        log.info("   Scrape length: {} bytes", scrape.length());  // ⬅️ Ajoutez ceci
        return scrape;
    }
    
    /**
     * Health check endpoint
     * 
     * GET /actuator/health
     */
    @GetMapping("/health")
    public ResponseEntity<Health> health() {
        // TODO: Add custom health checks
        Health health = Health.up()
            .withDetail("status", "UP")
            .withDetail("application", "rag-assistant")
            .build();
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Metrics summary (human-readable)
     * 
     * GET /actuator/metrics/summary
     */
    @GetMapping("/metrics/summary")
    public ResponseEntity<Map<String, Object>> metricsSummary() {
        // Extract key metrics
        double queryDurationP95 = prometheusRegistry.get("rag.query.duration")
            .timer()
            .mean(java.util.concurrent.TimeUnit.MILLISECONDS);
        
        long queriesTotal = (long) prometheusRegistry.get("rag.queries.total")
            .counter()
            .count();
        
        long queriesSuccess = (long) prometheusRegistry.get("rag.queries.success")
            .counter()
            .count();
        
        long activeConnections = (long) prometheusRegistry.get("rag.connections.active")
            .gauge()
            .value();
        
        Map<String, Object> summary = Map.of(
            "queries", Map.of(
                "total", queriesTotal,
                "success", queriesSuccess,
                "success_rate", queriesTotal > 0 ? (double) queriesSuccess / queriesTotal : 0.0
            ),
            "performance", Map.of(
                "avg_duration_ms", queryDurationP95
            ),
            "connections", Map.of(
                "active", activeConnections
            )
        );
        
        return ResponseEntity.ok(summary);
    }
}