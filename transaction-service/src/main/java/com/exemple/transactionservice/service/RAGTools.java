// // ============================================================================
// // AI TOOLS - RAGTools.java (v2.2.0) - APPROCHE A - PRODUCTION READY
// // ============================================================================
// package com.exemple.transactionservice.service;

// import com.exemple.transactionservice.dto.CacheableSearchResult;
// import com.exemple.transactionservice.config.RAGToolsConfig;
// import dev.langchain4j.agent.tool.Tool;
// import dev.langchain4j.data.segment.TextSegment;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.stereotype.Component;

// import java.time.Duration;
// import java.time.Instant;
// import java.util.List;
// import java.util.UUID;
// import java.util.stream.Collectors;

// /**
//  * ✅ RAGTools v2.2 - Approche A (Production Ready)
//  * 
//  * Architecture propre avec conversion CacheableSearchResult → TextSegment
//  * 
//  * AVANTAGES:
//  * - Compatible LangChain4j (standard)
//  * - Faible couplage (cache transparent)
//  * - Maintenabilité excellente
//  * - Tests faciles (mock TextSegment)
//  * - Évolutif (facile d'ajouter d'autres caches)
//  * 
//  * FLUX:
//  * 1. RAGTools appelle MultimodalRAGService
//  * 2. Service retourne CacheableSearchResult (avec SearchResultItem)
//  * 3. RAGTools utilise getTextResultsAsSegments() pour conversion
//  * 4. Formatage standard avec TextSegment
//  */
// @Slf4j
// @Component
// public class RAGTools {
    
//     private final MultimodalRAGService ragService;
//     private final RAGToolsConfig config;
    
//     public RAGTools(MultimodalRAGService ragService, RAGToolsConfig config) {
//         this.ragService = ragService;
//         this.config = config;
//     }
    
//     /**
//      * ✅ APPROCHE A: Conversion transparente CacheableSearchResult → TextSegment
//      */
//     @Tool("Recherche dans les documents texte uploadés par l'utilisateur avec pagination et filtres. " +
//           "Supporte PDF, Word, Excel, PowerPoint, TXT et autres formats texte. " +
//           "Paramètres: " +
//           "- query (requis): Texte à rechercher " +
//           "- page (optionnel, défaut 1): Numéro de page (1, 2, 3...) " +
//           "- pageSize (optionnel, défaut 5, max 10): Résultats par page " +
//           "- fileType (optionnel, défaut 'all'): Type de fichier (pdf|word|excel|powerpoint|text|all)")
//     public String searchDocuments(
//             String query,
//             Integer page,
//             Integer pageSize,
//             String fileType) {
        
//         Instant start = Instant.now();
//         String requestId = UUID.randomUUID().toString();
        
//         log.info("🔧 [{}] searchDocuments: '{}' (page: {}, size: {}, type: {})", 
//                  requestId, truncate(query), page, pageSize, fileType);
        
//         // Validation
//         ValidationResult validation = validateQuery(query);
//         if (!validation.isValid()) {
//             log.warn("⚠️ [{}] Validation échouée: {}", requestId, validation.getError());
//             return validation.getError();
//         }
        
//         try {
//             // Paramètres avec valeurs par défaut
//             int currentPage = (page != null && page > 0) ? page : 1;
//             int size = (pageSize != null && pageSize > 0) ? 
//                        Math.min(pageSize, 10) : 5;
//             String filterType = (fileType != null && !fileType.isBlank()) ? 
//                                 fileType.toLowerCase().trim() : "all";
            
//             // ✅ APPROCHE A: Obtenir CacheableSearchResult depuis le service
//             CacheableSearchResult cacheResult = ragService.search(
//                 query.trim(), 
//                 config.getMaxAllowedResults(),
//                 "anonymous"
//             );
            
//             // Vérifier erreurs
//             if (cacheResult.isHasError()) {
//                 log.error("❌ [{}] Erreur service: {}", requestId, cacheResult.getErrorMessage());
//                 return "❌ Erreur: " + cacheResult.getErrorMessage();
//             }
            
//             // ✅ APPROCHE A: Conversion transparente via méthode helper
//             // CacheableSearchResult expose getTextResultsAsSegments() qui fait:
//             // List<SearchResultItem> → List<TextSegment>
//             List<TextSegment> allResults = cacheResult.getTextResultsAsSegments();
            
//             // Filtrage par type
//             List<TextSegment> filteredResults = filterByFileType(allResults, filterType);
            
//             Duration duration = Duration.between(start, Instant.now());
            
