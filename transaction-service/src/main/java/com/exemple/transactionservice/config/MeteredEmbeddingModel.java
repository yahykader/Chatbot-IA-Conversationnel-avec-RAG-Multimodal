package com.exemple.transactionservice.config;

import com.exemple.transactionservice.service.rag.ingestion.metrics.OpenAiMetrics;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class MeteredEmbeddingModel implements EmbeddingModel {
    
    private final EmbeddingModel delegate;
    private final OpenAiMetrics metrics;
    
    @Override
    public Response<Embedding> embed(String text) {
        long start = System.currentTimeMillis();
        try {
            Response<Embedding> response = delegate.embed(text);
            long duration = System.currentTimeMillis() - start;
            metrics.recordCall("embed_text", duration);
            return response;
        } catch (Exception e) {
            metrics.recordError("embed_text");
            throw e;
        }
    }
    
    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        long start = System.currentTimeMillis();
        try {
            Response<Embedding> response = delegate.embed(textSegment);
            long duration = System.currentTimeMillis() - start;
            metrics.recordCall("embed_segment", duration);
            return response;
        } catch (Exception e) {
            metrics.recordError("embed_segment");
            throw e;
        }
    }
    
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        long start = System.currentTimeMillis();
        try {
            Response<List<Embedding>> response = delegate.embedAll(textSegments);
            long duration = System.currentTimeMillis() - start;
            metrics.recordCall("embed_batch", duration);
            return response;
        } catch (Exception e) {
            metrics.recordError("embed_batch");
            throw e;
        }
    }
}
