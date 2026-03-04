// ============================================================================
// CONFIG - CircuitBreakerConfig.java
// Configuration Circuit Breaker avec Resilience4j
// ============================================================================
package com.exemple.transactionservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration Circuit Breaker pour protéger les services externes.
 * 
 * Circuit Breaker Pattern :
 * - Protège l'application contre les défaillances en cascade
 * - États : CLOSED → OPEN → HALF_OPEN → CLOSED
 * - Fallback automatique quand service externe indisponible
 * 
 * Services protégés :
 * 1. Vision AI (GPT-4 Vision, Claude Vision, etc.)
 * 2. Redis (déduplication, cache)
 * 3. ClamAV (antivirus)
 * 4. External APIs (si utilisées)
 * 
 * Configuration par service :
 * - visionAI : 50% failure rate → OPEN, 30s wait, 5 calls test
 * - redis : 70% failure rate → OPEN, 10s wait, 3 calls test
 * - clamAV : 60% failure rate → OPEN, 20s wait, 3 calls test
 * - default : 50% failure rate → OPEN, 30s wait, 5 calls test
 * 
 * Usage dans services :
 * <pre>
 * @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackAnalysis")
 * public String analyzeImage(BufferedImage image) throws IOException {
 *     return visionModel.generate(...);
 * }
 * 
 * private String fallbackAnalysis(BufferedImage image, Exception e) {
 *     log.warn("Circuit breaker activé, fallback");
 *     return "[Image non analysée - service temporairement indisponible]";
 * }
 * </pre>
 */
@Slf4j
@Configuration
public class CircuitBreakerConfiguration {
    
    /**
     * Configuration Circuit Breaker pour Vision AI
     * 
     * Vision AI peut être lent ou indisponible (rate limits, timeouts).
     * Configuration plus tolérante avec wait duration plus longue.
     */
    @Bean
    public io.github.resilience4j.circuitbreaker.CircuitBreakerConfig visionAICircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            // Nombre minimum d'appels avant calcul du taux d'échec
            .minimumNumberOfCalls(10)
            
            // Seuil d'échec pour ouvrir le circuit (50%)
            .failureRateThreshold(50.0f)
            
            // Durée en état OPEN avant passage en HALF_OPEN
            .waitDurationInOpenState(Duration.ofSeconds(30))
            
            // Nombre d'appels en HALF_OPEN pour tester le service
            .permittedNumberOfCallsInHalfOpenState(5)
            
            // Seuil de slow calls (appels > slowCallDurationThreshold)
            .slowCallRateThreshold(80.0f)
            .slowCallDurationThreshold(Duration.ofSeconds(10))
            
            // Taille du sliding window (ring buffer)
            .slidingWindowType(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            
            // Exceptions considérées comme échecs
            .recordExceptions(
                java.io.IOException.class,
                java.util.concurrent.TimeoutException.class,
                RuntimeException.class
            )
            
            // Exceptions ignorées (ne comptent pas comme échecs)
            .ignoreExceptions(
                IllegalArgumentException.class
            )
            
            .build();
    }
    
    /**
     * Configuration Circuit Breaker pour Redis
     * 
     * Redis doit être très disponible. Configuration plus stricte.
     */
    @Bean
    public io.github.resilience4j.circuitbreaker.CircuitBreakerConfig redisCircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .minimumNumberOfCalls(5)
            
            // Seuil plus élevé car Redis est critique (70%)
            .failureRateThreshold(70.0f)
            
            // Wait duration courte car Redis doit revenir vite
            .waitDurationInOpenState(Duration.ofSeconds(10))
            
            .permittedNumberOfCallsInHalfOpenState(3)
            
            .slowCallRateThreshold(90.0f)
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            
            .slidingWindowType(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            
            .recordExceptions(
                org.springframework.data.redis.RedisConnectionFailureException.class,
                java.io.IOException.class,
                java.util.concurrent.TimeoutException.class
            )
            
            .build();
    }
    
    /**
     * Configuration Circuit Breaker pour ClamAV Antivirus
     * 
     * ClamAV peut être lent sur gros fichiers.
     */
    @Bean
    public io.github.resilience4j.circuitbreaker.CircuitBreakerConfig clamAVCircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .minimumNumberOfCalls(5)
            
            .failureRateThreshold(60.0f)
            
            .waitDurationInOpenState(Duration.ofSeconds(20))
            
