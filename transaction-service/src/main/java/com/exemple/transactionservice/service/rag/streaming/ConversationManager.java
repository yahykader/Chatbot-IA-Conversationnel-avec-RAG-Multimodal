package com.exemple.transactionservice.service.rag.streaming;

import com.exemple.transactionservice.service.rag.streaming.model.ConversationState;
import com.exemple.transactionservice.service.rag.streaming.model.ConversationState.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Gestionnaire de conversations avec persistence Redis
 * 
 * Fonctionnalités:
 * - Création/récupération conversations
 * - Ajout messages avec historique
 * - Context tracking (documents utilisés)
 * - TTL automatique
 * - Multi-turn support
 */
@Slf4j
@Service
public class ConversationManager {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String KEY_PREFIX = "conversation:";
    private static final int DEFAULT_TTL_SECONDS = 3600; // 1 heure
    private static final int MAX_MESSAGES = 100;
    
    public ConversationManager(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Crée une nouvelle conversation
     */
    public ConversationState createConversation(String userId) {
        String conversationId = generateConversationId();
        
        ConversationState state = ConversationState.builder()
            .conversationId(conversationId)
            .userId(userId)
            .createdAt(Instant.now())
            .lastActivity(Instant.now())
            .messages(new ArrayList<>())
            .context(new ArrayList<>())
            .metadata(new HashMap<>())
            .ttlSeconds(DEFAULT_TTL_SECONDS)
            .build();
        
        saveConversation(state);
        
        log.info("📝 Created conversation: {} for user: {}", conversationId, userId);
        
        return state;
    }
    
    /**
     * Récupère une conversation existante
     */
    public Optional<ConversationState> getConversation(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        String json = redisTemplate.opsForValue().get(key);
        
        if (json == null) {
            log.debug("❌ Conversation not found: {}", conversationId);
            return Optional.empty();
        }
        
        try {
            ConversationState state = objectMapper.readValue(json, ConversationState.class);
            log.debug("✅ Retrieved conversation: {} ({} messages)", 
                conversationId, state.getMessages().size());
            return Optional.of(state);
            
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to deserialize conversation: {}", conversationId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Ajoute un message utilisateur
     */
    public ConversationState addUserMessage(
            String conversationId, 
            String content) {
        
        ConversationState state = getConversation(conversationId)
            .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
        
        Message message = Message.builder()
            .role("user")
            .content(content)
            .timestamp(Instant.now())
            .metadata(new HashMap<>())
            .build();
        
        state.getMessages().add(message);
        state.setLastActivity(Instant.now());
        
        // Limiter nombre de messages
        if (state.getMessages().size() > MAX_MESSAGES) {
            state.getMessages().remove(0);
            log.warn("⚠️ Conversation {} exceeded max messages, removed oldest", conversationId);
        }
        
        saveConversation(state);
        
        log.debug("💬 Added user message to conversation: {}", conversationId);
        
        return state;
    }
    
    /**
     * Ajoute une réponse assistant
     */
    public ConversationState addAssistantMessage(
            String conversationId,
            String content,
            List<SourceReference> sources,
            Map<String, Object> metadata) {
        
        ConversationState state = getConversation(conversationId)
            .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
        
        Message message = Message.builder()
            .role("assistant")
            .content(content)
            .timestamp(Instant.now())
            .sources(sources != null ? sources : new ArrayList<>())
            .metadata(metadata != null ? metadata : new HashMap<>())
            .build();
        
        state.getMessages().add(message);
        state.setLastActivity(Instant.now());
        
        // Update context avec nouvelles sources
        if (sources != null) {
            updateContext(state, sources);
        }
        
        saveConversation(state);
        
        log.debug("🤖 Added assistant message to conversation: {}", conversationId);
        
        return state;
    }
    
    /**
     * Récupère l'historique des messages
     */
    public List<Message> getMessageHistory(String conversationId, int maxMessages) {
        return getConversation(conversationId)
            .map(state -> {
                List<Message> messages = state.getMessages();
                int size = messages.size();
                int fromIndex = Math.max(0, size - maxMessages);
                return messages.subList(fromIndex, size);
            })
            .orElse(new ArrayList<>());
    }
    
    /**
     * Enrichit une query avec le contexte conversationnel
     */
    public String enrichQueryWithContext(String conversationId, String query) {
        Optional<ConversationState> stateOpt = getConversation(conversationId);
        
        if (stateOpt.isEmpty()) {
            return query;
        }
        
        ConversationState state = stateOpt.get();
        List<Message> messages = state.getMessages();
        
        if (messages.isEmpty()) {
            return query;
        }
        
        // Prendre les 3 derniers messages pour contexte
        int startIdx = Math.max(0, messages.size() - 3);
        List<Message> recentMessages = messages.subList(startIdx, messages.size());
        
        // Construire contexte enrichi
        StringBuilder enriched = new StringBuilder();
        enriched.append("Contexte de la conversation:\n");
        
        for (Message msg : recentMessages) {
            enriched.append(msg.getRole()).append(": ")
                    .append(truncate(msg.getContent(), 200))
                    .append("\n");
        }
        
        enriched.append("\nNouvelle question:\n").append(query);
        
        log.debug("🔄 Enriched query with {} previous messages", recentMessages.size());
        
        return enriched.toString();
    }
    
    /**
     * Supprime une conversation
     */
    public void deleteConversation(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        redisTemplate.delete(key);
        log.info("🗑️ Deleted conversation: {}", conversationId);
    }
    
    /**
     * Renouvelle le TTL d'une conversation
     */
    public void refreshTTL(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        redisTemplate.expire(key, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
    }
    
    // ========================================================================
    // PRIVATE METHODS
    // ========================================================================
    
    /**
     * Sauvegarde la conversation dans Redis
     */
    private void saveConversation(ConversationState state) {
        String key = KEY_PREFIX + state.getConversationId();
        
        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(
                key, 
                json, 
                state.getTtlSeconds(), 
                TimeUnit.SECONDS
            );
            
        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize conversation: {}", state.getConversationId(), e);
            throw new RuntimeException("Failed to save conversation", e);
        }
    }
    
    /**
     * Met à jour le contexte avec nouvelles sources
     */
    private void updateContext(ConversationState state, List<SourceReference> sources) {
        int currentMessageIdx = state.getMessages().size() - 1;
        
        for (SourceReference source : sources) {
            String docId = source.getFile();
            
            // Chercher si document déjà dans contexte
            Optional<ContextItem> existingItem = state.getContext().stream()
                .filter(item -> item.getDocId().equals(docId))
                .findFirst();
            
            if (existingItem.isPresent()) {
                // Ajouter référence au message courant
                if (!existingItem.get().getUsedInMessages().contains(currentMessageIdx)) {
                    existingItem.get().getUsedInMessages().add(currentMessageIdx);
                }
            } else {
                // Créer nouveau contexte item
                ContextItem newItem = ContextItem.builder()
                    .docId(docId)
                    .relevance(source.getRelevance())
                    .usedInMessages(new ArrayList<>(List.of(currentMessageIdx)))
                    .build();
                
                state.getContext().add(newItem);
            }
        }
    }
    
    /**
     * Génère un ID de conversation unique
     */
    private String generateConversationId() {
        return "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    /**
     * Tronque un texte
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}