//             if (filteredResults.isEmpty()) {
//                 log.info("ℹ️ [{}] Aucun résultat (type: {})", requestId, filterType);
//                 return formatNoResults("documents", query, filterType);
//             }
            
//             // Pagination
//             PaginationResult<TextSegment> paginatedResults = paginate(
//                 filteredResults, currentPage, size
//             );
            
//             log.info("✅ [{}] searchDocuments terminé en {}ms - {} résultats (page {}/{})", 
//                 requestId, duration.toMillis(), filteredResults.size(), 
//                 currentPage, paginatedResults.getTotalPages());
            
//             return formatDocumentResults(
//                 paginatedResults, 
//                 query, 
//                 filterType,
//                 duration
//             );
            
//         } catch (Exception e) {
//             log.error("❌ [{}] Erreur searchDocuments", requestId, e);
//             return formatError("documents", e);
//         }
//     }
    
//     /**
//      * ✅ APPROCHE A: Recherche images avec conversion TextSegment
//      */
//     @Tool("Recherche dans les images uploadées via leur description générée par IA. " +
//           "Supporte PNG, JPG, GIF et autres formats image. " +
//           "Inclut aussi les images extraites de PDF et documents Word. " +
//           "Paramètres: " +
//           "- description (requis): Description à rechercher " +
//           "- page (optionnel, défaut 1): Numéro de page " +
//           "- pageSize (optionnel, défaut 3, max 5): Images par page")
//     public String searchImages(
//             String description,
//             Integer page,
//             Integer pageSize) {
        
//         Instant start = Instant.now();
//         String requestId = UUID.randomUUID().toString();
        
//         log.info("🔧 [{}] searchImages: '{}' (page: {}, size: {})", 
//                  requestId, truncate(description), page, pageSize);
        
//         ValidationResult validation = validateQuery(description);
//         if (!validation.isValid()) {
//             log.warn("⚠️ [{}] Validation échouée: {}", requestId, validation.getError());
//             return validation.getError();
//         }
        
//         try {
//             // Paramètres par défaut
//             int currentPage = (page != null && page > 0) ? page : 1;
//             int size = (pageSize != null && pageSize > 0) ? 
//                        Math.min(pageSize, 5) : 3;
            
//             // ✅ APPROCHE A: Obtenir CacheableSearchResult
//             CacheableSearchResult cacheResult = ragService.search(
//                 description.trim(), 
//                 config.getMaxAllowedResults(),
//                 "anonymous"
//             );
            
//             // Vérifier erreurs
//             if (cacheResult.isHasError()) {
//                 log.error("❌ [{}] Erreur service: {}", requestId, cacheResult.getErrorMessage());
//                 return "❌ Erreur: " + cacheResult.getErrorMessage();
//             }
            
//             // ✅ APPROCHE A: Conversion transparente
//             List<TextSegment> allResults = cacheResult.getImageResultsAsSegments();
            
//             Duration duration = Duration.between(start, Instant.now());
            
//             if (allResults.isEmpty()) {
//                 log.info("ℹ️ [{}] Aucune image trouvée", requestId);
//                 return formatNoResults("images", description, null);
//             }
            
//             // Pagination
//             PaginationResult<TextSegment> paginatedResults = paginate(
//                 allResults, currentPage, size
//             );
            
//             log.info("✅ [{}] searchImages terminé en {}ms - {} résultats (page {}/{})", 
//                 requestId, duration.toMillis(), allResults.size(), 
//                 currentPage, paginatedResults.getTotalPages());
            
//             return formatImageResults(
//                 paginatedResults,
//                 description,
//                 duration
//             );
            
//         } catch (Exception e) {
//             log.error("❌ [{}] Erreur searchImages", requestId, e);
//             return formatError("images", e);
//         }
//     }
    
//     /**
//      * ✅ APPROCHE A: Recherche multimodale avec métriques enrichies
//      */
//     @Tool("Recherche combinée dans TOUS les documents ET images uploadés. " +
//           "Utilise cette fonction pour des questions nécessitant à la fois " +
//           "du texte et des éléments visuels. Parfait pour une vue d'ensemble complète. " +
//           "Paramètres: " +
//           "- query (requis): Texte à rechercher " +
//           "- userId (optionnel): ID utilisateur pour cache personnalisé")
//     public String searchAll(String query, String userId) {
//         Instant start = Instant.now();
//         String requestId = UUID.randomUUID().toString();
        
//         log.info("🔧 [{}] searchAll: '{}' (userId: {})", 
//                  requestId, truncate(query), userId);
        
