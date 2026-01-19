// ============================================================================
// DTO - FileUploadResponse.java
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
@Schema(description = "Réponse d'upload")
public class FileUploadResponse {
    
    @Schema(description = "ID unique de la requête")
    private String requestId;
    
    @Schema(description = "Succès de l'upload")
    private boolean success;
    
    @Schema(description = "Message de confirmation")
    private String message;
    
    @Schema(description = "Nom du fichier")
    private String filename;
    
    @Schema(description = "Taille du fichier (bytes)")
    private Long size;
    
    @Schema(description = "Type MIME")
    private String contentType;
    
    @Schema(description = "Message d'erreur")
    private String error;
    
    @Schema(description = "Temps de traitement (ms)")
    private Long processingTimeMs;
    
    @Schema(description = "Timestamp")
    private Instant timestamp;
}