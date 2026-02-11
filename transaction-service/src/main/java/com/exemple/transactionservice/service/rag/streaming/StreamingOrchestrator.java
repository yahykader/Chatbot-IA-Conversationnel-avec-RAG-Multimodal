package com.exemple.transactionservice.service.rag.streaming;

import com.exemple.transactionservice.service.rag.retrieval.RetrievalAugmentorOrchestrator;
import com.exemple.transactionservice.service.rag.retrieval.RetrievalAugmentorOrchestrator.RetrievalAugmentorResult;
import com.exemple.transactionservice.service.rag.streaming.model.StreamingResponse;
import com.exemple.transactionservice.service.rag.streaming.model.StreamingRequest;
import com.exemple.transactionservice.service.rag.streaming.model.ConversationState;
import com.exemple.transactionservice.service.rag.streaming.model.StreamingEvent;
import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrateur de streaming RAG
 * 
 * ✅ ADAPTÉ AVEC RAGMetrics unifié
 * ✅ AJOUT: LLM Cost Tracking
 * 
 * Pipeline complet:
 * 1. Query processing (Retrieval Augmentor)
 * 2. Conversation management
 * 3. OpenAI streaming generation
 * 4. Event emission
 * 5. Response finalization
 * 6. Cost tracking (NEW)
 * 
 * @author RAG Team
 * @version 3.1 - Ajout LLM Cost Tracking
 */
@Slf4j
@Service
public class StreamingOrchestrator {
    
    private final RetrievalAugmentorOrchestrator retrievalAugmentor;
    private final ConversationManager conversationManager;
    private final EventEmitter eventEmitter;
    private final StreamingChatLanguageModel streamingModel;
    private final RAGMetrics ragMetrics;  // ✅ Métriques unifiées
    
    // Pattern pour citations: <cite index="1">...</cite>
    private static final Pattern CITATION_PATTERN = 
        Pattern.compile("<cite\\s+index=\"(\\d+)\">([^<]+)</cite>");
    
    // ========================================================================
    // 💰 LLM COST TRACKING - Prix par modèle (en USD par token)
    // ========================================================================
    
    /**
     * Prix des tokens d'input par modèle
     * Source: https://openai.com/pricing (Janvier 2025)
     */
    private static final Map<String, Double> INPUT_TOKEN_COST = Map.of(
        "gpt-4o", 0.000005,              // $5 / 1M tokens
        "gpt-4o-mini", 0.00000015,       // $0.15 / 1M tokens
        "gpt-4-turbo", 0.00001,          // $10 / 1M tokens
        "gpt-3.5-turbo", 0.0000005,      // $0.5 / 1M tokens
        "claude-3-opus", 0.000015,       // $15 / 1M tokens
        "claude-3-sonnet", 0.000003      // $3 / 1M tokens
    );
    
    /**
     * Prix des tokens d'output par modèle
     */
    private static final Map<String, Double> OUTPUT_TOKEN_COST = Map.of(
        "gpt-4o", 0.000015,              // $15 / 1M tokens
        "gpt-4o-mini", 0.0000006,        // $0.6 / 1M tokens
        "gpt-4-turbo", 0.00003,          // $30 / 1M tokens
        "gpt-3.5-turbo", 0.0000015,      // $1.5 / 1M tokens
        "claude-3-opus", 0.000075,       // $75 / 1M tokens
        "claude-3-sonnet", 0.000015      // $15 / 1M tokens
    );
    
    /**
     * Nom du modèle par défaut (à adapter selon votre config)
     */
    private static final String DEFAULT_MODEL_NAME = "gpt-4o-mini";
    
    public StreamingOrchestrator(
            RetrievalAugmentorOrchestrator retrievalAugmentor,
            ConversationManager conversationManager,
            EventEmitter eventEmitter,
            StreamingChatLanguageModel streamingModel,
            RAGMetrics ragMetrics) {  // ✅ Injection RAGMetrics
        
        this.retrievalAugmentor = retrievalAugmentor;
        this.conversationManager = conversationManager;
        this.eventEmitter = eventEmitter;
        this.streamingModel = streamingModel;
        this.ragMetrics = ragMetrics;  // ✅ Injection
    }
    
