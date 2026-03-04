// ============================================================================
// CONFIG - AsyncConfig.java
// Configuration du traitement asynchrone avec ThreadPool
// ============================================================================
package com.exemple.transactionservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration du traitement asynchrone pour l'ingestion.
 * 
 * Permet de traiter les fichiers de manière non-bloquante:
 * - Upload → Retour immédiat à l'utilisateur
 * - Traitement en background
 * - Notification à la fin
 * 
 * Configuration ThreadPool:
 * - Core threads: 4 (threads toujours actifs)
 * - Max threads: 8 (pic de charge)
 * - Queue capacity: 50 (files d'attente)
 * - Reject policy: CallerRuns (sécurité)
 * 
 * Usage:
 * @Async("ingestionExecutor")
 * public CompletableFuture<IngestionResult> ingestAsync(...) { ... }
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    
    @Value("${ingestion.async.core-pool-size:4}")
    private int corePoolSize;
    
    @Value("${ingestion.async.max-pool-size:8}")
    private int maxPoolSize;
    
    @Value("${ingestion.async.queue-capacity:50}")
    private int queueCapacity;
    
    @Value("${ingestion.async.thread-name-prefix:ingestion-}")
    private String threadNamePrefix;
    
    @Value("${ingestion.async.await-termination-seconds:60}")
    private int awaitTerminationSeconds;
    
    /**
     * Executor principal pour l'ingestion asynchrone
     */
    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Configuration ThreadPool
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        
        // Configuration arrêt gracieux
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        
        // Politique de rejet: CallerRuns (thread appelant exécute si queue pleine)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Initialisation
        executor.initialize();
        
        log.info("✅ ThreadPool Ingestion configuré: core={} max={} queue={}", 
            corePoolSize, maxPoolSize, queueCapacity);
        
        return executor;
    }
    
    /**
     * Executor secondaire pour tâches à faible priorité
     */
    @Bean(name = "lowPriorityExecutor")
    public Executor lowPriorityExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("low-priority-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        
        // Politique de rejet: Discard (ignore si queue pleine)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        
        executor.initialize();
        
        log.info("✅ ThreadPool Low Priority configuré");
        
        return executor;
    }
    
    /**
     * Gestionnaire d'exceptions non catchées dans threads async
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("❌ [Async] Exception non gérée dans méthode async: {}", 
                method.getName(), throwable);
            
            log.error("❌ [Async] Paramètres: {}", 
                params != null && params.length > 0 ? params[0] : "none");
            
            // TODO: Envoyer alerte (email, Slack, etc.)
        };
    }
    
    /**
     * Executor par défaut (si pas spécifié)
     */
    @Override
    public Executor getAsyncExecutor() {
        return ingestionExecutor();
    }
    
    /**
     * Bean pour monitoring du ThreadPool
     */
    @Bean
    public ThreadPoolMonitor threadPoolMonitor(
            Executor ingestionExecutor,
            Executor lowPriorityExecutor) {
        
        return new ThreadPoolMonitor(
            (ThreadPoolTaskExecutor) ingestionExecutor,
            (ThreadPoolTaskExecutor) lowPriorityExecutor
        );
    }
    
    /**
     * Classe interne pour monitoring du ThreadPool
     */
    public static class ThreadPoolMonitor {
        
        private final ThreadPoolTaskExecutor ingestionExecutor;
        private final ThreadPoolTaskExecutor lowPriorityExecutor;
        
        public ThreadPoolMonitor(
                ThreadPoolTaskExecutor ingestionExecutor,
                ThreadPoolTaskExecutor lowPriorityExecutor) {
            
            this.ingestionExecutor = ingestionExecutor;
            this.lowPriorityExecutor = lowPriorityExecutor;
        }
        
        /**
         * Retourne les statistiques du ThreadPool principal
         */
        public ThreadPoolStats getIngestionStats() {
            return new ThreadPoolStats(
                ingestionExecutor.getActiveCount(),
                ingestionExecutor.getPoolSize(),
                ingestionExecutor.getCorePoolSize(),
                ingestionExecutor.getMaxPoolSize(),
                ingestionExecutor.getThreadPoolExecutor().getQueue().size(),
                ingestionExecutor.getThreadPoolExecutor().getCompletedTaskCount()
            );
        }
        
        /**
         * Retourne les statistiques du ThreadPool low priority
         */
        public ThreadPoolStats getLowPriorityStats() {
            return new ThreadPoolStats(
                lowPriorityExecutor.getActiveCount(),
                lowPriorityExecutor.getPoolSize(),
                lowPriorityExecutor.getCorePoolSize(),
                lowPriorityExecutor.getMaxPoolSize(),
                lowPriorityExecutor.getThreadPoolExecutor().getQueue().size(),
                lowPriorityExecutor.getThreadPoolExecutor().getCompletedTaskCount()
            );
        }
        
        /**
         * Log les statistiques
         */
        public void logStats() {
            ThreadPoolStats ingestion = getIngestionStats();
            ThreadPoolStats lowPrio = getLowPriorityStats();
            
            log.info("📊 [ThreadPool] Ingestion: active={}/{} queue={} completed={}",
                ingestion.activeCount, ingestion.maxPoolSize, 
                ingestion.queueSize, ingestion.completedTasks);
            
            log.info("📊 [ThreadPool] LowPriority: active={}/{} queue={} completed={}",
                lowPrio.activeCount, lowPrio.maxPoolSize,
                lowPrio.queueSize, lowPrio.completedTasks);
        }
        
        /**
         * Vérifie si le ThreadPool est saturé
         */
        public boolean isIngestionPoolSaturated() {
            ThreadPoolStats stats = getIngestionStats();
            return stats.activeCount >= stats.maxPoolSize && 
                   stats.queueSize > (stats.maxPoolSize * 2);
        }
    }
    
    /**
     * Record pour statistiques ThreadPool
     */
    public record ThreadPoolStats(
        int activeCount,
        int poolSize,
        int corePoolSize,
        int maxPoolSize,
        int queueSize,
        long completedTasks
    ) {
        public double getUtilization() {
            return maxPoolSize > 0 ? (double) activeCount / maxPoolSize * 100 : 0;
        }
        
        public boolean isIdle() {
            return activeCount == 0 && queueSize == 0;
        }
        
        public boolean isBusy() {
            return activeCount >= corePoolSize;
        }
        
        public boolean isOverloaded() {
            return activeCount >= maxPoolSize && queueSize > 0;
        }
    }
}