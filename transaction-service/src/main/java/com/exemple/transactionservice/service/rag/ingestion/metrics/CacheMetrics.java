package com.exemple.transactionservice.service.rag.ingestion.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CacheMetrics {
    
    private final Counter cacheHits;
    private final Counter cacheMisses;
    
    public CacheMetrics(MeterRegistry registry) {
        this.cacheHits = Counter.builder("cache_hits_total")
            .description("Total cache hits")
            .tag("cache", "embedding")
            .register(registry);
        
        this.cacheMisses = Counter.builder("cache_misses_total")
            .description("Total cache misses")
            .tag("cache", "embedding")
            .register(registry);
        
        log.info("✅ CacheMetrics initialisé");
    }
    
    public void recordHit() {
        cacheHits.increment();
    }
    
    public void recordMiss() {
        cacheMisses.increment();
    }
}