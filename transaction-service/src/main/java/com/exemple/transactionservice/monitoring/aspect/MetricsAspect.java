package com.exemple.transactionservice.monitoring.aspect;

import com.exemple.transactionservice.monitoring.MetricsService;
import com.exemple.transactionservice.service.rag.retrieval.RetrievalAugmentorOrchestrator.RetrievalAugmentorResult;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Aspect AOP pour collecter métriques automatiquement
 *
 * Intercepte les méthodes clés et enregistre métriques
 */
@Slf4j
@Aspect
@Component
public class MetricsAspect {

    private final MetricsService metricsService;

    public MetricsAspect(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * Track Retrieval Augmentor execution
     */
    @Around("execution(* com.exemple.transactionservice.service.rag.retrieval.RetrievalAugmentorOrchestrator.execute(..))")
    public Object trackRetrievalAugmentor(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metricsService.startQuery();

        try {
            Object result = joinPoint.proceed();

            if (result instanceof RetrievalAugmentorResult augmentorResult) {
                if (augmentorResult.isSuccess()) {

                    String strategy = augmentorResult.getRoutingDecision().getStrategy().name();

                    // Record retrieval metrics
                    metricsService.recordRetrieval(
                        augmentorResult.getTotalDurationMs(),
                        augmentorResult.getAggregatedContext().getInputChunks(),
                        augmentorResult.getAggregatedContext().getFinalSelected(),
                        strategy
                    );

                    // Record average relevance
                    double avgRelevance = calculateAverageRelevance(augmentorResult);
                    metricsService.recordAverageRelevance(avgRelevance);

                    // Check if empty result
                    if (augmentorResult.getAggregatedContext().getFinalSelected() == 0) {
                        metricsService.recordEmptyResult();
                    }

                    // ✅ FIX: marquer le succès (sinon query latency/success rate restent vides)
                    metricsService.recordQuerySuccess(sample, strategy);

                } else {
                    // Échec "fonctionnel" -> on le compte comme erreur
                    metricsService.recordQueryError(sample, "RetrievalFailed");
                }
            } else {
                // Si type inattendu, on compte comme succès générique
                metricsService.recordQuerySuccess(sample, "unknown");
            }

            return result;

        } catch (Throwable e) {
            metricsService.recordQueryError(sample, e.getClass().getSimpleName());
            throw e;
        }
    }

    /**
     * Track streaming orchestrator
     */
    @Around("execution(* com.exemple.transactionservice.service.rag.streaming.StreamingOrchestrator.executeStreaming(..))")
    public Object trackStreamingOrchestrator(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = metricsService.startQuery();

        try {
            Object result = joinPoint.proceed();

            // Record success
            metricsService.recordQuerySuccess(sample, "streaming");

            return result;

        } catch (Throwable e) {
            metricsService.recordQueryError(sample, e.getClass().getSimpleName());
            throw e;
        }
    }

    /**
     * Track SSE connections
     */
    @Around("execution(* com.exemple.transactionservice.controller.StreamingAssistantController.stream(..))")
    public Object trackSSEConnection(ProceedingJoinPoint joinPoint) throws Throwable {
        metricsService.incrementActiveConnections();

        Object result = joinPoint.proceed();

        // ✅ FIX: décrémenter à la fin réelle de la connexion
        if (result instanceof SseEmitter emitter) {
            emitter.onCompletion(metricsService::decrementActiveConnections);
            emitter.onTimeout(metricsService::decrementActiveConnections);
            emitter.onError(ex -> metricsService.decrementActiveConnections());
        } else {
            // fallback si ce n’est pas un emitter
            metricsService.decrementActiveConnections();
        }

        return result;
    }

    /**
     * Track conversation operations
     */
    @Around("execution(* com.exemple.transactionservice.service.rag.streaming.ConversationManager.createConversation(..))")
    public Object trackConversationCreation(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        // Count conversation created
        metricsService.recordCacheMiss("conversation"); // New conversation = cache miss

        return result;
    }

    @Around("execution(* com.exemple.transactionservice.service.rag.streaming.ConversationManager.getConversation(..))")
    public Object trackConversationRetrieval(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        if (result != null && ((java.util.Optional<?>) result).isPresent()) {
            metricsService.recordCacheHit("conversation");
        } else {
            metricsService.recordCacheMiss("conversation");
        }

        return result;
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private double calculateAverageRelevance(RetrievalAugmentorResult result) {
        if (result.getAggregatedContext() == null ||
            result.getAggregatedContext().getChunks().isEmpty()) {
            return 0.0;
        }

        return result.getAggregatedContext().getChunks().stream()
            .mapToDouble(chunk -> chunk.getFinalScore())
            .average()
            .orElse(0.0);
    }
}