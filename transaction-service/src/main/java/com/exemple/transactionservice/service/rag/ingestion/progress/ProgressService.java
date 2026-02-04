package com.exemple.transactionservice.service.rag.ingestion.progress;

import com.exemple.transactionservice.dto.UploadProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    public void sendProgress(UploadProgress progress) {
        String destination = "/topic/upload-progress/" + progress.getBatchId();
        messagingTemplate.convertAndSend(destination, progress);
        log.debug("📊 Progress: {}% - {}", progress.getProgressPercentage(), progress.getMessage());
    }
    
    // Méthodes helper
    public void uploadStarted(String batchId, String filename, long totalBytes) {
        sendProgress(new UploadProgress(batchId, filename, "UPLOAD", 5, "Téléchargement..."));
    }
    
    public void uploadCompleted(String batchId, String filename) {
        sendProgress(new UploadProgress(batchId, filename, "UPLOAD", 10, "Téléchargement terminé"));
    }
    
    public void processingStarted(String batchId, String filename) {
        sendProgress(new UploadProgress(batchId, filename, "PROCESSING", 15, "Analyse du document..."));
    }
    
    public void embeddingProgress(String batchId, String filename, int current, int total) {
        int percentage = 50 + (int)((current / (double)total) * 40);
        String message = String.format("Embeddings: %d/%d", current, total);
        UploadProgress progress = new UploadProgress(batchId, filename, "EMBEDDING", percentage, message);
        progress.setEmbeddingsCreated(current);
        sendProgress(progress);
    }
    
    public void completed(String batchId, String filename, int textEmbeddings, int imageEmbeddings) {
        String message = String.format("✅ Terminé! %d texte, %d images", textEmbeddings, imageEmbeddings);
        UploadProgress progress = new UploadProgress(batchId, filename, "COMPLETED", 100, message);
        progress.setEmbeddingsCreated(textEmbeddings);
        progress.setImagesProcessed(imageEmbeddings);
        sendProgress(progress);
    }
    
    public void error(String batchId, String filename, String errorMessage) {
        sendProgress(new UploadProgress(batchId, filename, "ERROR", 0, "Erreur: " + errorMessage));
    }

    public void chunkingProgress(String batchId, String filename, int current, int total) {
        int percentage = 15 + (int) ((current / (double) total) * 25); // 15 -> 40
        String message = String.format("Chunking: %d/%d", current, total);

        UploadProgress progress = new UploadProgress(batchId, filename, "CHUNKING", percentage, message);
        // Si tu as un champ dédié côté DTO, tu peux l’alimenter (sinon supprime)
        progress.setChunksCreated(current);

        sendProgress(progress);
    }

    public void imageProgress(String batchId, String filename, int current, int total) {
        int percentage = 90 + (int) ((current / (double) total) * 10); // 90 -> 100
        String message = String.format("Images: %d/%d", current, total);

        UploadProgress progress = new UploadProgress(batchId, filename, "IMAGES", percentage, message);
        progress.setImagesProcessed(current);

        sendProgress(progress);
    }

}