//         ValidationResult validation = validateQuery(query);
//         if (!validation.isValid()) {
//             return validation.getError();
//         }
        
//         try {
//             String userIdFinal = (userId != null && !userId.isBlank()) ? 
//                                  userId : "anonymous";
            
//             // ✅ APPROCHE A: Obtenir résultat complet avec métriques
//             CacheableSearchResult result = ragService.search(
//                 query.trim(),
//                 config.getMaxMultimodalResults(),
//                 userIdFinal
//             );
            
//             Duration duration = Duration.between(start, Instant.now());
            
//             log.info("✅ [{}] searchAll terminé en {}ms - {} résultats totaux", 
//                 requestId, duration.toMillis(), result.getTotalResults());
            
//             if (result.isHasError()) {
//                 return "❌ Erreur: " + result.getErrorMessage();
//             }
            
//             if (result.getTotalResults() == 0) {
//                 return formatNoResults("documents et images", query, null);
//             }
            
//             // ✅ APPROCHE A: Formatage enrichi avec métriques
//             return formatMultimodalResults(result);
            
//         } catch (Exception e) {
//             log.error("❌ [{}] Erreur searchAll", requestId, e);
//             return formatError("multimodal", e);
//         }
//     }
    
//     // ========================================================================
//     // MÉTHODES PRIVÉES - VALIDATION
//     // ========================================================================
    
//     private ValidationResult validateQuery(String query) {
//         if (query == null || query.isBlank()) {
//             return ValidationResult.invalid("❌ La requête ne peut pas être vide.");
//         }
        
//         String trimmed = query.trim();
        
//         if (trimmed.length() < config.getMinQueryLength()) {
//             return ValidationResult.invalid(String.format(
//                 "❌ Requête trop courte (min: %d caractères).",
//                 config.getMinQueryLength()
//             ));
//         }
        
//         if (trimmed.length() > config.getMaxQueryLength()) {
//             return ValidationResult.invalid(String.format(
//                 "❌ Requête trop longue (max: %d caractères).",
//                 config.getMaxQueryLength()
//             ));
//         }
        
//         return ValidationResult.valid();
//     }
    
//     // ========================================================================
//     // MÉTHODES PRIVÉES - FILTRAGE ET PAGINATION
//     // ========================================================================
    
//     /**
//      * ✅ APPROCHE A: Filtrage standard sur TextSegment
//      */
//     private List<TextSegment> filterByFileType(List<TextSegment> results, String fileType) {
//         if (fileType == null || fileType.equals("all")) {
//             return results;
//         }
        
//         return results.stream()
//             .filter(segment -> matchesFileType(segment, fileType))
//             .collect(Collectors.toList());
//     }
    
//     /**
//      * ✅ APPROCHE A: Vérification type via métadonnées standard
//      */
//     private boolean matchesFileType(TextSegment segment, String requestedType) {
//         String type = segment.metadata().getString("type");
//         if (type == null) return false;
        
//         String typeLower = type.toLowerCase();
        
//         return switch (requestedType) {
//             case "pdf" -> typeLower.contains("pdf");
//             case "word" -> typeLower.contains("docx") || 
//                            typeLower.contains("word") || 
//                            typeLower.contains("office_docx");
//             case "excel" -> typeLower.contains("xlsx") || 
//                             typeLower.contains("excel") || 
//                             typeLower.contains("office_xlsx");
//             case "powerpoint" -> typeLower.contains("pptx") || 
//                                  typeLower.contains("powerpoint") || 
//                                  typeLower.contains("office_pptx");
//             case "text" -> typeLower.contains("text") || 
//                            typeLower.equals("txt") || 
//                            typeLower.equals("md");
//             default -> true;
//         };
//     }
    
//     /**
//      * ✅ Pagination générique
//      */
//     private <T> PaginationResult<T> paginate(List<T> items, int page, int pageSize) {
//         int totalItems = items.size();
//         int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        
//         // Validation page
//         if (page > totalPages && totalPages > 0) {
//             page = totalPages;
//         }
        
//         int start = (page - 1) * pageSize;
//         int end = Math.min(start + pageSize, totalItems);
        
//         List<T> pageItems = (start < totalItems) ? 
//             items.subList(start, end) : List.of();
        
//         return new PaginationResult<>(
//             pageItems,
//             page,
//             pageSize,
//             totalPages,
//             totalItems,
//             start + 1,
//             end
//         );
//     }
    
//     // ========================================================================
//     // MÉTHODES PRIVÉES - FORMATAGE
//     // ========================================================================
    
