// ============================================================================
// DTO - ChatResponse.java
// ============================================================================
package com.exemple.transactionservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Réponse de chat")
public class ChatResponse {
    
    @Schema(description = "ID unique de la requête")
    private String requestId;
    
    @Schema(description = "Succès de la requête")
    private boolean success;
    
    @Schema(description = "Réponse de l'assistant")
    private String response;
    
    @Schema(description = "ID utilisateur")
    private String userId;
    
    @Schema(description = "Message d'erreur")
    private String error;
    
    @Schema(description = "Temps de traitement (ms)")
    private Long processingTimeMs;
    
    @Schema(description = "Timestamp")
    private Instant timestamp;
}