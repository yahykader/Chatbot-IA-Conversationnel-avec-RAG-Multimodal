// ============================================================================
// DTO - ChatRequest.java
// ============================================================================
package com.exemple.transactionservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requête de chat")
public class ChatRequest {
    
    @NotBlank(message = "userId est requis")
    @Size(min = 1, max = 100)
    @Schema(description = "Identifiant utilisateur", example = "user-123")
    private String userId;
    
    @NotBlank(message = "message est requis")
    @Size(min = 1, max = 5000)
    @Schema(description = "Message à envoyer", example = "Explique-moi GitLab CI/CD")
    private String message;
}