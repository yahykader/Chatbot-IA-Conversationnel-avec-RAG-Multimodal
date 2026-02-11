package com.exemple.transactionservice.service.rag.retrieval.query;

import com.exemple.transactionservice.config.RetrievalConfig;
import com.exemple.transactionservice.service.rag.retrieval.model.RoutingDecision;
import com.exemple.transactionservice.service.rag.retrieval.model.RoutingDecision.RetrieverConfig;
import com.exemple.transactionservice.service.rag.retrieval.model.RoutingDecision.Strategy;
import com.exemple.transactionservice.service.rag.retrieval.model.RoutingDecision.Priority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Service de routing de requêtes
 * 
 * Analyse la query et décide quelle stratégie de retrieval utiliser
 * 
 * Stratégies:
 * - TEXT_ONLY: Questions factuelles simples
 * - IMAGE_ONLY: Demandes de graphiques/charts
 * - HYBRID: Requêtes complexes (default)
 * - STRUCTURED: Données chiffrées, tableaux
 */
@Slf4j
@Service
public class QueryRouterService {
    
    private final RetrievalConfig config;
    
    // Patterns de détection
    private static final List<Pattern> IMAGE_PATTERNS = List.of(
        Pattern.compile("\\b(graphique|chart|diagramme|image|photo|figure)s?\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(montre|affiche|visualise|présente).*(graphique|chart|diagramme)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(courbe|histogramme|camembert|bar chart)s?\\b", Pattern.CASE_INSENSITIVE)
    );
    
    private static final List<Pattern> STRUCTURED_PATTERNS = List.of(
        Pattern.compile("\\b(tableau|table|données|data|chiffres?)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(nombre|montant|prix|coût|total|somme)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b\\d+\\s*(k€|M€|€|USD|%)\\b", Pattern.CASE_INSENSITIVE)
    );
    
    private static final List<Pattern> TEXT_PATTERNS = List.of(
        Pattern.compile("\\b(résumé|synthèse|explique|décris|qu'est-ce|pourquoi|comment)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(définition|concept|principe|explication)s?\\b", Pattern.CASE_INSENSITIVE)
    );
    
    private static final List<Pattern> ANALYTICAL_PATTERNS = List.of(
        Pattern.compile("\\b(analyse|compare|évalue|contraste|tendance)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(évolution|progression|croissance|baisse|variation)\\b", Pattern.CASE_INSENSITIVE)
    );
    
    public QueryRouterService(RetrievalConfig config) {
        this.config = config;
    }
    
    /**
     * Détermine la stratégie de retrieval optimale
     */
    public RoutingDecision route(String query) {
        if (!config.getQueryRouter().isEnabled()) {
            return createDefaultDecision(query);
        }
        
        long startTime = System.currentTimeMillis();
        
        // Analyse de la query
        QueryAnalysis analysis = analyzeQuery(query);
        
        // Sélection stratégie
        Strategy strategy = selectStrategy(analysis);
        
        // Configuration retrievers
        Map<String, RetrieverConfig> retrieversConfig = configureRetrievers(strategy, analysis);
        
        // Estimation latence
        long estimatedLatency = estimateTotalLatency(retrieversConfig);
        
        long duration = System.currentTimeMillis() - startTime;
        
        RoutingDecision decision = RoutingDecision.builder()
            .strategy(strategy)
            .confidence(analysis.confidence)
            .retrievers(retrieversConfig)
            .estimatedTotalDurationMs(estimatedLatency)
            .parallelExecution(true)
            .build();
        
        log.info("🔀 Routing: {} → {} (confidence={}, latency=~{}ms)", 
            truncate(query, 50), strategy, 
            String.format("%.2f", analysis.confidence), estimatedLatency);
        
        return decision;
    }
    
    /**
     * Analyse la query et détecte les patterns
     */
    private QueryAnalysis analyzeQuery(String query) {
        QueryAnalysis analysis = new QueryAnalysis();
        
        String normalized = query.toLowerCase();
        
        // Détection patterns
        analysis.hasImageKeywords = matchesAny(normalized, IMAGE_PATTERNS);
        analysis.hasStructuredKeywords = matchesAny(normalized, STRUCTURED_PATTERNS);
        analysis.hasTextKeywords = matchesAny(normalized, TEXT_PATTERNS);
        analysis.hasAnalyticalKeywords = matchesAny(normalized, ANALYTICAL_PATTERNS);
        
        // Détection intent
        if (query.endsWith("?")) {
            analysis.isQuestion = true;
        }
        
        if (normalized.contains("compar") || normalized.contains("versus") || normalized.contains("vs")) {
            analysis.isComparative = true;
        }
        
        // Score de confiance
        int matchCount = 0;
        if (analysis.hasImageKeywords) matchCount++;
        if (analysis.hasStructuredKeywords) matchCount++;
        if (analysis.hasTextKeywords) matchCount++;
        if (analysis.hasAnalyticalKeywords) matchCount++;
        
        analysis.confidence = matchCount > 0 ? 0.7 + (matchCount * 0.1) : 0.6;
        
        return analysis;
    }
    