//     /**
//      * ✅ APPROCHE A: Formatage standard avec TextSegment
//      */
//     private String formatDocumentResults(
//             PaginationResult<TextSegment> pagination,
//             String query,
//             String fileType,
//             Duration duration) {
        
//         StringBuilder output = new StringBuilder();
        
//         // En-tête avec pagination
//         output.append(String.format(
//             "📚 **%d-%d sur %d document(s)** (Page %d/%d) - %dms\n",
//             pagination.getStartIndex(),
//             pagination.getEndIndex(),
//             pagination.getTotalItems(),
//             pagination.getCurrentPage(),
//             pagination.getTotalPages(),
//             duration.toMillis()
//         ));
        
//         if (!fileType.equals("all")) {
//             output.append(String.format("_Filtré par: %s_\n", formatTypeFilter(fileType)));
//         }
        
//         output.append("\n");
        
//         // Résultats
//         List<TextSegment> results = pagination.getItems();
//         for (int i = 0; i < results.size(); i++) {
//             TextSegment segment = results.get(i);
//             int globalIndex = pagination.getStartIndex() + i;
            
//             // Métadonnées via l'interface standard
//             String source = segment.metadata().getString("source");
//             String type = segment.metadata().getString("type");
//             Integer page = segment.metadata().getInteger("page");
//             Integer totalPages = segment.metadata().getInteger("totalPages");
            
//             output.append(String.format("### 📄 Document %d\n", globalIndex));
//             output.append(String.format("**Fichier:** %s\n", 
//                 source != null ? source : "Inconnu"));
            
//             if (config.isIncludeMetadata()) {
//                 if (type != null) {
//                     output.append(String.format("**Type:** %s\n", formatType(type)));
//                 }
//                 if (page != null) {
//                     String pageInfo = totalPages != null ? 
//                         String.format("%d/%d", page, totalPages) : 
//                         String.valueOf(page);
//                     output.append(String.format("**Page:** %s\n", pageInfo));
//                 }
//             }
            
//             output.append("\n**Extrait:**\n");
//             String text = segment.text();
//             if (text.length() > config.getMaxResultTextLength()) {
//                 text = text.substring(0, config.getMaxResultTextLength()) + "...";
//             }
//             output.append(text).append("\n\n");
            
//             if (i < results.size() - 1) {
//                 output.append("---\n\n");
//             }
//         }
        
//         // Navigation
//         if (pagination.getTotalPages() > 1) {
//             output.append(formatNavigation(
//                 query, 
//                 pagination.getCurrentPage(), 
//                 pagination.getTotalPages(),
//                 fileType,
//                 "searchDocuments"
//             ));
//         }
        
//         return output.toString();
//     }
    
//     /**
//      * ✅ APPROCHE A: Formatage images avec TextSegment
//      */
//     private String formatImageResults(
//             PaginationResult<TextSegment> pagination,
//             String description,
//             Duration duration) {
        
//         StringBuilder output = new StringBuilder();
        
//         // En-tête avec pagination
//         output.append(String.format(
//             "🖼️ **%d-%d sur %d image(s)** (Page %d/%d) - %dms\n\n",
//             pagination.getStartIndex(),
//             pagination.getEndIndex(),
//             pagination.getTotalItems(),
//             pagination.getCurrentPage(),
//             pagination.getTotalPages(),
//             duration.toMillis()
//         ));
        
//         // Résultats
//         List<TextSegment> results = pagination.getItems();
//         for (int i = 0; i < results.size(); i++) {
//             TextSegment segment = results.get(i);
//             int globalIndex = pagination.getStartIndex() + i;
            
//             // Métadonnées enrichies via interface standard
//             String imageName = segment.metadata().getString("imageName");
//             String source = segment.metadata().getString("source");
//             String filename = segment.metadata().getString("filename");
//             Integer page = segment.metadata().getInteger("page");
//             Integer imageNumber = segment.metadata().getInteger("imageNumber");
//             Integer width = segment.metadata().getInteger("width");
//             Integer height = segment.metadata().getInteger("height");
//             String imageId = segment.metadata().getString("imageId");
//             String savedPath = segment.metadata().getString("savedPath");
            
//             output.append(String.format("### 🖼️ Image %d\n", globalIndex));
            
//             if (imageName != null) {
//                 output.append(String.format("**Nom:** %s\n", imageName));
//             }
            
