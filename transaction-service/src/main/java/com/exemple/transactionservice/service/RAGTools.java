// ============================================================================
// AI TOOLS - RAGTools.java (ENRICHI AVEC MÉTADONNÉES)
// ============================================================================
package com.exemple.transactionservice.service;

import com.exemple.transactionservice.config.RAGToolsConfig;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class RAGTools {
    
    private final MultimodalRAGService ragService;
    private final RAGToolsConfig config;
    
    public RAGTools(MultimodalRAGService ragService, RAGToolsConfig config) {
        this.ragService = ragService;
        this.config = config;
    }
    
    /**
     * Recherche dans les documents texte uploadés (ENRICHI)
     */
    @Tool("Recherche dans les documents texte uploadés par l'utilisateur. " +
          "Supporte PDF, Word, Excel, PowerPoint, TXT et autres formats texte. " +
          "Utilise cette fonction pour des questions sur le contenu des documents.")
    public String searchDocuments(String query) {
        Instant start = Instant.now();
        String requestId = UUID.randomUUID().toString();
        log.info("🔧 [{}] searchDocuments: '{}'", requestId, truncate(query));
        
        ValidationResult validation = validateQuery(query);
        if (!validation.isValid()) {
            log.warn("⚠️ [{}] Validation échouée: {}", requestId, validation.getError());
            return validation.getError();
        }
        
        try {
            List<TextSegment> results = ragService.searchText(
                query.trim(), 
                config.getMaxDocumentResults()
            );
            
            Duration duration = Duration.between(start, Instant.now());
            log.info("✅ [{}] searchDocuments terminé en {}ms - {} résultats", 
                requestId, duration.toMillis(), results.size());
            
            if (results.isEmpty()) {
                return formatNoResults("documents", query);
            }
            
            return formatDocumentResults(results, duration);
            
        } catch (Exception e) {
            log.error("❌ [{}] Erreur searchDocuments", requestId, e);
            return formatError("documents", e);
        }
    }
    
    /**
     * Recherche dans les images uploadées (ENRICHI)
     */
    @Tool("Recherche dans les images uploadées via leur description générée par IA. " +
          "Supporte PNG, JPG, GIF et autres formats image. " +
          "Inclut aussi les images extraites de PDF et documents Word. " +
          "Utilise cette fonction pour chercher des visuels, diagrammes, captures d'écran.")
    public String searchImages(String description) {
        Instant start = Instant.now();
        String requestId = UUID.randomUUID().toString();
        log.info("🔧 [{}] searchImages: '{}'", requestId, truncate(description));
        
        ValidationResult validation = validateQuery(description);
        if (!validation.isValid()) {
            log.warn("⚠️ [{}] Validation échouée: {}", requestId, validation.getError());
            return validation.getError();
        }
        
        try {
            List<TextSegment> results = ragService.searchImages(
                description.trim(), 
                config.getMaxImageResults()
            );
            
            Duration duration = Duration.between(start, Instant.now());
            log.info("✅ [{}] searchImages terminé en {}ms - {} résultats", 
                requestId, duration.toMillis(), results.size());
            
            if (results.isEmpty()) {
                return formatNoResults("images", description);
            }
            
            return formatImageResults(results, duration);
            
        } catch (Exception e) {
            log.error("❌ [{}] Erreur searchImages", requestId, e);
            return formatError("images", e);
        }
    }
    
    /**
     * Recherche multimodale (NOUVEAU - ENRICHI)
     */
    @Tool("Recherche combinée dans TOUS les documents ET images uploadés. " +
          "Utilise cette fonction pour des questions nécessitant à la fois " +
          "du texte et des éléments visuels. Parfait pour une vue d'ensemble complète.")
    public String searchAll(String query) {
        Instant start = Instant.now();
        String requestId = UUID.randomUUID().toString();
        log.info("🔧 [{}] searchAll: '{}'", requestId, truncate(query));
        
        ValidationResult validation = validateQuery(query);
        if (!validation.isValid()) {
            return validation.getError();
        }
        
        try {
            MultimodalRAGService.MultimodalSearchResult result = ragService.search(
                query.trim(),
                config.getMaxMultimodalResults()
            );
            
            Duration duration = Duration.between(start, Instant.now());
            log.info("✅ [{}] searchAll terminé en {}ms - {} résultats totaux", 
                requestId, duration.toMillis(), result.getTotalResults());
            
            if (result.isHasError()) {
                return "❌ Erreur: " + result.getErrorMessage();
            }
            
            if (result.getTotalResults() == 0) {
                return formatNoResults("documents et images", query);
            }
            
            return formatMultimodalResults(result);
            
        } catch (Exception e) {
            log.error("❌ [{}] Erreur searchAll", requestId, e);
            return formatError("multimodal", e);
        }
    }
    
    // ========================================================================
    // MÉTHODES PRIVÉES - VALIDATION
    // ========================================================================
    
    private ValidationResult validateQuery(String query) {
        if (query == null || query.isBlank()) {
            return ValidationResult.invalid("❌ La requête ne peut pas être vide.");
        }
        
        String trimmed = query.trim();
        
        if (trimmed.length() < config.getMinQueryLength()) {
            return ValidationResult.invalid(String.format(
                "❌ Requête trop courte (min: %d caractères).",
                config.getMinQueryLength()
            ));
        }
        
        if (trimmed.length() > config.getMaxQueryLength()) {
            return ValidationResult.invalid(String.format(
                "❌ Requête trop longue (max: %d caractères).",
                config.getMaxQueryLength()
            ));
        }
        
        return ValidationResult.valid();
    }
    
    // ========================================================================
    // MÉTHODES PRIVÉES - FORMATAGE (ENRICHI AVEC MÉTADONNÉES)
    // ========================================================================
    
    private String formatDocumentResults(List<TextSegment> results, Duration duration) {
        StringBuilder output = new StringBuilder();
        output.append(String.format("📚 **%d document(s) trouvé(s)** (%dms)\n\n", 
            results.size(), duration.toMillis()));
        
        for (int i = 0; i < results.size(); i++) {
            TextSegment segment = results.get(i);
            
            // Extraction des métadonnées
            String source = segment.metadata().getString("source");
            String type = segment.metadata().getString("type");
            Integer page = segment.metadata().getInteger("page");
            
            output.append(String.format("### 📄 Document %d\n", i + 1));
            output.append(String.format("**Fichier:** %s\n", source != null ? source : "Inconnu"));
            
            if (config.isIncludeMetadata()) {
                if (type != null) {
                    output.append(String.format("**Type:** %s\n", formatType(type)));
                }
                if (page != null) {
                    output.append(String.format("**Page:** %d\n", page));
                }
            }
            
            output.append("\n**Extrait:**\n");
            String text = segment.text();
            if (text.length() > config.getMaxResultTextLength()) {
                text = text.substring(0, config.getMaxResultTextLength()) + "...";
            }
            output.append(text).append("\n\n");
            
            if (i < results.size() - 1) {
                output.append("---\n\n");
            }
        }
        
        return output.toString();
    }
    
    private String formatImageResults(List<TextSegment> results, Duration duration) {
        StringBuilder output = new StringBuilder();
        output.append(String.format("🖼️ **%d image(s) trouvée(s)** (%dms)\n\n", 
            results.size(), duration.toMillis()));
        
        for (int i = 0; i < results.size(); i++) {
            TextSegment segment = results.get(i);
            
            // Extraction des métadonnées enrichies
            String imageName = segment.metadata().getString("imageName");
            String source = segment.metadata().getString("source");
            String filename = segment.metadata().getString("filename");
            Integer page = segment.metadata().getInteger("page");
            Integer imageNumber = segment.metadata().getInteger("imageNumber");
            Integer width = segment.metadata().getInteger("width");
            Integer height = segment.metadata().getInteger("height");
            String imageId = segment.metadata().getString("imageId");
            
            output.append(String.format("### 🖼️ Image %d\n", i + 1));
            
            if (imageName != null) {
                output.append(String.format("**Nom:** %s\n", imageName));
            }
            
            if (config.isIncludeMetadata()) {
                if (filename != null) {
                    output.append(String.format("**Fichier source:** %s\n", filename));
                }
                if (source != null && !"pdf".equals(source)) {
                    output.append(String.format("**Source:** %s\n", formatType(source)));
                }
                if (page != null) {
                    output.append(String.format("**Page:** %d\n", page));
                }
                if (imageNumber != null) {
                    output.append(String.format("**Image n°:** %d\n", imageNumber));
                }
                if (width != null && height != null) {
                    output.append(String.format("**Dimensions:** %dx%d px\n", width, height));
                }
            }
            
            output.append("\n**Description IA:**\n");
            output.append(segment.text()).append("\n\n");
            
            if (imageId != null) {
                output.append(String.format("_[Référence: %s]_\n\n", imageId));
            }
            
            if (i < results.size() - 1) {
                output.append("---\n\n");
            }
        }
        
        return output.toString();
    }
    
    private String formatMultimodalResults(MultimodalRAGService.MultimodalSearchResult result) {
        StringBuilder output = new StringBuilder();
        output.append(String.format(
            "🔍 **Résultats combinés** (%d documents + %d images)\n\n",
            result.getTextResults().size(),
            result.getImageResults().size()
        ));
        
        // Documents
        if (!result.getTextResults().isEmpty()) {
            output.append("## 📚 Documents\n\n");
            for (int i = 0; i < result.getTextResults().size(); i++) {
                TextSegment segment = result.getTextResults().get(i);
                String source = segment.metadata().getString("source");
                String type = segment.metadata().getString("type");
                
                output.append(String.format("**%d.** %s", i + 1, source != null ? source : "Document"));
                if (type != null && config.isIncludeMetadata()) {
                    output.append(String.format(" (%s)", formatType(type)));
                }
                output.append("\n");
                
                String text = segment.text();
                if (text.length() > 200) {
                    text = text.substring(0, 197) + "...";
                }
                output.append(text).append("\n\n");
            }
        }
        
        // Images
        if (!result.getImageResults().isEmpty()) {
            output.append("## 🖼️ Images\n\n");
            for (int i = 0; i < result.getImageResults().size(); i++) {
                TextSegment segment = result.getImageResults().get(i);
                String imageName = segment.metadata().getString("imageName");
                String filename = segment.metadata().getString("filename");
                
                output.append(String.format("**%d.** %s", i + 1, 
                    imageName != null ? imageName : (filename != null ? filename : "Image")));
                output.append("\n");
                
                String desc = segment.text();
                if (desc.length() > 150) {
                    desc = desc.substring(0, 147) + "...";
                }
                output.append(desc).append("\n\n");
            }
        }
        
        // Métriques (optionnel)
        if (config.isShowMetrics()) {
            output.append("---\n\n");
            output.append("**Statistiques:**\n");
            output.append(String.format("- Temps total: %dms\n", result.getTotalDurationMs()));
            output.append(String.format("- Score moyen documents: %.1f%%\n", 
                result.getTextMetrics().getAverageScore() * 100));
            output.append(String.format("- Score moyen images: %.1f%%\n", 
                result.getImageMetrics().getAverageScore() * 100));
        }
        
        return output.toString();
    }
    
    private String formatNoResults(String contentType, String query) {
        return String.format(
            "ℹ️ Aucun résultat trouvé dans les %s pour: \"%s\"\n\n" +
            "**Suggestions:**\n" +
            "- Essayez des mots-clés différents\n" +
            "- Utilisez des termes plus généraux\n" +
            "- Vérifiez que des fichiers ont été uploadés",
            contentType,
            truncate(query)
        );
    }
    
    private String formatError(String searchType, Exception e) {
        return String.format(
            "❌ Erreur lors de la recherche %s: %s\n\n" +
            "Veuillez réessayer.",
            searchType,
            e.getMessage()
        );
    }
    
    private String formatType(String type) {
        if (type == null) return "inconnu";
        
        return switch (type) {
            case "pdf" -> "PDF";
            case "docx", "office_docx" -> "Word";
            case "xlsx", "office_xlsx" -> "Excel";
            case "pptx", "office_pptx" -> "PowerPoint";
            case "text", "txt" -> "Texte";
            case "image" -> "Image";
            default -> type.contains("pdf_page") ? "PDF (page)" : type;
        };
    }
    
    private String truncate(String text) {
        if (text == null) return "null";
        return text.length() <= 100 ? text : text.substring(0, 97) + "...";
    }
    
    // ========================================================================
    // CLASSE INTERNE - VALIDATION
    // ========================================================================
    
    private static class ValidationResult {
        private final boolean valid;
        private final String error;
        
        private ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getError() {
            return error;
        }
    }
}