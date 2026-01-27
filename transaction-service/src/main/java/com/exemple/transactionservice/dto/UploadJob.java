package com.exemple.transactionservice.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class UploadJob {
    private final String jobId;
    private final String filename;
    private final long fileSize;
    private UploadStatus status = UploadStatus.PENDING;
    private int progress = 0;
    private String errorMessage;
    private final Instant createdAt = Instant.now();
    private Instant completedAt;
    
    public UploadJob(String jobId, String filename, long fileSize) {
        this.jobId = jobId;
        this.filename = filename;
        this.fileSize = fileSize;
    }
    
    public String getMessage() {
        return switch (status) {
            case PENDING -> "Upload en attente...";
            case PROCESSING -> "Traitement en cours (" + progress + "%)...";
            case COMPLETED -> "Upload terminé";
            case FAILED -> "Échec: " + (errorMessage != null ? errorMessage : "Erreur inconnue");
        };
    }
}