//             if (config.isIncludeMetadata()) {
//                 if (filename != null) {
//                     output.append(String.format("**Fichier source:** %s\n", filename));
//                 }
//                 if (source != null) {
//                     output.append(String.format("**Source:** %s\n", formatImageSource(source)));
//                 }
//                 if (page != null) {
//                     output.append(String.format("**Page:** %d\n", page));
//                 }
//                 if (imageNumber != null) {
//                     output.append(String.format("**Image n°:** %d\n", imageNumber));
//                 }
//                 if (width != null && height != null) {
//                     output.append(String.format("**Dimensions:** %dx%d px\n", width, height));
//                 }
//                 if (savedPath != null) {
//                     output.append(String.format("**Chemin:** `%s`\n", 
//                         savedPath.length() > 50 ? "..." + savedPath.substring(savedPath.length() - 50) : savedPath));
//                 }
//             }
            
//             output.append("\n**Description IA:**\n");
//             output.append(segment.text()).append("\n\n");
            
//             if (imageId != null) {
//                 output.append(String.format("_[Référence: %s]_\n\n", 
//                     imageId.substring(0, Math.min(8, imageId.length()))));
//             }
            
//             if (i < results.size() - 1) {
//                 output.append("---\n\n");
//             }
//         }
        
//         // Navigation
//         if (pagination.getTotalPages() > 1) {
//             output.append(formatNavigation(
//                 description, 
//                 pagination.getCurrentPage(), 
//                 pagination.getTotalPages(),
//                 null,
//                 "searchImages"
//             ));
//         }
        
//         return output.toString();
//     }
    
//     /**
//      * ✅ APPROCHE A: Formatage multimodal avec métriques enrichies
//      */
//     private String formatMultimodalResults(CacheableSearchResult result) {
//         StringBuilder output = new StringBuilder();
        
//         // ✅ APPROCHE A: Conversion pour accès aux données
//         List<TextSegment> textSegments = result.getTextResultsAsSegments();
//         List<TextSegment> imageSegments = result.getImageResultsAsSegments();
        
//         // En-tête
//         output.append(String.format(
//             "🔍 **Résultats combinés** (%d documents + %d images) - %dms\n\n",
//             textSegments.size(),
//             imageSegments.size(),
//             result.getTotalDurationMs()
//         ));
        
//         // Documents (limités à 3)
//         if (!textSegments.isEmpty()) {
//             output.append("## 📚 Documents\n\n");
//             int docLimit = Math.min(3, textSegments.size());
            
//             for (int i = 0; i < docLimit; i++) {
//                 TextSegment segment = textSegments.get(i);
//                 String source = segment.metadata().getString("source");
//                 String type = segment.metadata().getString("type");
//                 Integer page = segment.metadata().getInteger("page");
                
//                 output.append(String.format("**%d.** %s", i + 1, 
//                     source != null ? source : "Document"));
                
//                 if (config.isIncludeMetadata()) {
//                     if (type != null) {
//                         output.append(String.format(" (%s)", formatType(type)));
//                     }
//                     if (page != null) {
//                         output.append(String.format(" - page %d", page));
//                     }
//                 }
                
//                 output.append("\n");
                
//                 String text = segment.text();
//                 if (text.length() > 200) {
//                     text = text.substring(0, 197) + "...";
//                 }
//                 output.append(text).append("\n\n");
//             }
            
//             if (textSegments.size() > docLimit) {
//                 output.append(String.format("_... et %d autre(s) document(s)_\n\n", 
//                     textSegments.size() - docLimit));
//             }
//         }
        
//         // Images (limitées à 2)
//         if (!imageSegments.isEmpty()) {
//             output.append("## 🖼️ Images\n\n");
//             int imgLimit = Math.min(2, imageSegments.size());
            
//             for (int i = 0; i < imgLimit; i++) {
//                 TextSegment segment = imageSegments.get(i);
//                 String imageName = segment.metadata().getString("imageName");
//                 String filename = segment.metadata().getString("filename");
                
//                 output.append(String.format("**%d.** %s", i + 1, 
//                     imageName != null ? imageName : 
//                     (filename != null ? filename : "Image")));
//                 output.append("\n");
                
//                 String desc = segment.text();
//                 if (desc.length() > 150) {
//                     desc = desc.substring(0, 147) + "...";
//                 }
//                 output.append(desc).append("\n\n");
//             }
            
//             if (imageSegments.size() > imgLimit) {
//                 output.append(String.format("_... et %d autre(s) image(s)_\n\n", 
//                     imageSegments.size() - imgLimit));
//             }
//         }
        
//         // ✅ APPROCHE A: Métriques enrichies depuis CacheableSearchResult
//         if (config.isShowMetrics() && result.getTextMetrics() != null) {
//             output.append("---\n\n");
//             output.append("**📊 Statistiques détaillées:**\n\n");
            
