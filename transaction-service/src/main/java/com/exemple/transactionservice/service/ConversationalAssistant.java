// ============================================================================
// SERVICE - ConversationalAssistant.java (SANS processToken / streaming tokens bruts)
// ============================================================================
package com.exemple.transactionservice.service;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ConversationalAssistant {

    private final StreamingChatLanguageModel streamingChatModel;
    private final MultimodalRAGService ragService;
    private final RAGTools ragTools;

    private final Map<String, ConversationContext> conversationCache = new ConcurrentHashMap<>();

    public ConversationalAssistant(
            StreamingChatLanguageModel streamingChatModel,
            MultimodalRAGService ragService,
            RAGTools ragTools) {
        this.streamingChatModel = streamingChatModel;
        this.ragService = ragService;
        this.ragTools = ragTools;

        log.info("✅ ConversationalAssistant initialisé avec support multimodal");
    }

    public Flux<String> chatStream(String userId, String userMessage) {
        Instant start = Instant.now();
        String sessionId = userId + "_" + System.currentTimeMillis();

        log.info("💬 [{}] Chat streaming - User: {}, Message: '{}'",
                sessionId, userId, truncate(userMessage, 100));

        try {
            String enhancedPrompt = buildEnhancedMultimodalPrompt(userId, userMessage);

            if (log.isDebugEnabled()) {
                log.debug("📤 [{}] Prompt ({} chars):\n{}",
                        sessionId, enhancedPrompt.length(), truncate(enhancedPrompt, 500));
            }

            return Flux.create(sink -> {
                StringBuilder fullResponse = new StringBuilder();

                streamingChatModel.chat(enhancedPrompt, new StreamingChatResponseHandler() {

                    @Override
                    public void onPartialResponse(String token) {
                        if (token == null || token.isEmpty()) {
                            return;
                        }

                        // IMPORTANT: on stream les tokens BRUTS, sans aucune manipulation
                        fullResponse.append(token);
                        sink.next(token);

                        if (log.isTraceEnabled() && fullResponse.length() < 100) {
                            log.trace("📥 [{}] Token brut: [{}]", sessionId, token);
                        }
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        long durationMs = System.currentTimeMillis() - start.toEpochMilli();

                        log.info("✅ [{}] Streaming terminé - {} chars en {}ms",
                                sessionId, fullResponse.length(), durationMs);

                        // Conserver exactement ce qui a été streamé
                        updateConversationContext(userId, userMessage, fullResponse.toString());

                        if (log.isDebugEnabled()) {
                            log.debug("📝 [{}] Réponse:\n{}",
                                    sessionId, truncate(fullResponse.toString(), 200));
                        }

                        sink.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("❌ [{}] Erreur streaming", sessionId, error);
                        sink.error(new RuntimeException(
                                "Erreur lors de la génération de la réponse: " + error.getMessage(),
                                error
                        ));
                    }
                });
            });

        } catch (Exception e) {
            log.error("❌ [{}] Erreur lors de la préparation du chat", sessionId, e);
            return Flux.error(new RuntimeException(
                    "Erreur lors de la préparation de la réponse: " + e.getMessage(),
                    e
            ));
        }
    }

    /**
     * Construction du prompt multimodal
     */
    private String buildEnhancedMultimodalPrompt(String userId, String userMessage) {
        log.debug("🔨 Construction prompt multimodal pour: {}", truncate(userMessage, 50));

        ConversationContext context = conversationCache.get(userId);
        MultimodalRAGService.MultimodalSearchResult searchResult =
                ragService.search(userMessage, 5);

        int totalDocs = searchResult.getTextResults().size();
        int totalImages = searchResult.getImageResults().size();

        log.info("📚 RAG: {} documents, {} images", totalDocs, totalImages);

        StringBuilder prompt = new StringBuilder();

        prompt.append("Tu es un assistant IA avancé avec accès aux documents uploadés.\n\n");

        prompt.append("📋 TES CAPACITÉS:\n");
        prompt.append("- Accès aux documents texte (PDF, Word, Excel, PowerPoint, TXT, etc.)\n");
        prompt.append("- Accès aux images (avec descriptions IA)\n");
        prompt.append("- Accès aux images extraites de PDF et documents Word\n");
        prompt.append("- Recherche sémantique avancée\n\n");

        prompt.append("🎯 RÈGLES IMPÉRATIVES:\n");
        prompt.append("1. Réponds UNIQUEMENT avec les informations des documents fournis\n");
        prompt.append("2. Si l'information n'est pas dans les documents, dis-le clairement\n");
        prompt.append("3. Cite TOUJOURS tes sources: (Source: nom_fichier.ext)\n");
        prompt.append("4. Pour les PDFs, ajoute le numéro de page: (Source: fichier.pdf, page 3)\n");
        prompt.append("5. Structure ta réponse avec des paragraphes et sauts de ligne\n");
        prompt.append("6. Utilise le markdown:\n");
        prompt.append("   - **Texte en gras** pour les titres\n");
        prompt.append("   - Sauts de ligne entre les sections\n");
        prompt.append("   - Listes à puces si pertinent\n\n");

        if (context != null && !context.isEmpty()) {
            prompt.append("💬 CONTEXTE CONVERSATION:\n");
            prompt.append(context.getSummary());
            prompt.append("\n\n");
        }

        if (!searchResult.getTextResults().isEmpty()) {
            prompt.append("═══════════════════════════════════════════════════════════\n");
            prompt.append("📄 DOCUMENTS TEXTE DISPONIBLES\n");
            prompt.append("═══════════════════════════════════════════════════════════\n\n");

            int docNum = 1;
            for (var segment : searchResult.getTextResults()) {
                String source = segment.metadata().getString("source");
                String type = segment.metadata().getString("type");
                Integer page = segment.metadata().getInteger("page");
                String text = segment.text();

                // Formatage basique (SANS NETTOYAGE)
                String formattedText = formatText(text);

                prompt.append(String.format("📄 DOCUMENT #%d\n", docNum));
                prompt.append(String.format("Fichier: %s\n", source != null ? source : "Inconnu"));

                if (type != null) {
                    prompt.append(String.format("Type: %s\n", formatDocumentType(type)));
                }
                if (page != null) {
                    prompt.append(String.format("Page: %d\n", page));
                }

                prompt.append("───────────────────────────────────────────────────────────\n");
                prompt.append("CONTENU:\n");
                prompt.append(formattedText);
                prompt.append("\n═══════════════════════════════════════════════════════════\n\n");

                docNum++;
            }
        }

        if (!searchResult.getImageResults().isEmpty()) {
            prompt.append("═══════════════════════════════════════════════════════════\n");
            prompt.append("🖼️ IMAGES DISPONIBLES\n");
            prompt.append("═══════════════════════════════════════════════════════════\n\n");

            int imgNum = 1;
            for (var segment : searchResult.getImageResults()) {
                String imageName = segment.metadata().getString("imageName");
                String filename = segment.metadata().getString("filename");
                Integer page = segment.metadata().getInteger("page");
                String description = segment.text();

                prompt.append(String.format("🖼️ IMAGE #%d\n", imgNum));

                if (imageName != null) {
                    prompt.append(String.format("Nom: %s\n", imageName));
                }
                if (filename != null) {
                    prompt.append(String.format("Fichier source: %s\n", filename));
                }
                if (page != null) {
                    prompt.append(String.format("Page: %d\n", page));
                }

                prompt.append("───────────────────────────────────────────────────────────\n");
                prompt.append("DESCRIPTION:\n");
                // Formatage basique (SANS NETTOYAGE)
                prompt.append(formatText(description));
                prompt.append("\n═══════════════════════════════════════════════════════════\n\n");

                imgNum++;
            }
        }

        if (totalDocs == 0 && totalImages == 0) {
            prompt.append("⚠️ AUCUN DOCUMENT PERTINENT TROUVÉ\n\n");
            prompt.append("Aucun document ne correspond à la recherche.\n");
            prompt.append("Informe l'utilisateur qu'il doit uploader des fichiers.\n\n");
        }

        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("❓ QUESTION DE L'UTILISATEUR\n");
        prompt.append("═══════════════════════════════════════════════════════════\n\n");
        prompt.append(userMessage);
        prompt.append("\n\n");

        prompt.append("═══════════════════════════════════════════════════════════\n");
        prompt.append("✍️ TA RÉPONSE (en français, bien formatée)\n");
        prompt.append("═══════════════════════════════════════════════════════════\n\n");

        prompt.append("Réponds maintenant en utilisant UNIQUEMENT les informations ");
        prompt.append("des documents ci-dessus. Structure bien ta réponse et cite tes sources.\n\n");

        return prompt.toString();
    }

    /**
     * Formatage basique du texte (SANS NETTOYAGE DES ESPACES PARASITES)
     */
    private String formatText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String result = text;

        // Corrections basiques de mise en forme
        result = result.replaceAll("\\.([A-ZÀ-Ú])", ". $1");
        result = result.replaceAll(",([A-Za-zÀ-ú])", ", $1");
        result = result.replaceAll(":([A-Za-zÀ-ú])", ": $1");
        result = result.replaceAll(";([A-Za-zÀ-ú])", "; $1");
        result = result.replaceAll("\\?([A-Za-zÀ-ú])", "? $1");
        result = result.replaceAll("!([A-Za-zÀ-ú])", "! $1");
        result = result.replaceAll("\\)([A-Za-zÀ-ú])", ") $1");
        result = result.replaceAll("([A-Za-zÀ-ú])\\(", "$1 (");
        result = result.replaceAll("([a-zà-ú])([A-ZÀ-Ú])", "$1 $2");
        result = result.replaceAll("(\\d)([A-Za-zÀ-ú])", "$1 $2");

        // NOTE: ce bloc compresse les espaces. Si vous voulez préserver davantage le markdown (\n),
        // remplacez "\\s+" par "[ \\t\\x0B\\f\\r]+".
        result = result.replaceAll("\\s+", " ");
        result = result.replaceAll("\\s+([.,;:!?])", "$1");

        result = result.trim();

        // Limiter la longueur
        if (result.length() > 2000) {
            result = result.substring(0, 1997) + "...";
        }

        return result;
    }

    /**
     * Formater le type de document
     */
    private String formatDocumentType(String type) {
        if (type == null) return "Inconnu";

        return switch (type.toLowerCase()) {
            case "pdf", "pdf_text_only" -> "PDF";
            case "docx", "office_docx" -> "Microsoft Word";
            case "xlsx", "office_xlsx" -> "Microsoft Excel";
            case "pptx", "office_pptx" -> "Microsoft PowerPoint";
            case "text", "txt" -> "Fichier texte";
            case "md" -> "Markdown";
            case "image" -> "Image";
            default -> type.contains("pdf_page") ? "PDF (extrait de page)" : type;
        };
    }

    /**
     * Mettre à jour le contexte de conversation
     */
    private void updateConversationContext(String userId, String question, String response) {
        ConversationContext context = conversationCache.computeIfAbsent(
                userId,
                k -> new ConversationContext()
        );

        context.addExchange(question, response);
        context.trim(3);

        log.debug("💾 Contexte mis à jour pour {}: {} échanges", userId, context.getExchangeCount());
    }

    /**
     * Tronquer le texte pour les logs
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "null";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Classe interne pour gérer le contexte de conversation
     */
    private static class ConversationContext {
        private final java.util.Deque<Exchange> exchanges = new java.util.LinkedList<>();

        public void addExchange(String question, String response) {
            exchanges.addLast(new Exchange(question, response, Instant.now()));
        }

        public void trim(int maxExchanges) {
            while (exchanges.size() > maxExchanges) {
                exchanges.removeFirst();
            }
        }

        public String getSummary() {
            if (exchanges.isEmpty()) return "";

            StringBuilder summary = new StringBuilder();
            int num = 1;

            for (Exchange exchange : exchanges) {
                summary.append(String.format("Échange %d:\n", num++));
                summary.append(String.format("Q: %s\n", truncateText(exchange.question, 100)));
                summary.append(String.format("R: %s\n\n", truncateText(exchange.response, 150)));
            }

            return summary.toString();
        }

        public int getExchangeCount() {
            return exchanges.size();
        }

        public boolean isEmpty() {
            return exchanges.isEmpty();
        }

        private static String truncateText(String text, int maxLength) {
            if (text == null || text.length() <= maxLength) return text;
            return text.substring(0, maxLength - 3) + "...";
        }

        private record Exchange(String question, String response, Instant timestamp) {}
    }
}
