package com.exemple.transactionservice.service.rag.ingestion.progress;

import com.exemple.transactionservice.dto.UploadProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Helper pour notifier les mises à jour de progression.
 * 
 * Encapsule les appels au ProgressService avec gestion d'erreur
 * pour éviter que les erreurs de WebSocket ne bloquent l'ingestion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgressNotifier {
    
    private final ProgressService progressService;
    
    /**
     * Notifie une mise à jour de progression
     */
    public void notifyProgress(String batchId, String filename, String stage, 
                               int percentage, String message) {
        try {
            UploadProgress progress = new UploadProgress(
                batchId, filename, stage, percentage, message
            );
            progressService.sendProgress(progress);
        } catch (Exception e) {
            log.debug("⚠️ Impossible d'envoyer progress: {}", e.getMessage());
        }
    }
    
    /**
     * Notifie le début de l'upload
     */
    public void uploadStarted(String batchId, String filename, long fileSize) {
        try {
            progressService.uploadStarted(batchId, filename, fileSize);
        } catch (Exception e) {
            log.debug("⚠️ Impossible d'envoyer uploadStarted: {}", e.getMessage());
        }
    }
    
    /**
     * Notifie la fin de l'upload
     */
    public void uploadCompleted(String batchId, String filename) {
        try {
            progressService.uploadCompleted(batchId, filename);
        } catch (Exception e) {
            log.debug("⚠️ Impossible d'envoyer uploadCompleted: {}", e.getMessage());
        }
    }
    
    /**
     * Notifie le début du traitement
     */
    public void processingStarted(String batchId, String filename) {
        try {
            progressService.processingStarted(batchId, filename);
        } catch (Exception e) {
            log.debug("⚠️ Impossible d'envoyer processingStarted: {}", e.getMessage());
        }
    }
    
    /**
     * Notifie le progrès du chunking
     */
    public void chunkingProgress(String batchId, String filename, int current, int total) {
        try {
            progressService.chunkingProgress(batchId, filename, current, total);
        } catch (Exception e) {
            log.debug("⚠️ Impossible d'envoyer chunkingProgress: {}", e.getMessage());
        }
    }
    
    /**
     * Notifie le progrès des embeddings
     */
    public void embeddingProgress(String batchId, String filename, int current, int total) {
        try {
            progressService.embeddingProgress(batchId, filename, current, total);
        } catch (Exception e) {
            log.debug("⚠️ Impossible d'envoyer embeddingProgress: {}", e.getMessage());
        }
    }
    
    /**
     * Notifie le progrès des images
     */
    public void imageProgress(String batchId, String filename, int current, int total) {
        try {
            progressService.imageProgress(batchId, filename, current, total);
        } catch (Exception e) {
            log.debug("⚠️ Impossible d'envoyer imageProgress: {}", e.getMessage());
        }
    }
    
    /**
     * Notifie une erreur
     */
    public void error(String batchId, String filename, String errorMessage) {
        try {
            progressService.error(batchId, filename, errorMessage);
        } catch (Exception e) {
            log.debug("⚠️ Impossible d'envoyer erreur: {}", e.getMessage());
        }
    }
    
    /**
     * Notifie la complétion
     */
    public void completed(String batchId, String filename, 
                         int textEmbeddings, int imageEmbeddings) {
        try {
            progressService.completed(batchId, filename, textEmbeddings, imageEmbeddings);
        } catch (Exception e) {
            log.debug("⚠️ Impossible d'envoyer complétion: {}", e.getMessage());
        }
    }
}