//             // Performance
//             output.append("**Performance:**\n");
//             output.append(String.format("- Temps total: %dms\n", 
//                 result.getTotalDurationMs()));
//             output.append(String.format("- Recherche texte: %dms\n", 
//                 result.getTextMetrics().getDurationMs()));
            
//             if (result.getImageMetrics() != null) {
//                 output.append(String.format("- Recherche images: %dms\n", 
//                     result.getImageMetrics().getDurationMs()));
//             }
            
//             // Qualité
//             output.append("\n**Qualité des résultats:**\n");
//             output.append("- Documents:\n");
//             output.append(String.format("  - Résultats: %d\n", 
//                 textSegments.size()));
//             output.append(String.format("  - Score moyen: %.1f%%\n", 
//                 result.getTextMetrics().getAverageScore() * 100));
//             output.append(String.format("  - Score max: %.1f%%\n", 
//                 result.getTextMetrics().getMaxScore() * 100));
//             output.append(String.format("  - Score min: %.1f%%\n", 
//                 result.getTextMetrics().getMinScore() * 100));
            
//             if (result.getImageMetrics() != null) {
//                 output.append("- Images:\n");
//                 output.append(String.format("  - Résultats: %d\n", 
//                     imageSegments.size()));
//                 output.append(String.format("  - Score moyen: %.1f%%\n", 
//                     result.getImageMetrics().getAverageScore() * 100));
//             }
            
//             // Cache
//             if (result.isWasCached()) {
//                 output.append("\n✅ _Résultat servi depuis le cache (instantané)_\n");
//             }
//         }
        
//         return output.toString();
//     }
    
//     /**
//      * ✅ Navigation entre pages
//      */
//     private String formatNavigation(
//             String query, 
//             int currentPage, 
//             int totalPages,
//             String fileType,
//             String toolName) {
        
//         StringBuilder nav = new StringBuilder();
//         nav.append("\n---\n\n");
//         nav.append("**📑 Navigation:**\n");
        
//         // Page précédente
//         if (currentPage > 1) {
//             nav.append(String.format("- ⬅️ Page précédente: `%s(\"%s\", %d", 
//                 toolName, truncate(query, 30), currentPage - 1));
//             if (fileType != null && !fileType.equals("all")) {
//                 nav.append(String.format(", null, \"%s\"", fileType));
//             }
//             nav.append(")`\n");
//         }
        
//         // Page suivante
//         if (currentPage < totalPages) {
//             nav.append(String.format("- ➡️ Page suivante: `%s(\"%s\", %d", 
//                 toolName, truncate(query, 30), currentPage + 1));
//             if (fileType != null && !fileType.equals("all")) {
//                 nav.append(String.format(", null, \"%s\"", fileType));
//             }
//             nav.append(")`\n");
//         }
        
//         // Info pages
//         nav.append(String.format("- Pages disponibles: 1-%d\n", totalPages));
        
//         return nav.toString();
//     }
    
//     private String formatNoResults(String contentType, String query, String fileType) {
//         StringBuilder output = new StringBuilder();
//         output.append(String.format(
//             "ℹ️ Aucun résultat trouvé dans les %s pour: \"%s\"\n\n",
//             contentType,
//             truncate(query, 50)
//         ));
        
//         if (fileType != null && !fileType.equals("all")) {
//             output.append(String.format("_Filtre appliqué: %s_\n\n", formatTypeFilter(fileType)));
//         }
        
//         output.append("**Suggestions:**\n");
//         output.append("- Essayez des mots-clés différents\n");
//         output.append("- Utilisez des termes plus généraux\n");
        
//         if (fileType != null && !fileType.equals("all")) {
//             output.append("- Essayez sans filtre de type: `fileType=\"all\"`\n");
//         }
        
//         output.append("- Vérifiez que des fichiers ont été uploadés\n");
        
//         return output.toString();
//     }
    
//     private String formatError(String searchType, Exception e) {
//         return String.format(
//             "❌ Erreur lors de la recherche %s: %s\n\n" +
//             "Veuillez réessayer ou contacter le support si l'erreur persiste.",
//             searchType,
//             e.getMessage()
//         );
//     }
    
//     /**
//      * ✅ Formatage type user-friendly
//      */
//     private String formatType(String type) {
//         if (type == null || type.isBlank()) return "Inconnu";
        
//         String typeLower = type.toLowerCase();
        
