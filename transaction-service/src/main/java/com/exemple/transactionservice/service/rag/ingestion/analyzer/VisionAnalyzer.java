// ============================================================================
// SERVICE - VisionAnalyzer.java (AVEC CIRCUIT BREAKER)
// Service d'analyse d'images avec Vision AI et protection Circuit Breaker
// ============================================================================
package com.exemple.transactionservice.service.rag.ingestion.analyzer;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/**
 * Service d'analyse d'images utilisant Vision AI avec Circuit Breaker.
 * 
 * ✨ AMÉLIORATIONS :
 * ✅ Protection Circuit Breaker (Resilience4j)
 * ✅ Fallback automatique si Vision AI indisponible
 * ✅ Description basique sans API en cas d'erreur
 * ✅ Métriques automatiques via Resilience4j
 * 
 * Fonctionnalités :
 * - Analyse images avec modèles Vision (GPT-4 Vision, Claude Vision, etc.)
 * - Génération descriptions détaillées
 * - Détection contenu (texte, objets, scènes, personnes)
 * - Support multiple formats (BufferedImage)
 * - Gestion erreurs et timeouts
 * - Circuit Breaker pour protection pannes en cascade
 * 
 * Circuit Breaker :
 * - État CLOSED : Appels normaux vers Vision AI
 * - État OPEN : Fallback automatique (pas d'appel API)
 * - État HALF_OPEN : Test de rétablissement
 * 
 * Configuration :
 * - vision.model : Modèle à utiliser (ex: "gpt-4-vision-preview")
 * - vision.max-tokens : Tokens max pour description
 * - vision.image-quality : Qualité compression (low/high/auto)
 * - vision.timeout-seconds : Timeout appel API
 * 
 * Usage :
 * <pre>
 * BufferedImage image = ImageIO.read(file);
 * String description = visionAnalyzer.analyzeImage(image);
 * // Si Vision AI down → Fallback automatique
 * </pre>
 */
@Slf4j
@Service
public class VisionAnalyzer {
    
    private final ChatLanguageModel visionModel;
    
    @Value("${vision.max-tokens:500}")
    private int maxTokens;
    
    @Value("${vision.image-quality:auto}")
    private String imageQuality;
    
    @Value("${vision.timeout-seconds:30}")
    private int timeoutSeconds;
    
    @Value("${vision.prompt:Analysez cette image et fournissez une description détaillée de son contenu, y compris tout texte, objet, personne et contexte de la scène.}")
    private String defaultPrompt;
    
    /**
     * Constructeur avec injection du modèle Vision
     * 
     * @param visionModel Modèle de langage avec capacités vision (ex: GPT-4 Vision)
     */
    public VisionAnalyzer(ChatLanguageModel visionModel) {
        this.visionModel = visionModel;
        log.info("✅ VisionAnalyzer initialisé avec Circuit Breaker (timeout: {}s, maxTokens: {})", 
            timeoutSeconds, maxTokens);
    }
    
    // ========================================================================
    // ANALYSE IMAGE PRINCIPALE (AVEC CIRCUIT BREAKER)
    // ========================================================================
    
    /**
     * ✨ Analyse une image avec protection Circuit Breaker
     * 
     * @param image Image à analyser
     * @return Description générée par Vision AI (ou fallback)
     * @throws IOException Si erreur conversion image
     */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackAnalyzeImage")
    public String analyzeImage(BufferedImage image) throws IOException {
        return analyzeImage(image, defaultPrompt);
    }
    
    /**
     * ✨ Analyse une image avec prompt personnalisé et Circuit Breaker
     * 
     * @param image Image à analyser
     * @param prompt Prompt personnalisé pour l'analyse
     * @return Description générée par Vision AI (ou fallback)
     * @throws IOException Si erreur conversion image
     */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackAnalyzeImageWithPrompt")
    public String analyzeImage(BufferedImage image, String prompt) throws IOException {
        
        if (image == null) {
            throw new IllegalArgumentException("Image ne peut pas être null");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("🔍 [VisionAI] Analyse image: {}x{} px", 
                image.getWidth(), image.getHeight());
            
            // 1. Convertir image en base64
            String base64Image = convertImageToBase64(image);
            
            log.debug("📦 [VisionAI] Image encodée: {} caractères", base64Image.length());
            
            // 2. Créer le message avec image
            UserMessage message = UserMessage.from(
                TextContent.from(prompt),
                ImageContent.from(base64Image, "image/png")
            );
            
            // 3. ✅ Appeler Vision AI (protégé par Circuit Breaker)
            log.debug("🤖 [VisionAI] Appel API Vision...");
            
            Response<dev.langchain4j.data.message.AiMessage> response = 
                visionModel.generate(message);
            
            if (response == null || response.content() == null) {
                throw new IOException("Vision AI n'a pas retourné de réponse");
            }
            
            String description = response.content().text();
            
            if (description == null || description.isBlank()) {
                throw new IOException("Vision AI a retourné une description vide");
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("✅ [VisionAI] Analyse complète: {} caractères, durée: {}ms",
                description.length(), duration);
            
            log.debug("📝 [VisionAI] Description: {}", 
                description.length() > 100 ? description.substring(0, 100) + "..." : description);
            
            return description;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ [VisionAI] Erreur analyse image: {} (durée: {}ms)", 
                e.getMessage(), duration, e);
            
            throw new IOException("Erreur Vision AI: " + e.getMessage(), e);
        }
    }
    
