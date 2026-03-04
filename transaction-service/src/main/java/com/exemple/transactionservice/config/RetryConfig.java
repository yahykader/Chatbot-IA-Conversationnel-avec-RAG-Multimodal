// ============================================================================
// CONFIG - RetryConfig.java
// Configuration Retry avec backoff exponentiel
// ============================================================================
package com.exemple.transactionservice.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Configuration du mécanisme de retry avec backoff exponentiel.
 * 
 * Permet de rejouer automatiquement les opérations qui échouent 
 * à cause d'erreurs transitoires :
 * - IOException (réseau)
 * - TimeoutException (timeout API)
 * - SocketTimeoutException (connexion)
 * 
 * Backoff exponentiel :
 * - Tentative 1 : Immédiate
 * - Tentative 2 : Wait 1s
 * - Tentative 3 : Wait 2s
 * - Tentative 4 : Wait 4s (si max=4)
 * 
 * Usage :
 * @Retryable(
 *   value = {IOException.class},
 *   maxAttempts = 3,
 *   backoff = @Backoff(delay = 1000, multiplier = 2)
 * )
 * public String callExternalApi() { ... }
 * 
 * Ou programmatique :
 * RetryTemplate template = retryTemplate();
 * template.execute(context -> callExternalApi());
 */
@Slf4j
@Configuration
@EnableRetry
public class RetryConfig {

    @Value("${ingestion.retry.max-attempts:3}")
    private int maxAttempts;  // ← Cette variable existe déjà
    
    /**
     * RetryTemplate pour usage programmatique
     * (alternative à @Retryable)
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Politique de retry
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        
        // Backoff exponentiel
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000); // 1 seconde
        backOffPolicy.setMultiplier(2.0); // x2 à chaque fois
        backOffPolicy.setMaxInterval(10000); // Max 10 secondes
        
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        // Listeners pour logging
        retryTemplate.registerListener(new RetryLoggingListener());
        
        log.info("✅ RetryTemplate configuré: maxAttempts=3, backoff=exponential(1s, x2)");
        
        return retryTemplate;
    }
    
    /**
     * RetryTemplate spécifique pour appels externes (Vision AI, etc.)
     */
    @Bean(name = "externalApiRetryTemplate")
    public RetryTemplate externalApiRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Retry seulement pour certaines exceptions
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(IOException.class, true);
        retryableExceptions.put(TimeoutException.class, true);
        retryableExceptions.put(SocketTimeoutException.class, true);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        
        // Backoff plus agressif pour API externes
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(2000); // 2 secondes
        backOffPolicy.setMultiplier(2.5); // x2.5
        backOffPolicy.setMaxInterval(20000); // Max 20 secondes
        
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.registerListener(new RetryLoggingListener());
        
        log.info("✅ ExternalApiRetryTemplate configuré: maxAttempts=3, backoff=exponential(2s, x2.5)");
        
        return retryTemplate;
    }
    
    /**
     * Listener pour logger les tentatives de retry
     */
    private static class RetryLoggingListener implements org.springframework.retry.RetryListener {
        
        @Override
        public <T, E extends Throwable> boolean open(
                org.springframework.retry.RetryContext context, 
                org.springframework.retry.RetryCallback<T, E> callback) {
            
            // Premier essai
            log.debug("🔄 [Retry] Tentative initiale");
            return true; // Continue
        }
        
        @Override
        public <T, E extends Throwable> void close(
                org.springframework.retry.RetryContext context, 
                org.springframework.retry.RetryCallback<T, E> callback, 
                Throwable throwable) {
            
            int retryCount = context.getRetryCount();
            
            if (throwable == null) {
                if (retryCount > 0) {
                    log.info("✅ [Retry] Succès après {} tentatives", retryCount + 1);
                }
            } else {
                log.error("❌ [Retry] Échec définitif après {} tentatives: {}", 
                    retryCount + 1, throwable.getMessage());
            }
        }
        
        @Override
        public <T, E extends Throwable> void onError(
                org.springframework.retry.RetryContext context, 
                org.springframework.retry.RetryCallback<T, E> callback, 
                Throwable throwable) {
            
            int retryCount = context.getRetryCount();
            
            log.warn("⚠️ [Retry] Tentative {} échouée: {} - Nouvelle tentative...", 
                retryCount + 1, throwable.getClass().getSimpleName());
        }
    }
    
    /**
     * Helper pour usage manuel du retry
     */
    @Bean
    public RetryHelper retryHelper(RetryTemplate retryTemplate) {
        return new RetryHelper(retryTemplate, maxAttempts);
    }
    
    /**
     * Classe helper pour simplifier l'usage du retry
     */
    public static class RetryHelper {
        
        private final RetryTemplate retryTemplate;
        private final int maxAttempts;
        
        public RetryHelper(RetryTemplate retryTemplate, int maxAttempts) {
            this.retryTemplate = retryTemplate;
            this.maxAttempts = maxAttempts;
        }
        
        /**
         * Exécute une opération avec retry
         */
        public <T> T executeWithRetry(Supplier<T> operation) throws Exception {
            try {
                return retryTemplate.execute(context -> operation.get());
            } catch (Exception e) {
                log.error("Échec après {} tentatives", maxAttempts, e);
                throw e;
            }
        }
        /**
         * Exécute une opération avec retry et fallback
         */
        public <T> T executeWithRetryAndFallback (RetryableOperation<T> operation, T fallbackValue) throws Exception {
           try {
                return retryTemplate.execute(
                    context -> operation.execute(),
                    context -> {
                        log.warn("⚠️ [Retry] Fallback utilisé après échecs");
                    return fallbackValue;
                }
            );
           }
           catch(Exception e) {
                log.error("Échec après {} tentatives retry et fallback", e);
                throw e;
           } 

        }
        
        /**
         * Interface fonctionnelle pour opérations retry
         */
        @FunctionalInterface
        public interface RetryableOperation<T> {
            T execute() throws Exception;
        }
    }
}