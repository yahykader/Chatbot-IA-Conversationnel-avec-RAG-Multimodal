package com.exemple.transactionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DuplicateInfo {
    private String jobId;
    private String originalFileName;
    private LocalDateTime uploadedAt;
    private String fingerprint;
    private Long fileSize;
}