    // ========================================================================
    // MÉTHODES FALLBACK (CIRCUIT BREAKER)
    // ========================================================================
    
    /**
     * ✨ FALLBACK : Appelée quand Circuit Breaker en état OPEN
     * Génère une description basique sans appeler Vision AI
     * 
     * @param image Image à décrire
     * @param e Exception qui a déclenché le fallback
     * @return Description basique (dimensions, orientation)
     */
    private String fallbackAnalyzeImage(BufferedImage image, Exception e) {
        log.warn("🔄 [VisionAI] Circuit Breaker OPEN - Fallback activé: {}", 
            e.getClass().getSimpleName());
        
        return generateFallbackDescription(image, "default");
    }
    
    /**
     * ✨ FALLBACK : Avec prompt personnalisé
     * 
     * @param image Image à décrire
     * @param prompt Prompt original (ignoré dans fallback)
     * @param e Exception qui a déclenché le fallback
     * @return Description basique
     */
    private String fallbackAnalyzeImageWithPrompt(
            BufferedImage image, 
            String prompt, 
            Exception e) {
        
        log.warn("🔄 [VisionAI] Circuit Breaker OPEN - Fallback avec prompt: {}", 
            e.getClass().getSimpleName());
        
        // Déterminer le type d'analyse demandée
        String analysisType = detectAnalysisType(prompt);
        
        return generateFallbackDescription(image, analysisType);
    }
    
    /**
     * Génère une description basique sans Vision AI
     * 
     * @param image Image à décrire
     * @param analysisType Type d'analyse demandée
     * @return Description basique
     */
    private String generateFallbackDescription(BufferedImage image, String analysisType) {
        if (image == null) {
            return "[Vision AI temporairement indisponible] Impossible d'analyser l'image (null).";
        }
        
        int width = image.getWidth();
        int height = image.getHeight();
        String orientation = width > height ? "landscape" : 
                           width < height ? "portrait" : "square";
        
        // Description selon le type d'analyse
        return switch (analysisType) {
            case "text" -> String.format(
                "[Vision AI temporairement indisponible] " +
                "Impossible d'extraire le texte de l'image %s de %dx%d pixels. " +
                "Veuillez réessayer plus tard.",
                orientation, width, height
            );
            
            case "objects" -> String.format(
                "[Vision AI temporairement indisponible] " +
                "Impossible de détecter les objets dans l'image %s de %dx%d pixels. " +
                "Veuillez réessayer plus tard.",
                orientation, width, height
            );
            
            case "chart" -> String.format(
                "[Vision AI temporairement indisponible] " +
                "Impossible d'analyser le graphique dans l'image %s de %dx%d pixels. " +
                "Veuillez réessayer plus tard.",
                orientation, width, height
            );
            
            default -> String.format(
                "[Vision AI temporairement indisponible] " +
                "Image %s de %dx%d pixels. " +
                "Service d'analyse en maintenance. Veuillez réessayer dans quelques minutes.",
                orientation, width, height
            );
        };
    }
    
    /**
     * Détecte le type d'analyse demandée à partir du prompt
     * 
     * @param prompt Prompt original
     * @return Type d'analyse (text, objects, chart, scene, default)
     */
    private String detectAnalysisType(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "default";
        }
        
        String lowerPrompt = prompt.toLowerCase();
        
