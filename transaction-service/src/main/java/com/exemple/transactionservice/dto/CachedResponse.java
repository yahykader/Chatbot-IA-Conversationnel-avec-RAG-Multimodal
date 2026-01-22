package com.exemple.transactionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ✅ Modèle pour stocker les réponses LLM en cache
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String question;           // Question originale
    private String response;           // Réponse du LLM
    private String model;              // Modèle utilisé (gpt-4, claude-3, etc.)
    private LocalDateTime timestamp;   // Date de création
    private Integer tokensUsed;        // Nombre de tokens
    private Long responseTimeMs;       // Temps de réponse original
    private String userId;             // ID utilisateur (optionnel)
    private String conversationId;     // ID conversation (optionnel)
}