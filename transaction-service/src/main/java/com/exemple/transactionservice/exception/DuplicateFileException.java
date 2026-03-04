package com.exemple.transactionservice.exception;

import lombok.Getter;

/**
 * ✅ MISE À JOUR: Support UUID String pour batchId
 */
@Getter
public class DuplicateFileException extends RuntimeException {
    
    /**
     * BatchId du fichier existant (UUID String ou numérique)
     */
    private final String existingBatchId;
    
    public DuplicateFileException(String message, String existingBatchId) {
        super(message);
        this.existingBatchId = existingBatchId;
    }
    
    public DuplicateFileException(String message) {
        super(message);
        this.existingBatchId = null;
    }
}