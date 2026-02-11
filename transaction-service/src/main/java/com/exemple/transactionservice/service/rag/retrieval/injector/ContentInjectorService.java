package com.exemple.transactionservice.service.rag.retrieval.injector;

import com.exemple.transactionservice.config.RetrievalConfig;
import com.exemple.transactionservice.service.rag.retrieval.model.AggregatedContext;
import com.exemple.transactionservice.service.rag.retrieval.model.AggregatedContext.SelectedChunk;
import com.exemple.transactionservice.service.rag.retrieval.model.InjectedPrompt;
import com.exemple.transactionservice.service.rag.retrieval.model.InjectedPrompt.PromptStructure;
import com.exemple.transactionservice.service.rag.retrieval.model.InjectedPrompt.SourceReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service d'injection de contenu dans le prompt
 * 
 * Construit le prompt final optimisé pour Claude:
 * - System prompt (cached)
 * - Documents context (avec métadonnées)
 * - User query
 * - Instructions (citations, format)
 * 
 * Optimise l'utilisation des tokens
 */
@Slf4j
@Service
public class ContentInjectorService {
    
    private final RetrievalConfig config;
    
    // Token estimés par caractère (approximatif)
    private static final double TOKENS_PER_CHAR = 0.25;
    
    public ContentInjectorService(RetrievalConfig config) {
        this.config = config;
    }
    
    /**
     * Injecte le contexte dans un prompt structuré
     */
    public InjectedPrompt injectContext(
            AggregatedContext context,
            String originalQuery) {
        
        long startTime = System.currentTimeMillis();
        
        log.info("💉 Injecting context: {} chunks into prompt", 
            context.getChunks().size());
        
        // Build prompt components
        String systemPrompt = buildSystemPrompt();
        String documentsContext = buildDocumentsContext(context.getChunks());
        String instructions = buildInstructions();
        
        // Assemble full prompt
        StringBuilder fullPrompt = new StringBuilder();
        fullPrompt.append(systemPrompt).append("\n\n");
        fullPrompt.append(documentsContext).append("\n\n");
        fullPrompt.append("<query>\n").append(originalQuery).append("\n</query>\n\n");
        fullPrompt.append(instructions);
        
        // Calculate tokens
        int systemTokens = estimateTokens(systemPrompt);
        int contextTokens = estimateTokens(documentsContext);
        int queryTokens = estimateTokens(originalQuery);
        int instructionsTokens = estimateTokens(instructions);
        int totalTokens = systemTokens + contextTokens + queryTokens + instructionsTokens;
        
        // Context usage
        int maxTokens = config.getContentInjector().getMaxTokens();
        double usagePercent = (totalTokens / (double) maxTokens) * 100;
        
        // Extract sources
        List<SourceReference> sources = extractSources(context.getChunks());
        
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("✅ Context injected: {} tokens ({:.1f}% of {}K), {} sources, {}ms", 
            totalTokens, usagePercent, maxTokens / 1000, sources.size(), duration);
        
        if (totalTokens > maxTokens * 0.8) {
            log.warn("⚠️ Context usage > 80%: consider reducing chunks");
        }
        
        return InjectedPrompt.builder()
            .fullPrompt(fullPrompt.toString())
            .structure(PromptStructure.builder()
                .systemPrompt(systemPrompt)
                .documentsContext(documentsContext)
                .userQuery(originalQuery)
                .instructions(instructions)
                .systemTokens(systemTokens)
                .contextTokens(contextTokens)
                .queryTokens(queryTokens)
                .instructionsTokens(instructionsTokens)
                .totalTokens(totalTokens)
                .build())
            .sources(sources)
            .contextUsagePercent(usagePercent)
            .durationMs(duration)
            .build();
    }
    
    /**
     * Construit le system prompt (cacheable)
     */
    private String buildSystemPrompt() {
        return """
            Tu es un assistant RAG (Retrieval-Augmented Generation) expert.
            
            Règles importantes:
            1. Utilise UNIQUEMENT les documents fournis pour répondre
            2. Si l'information n'est pas dans les documents, dis-le clairement
            3. Ne jamais inventer d'informations
            4. Cite systématiquement tes sources
            5. Si plusieurs sources disent la même chose, mentionne-les toutes
            6. Reste factuel et précis
            """;
    }
    
    /**
     * Construit le contexte des documents
     */
    private String buildDocumentsContext(List<SelectedChunk> chunks) {
        StringBuilder context = new StringBuilder();
        context.append("<documents>\n");
        
        for (int i = 0; i < chunks.size(); i++) {
            SelectedChunk chunk = chunks.get(i);
            Map<String, Object> metadata = chunk.getMetadata();
            
            context.append("  <document index=\"").append(i + 1).append("\">\n");
            
            // Metadata
            context.append("    <source>")
                   .append(getMetadataString(metadata, "source", "filename", "file"))
                   .append("</source>\n");
            
            if (metadata.containsKey("page")) {
                context.append("    <page>")
                       .append(metadata.get("page"))
                       .append("</page>\n");
            }
            
            if (metadata.containsKey("slide")) {
                context.append("    <slide>")
                       .append(metadata.get("slide"))
                       .append("</slide>\n");
            }
            
            String type = getMetadataString(metadata, "type");
            context.append("    <type>").append(type).append("</type>\n");
            
            context.append("    <relevance>")
                   .append(String.format("%.3f", chunk.getFinalScore()))
                   .append("</relevance>\n");
            
            // Content
            context.append("    <content>\n");
            context.append(chunk.getContent().trim());
            context.append("\n    </content>\n");
            
            context.append("  </document>\n\n");
        }
        
        context.append("</documents>");
        
        return context.toString();
    }
    
    /**
     * Construit les instructions
     */
    private String buildInstructions() {
        StringBuilder instructions = new StringBuilder();
        instructions.append("<instructions>\n");
        
        if (config.getContentInjector().isEnableCitations()) {
            String format = config.getContentInjector().getCitationFormat();
            instructions.append("1. Cite systématiquement avec: ")
                       .append(format)
                       .append("\n");
            instructions.append("2. Utilise l'index du document (1, 2, 3...)\n");
        }
        
        instructions.append("3. Réponds de manière structurée et claire\n");
        instructions.append("4. Utilise un ton professionnel\n");
        instructions.append("5. Si incertain, mentionne-le explicitement\n");
        instructions.append("6. Ne répète pas les mêmes informations\n");
        instructions.append("</instructions>");
        
        return instructions.toString();
    }
    
    /**
     * Extrait les sources pour référence
     */
    private List<SourceReference> extractSources(List<SelectedChunk> chunks) {
        return chunks.stream()
            .map(chunk -> {
                Map<String, Object> metadata = chunk.getMetadata();
                
                String file = getMetadataString(metadata, "source", "filename", "file");
                Object page = metadata.get("page");
                if (page == null) {
                    page = metadata.get("slide");
                }
                
                String type = getMetadataString(metadata, "type");
                int tokens = estimateTokens(chunk.getContent());
                
                return SourceReference.builder()
                    .file(file)
                    .page(page)
                    .relevance(chunk.getFinalScore())
                    .tokens(tokens)
                    .type(type)
                    .build();
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Estime le nombre de tokens
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() * TOKENS_PER_CHAR);
    }
    
    /**
     * Helper: récupère valeur metadata avec fallbacks
     */
    private String getMetadataString(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return "unknown";
    }
}