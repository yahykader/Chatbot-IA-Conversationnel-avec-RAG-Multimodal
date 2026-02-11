// ============================================================================
// CONFIGURATION - PgVectorConfig.java
// Configuration PgVector + OpenAI + RAGMetrics unifié
// ============================================================================
package com.exemple.transactionservice.config;

import com.exemple.transactionservice.service.rag.metrics.RAGMetrics;
import com.exemple.transactionservice.service.rag.ingestion.cache.EmbeddingCache;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;

import java.util.List;

/**
 * Configuration PgVector + OpenAI
 * 
 * ✅ ADAPTÉ AVEC RAGMetrics unifié
 * 
 * Beans créés:
 * - EmbeddingModel (metered)
 * - ChatLanguageModel
 * - StreamingChatLanguageModel
 * - textEmbeddingStore (PgVector)
 * - imageEmbeddingStore (PgVector)
 * - Health Indicator
 * 
 * @author RAG Team
 * @version 3.0 - Adapté avec RAGMetrics unifié
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name="openai.enabled", havingValue="true")
public class PgVectorConfig {

    // ========================================================================
    // PROPRIÉTÉS - PgVector
    // ========================================================================
    
    @Value("${pgvector.host:localhost}")
    private String host;

    @Value("${pgvector.port:5432}")
    private int port;

    @Value("${pgvector.database:vectordb}")
    private String database;

    @Value("${pgvector.user:admin}")
    private String user;

    @Value("${pgvector.password:1234}")
    private String password;
    
    @Value("${pgvector.dimension:1536}")
    private int embeddingDimension;
    
    @Value("${pgvector.connection.pool.size:10}")
    private int connectionPoolSize;
    
    @Value("${pgvector.connection.timeout:30}")
    private int connectionTimeoutSeconds;

    // ========================================================================
    // PROPRIÉTÉS - OpenAI
    // ========================================================================
    
    @Value("${openai.api.key:}")
    private String openAiKey;

    @Value("${openai.enabled:true}")
    private boolean openAiEnabled;
    
    @Value("${openai.embedding.model:text-embedding-3-small}")
    private String embeddingModelName;
    
    @Value("${openai.chat.model:gpt-4o}")
    private String chatModelName;
    
    @Value("${openai.temperature:0.7}")
    private double temperature;
    
    @Value("${openai.max.tokens:2000}")
    private int maxTokens;
    
    @Value("${openai.timeout.seconds:60}")
    private int timeoutSeconds;
    
    @Value("${openai.max.retries:3}")
    private int maxRetries;
    
    @Value("${openai.log.requests:false}")
    private boolean logRequests;
    
    @Value("${openai.log.responses:false}")
    private boolean logResponses;

    // ========================================================================
    // VALIDATION POST-CONSTRUCTION
    // ========================================================================
    
    @PostConstruct
    public void validateConfiguration() {
        log.info("🔧 Validation de la configuration PgVector et OpenAI...");
        
        validateOpenAiConfiguration();
        validatePgVectorConfiguration();
        testPgVectorConnection();
        
        log.info("✅ Configuration validée avec succès");
    }
    
    private void validateOpenAiConfiguration() {
        if (openAiKey == null || openAiKey.isBlank()) {
            throw new IllegalStateException(
                "❌ Configuration OpenAI invalide: " +
                "La clé API 'openai.api.key' est requise dans application.properties"
            );
        }

        if (!openAiEnabled) {
            log.warn("OpenAI désactivé (openai.enabled=false)");
            return;
        }
        
        if (!openAiKey.startsWith("sk-")) {
            log.warn("⚠️ La clé API OpenAI ne commence pas par 'sk-'");
        }
        
        String maskedKey = maskApiKey(openAiKey);
        log.info("✅ OpenAI API Key: {}", maskedKey);
        log.info("   - Embedding Model: {}", embeddingModelName);
        log.info("   - Chat Model: {}", chatModelName);
        log.info("   - Dimension: {}", embeddingDimension);
    }
    
    private void validatePgVectorConfiguration() {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                "❌ Configuration PgVector invalide: 'pgvector.password' requis"
            );
        }
        
        if (port < 1 || port > 65535) {
            throw new IllegalStateException(
                "❌ Port PgVector invalide: " + port
            );
        }
        
        if (embeddingDimension <= 0) {
            throw new IllegalStateException(
                "❌ Dimension invalide: " + embeddingDimension
            );
        }
        
        log.info("✅ Configuration PgVector valide");
        log.info("   - Host: {}:{}", host, port);
        log.info("   - Database: {}", database);
        log.info("   - User: {}", user);
    }
    
    private void testPgVectorConnection() {
        String jdbcUrl = String.format(
            "jdbc:postgresql://%s:%d/%s", 
            host, port, database
        );
        
        try {
            log.info("🔌 Test connexion PgVector: {}", jdbcUrl);
            
            try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
                if (conn.isValid(5)) {
                    log.info("✅ Connexion PgVector établie");
                } else {
                    log.warn("⚠️ Connexion établie mais validation échouée");
                }
            }
            
        } catch (SQLException e) {
            log.error("❌ Impossible de se connecter à PgVector", e);
            throw new IllegalStateException(
                "Échec connexion PgVector. Vérifiez que pgvector est installé: " +
                "CREATE EXTENSION IF NOT EXISTS vector;", 
                e
            );
        }
    }
    
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        return apiKey.substring(0, 7) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    // ========================================================================
    // BEAN 1 : EMBEDDING MODEL (Metered)
    // ========================================================================
    
    /**
     * EmbeddingModel avec métriques RAGMetrics
     */
    @Bean
    public EmbeddingModel embeddingModel(RAGMetrics ragMetrics, EmbeddingCache cache) {
        log.info("🧠 Création EmbeddingModel (metered)");
        log.info("   - Model: {}", embeddingModelName);
        log.info("   - Dimension: {}", embeddingDimension);
        log.info("   - Timeout: {}s", timeoutSeconds);
        log.info("   - Max Retries: {}", maxRetries);
        
        EmbeddingModel baseModel = OpenAiEmbeddingModel.builder()
            .apiKey(openAiKey)
            .modelName(embeddingModelName)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .maxRetries(maxRetries)
            .logRequests(logRequests)
            .logResponses(logResponses)
            .build();
        
            // 2. Ajouter métriques
        EmbeddingModel metered = new MeteredEmbeddingModel(baseModel, ragMetrics);
        // ✅ Wrapper avec RAGMetrics
        return metered;
    }
    
    /**
     * Wrapper EmbeddingModel avec tracking RAGMetrics
     * 
     * ✅ COMPLET - Toutes les méthodes implémentées
     */
    private static class MeteredEmbeddingModel implements EmbeddingModel {
        
        private final EmbeddingModel delegate;
        private final RAGMetrics ragMetrics;
        
        public MeteredEmbeddingModel(EmbeddingModel delegate, RAGMetrics ragMetrics) {
            this.delegate = delegate;
            this.ragMetrics = ragMetrics;
        }
        
        @Override
        public Response<Embedding> embed(String text) {
            long start = System.currentTimeMillis();
            
            try {
                Response<Embedding> response = delegate.embed(text);
                long duration = System.currentTimeMillis() - start;
                
                // ✅ MÉTRIQUE: Embedding API call
                ragMetrics.recordApiCall("embed_text", duration);
                
                return response;
                
            } catch (Exception e) {
                // ✅ MÉTRIQUE: Embedding API error
                ragMetrics.recordApiError("embed_text");
                throw e;
            }
        }
        
        @Override
        public Response<Embedding> embed(TextSegment textSegment) {
            return embed(textSegment.text());
        }
        
        // ✅ FIX: Implémenter embedAll()
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            long start = System.currentTimeMillis();
            
            try {
                Response<List<Embedding>> response = delegate.embedAll(textSegments);
                long duration = System.currentTimeMillis() - start;
                
                // ✅ MÉTRIQUE: Batch embedding
                ragMetrics.recordApiCall("embed_text_batch", duration);
                
                return response;
                
            } catch (Exception e) {
                ragMetrics.recordApiError("embed_text_batch");
                throw e;
            }
        }
        
        @Override
        public int dimension() {
            return delegate.dimension();
        }
    }
    // ========================================================================
    // BEAN 2 : TEXT EMBEDDING STORE (PgVector)
    // ========================================================================
    
    @Bean(name = "textEmbeddingStore")
    public EmbeddingStore<TextSegment> textEmbeddingStore() {
        log.info("📚 Création textEmbeddingStore (PgVector)");
        
        return createPgVectorStore(
            "text_embeddings",
            "Store pour documents texte (PDF, DOCX, TXT, etc.)"
        );
    }

    // ========================================================================
    // BEAN 3 : IMAGE EMBEDDING STORE (PgVector)
    // ========================================================================
    
    @Bean(name = "imageEmbeddingStore")
    public EmbeddingStore<TextSegment> imageEmbeddingStore() {
        log.info("🖼️ Création imageEmbeddingStore (PgVector)");
        
        return createPgVectorStore(
            "image_embeddings",
            "Store pour descriptions images Vision AI"
        );
    }
    
    private EmbeddingStore<TextSegment> createPgVectorStore(
            String tableName, 
            String description) {
        
        log.info("   - Table: {}", tableName);
        log.info("   - Description: {}", description);
        log.info("   - Dimension: {}", embeddingDimension);
        
        try {
            return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .table(tableName)
                .dimension(embeddingDimension)
                .createTable(true)
                .dropTableFirst(false)
                .build();
            
        } catch (Exception e) {
            log.error("❌ Échec création store '{}'", tableName, e);
            throw new IllegalStateException(
                "Impossible de créer store PgVector '" + tableName + "'. " +
                "Vérifiez que pgvector est installé: " +
                "CREATE EXTENSION IF NOT EXISTS vector;",
                e
            );
        }
    }

    // ========================================================================
    // BEAN 4 : CHAT MODEL (OpenAI GPT)
    // ========================================================================
    
    @Bean
    public ChatLanguageModel chatModel() {
        log.info("🤖 Création ChatLanguageModel");
        log.info("   - Model: {}", chatModelName);
        log.info("   - Temperature: {}", temperature);
        log.info("   - Max Tokens: {}", maxTokens);
        
        return OpenAiChatModel.builder()
            .apiKey(openAiKey)
            .modelName(chatModelName)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .maxRetries(maxRetries)
            .logRequests(logRequests)
            .logResponses(logResponses)
            .build();
    }

    // ========================================================================
    // BEAN 5 : STREAMING CHAT MODEL (OpenAI GPT)
    // ========================================================================
    
    @Bean
    public StreamingChatLanguageModel streamingChatModel() {
        log.info("🌊 Création StreamingChatLanguageModel");
        log.info("   - Model: {}", chatModelName);
        log.info("   - Temperature: {}", temperature);
        
        return OpenAiStreamingChatModel.builder()
            .apiKey(openAiKey)
            .modelName(chatModelName)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .logRequests(logRequests)
            .logResponses(logResponses)
            .build();
    }

    // ========================================================================
    // BEAN 6 : HEALTH INDICATOR (Actuator)
    // ========================================================================
    
    @Bean
    public HealthIndicator pgVectorHealthIndicator() {
        return () -> {
            try {
                String jdbcUrl = String.format(
                    "jdbc:postgresql://%s:%d/%s", 
                    host, port, database
                );
                
                try (Connection conn = DriverManager.getConnection(
                        jdbcUrl, user, password)) {
                    
                    if (conn.isValid(5)) {
                        return Health.up()
                            .withDetail("pgvector.host", host + ":" + port)
                            .withDetail("pgvector.database", database)
                            .withDetail("pgvector.status", "connected")
                            .withDetail("openai.configured", openAiKey != null)
                            .withDetail("embedding.dimension", embeddingDimension)
                            .build();
                    } else {
                        return Health.down()
                            .withDetail("error", "Connection validation failed")
                            .build();
                    }
                }
                
            } catch (Exception e) {
                return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("pgvector.host", host + ":" + port)
                    .build();
            }
        };
    }
}