package com.exemple.transactionservice.service.rag.ingestion.config;

import com.exemple.transactionservice.service.rag.ingestion.strategy.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class IngestionConfig {
    
    @Bean
    public List<IngestionStrategy> ingestionStrategies(
            PdfIngestionStrategy pdf,
            DocxIngestionStrategy docx,
            XlsxIngestionStrategy xlsx,
            ImageIngestionStrategy image,
            TextIngestionStrategy text,
            TikaIngestionStrategy tika) {
        
        // Ordre = priorité de sélection
        return List.of(pdf, docx, xlsx, image, text, tika);
    }
}
