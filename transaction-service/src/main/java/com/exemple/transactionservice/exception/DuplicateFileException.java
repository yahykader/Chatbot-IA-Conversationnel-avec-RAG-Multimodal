package com.exemple.transactionservice.exception;

/**
 * Exception levée quand un fichier a déjà été ingéré
 */
public class DuplicateFileException extends Exception {
    
    private final String existingBatchId;
    
    // Constructeur original (pour compatibilité)
    public DuplicateFileException(String message) {
        super(message);
        this.existingBatchId = null;
    }
    
    // ✅ Nouveau constructeur avec batchId
    public DuplicateFileException(String message, String existingBatchId) {
        super(message);
        this.existingBatchId = existingBatchId;
    }
    
    // ✅ Getter pour batchId
    public String getExistingBatchId() {
        return existingBatchId;
    }
}