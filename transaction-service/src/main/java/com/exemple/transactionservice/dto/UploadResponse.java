package com.exemple.transactionservice.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UploadResponse {
    private String jobId;
    private String fileName;
    private String status;
    private String message;
    private boolean duplicate;  // ← CHANGÉ: enlever "is" du nom
    private String existingJobId;
    private DuplicateInfo duplicateInfo;
    private Long fileSize;
    private Long fileSizeKB;
    
    public static UploadResponse success(String jobId, String fileName, long fileSize) {
        UploadResponse response = new UploadResponse();
        response.setJobId(jobId);
        response.setFileName(fileName);
        response.setStatus("processing");
        response.setMessage("Upload démarré avec succès");
        response.setDuplicate(false);  // ← CHANGÉ: setDuplicate au lieu de setIsDuplicate
        response.setFileSize(fileSize);
        response.setFileSizeKB(fileSize / 1024);
        return response;
    }
    
    public static UploadResponse duplicate(String existingJobId, String fileName, long fileSize, DuplicateInfo info) {
        UploadResponse response = new UploadResponse();
        response.setJobId(existingJobId);
        response.setExistingJobId(existingJobId);
        response.setFileName(fileName);
        response.setStatus("duplicate");
        response.setMessage("Fichier déjà uploadé");
        response.setDuplicate(true);  // ← CHANGÉ: setDuplicate au lieu de setIsDuplicate
        response.setDuplicateInfo(info);
        response.setFileSize(fileSize);
        response.setFileSizeKB(fileSize / 1024);
        return response;
    }
    
    public static UploadResponse error(String message, String jobId, String fileName) {
        UploadResponse response = new UploadResponse();
        response.setJobId(jobId);
        response.setFileName(fileName);
        response.setStatus("failed");
        response.setMessage(message);
        response.setDuplicate(false);  // ← CHANGÉ: setDuplicate au lieu de setIsDuplicate
        return response;
    }
    
    // Méthode helper pour compatibilité avec le frontend qui attend "isDuplicate"
    public boolean isDuplicate() {
        return duplicate;
    }
}