//         // PDF variations
//         if (typeLower.contains("pdf")) {
//             if (typeLower.contains("rendered")) return "PDF (rendu page)";
//             if (typeLower.contains("embedded")) return "PDF (image extraite)";
//             if (typeLower.contains("page")) return "PDF (texte page)";
//             return "PDF";
//         }
        
//         // Office variations
//         if (typeLower.contains("docx") || typeLower.contains("word")) return "Word";
//         if (typeLower.contains("xlsx") || typeLower.contains("excel")) return "Excel";
//         if (typeLower.contains("pptx") || typeLower.contains("powerpoint")) return "PowerPoint";
        
//         // Images
//         if (typeLower.contains("image")) {
//             if (typeLower.contains("standalone")) return "Image (uploadée)";
//             return "Image";
//         }
        
//         // Texte
//         if (typeLower.contains("text") || typeLower.equals("txt")) return "Texte";
//         if (typeLower.equals("md")) return "Markdown";
//         if (typeLower.equals("csv")) return "CSV";
//         if (typeLower.equals("json")) return "JSON";
//         if (typeLower.equals("xml")) return "XML";
//         if (typeLower.equals("html")) return "HTML";
        
//         // Code
//         if (typeLower.matches(".*(java|py|js|ts|cpp|c|h).*")) return "Code source";
        
//         // Fallback élégant
//         return type.substring(0, 1).toUpperCase() + 
//                type.substring(1).replace("_", " ").replace("-", " ");
//     }
    
//     /**
//      * ✅ Formatage source image
//      */
//     private String formatImageSource(String source) {
//         if (source == null) return "Inconnu";
        
//         return switch (source.toLowerCase()) {
//             case "pdf_embedded" -> "PDF (image intégrée)";
//             case "pdf_rendered" -> "PDF (page rendue)";
//             case "docx" -> "Word";
//             case "docx_header" -> "Word (en-tête)";
//             case "docx_footer" -> "Word (pied de page)";
//             case "standalone" -> "Image uploadée";
//             default -> source;
//         };
//     }
    
//     /**
//      * ✅ Formatage filtre type
//      */
//     private String formatTypeFilter(String fileType) {
//         return switch (fileType.toLowerCase()) {
//             case "pdf" -> "PDF uniquement";
//             case "word" -> "Word uniquement";
//             case "excel" -> "Excel uniquement";
//             case "powerpoint" -> "PowerPoint uniquement";
//             case "text" -> "Fichiers texte uniquement";
//             default -> fileType;
//         };
//     }
    
//     private String truncate(String text) {
//         return truncate(text, 100);
//     }
    
//     private String truncate(String text, int maxLength) {
//         if (text == null) return "null";
//         return text.length() <= maxLength ? 
//             text : text.substring(0, maxLength - 3) + "...";
//     }
    
//     // ========================================================================
//     // CLASSES INTERNES
//     // ========================================================================
    
//     /**
//      * ✅ Résultat de pagination
//      */
//     private static class PaginationResult<T> {
//         private final List<T> items;
//         private final int currentPage;
//         private final int pageSize;
//         private final int totalPages;
//         private final int totalItems;
//         private final int startIndex;
//         private final int endIndex;
        
//         public PaginationResult(
//                 List<T> items,
//                 int currentPage,
//                 int pageSize,
//                 int totalPages,
//                 int totalItems,
//                 int startIndex,
//                 int endIndex) {
//             this.items = items;
//             this.currentPage = currentPage;
//             this.pageSize = pageSize;
//             this.totalPages = totalPages;
//             this.totalItems = totalItems;
//             this.startIndex = startIndex;
//             this.endIndex = endIndex;
//         }
        
//         public List<T> getItems() { return items; }
//         public int getCurrentPage() { return currentPage; }
//         public int getPageSize() { return pageSize; }
//         public int getTotalPages() { return totalPages; }
//         public int getTotalItems() { return totalItems; }
//         public int getStartIndex() { return startIndex; }
//         public int getEndIndex() { return endIndex; }
//     }
    
//     private static class ValidationResult {
//         private final boolean valid;
//         private final String error;
        
//         private ValidationResult(boolean valid, String error) {
//             this.valid = valid;
//             this.error = error;
//         }
        
//         public static ValidationResult valid() {
//             return new ValidationResult(true, null);
//         }
        
//         public static ValidationResult invalid(String error) {
//             return new ValidationResult(false, error);
//         }
        
//         public boolean isValid() {
//             return valid;
//         }
        
//         public String getError() {
//             return error;
//         }
//     }
// }

