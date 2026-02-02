package com.exemple.transactionservice.service.rag.ingestion.strategy;

import com.exemple.transactionservice.service.rag.ingestion.model.IngestionResult;
import org.springframework.web.multipart.MultipartFile;

public interface IngestionStrategy {
    boolean canHandle(MultipartFile file, String extension);
    IngestionResult ingest(MultipartFile file, String batchId) throws Exception;
    String getName();
    default int getPriority() { return 5; }
}