    /**
     * Execute le pipeline complet de streaming
     */
    public CompletableFuture<StreamingResponse> executeStreaming(
            String sessionId,
            StreamingRequest request) {
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            log.info("🚀 ========== STREAMING ORCHESTRATOR START ==========");
            log.info("📝 Session: {}, Query: {}", sessionId, request.getQuery());
            
            // ✅ MÉTRIQUE: Début query
            ragMetrics.startQuery();
            
            try {
                // ========== STEP 1: CONVERSATION MANAGEMENT ==========
                ConversationState conversation = handleConversation(request);
                
                eventEmitter.emit(sessionId, StreamingEvent.builder()
                    .type(StreamingEvent.Type.QUERY_RECEIVED)
                    .sessionId(sessionId)
                    .conversationId(conversation.getConversationId())
                    .data(Map.of(
                        "query", request.getQuery(),
                        "conversationId", conversation.getConversationId()
                    ))
                    .timestamp(Instant.now())
                    .build());
                
                // ========== STEP 2: RETRIEVAL AUGMENTOR ==========
                log.info("🧠 [1/3] Executing Retrieval Augmentor...");
                
                long retrievalStart = System.currentTimeMillis();
                
                String enrichedQuery = request.getConversationId() != null 
                    ? conversationManager.enrichQueryWithContext(
                        request.getConversationId(), 
                        request.getQuery())
                    : request.getQuery();
                
                RetrievalAugmentorResult augmentorResult = 
                    retrievalAugmentor.execute(enrichedQuery);
                
                if (!augmentorResult.isSuccess()) {
                    throw new RuntimeException("Retrieval Augmentor failed: " + 
                        augmentorResult.getErrorMessage());
                }
                
                long retrievalDuration = System.currentTimeMillis() - retrievalStart;
                
                // Émettre événements du Retrieval Augmentor
                emitRetrievalEvents(sessionId, augmentorResult);
                
                log.info("✅ [1/3] Retrieval complete: {} chunks, {} tokens",
                    augmentorResult.getAggregatedContext().getFinalSelected(),
                    augmentorResult.getInjectedPrompt().getStructure().getTotalTokens());
                
                // ========== STEP 3: OPENAI STREAMING GENERATION ==========
                log.info("💬 [2/3] Starting OpenAI streaming...");
                
                eventEmitter.emit(sessionId, StreamingEvent.builder()
                    .type(StreamingEvent.Type.GENERATION_START)
                    .sessionId(sessionId)
                    .data(Map.of(
                        "model", DEFAULT_MODEL_NAME,
                        "temperature", request.getTemperature()
                    ))
                    .timestamp(Instant.now())
                    .build());
                
                StreamingGenerationResult generationResult = new StreamingGenerationResult();
                
                String fullPrompt = augmentorResult.getInjectedPrompt().getFullPrompt();
                
                // 💰 Calculer les tokens d'input (pour le coût)
                int inputTokens = estimateTokens(fullPrompt);
                
                long generationStart = System.currentTimeMillis();
                
                streamAiResponse(
                    sessionId,
                    fullPrompt,
                    streamingModel,
                    eventEmitter,
                    generationResult
                );
                
                long generationDuration = System.currentTimeMillis() - generationStart;
                
                // ✅ MÉTRIQUE: Génération
                ragMetrics.recordGeneration(
                    generationDuration,
                    generationResult.totalTokens
                );
                
                // ========================================================================
                // 💰 NOUVEAU: CALCUL ET ENREGISTREMENT DU COÛT LLM
                // ========================================================================
                
                int outputTokens = generationResult.totalTokens;
                
                // Calculer le coût
                double inputCost = inputTokens * INPUT_TOKEN_COST.getOrDefault(DEFAULT_MODEL_NAME, 0.000001);
                double outputCost = outputTokens * OUTPUT_TOKEN_COST.getOrDefault(DEFAULT_MODEL_NAME, 0.000001);
                double totalCost = inputCost + outputCost;
                
                // ✅ ENREGISTRER LA MÉTRIQUE DE COÛT
                ragMetrics.recordLLMCost(totalCost, inputTokens, outputTokens);
                
                log.info("💰 LLM Cost - Model: {}, Cost: ${}, Input: {} tokens, Output: {} tokens",
                    DEFAULT_MODEL_NAME,
                    String.format("%.6f", totalCost),
                    inputTokens,
                    outputTokens);
                
                // ========================================================================
                
                // ✅ MÉTRIQUE: Citations
                if (generationResult.citations != null) {
                    for (int i = 0; i < generationResult.citations.size(); i++) {
                        ragMetrics.recordCitation();
                    }
                }
                
                log.info("✅ [2/3] Generation complete: {} tokens, {} citations, cost: ${}",
                    generationResult.totalTokens,
                    generationResult.citations != null ? generationResult.citations.size() : 0,
                    String.format("%.6f", totalCost));
                
                // ========== STEP 4: FINALIZATION ==========
                log.info("🎯 [3/3] Finalizing response...");
                
                StreamingResponse response = finalizeResponse(
                    sessionId,
                    conversation,
                    request,
                    augmentorResult,
                    generationResult
                );
                
                // Conversation: Ajouter message assistant
                List<ConversationState.SourceReference> conversationSources = 
                    augmentorResult.getSources().stream()
                        .map(src -> ConversationState.SourceReference.builder()
                            .file(src.getFile())
                            .page(src.getPage())
                            .relevance(src.getRelevance())
                            .build())
                        .collect(Collectors.toList());
                
                conversationManager.addAssistantMessage(
                    conversation.getConversationId(),
                    generationResult.fullText,
                    conversationSources,
                    Map.of(
                        "tokens", generationResult.totalTokens,
                        "duration_ms", System.currentTimeMillis() - startTime,
                        "cost_usd", totalCost  // ✅ Ajouter le coût aux métadonnées
                    )
                );
                
                // ✅ MÉTRIQUE: Enregistrer message conversation
                ragMetrics.recordConversationMessage(
                    "assistant",
                    generationResult.totalTokens
                );
                
                long totalDuration = System.currentTimeMillis() - startTime;
                
                // ✅ MÉTRIQUE: Pipeline complet
                ragMetrics.recordPipeline(
                    totalDuration,
                    retrievalDuration,
                    generationDuration
                );
                
                log.info("✅ ========== STREAMING ORCHESTRATOR COMPLETE ==========");
                log.info("📊 Total: {}ms | Retrieval={}ms | Generation={}ms | Cost=${}", 
                    totalDuration,
                    retrievalDuration,
                    generationDuration,
                    String.format("%.6f", totalCost));
                
                // Émettre événement final
                eventEmitter.emitComplete(sessionId, Map.of(
                    "response", response,
                    "metadata", Map.of(
                        "totalDurationMs", totalDuration,
                        "retrievalDurationMs", retrievalDuration,
                        "generationDurationMs", generationDuration,
                        "costUSD", totalCost,  // ✅ Inclure le coût
                        "inputTokens", inputTokens,
                        "outputTokens", outputTokens
                    )
                ));
                
                // Complete SSE stream
                eventEmitter.complete(sessionId);
                
                return response;
                
            } catch (Exception e) {
                log.error("❌ Streaming orchestrator failed", e);
                
                eventEmitter.emitError(sessionId, e.getMessage(), "ORCHESTRATOR_ERROR");
                eventEmitter.completeWithError(sessionId, e);
                
                throw new RuntimeException("Streaming failed", e);
                
            } finally {
                // ✅ MÉTRIQUE: Fin query
                ragMetrics.endQuery();
            }
        });
    }
    
    // ========================================================================
    // PRIVATE METHODS
    // ========================================================================
    
    /**
     * Gère la conversation (création ou récupération)
     */
    private ConversationState handleConversation(StreamingRequest request) {
        if (request.getConversationId() != null) {
            Optional<ConversationState> existing = 
                conversationManager.getConversation(request.getConversationId());
            
            if (existing.isPresent()) {
                ConversationState conv = existing.get();
                conversationManager.addUserMessage(conv.getConversationId(), request.getQuery());
                
                // ✅ MÉTRIQUE: Message user
                int tokenCount = estimateTokens(request.getQuery());
                ragMetrics.recordConversationMessage("user", tokenCount);
                
                return conv;
            }
        }
        
        // Créer nouvelle conversation
        ConversationState newConv = conversationManager.createConversation(request.getUserId());
        conversationManager.addUserMessage(newConv.getConversationId(), request.getQuery());
        
        // ✅ MÉTRIQUE: Message user
        int tokenCount = estimateTokens(request.getQuery());
        ragMetrics.recordConversationMessage("user", tokenCount);
        
        return newConv;
    }
    
    /**
     * Émet les événements du Retrieval Augmentor
     */
    private void emitRetrievalEvents(String sessionId, RetrievalAugmentorResult result) {
        // Query transformed
        eventEmitter.emit(sessionId, StreamingEvent.builder()
            .type(StreamingEvent.Type.QUERY_TRANSFORMED)
            .sessionId(sessionId)
            .data(Map.of(
                "variants", result.getTransformResult().getVariants(),
                "method", result.getTransformResult().getMethod()
            ))
            .timestamp(Instant.now())
            .build());
        
        // Routing decision
        eventEmitter.emit(sessionId, StreamingEvent.builder()
            .type(StreamingEvent.Type.ROUTING_DECISION)
            .sessionId(sessionId)
            .data(Map.of(
                "strategy", result.getRoutingDecision().getStrategy().name(),
                "confidence", result.getRoutingDecision().getConfidence()
            ))
            .timestamp(Instant.now())
            .build());
        
        // Retrieval complete
        eventEmitter.emit(sessionId, StreamingEvent.builder()
            .type(StreamingEvent.Type.RETRIEVAL_COMPLETE)
            .sessionId(sessionId)
            .data(Map.of(
                "totalChunks", result.getAggregatedContext().getInputChunks(),
                "finalSelected", result.getAggregatedContext().getFinalSelected()
            ))
            .timestamp(Instant.now())
            .build());
        
        // Context ready
        eventEmitter.emit(sessionId, StreamingEvent.builder()
            .type(StreamingEvent.Type.CONTEXT_READY)
            .sessionId(sessionId)
            .data(Map.of(
                "tokens", result.getInjectedPrompt().getStructure().getTotalTokens(),
                "sources", result.getSources().size()
            ))
            .timestamp(Instant.now())
            .build());
    }
    
    /**
     * Stream la réponse OpenAI et émet tokens/citations en temps réel
     * 
     * ✅ AVEC CountDownLatch pour attendre la fin du streaming
     */
    private void streamAiResponse(
            String sessionId,
            String prompt,
            StreamingChatLanguageModel streamingModel,
            EventEmitter eventEmitter,
            StreamingGenerationResult result) {
        
        long startTime = System.currentTimeMillis();
        AtomicInteger tokenIndex = new AtomicInteger(0);
        List<DetectedCitation> citations = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();
        
        // ✅ CountDownLatch pour synchronisation
        final CountDownLatch latch = new CountDownLatch(1);
        
        log.info("🚀 Starting OpenAI streaming...");
        
        eventEmitter.emit(sessionId, StreamingEvent.builder()
            .type(StreamingEvent.Type.GENERATION_START)
            .sessionId(sessionId)
            .data(Map.of("timestamp", System.currentTimeMillis()))
            .timestamp(Instant.now())
            .build());
        
        try {
            streamingModel.generate(
                UserMessage.from(prompt),
                new StreamingResponseHandler<AiMessage>() {
                    
                    @Override
                    public void onNext(String token) {
                        if (token != null && !token.isEmpty()) {
                            fullText.append(token);
                            eventEmitter.emitToken(sessionId, token, tokenIndex.getAndIncrement());
                            
                            detectCitations(fullText.toString(), citations, sessionId, eventEmitter);
                        }
                    }
                    
                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        long duration = System.currentTimeMillis() - startTime;
                        
                        result.fullText = fullText.toString();
                        result.totalTokens = tokenIndex.get();
                        result.citations = citations;
                        result.durationMs = duration;
                        
                        log.info("✅ OpenAI streaming complete: {} tokens in {}ms", 
                            result.totalTokens, duration);
                        
                        eventEmitter.emit(sessionId, StreamingEvent.builder()
                            .type(StreamingEvent.Type.GENERATION_COMPLETE)
                            .sessionId(sessionId)
                            .data(Map.of("totalTokens", result.totalTokens))
                            .timestamp(Instant.now())
                            .build());
                        
                        // ✅ Débloquer
                        latch.countDown();
                    }
                    
                    @Override
                    public void onError(Throwable error) {
                        log.error("❌ OpenAI streaming error", error);
                        
                        eventEmitter.emitError(sessionId, 
                            "Generation error: " + error.getMessage(),
                            "GENERATION_ERROR");
                        
                        // ✅ Débloquer même en erreur
                        latch.countDown();
                    }
                }
            );
            
            // ✅ ATTENDRE la fin du streaming (timeout 60s)
            log.info("⏳ Waiting for streaming to complete...");
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            
            if (!completed) {
                log.warn("⚠️ Streaming timeout after 60s");
                result.fullText = "[ERROR: Streaming timeout]";
            } else {
                log.info("✅ Streaming wait completed - {} tokens", result.totalTokens);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Streaming interrupted", e);
            throw new RuntimeException("Streaming interrupted", e);
        } catch (Exception e) {
            log.error("❌ Streaming error", e);
            throw new RuntimeException("Streaming failed", e);
        }
    }
    
    /**
     * Détecte les citations dans le texte
     */
    private void detectCitations(
            String text,
            List<DetectedCitation> citations,
            String sessionId,
            EventEmitter eventEmitter) {
        
        Matcher matcher = CITATION_PATTERN.matcher(text);
        
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String content = matcher.group(2);
            
            boolean alreadyEmitted = citations.stream()
                .anyMatch(c -> c.getIndex() == index);
            
            if (!alreadyEmitted) {
                DetectedCitation citation = DetectedCitation.builder()
                    .index(index)
                    .content(content)
                    .build();
                
                citations.add(citation);
                
                eventEmitter.emit(sessionId, StreamingEvent.builder()
                    .type(StreamingEvent.Type.CITATION)
                    .sessionId(sessionId)
                    .data(Map.of("index", index, "content", content))
                    .timestamp(Instant.now())
                    .build());
            }
        }
    }
    
    /**
     * Finalise la réponse
     */
    private StreamingResponse finalizeResponse(
            String sessionId,
            ConversationState conversation,
            StreamingRequest request,
            RetrievalAugmentorResult augmentorResult,
            StreamingGenerationResult generationResult) {
        
        // Convertir sources
        List<StreamingResponse.SourceReference> sources = augmentorResult.getSources() != null
            ? augmentorResult.getSources().stream()
                .map(src -> StreamingResponse.SourceReference.builder()
                    .file(src.getFile())
                    .page(src.getPage())
                    .relevance(src.getRelevance())
                    .type(src.getType() != null ? src.getType() : "text")
                    .build())
                .collect(Collectors.toList())
            : new ArrayList<>();
        
        // Convertir citations
        List<StreamingResponse.Citation> streamingCitations = generationResult.citations != null
            ? generationResult.citations.stream()
                .map(c -> StreamingResponse.Citation.builder()
                    .index(c.getIndex())
                    .content(c.getContent())
                    .sourceFile(null)
                    .sourcePage(null)
                    .build())
                .collect(Collectors.toList())
            : new ArrayList<>();
        
        return StreamingResponse.builder()
            .sessionId(sessionId)
            .conversationId(conversation.getConversationId())
            .query(request.getQuery())
            .answer(generationResult.fullText)
            .sources(sources)
            .citations(streamingCitations)
            .metadata(StreamingResponse.Metadata.builder()
                .tokensGenerated(generationResult.totalTokens)
                .chunksRetrieved(augmentorResult.getAggregatedContext().getInputChunks())
                .chunksSelected(augmentorResult.getAggregatedContext().getFinalSelected())
                .retrievalDurationMs(augmentorResult.getTotalDurationMs())
                .generationDurationMs(generationResult.durationMs)
                .totalDurationMs(augmentorResult.getTotalDurationMs() + generationResult.durationMs)
                .build())
            .build();
    }
    
    /**
     * Estime le nombre de tokens d'un texte
     * 
     * Note: Estimation approximative basée sur la longueur
     * Pour plus de précision, utiliser une bibliothèque de tokenization
     */
    private int estimateTokens(String text) {
        // Estimation: ~4 caractères par token (méthode approximative)
        // Pour plus de précision, utiliser tiktoken ou un équivalent Java
        return text != null ? text.length() / 4 : 0;
    }
    
    // ========================================================================
    // HELPER CLASSES
    // ========================================================================
    
    /**
     * Citation détectée
     */
    @lombok.Data
    @lombok.Builder
    private static class DetectedCitation {
        private int index;
        private String content;
    }
    
    /**
     * Résultat de la génération streaming
     */
    @lombok.Data
    private static class StreamingGenerationResult {
        String fullText = "";
        int totalTokens = 0;
        List<DetectedCitation> citations = new ArrayList<>();
        long durationMs = 0;
    }
}