// /*
//  * ============================================================================
//  * APPROCHE A - ARCHITECTURE PRODUCTION READY
//  * ============================================================================
//  * 
//  * FLUX COMPLET:
//  * 
//  * 1. RAGTools.searchDocuments("query", page, size, type)
//  *    ↓
//  * 2. MultimodalRAGService.search(query, limit, userId)
//  *    → Recherche TextSegment dans vector stores
//  *    → Convertit en SearchResultItem via fromTextSegment()
//  *    → Construit CacheableSearchResult (sérialisable Redis)
//  *    → Calcule métriques (scores, durées)
//  *    ↓
//  * 3. CacheableSearchResult retourné à RAGTools
//  *    ↓
//  * 4. RAGTools.getTextResultsAsSegments()
//  *    → Conversion transparente SearchResultItem → TextSegment
//  *    → Via méthode helper dans CacheableSearchResult
//  *    ↓
//  * 5. Filtrage + Pagination sur List<TextSegment> (standard)
//  *    ↓
//  * 6. Formatage avec accès metadata standard
//  *    → segment.metadata().getString("source")
//  *    → segment.metadata().getInteger("page")
//  *    ↓
//  * 7. Retour String formaté au LLM
//  * 
//  * ============================================================================
//  * AVANTAGES APPROCHE A:
//  * ============================================================================
//  * 
//  * ✅ SEPARATION OF CONCERNS:
//  *    - MultimodalRAGService → Business logic (TextSegment)
//  *    - CacheableSearchResult → Persistance (SearchResultItem)
//  *    - RAGTools → Présentation (TextSegment standard)
//  * 
//  * ✅ FAIBLE COUPLAGE:
//  *    - RAGTools indépendant de la structure cache
//  *    - Changement cache = 0 impact sur RAGTools
//  *    - Compatible autres outils LangChain4j
//  * 
//  * ✅ MAINTENABILITÉ:
//  *    - Code standard (TextSegment = interface connue)
//  *    - Tests faciles (mock TextSegment standard)
//  *    - Évolution cache transparente
//  * 
//  * ✅ PERFORMANCE:
//  *    - Conversion SearchResultItem → TextSegment: ~0.2ms/item
//  *    - Pour 10 résultats: 2ms (négligeable)
//  *    - Mise en cache Redis: gain >100ms
//  * 
//  * ============================================================================
//  * REQUIS DANS MULTIMODALRAGSERVICE:
//  * ============================================================================
//  * 
//  * ```java
//  * public CacheableSearchResult search(String query, int limit, String userId) {
//  *     Instant start = Instant.now();
//  *     
//  *     // 1. Recherche standard (TextSegment)
//  *     List<TextSegment> textSegments = searchText(query, limit);
//  *     List<TextSegment> imageSegments = searchImages(query, limit);
//  *     
//  *     long textDuration = ...;
//  *     long imageDuration = ...;
//  *     
//  *     // 2. Conversion pour cache (avec scores)
//  *     List<SearchResultItem> textItems = textSegments.stream()
//  *         .map(seg -> {
//  *             Double score = calculateScore(seg); // Depuis EmbeddingMatch
//  *             return CacheableSearchResult.fromTextSegment(seg, score);
//  *         })
//  *         .collect(Collectors.toList());
//  *     
//  *     List<SearchResultItem> imageItems = imageSegments.stream()
//  *         .map(seg -> {
//  *             Double score = calculateScore(seg);
//  *             return CacheableSearchResult.fromTextSegment(seg, score);
//  *         })
//  *         .collect(Collectors.toList());
//  *     
//  *     // 3. Construction résultat
//  *     CacheableSearchResult result = new CacheableSearchResult();
//  *     result.setTextResults(textItems);
//  *     result.setImageResults(imageItems);
//  *     result.calculateMetrics(textDuration, imageDuration);
//  *     result.setWasCached(false);
//  *     result.setTimestamp(System.currentTimeMillis());
//  *     
//  *     // 4. Mise en cache Redis
//  *     String cacheKey = generateCacheKey(query, userId);
//  *     redisTemplate.opsForValue().set(cacheKey, result, 1, TimeUnit.HOURS);
//  *     
//  *     return result;
//  * }
//  * ```
//  * 
//  * ============================================================================
//  * UTILISATION:
//  * ============================================================================
//  * 
//  * // Documents avec filtrage
//  * ragTools.searchDocuments("contrat", 1, 5, "pdf")
//  * 
//  * // Images
//  * ragTools.searchImages("graphique", 1, 3)
//  * 
//  * // Recherche complète
//  * ragTools.searchAll("analyse financière", "user123")
//  */