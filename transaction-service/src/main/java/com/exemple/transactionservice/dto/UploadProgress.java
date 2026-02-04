package com.exemple.transactionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadProgress {
    private String batchId;
    private String filename;
    private String stage;
    private int progressPercentage;
    private String message;
    private Integer currentChunk;
    private Integer totalChunks;
    private Integer embeddingsCreated;
    private Integer imagesProcessed;
    private int chunksCreated;
    
    public UploadProgress(String batchId, String filename, String stage, 
                          int progressPercentage, String message) {
        this.batchId = batchId;
        this.filename = filename;
        this.stage = stage;
        this.progressPercentage = progressPercentage;
        this.message = message;
    }
}