    /**
     * Sélectionne la stratégie optimale
     */
    private Strategy selectStrategy(QueryAnalysis analysis) {
        // IMAGE_ONLY: Si demande explicite de graphique
        if (analysis.hasImageKeywords && !analysis.hasTextKeywords && !analysis.hasStructuredKeywords) {
            return Strategy.IMAGE_ONLY;
        }
        
        // STRUCTURED: Si demande de données chiffrées
        if (analysis.hasStructuredKeywords && !analysis.hasImageKeywords) {
            return Strategy.STRUCTURED;
        }
        
        // TEXT_ONLY: Si demande explicite de texte/explication
        if (analysis.hasTextKeywords && !analysis.hasImageKeywords && !analysis.hasStructuredKeywords) {
            return Strategy.TEXT_ONLY;
        }
        
        // HYBRID: Tout le reste (default)
        return Strategy.HYBRID;
    }
    
    /**
     * Configure les retrievers selon la stratégie
     */
    private Map<String, RetrieverConfig> configureRetrievers(Strategy strategy, QueryAnalysis analysis) {
        Map<String, RetrieverConfig> configs = new HashMap<>();
        
        switch (strategy) {
            case TEXT_ONLY -> {
                configs.put("text", RetrieverConfig.builder()
                    .enabled(true)
                    .priority(Priority.HIGH)
                    .topK(config.getRetrievers().getText().getTopK())
                    .estimatedLatencyMs(50)
                    .build());
                
                configs.put("image", RetrieverConfig.builder()
                    .enabled(false)
                    .priority(Priority.LOW)
                    .topK(0)
                    .estimatedLatencyMs(0)
                    .build());
                
                configs.put("bm25", RetrieverConfig.builder()
                    .enabled(false)
                    .priority(Priority.LOW)
                    .topK(0)
                    .estimatedLatencyMs(0)
                    .build());
            }
            
            case IMAGE_ONLY -> {
                configs.put("text", RetrieverConfig.builder()
                    .enabled(true)
                    .priority(Priority.LOW)
                    .topK(5)
                    .estimatedLatencyMs(30)
                    .build());
                
                configs.put("image", RetrieverConfig.builder()
                    .enabled(true)
                    .priority(Priority.HIGH)
                    .topK(config.getRetrievers().getImage().getTopK())
                    .estimatedLatencyMs(80)
                    .build());
                
                configs.put("bm25", RetrieverConfig.builder()
                    .enabled(false)
                    .priority(Priority.LOW)
                    .topK(0)
                    .estimatedLatencyMs(0)
                    .build());
            }
            
            case STRUCTURED -> {
                configs.put("text", RetrieverConfig.builder()
                    .enabled(true)
                    .priority(Priority.MEDIUM)
                    .topK(10)
                    .estimatedLatencyMs(50)
                    .build());
                
                configs.put("image", RetrieverConfig.builder()
                    .enabled(false)
                    .priority(Priority.LOW)
                    .topK(0)
                    .estimatedLatencyMs(0)
                    .build());
                
                configs.put("bm25", RetrieverConfig.builder()
                    .enabled(true)
                    .priority(Priority.HIGH)
                    .topK(config.getRetrievers().getBm25().getTopK())
                    .estimatedLatencyMs(40)
                    .build());
            }
            
            case HYBRID -> {
                configs.put("text", RetrieverConfig.builder()
                    .enabled(config.getRetrievers().getText().isEnabled())
                    .priority(Priority.HIGH)
                    .topK(config.getRetrievers().getText().getTopK())
                    .estimatedLatencyMs(50)
                    .build());
                
                configs.put("image", RetrieverConfig.builder()
                    .enabled(config.getRetrievers().getImage().isEnabled())
                    .priority(analysis.hasImageKeywords ? Priority.HIGH : Priority.MEDIUM)
                    .topK(config.getRetrievers().getImage().getTopK())
                    .estimatedLatencyMs(80)
                    .build());
                
                configs.put("bm25", RetrieverConfig.builder()
                    .enabled(config.getRetrievers().getBm25().isEnabled())
                    .priority(Priority.HIGH)
                    .topK(config.getRetrievers().getBm25().getTopK())
                    .estimatedLatencyMs(40)
                    .build());
            }
        }
        
        return configs;
    }
    
    /**
     * Estime la latence totale (parallel = max latency)
     */
    private long estimateTotalLatency(Map<String, RetrieverConfig> configs) {
        return configs.values().stream()
            .filter(RetrieverConfig::isEnabled)
            .mapToLong(RetrieverConfig::getEstimatedLatencyMs)
            .max()
            .orElse(100L);
    }
    
    /**
     * Décision par défaut (fallback)
     */
    private RoutingDecision createDefaultDecision(String query) {
        Strategy defaultStrategy = Strategy.valueOf(
            config.getQueryRouter().getDefaultStrategy().toUpperCase()
        );
        
        QueryAnalysis analysis = new QueryAnalysis();
        analysis.confidence = 0.5;
        
        return RoutingDecision.builder()
            .strategy(defaultStrategy)
            .confidence(0.5)
            .retrievers(configureRetrievers(defaultStrategy, analysis))
            .estimatedTotalDurationMs(100)
            .parallelExecution(true)
            .build();
    }
    
    /**
     * Vérifie si le texte matche au moins un pattern
     */
    private boolean matchesAny(String text, List<Pattern> patterns) {
        return patterns.stream().anyMatch(p -> p.matcher(text).find());
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * Résultat d'analyse de query
     */
    private static class QueryAnalysis {
        boolean hasImageKeywords = false;
        boolean hasStructuredKeywords = false;
        boolean hasTextKeywords = false;
        boolean hasAnalyticalKeywords = false;
        boolean isQuestion = false;
        boolean isComparative = false;
        double confidence = 0.0;
    }
}