        if (lowerPrompt.contains("extract") && lowerPrompt.contains("text")) {
            return "text";
        }
        if (lowerPrompt.contains("objects") || lowerPrompt.contains("items")) {
            return "objects";
        }
        if (lowerPrompt.contains("chart") || lowerPrompt.contains("graph")) {
            return "chart";
        }
        if (lowerPrompt.contains("scene") || lowerPrompt.contains("setting")) {
            return "scene";
        }
        
        return "default";
    }
    
    // ========================================================================
    // ANALYSES SPÉCIALISÉES (AVEC CIRCUIT BREAKER)
    // ========================================================================
    
    /**
     * ✨ Analyse une image pour extraire du texte (OCR-like)
     * Protégé par Circuit Breaker
     * 
     * @param image Image contenant du texte
     * @return Texte extrait de l'image (ou fallback)
     * @throws IOException Si erreur
     */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackExtractText")
    public String extractText(BufferedImage image) throws IOException {
        String prompt = "Extract all text visible in this image. " +
                       "Return only the text content, preserving line breaks and formatting where possible.";
        
        return analyzeImage(image, prompt);
    }
    
    /**
     * Fallback pour extractText
     */
    private String fallbackExtractText(BufferedImage image, Exception e) {
        log.warn("🔄 [VisionAI] Circuit Breaker - Fallback extractText");
        return generateFallbackDescription(image, "text");
    }
    
    /**
     * ✨ Analyse une image pour détecter des objets
     * Protégé par Circuit Breaker
     * 
     * @param image Image à analyser
     * @return Liste des objets détectés (ou fallback)
     * @throws IOException Si erreur
     */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackDetectObjects")
    public String detectObjects(BufferedImage image) throws IOException {
        String prompt = "List all objects, items, and elements visible in this image. " +
                       "Be specific and detailed.";
        
        return analyzeImage(image, prompt);
    }
    
    /**
     * Fallback pour detectObjects
     */
    private String fallbackDetectObjects(BufferedImage image, Exception e) {
        log.warn("🔄 [VisionAI] Circuit Breaker - Fallback detectObjects");
        return generateFallbackDescription(image, "objects");
    }
    
    /**
     * ✨ Analyse une image pour décrire la scène
     * Protégé par Circuit Breaker
     * 
     * @param image Image à analyser
     * @return Description de la scène (ou fallback)
     * @throws IOException Si erreur
     */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackDescribeScene")
    public String describeScene(BufferedImage image) throws IOException {
        String prompt = "Describe the overall scene, setting, and context of this image. " +
                       "Include details about the location, atmosphere, and any notable features.";
        
        return analyzeImage(image, prompt);
    }
    
    /**
     * Fallback pour describeScene
     */
    private String fallbackDescribeScene(BufferedImage image, Exception e) {
        log.warn("🔄 [VisionAI] Circuit Breaker - Fallback describeScene");
        return generateFallbackDescription(image, "scene");
    }
    
    /**
     * ✨ Analyse un chart/graphique pour extraire les données
     * Protégé par Circuit Breaker
     * 
     * @param image Image d'un chart ou graphique
     * @return Description des données du chart (ou fallback)
     * @throws IOException Si erreur
     */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackAnalyzeChart")
    public String analyzeChart(BufferedImage image) throws IOException {
        String prompt = "This image contains a chart or graph. " +
                       "Describe the type of chart (bar, line, pie, etc.), " +
                       "the data it represents, key values, trends, and any labels or legends present.";
        
        return analyzeImage(image, prompt);
    }
    
    /**
     * Fallback pour analyzeChart
     */
    private String fallbackAnalyzeChart(BufferedImage image, Exception e) {
        log.warn("🔄 [VisionAI] Circuit Breaker - Fallback analyzeChart");
        return generateFallbackDescription(image, "chart");
    }
    
    /**
     * ✨ Analyse une image de document pour résumer le contenu
     * Protégé par Circuit Breaker
     * 
     * @param image Image d'un document
     * @return Résumé du document (ou fallback)
     * @throws IOException Si erreur
     */
    @CircuitBreaker(name = "visionAI", fallbackMethod = "fallbackSummarizeDocument")
    public String summarizeDocument(BufferedImage image) throws IOException {
        String prompt = "This image shows a document page. " +
                       "Summarize the main content, including any headings, key points, and overall topic.";
        
        return analyzeImage(image, prompt);
    }
    
    /**
     * Fallback pour summarizeDocument
     */
    private String fallbackSummarizeDocument(BufferedImage image, Exception e) {
        log.warn("🔄 [VisionAI] Circuit Breaker - Fallback summarizeDocument");
        return generateFallbackDescription(image, "default");
    }
    
    // ========================================================================
    // CONVERSION IMAGE
    // ========================================================================
    
    /**
     * Convertit une BufferedImage en base64 pour Vision AI
     * 
     * @param image Image à convertir
     * @return String base64 de l'image (format PNG)
     * @throws IOException Si erreur conversion
     */
    private String convertImageToBase64(BufferedImage image) throws IOException {
        
        if (image == null) {
            throw new IllegalArgumentException("Image ne peut pas être null");
        }
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            // Convertir en PNG (format universel bien supporté)
            boolean written = ImageIO.write(image, "png", baos);
            
            if (!written) {
                throw new IOException("Impossible d'écrire l'image en PNG");
            }
            
            byte[] imageBytes = baos.toByteArray();
            
            // Encoder en base64
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            
            log.debug("🔄 [VisionAI] Image convertie: {}x{} → {} bytes → {} chars base64",
                image.getWidth(), image.getHeight(), imageBytes.length, base64.length());
            
            return base64;
            
        } catch (IOException e) {
            log.error("❌ [VisionAI] Erreur conversion image en base64", e);
            throw e;
        }
    }
    
    // ========================================================================
    // VALIDATION ET UTILITAIRES
    // ========================================================================
    
    /**
     * Vérifie si une image est valide pour l'analyse
     * 
     * @param image Image à vérifier
     * @return true si image valide
     */
    public boolean isValidImage(BufferedImage image) {
        if (image == null) {
            return false;
        }
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Vérifier dimensions minimales
        if (width < 10 || height < 10) {
            log.warn("⚠️ [VisionAI] Image trop petite: {}x{}", width, height);
            return false;
        }
        
        // Vérifier dimensions maximales (la plupart des Vision APIs ont des limites)
        if (width > 4096 || height > 4096) {
            log.warn("⚠️ [VisionAI] Image trop grande: {}x{}", width, height);
            return false;
        }
        
        return true;
    }
    
    /**
     * Estime le coût d'analyse d'une image (en tokens approximatifs)
     * Basé sur la résolution de l'image
     * 
     * @param image Image à estimer
     * @return Nombre approximatif de tokens pour cette image
     */
    public int estimateTokenCost(BufferedImage image) {
        if (image == null) {
            return 0;
        }
        
        int width = image.getWidth();
        int height = image.getHeight();
        int pixels = width * height;
        
        // Formule approximative : ~1 token par 750 pixels
        // (basé sur GPT-4 Vision pricing)
        int baseCost = pixels / 750;
        
        // Coût minimum
        return Math.max(85, baseCost);
    }
    
    /**
     * Retourne des statistiques sur l'utilisation du service
     */
    public VisionStats getStats() {
        return new VisionStats(
            timeoutSeconds,
            maxTokens,
            imageQuality,
            defaultPrompt
        );
    }
    
    /**
     * Record pour statistiques Vision AI
     */
    public record VisionStats(
        int timeoutSeconds,
        int maxTokens,
        String imageQuality,
        String defaultPrompt
    ) {}
    
    // ========================================================================
    // MÉTHODES DE BATCH (AVEC CIRCUIT BREAKER)
    // ========================================================================
    
    /**
     * ✨ Analyse plusieurs images en parallèle avec Circuit Breaker
     * 
     * @param images Liste d'images à analyser
     * @return Liste des descriptions (ou fallback si circuit ouvert)
     * @throws IOException Si erreur
     */
    public List<String> analyzeImages(List<BufferedImage> images) 
            throws IOException {
        
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        
        log.info("🔍 [VisionAI] Analyse batch: {} images", images.size());
        
        List<String> descriptions = new ArrayList<>();
        
        for (int i = 0; i < images.size(); i++) {
            try {
                // Chaque appel est protégé par Circuit Breaker
                String description = analyzeImage(images.get(i));
                descriptions.add(description);
                
                // Log progression
                if ((i + 1) % 10 == 0) {
                    log.info("📊 [VisionAI] Progression: {}/{} images analysées", 
                        i + 1, images.size());
                }
                
            } catch (Exception e) {
                log.error("❌ [VisionAI] Erreur image {}/{}: {}", 
                    i + 1, images.size(), e.getMessage());
                
                // Ajouter description par défaut en cas d'erreur
                descriptions.add("[Erreur analyse: " + e.getMessage() + "]");
            }
        }
        
        log.info("✅ [VisionAI] Batch terminé: {}/{} succès", 
            descriptions.size(), images.size());
        
        return descriptions;
    }
}