            .permittedNumberOfCallsInHalfOpenState(3)
            
            .slowCallRateThreshold(85.0f)
            .slowCallDurationThreshold(Duration.ofSeconds(15))
            
            .slidingWindowType(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(15)
            
            .recordExceptions(
                java.io.IOException.class,
                java.util.concurrent.TimeoutException.class,
                java.net.SocketTimeoutException.class
            )
            
            .build();
    }
    
    /**
     * Configuration par défaut pour autres services
     */
    @Bean
    public io.github.resilience4j.circuitbreaker.CircuitBreakerConfig defaultCircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .minimumNumberOfCalls(10)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowSize(20)
            .build();
    }
    
    /**
     * Registry principal des Circuit Breakers
     * 
     * Permet de créer des circuit breakers avec configurations nommées
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        
        // Enregistrer les configurations
        registry.addConfiguration("visionAI", visionAICircuitBreakerConfig());
        registry.addConfiguration("redis", redisCircuitBreakerConfig());
        registry.addConfiguration("clamAV", clamAVCircuitBreakerConfig());
        registry.addConfiguration("custom", defaultCircuitBreakerConfig());
        
        // Logger les événements du registry
        registry.getEventPublisher()
            .onEntryAdded(event -> 
                log.info("✅ [CircuitBreaker] Nouveau circuit breaker ajouté: {}", 
                    event.getAddedEntry().getName())
            )
            .onEntryRemoved(event -> 
                log.info("🗑️ [CircuitBreaker] Circuit breaker supprimé: {}", 
                    event.getRemovedEntry().getName())
            );
        
        log.info("✅ CircuitBreakerRegistry initialisé avec {} configurations", 
            registry.getAllCircuitBreakers().size());
        
        return registry;
    }
    
    /**
     * Circuit Breaker pour Vision AI
     */
    @Bean
    public CircuitBreaker visionAICircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("visionAI", "visionAI");
        
        // Logger les changements d'état
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("🔄 [CircuitBreaker] Vision AI - Transition: {} → {}", 
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState())
            )
            .onError(event -> 
                log.debug("❌ [CircuitBreaker] Vision AI - Erreur: {}", 
                    event.getThrowable().getClass().getSimpleName())
            )
            .onSuccess(event -> 
                log.debug("✅ [CircuitBreaker] Vision AI - Succès (durée: {}ms)", 
                    event.getElapsedDuration().toMillis())
            );
        
        return circuitBreaker;
    }
    
    /**
     * Circuit Breaker pour Redis
     */
    @Bean
    public CircuitBreaker redisCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("redis", "redis");
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("🔄 [CircuitBreaker] Redis - Transition: {} → {}", 
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState())
            )
            .onError(event -> 
                log.debug("❌ [CircuitBreaker] Redis - Erreur: {}", 
                    event.getThrowable().getClass().getSimpleName())
            );
        
        return circuitBreaker;
    }
    
    /**
     * Circuit Breaker pour ClamAV
     */
    @Bean
    public CircuitBreaker clamAVCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("clamAV", "clamAV");
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("🔄 [CircuitBreaker] ClamAV - Transition: {} → {}", 
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState())
            )
            .onError(event -> 
                log.debug("❌ [CircuitBreaker] ClamAV - Erreur: {}", 
                    event.getThrowable().getClass().getSimpleName())
            );
        
        return circuitBreaker;
    }
    
    /**
     * Retourne les statistiques de tous les circuit breakers
     */
    public static class CircuitBreakerStats {
        private final String name;
        private final String state;
        private final int failureRate;
        private final int bufferedCalls;
        private final int failedCalls;
        private final int successfulCalls;
        
        public CircuitBreakerStats(CircuitBreaker cb) {
            this.name = cb.getName();
            this.state = cb.getState().name();
            
            CircuitBreaker.Metrics metrics = cb.getMetrics();
            this.failureRate = (int) metrics.getFailureRate();
            this.bufferedCalls = metrics.getNumberOfBufferedCalls();
            this.failedCalls = metrics.getNumberOfFailedCalls();
            this.successfulCalls = metrics.getNumberOfSuccessfulCalls();
        }
        
        // Getters
        public String getName() { return name; }
        public String getState() { return state; }
        public int getFailureRate() { return failureRate; }
        public int getBufferedCalls() { return bufferedCalls; }
        public int getFailedCalls() { return failedCalls; }
        public int getSuccessfulCalls() { return successfulCalls; }
    }
}