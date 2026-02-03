package com.exemple.transactionservice.service.rag.ingestion.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpenAiMetrics {

    private final MeterRegistry registry;
    
    private final Counter apiCalls;
    private final Counter apiErrors;
    private final Timer apiDuration;
    
    public OpenAiMetrics(MeterRegistry registry) {
        this.registry = registry;
        
        this.apiCalls = Counter.builder("openai_api_calls_total")
            .description("Total OpenAI API calls")
            .tag("service", "openai")
            .register(registry);
        
        this.apiErrors = Counter.builder("openai_api_errors_total")
            .description("Total OpenAI API errors")
            .tag("service", "openai")
            .register(registry);
        
        this.apiDuration = Timer.builder("openai_api_duration_seconds")
            .description("OpenAI API call duration")
            .tag("service", "openai")
            .register(registry);
        
        log.info("✅ OpenAiMetrics initialisé");
    }

    public void recordCall(String operation, long durationMs) {
        apiCalls.increment(); // global

        registry.counter("openai_api_calls_total", "service", "openai", "operation", operation)
                .increment();

        apiDuration.record(java.time.Duration.ofMillis(durationMs));
    }
    
    public void recordError(String operation) {
        apiErrors.increment(); // global

        registry.counter("openai_api_errors_total", "service", "openai", "operation", operation)
                .increment();
    }
}