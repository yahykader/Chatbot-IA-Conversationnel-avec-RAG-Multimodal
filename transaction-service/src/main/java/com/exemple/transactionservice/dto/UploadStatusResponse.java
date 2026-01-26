package com.exemple.transactionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadStatusResponse {
    private String jobId;
    private String filename;
    private String status;
    private int progress;
    private String message;
    private String error;
    private Instant createdAt;
    private Instant completedAt;
}
