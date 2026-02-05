package com.exemple.transactionservice.service.rag.streaming;

import com.exemple.transactionservice.service.rag.retrieval.RetrievalAugmentorOrchestrator;
import com.exemple.transactionservice.service.rag.retrieval.RetrievalAugmentorOrchestrator.RetrievalAugmentorResult;
import com.exemple.transactionservice.service.rag.streaming.model.StreamingResponse;
import com.exemple.transactionservice.service.rag.streaming.model.StreamingRequest;
import com.exemple.transactionservice.service.rag.streaming.model.ConversationState;
import com.exemple.transactionservice.service.rag.streaming.model.StreamingEvent;
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
 * Pipeline complet:
 * 1. Query processing (Retrieval Augmentor)
 * 2. Conversation management
 * 3. OpenAi streaming generation
 * 4. Event emission
 * 5. Response finalization
 */
@Slf4j
@Service
public class StreamingOrchestrator {
    
    private final RetrievalAugmentorOrchestrator retrievalAugmentor;
    private final ConversationManager conversationManager;
    private final EventEmitter eventEmitter;
    private final StreamingChatLanguageModel streamingModel;
    
    // Pattern pour détecter citations: <cite index="1">...</cite>
    private static final Pattern CITATION_PATTERN = 
        Pattern.compile("<cite\\s+index=\"(\\d+)\">([^<]+)</cite>");
    
    public StreamingOrchestrator(
            RetrievalAugmentorOrchestrator retrievalAugmentor,
            ConversationManager conversationManager,
            EventEmitter eventEmitter,
            StreamingChatLanguageModel streamingModel) {
        
        this.retrievalAugmentor = retrievalAugmentor;
        this.conversationManager = conversationManager;
        this.eventEmitter = eventEmitter;
        this.streamingModel = streamingModel;
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
                        "model", "gpt-4-mini",
                        "temperature", request.getTemperature()
                    ))
                    .timestamp(Instant.now())
                    .build());
                
                StreamingGenerationResult generationResult = new StreamingGenerationResult();
                
                // ✅ CORRECTION 1: Utiliser fullPrompt de InjectedPrompt
                String fullPrompt = augmentorResult.getInjectedPrompt().getFullPrompt();
                
                streamAiResponse(
                    sessionId,
                    fullPrompt,
                    streamingModel,
                    eventEmitter,
                    generationResult
                );
                
                log.info("✅ [2/3] Generation complete: {} tokens, {} citations",
                    generationResult.totalTokens,
                    generationResult.citations != null ? generationResult.citations.size() : 0);
                
                // ========== STEP 4: FINALIZATION ==========
                log.info("🎯 [3/3] Finalizing response...");
                
                StreamingResponse response = finalizeResponse(
                    sessionId,
                    conversation,
                    request,
                    augmentorResult,
                    generationResult
                );
                
                // ✅ CORRECTION 2: Utiliser builder pour SourceReference
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
                        "duration_ms", System.currentTimeMillis() - startTime
                    )
                );
                
                long totalDuration = System.currentTimeMillis() - startTime;
                
                log.info("✅ ========== STREAMING ORCHESTRATOR COMPLETE ==========");
                log.info("📊 Total: {}ms | Retrieval={}ms | Generation={}ms",
                    totalDuration,
                    augmentorResult.getTotalDurationMs(),
                    generationResult.durationMs);
                
                // Émettre événement final
                eventEmitter.emitComplete(sessionId, Map.of(
                    "response", response,
                    "metadata", Map.of(
                        "totalDurationMs", totalDuration,
                        "retrievalDurationMs", augmentorResult.getTotalDurationMs(),
                        "generationDurationMs", generationResult.durationMs
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
            // Récupérer conversation existante
            Optional<ConversationState> existing = 
                conversationManager.getConversation(request.getConversationId());
            
            if (existing.isPresent()) {
                ConversationState conv = existing.get();
                conversationManager.addUserMessage(conv.getConversationId(), request.getQuery());
                return conv;
            }
        }
        
        // Créer nouvelle conversation
        ConversationState newConv = conversationManager.createConversation(request.getUserId());
        conversationManager.addUserMessage(newConv.getConversationId(), request.getQuery());
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
     * Stream la réponse de OpenAi et émet les tokens et citations en temps réel
     * @param sessionId ID de la session pour l'émission d'événements
     * @param prompt Prompt complet à envoyer à OpenAi
     * @param streamingModel Modèle de langage supportant le streaming
     * @param eventEmitter Émetteur d'événements pour envoyer les tokens et citations
     * @param result Objet pour stocker le résultat final de la génération
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

            // ✅ AJOUTER CountDownLatch pour attendre
        final CountDownLatch latch = new CountDownLatch(1);
        
        log.info("🚀 Starting OpenAi streaming generation...");
        
        // ✅ CORRECTION 3: Utiliser emit() avec builder
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
                            
                            // ✅ CORRECTION 4: Ajouter eventEmitter en paramètre
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
                        
                        log.info("✅ OpenAi streaming complete: {} tokens in {}ms", 
                            result.totalTokens, duration);
                        
                        // ✅ CORRECTION 6: Utiliser emit() avec builder
                        eventEmitter.emit(sessionId, StreamingEvent.builder()
                            .type(StreamingEvent.Type.GENERATION_COMPLETE)
                            .sessionId(sessionId)
                            .data(Map.of("totalTokens", result.totalTokens))
                            .timestamp(Instant.now())
                            .build());

                        // ✅ Débloquer le latch
                        latch.countDown();
                    }
                    
                    @Override
                    public void onError(Throwable error) {
                        log.error("❌ OpenAi streaming error", error);
                        
                        // ✅ CORRECTION 7: Ajouter 3ème paramètre errorCode
                        eventEmitter.emitError(sessionId, 
                            "Generation error: " + error.getMessage(),
                            "GENERATION_ERROR");

                        // ✅ Débloquer le latch même en cas d'erreur
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
                log.info("✅ Streaming wait completed");
            }
            
        } catch (Exception e) {
            log.error("❌ Error in Claude streaming", e);
            throw new RuntimeException("Claude streaming failed", e);
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
        
        Pattern citationPattern = Pattern.compile("<cite index=\"(\\d+)\">([^<]+)</cite>");
        Matcher matcher = citationPattern.matcher(text);
        
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
                
                // ✅ CORRECTION 8: Utiliser StreamingEvent.Type.CITATION avec builder
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
        
        // Convertir InjectedPrompt.SourceReference vers StreamingResponse.SourceReference
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
        
        // Convertir DetectedCitation vers StreamingResponse.Citation
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
    
    // ========================================================================
    // HELPER CLASSES
    // ========================================================================
    
    /**
     * Classe interne pour citations détectées
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
        List<DetectedCitation> citations = new ArrayList<>();  // ✅ Initialiser avec liste vide
        long durationMs = 0;
    }
}