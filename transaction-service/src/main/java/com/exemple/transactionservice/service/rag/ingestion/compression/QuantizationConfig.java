package com.exemple.transactionservice.service.rag.ingestion.compression;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration pour la compression/quantization des embeddings.
 * 
 * <p>Cette configuration permet de contrôler finement la compression des embeddings
 * pour optimiser le compromis entre qualité de recherche et utilisation ressources.
 * 
 * <h2>Configuration application.yml</h2>
 * <pre>
 * embedding:
 *   compression:
 *     enabled: true
 *     method: INT8           # INT8, INT16, ou NONE
 *     dimensions: 1536       # Dimensions des embeddings
 *     batch-compression: true
 *     quality-threshold: 0.98
 * </pre>
 * 
 * <h2>Profils Recommandés</h2>
 * 
 * <h3>Développement</h3>
 * <pre>
 * embedding.compression.enabled: false  # Désactivé en dev
 * </pre>
 * 
 * <h3>Production (Grande Échelle)</h3>
 * <pre>
 * embedding.compression.enabled: true
 * embedding.compression.method: INT8
 * embedding.compression.dimensions: 1536
 * </pre>
 * 
 * <h3>Production (Haute Qualité)</h3>
 * <pre>
 * embedding.compression.enabled: true
 * embedding.compression.method: INT16
 * embedding.compression.dimensions: 1536
 * </pre>
 * 
 * @author RAG Team
 * @version 1.0
 */
@Slf4j
@Configuration
public class QuantizationConfig {

    /**
     * Bean de configuration des propriétés de compression.
     */
    @Bean
    @ConfigurationProperties(prefix = "embedding.compression")
    public CompressionProperties compressionProperties() {
        return new CompressionProperties();
    }

    /**
     * Bean du compresseur avec configuration injectée.
     */
    @Bean
    public EmbeddingCompressor embeddingCompressor(CompressionProperties properties) {
        EmbeddingCompressor compressor = new EmbeddingCompressor(
            properties.isEnabled(),
            properties.getMethod(),
            properties.getDimensions()
        );

        if (properties.isEnabled()) {
            logCompressionInfo(properties);
        }

        return compressor;
    }

    /**
     * Affiche les informations de configuration au démarrage.
     */
    private void logCompressionInfo(CompressionProperties props) {
        log.info("╔════════════════════════════════════════════════════════╗");
        log.info("║  COMPRESSION EMBEDDINGS - CONFIGURATION               ║");
        log.info("╚════════════════════════════════════════════════════════╝");
        log.info("");
        log.info("  🗜️  Statut         : ACTIVÉ");
        log.info("  📊  Méthode        : {}", props.getMethod());
        log.info("  📏  Dimensions     : {}", props.getDimensions());
        log.info("  ⚡  Batch mode     : {}", props.isBatchCompression() ? "ON" : "OFF");
        log.info("  🎯  Seuil qualité  : {}", props.getQualityThreshold());
        log.info("");

        // Calculer économies estimées
        long originalSize = props.getDimensions() * 4L; // Float32
        long compressedSize = calculateCompressedSize(props);
        double reduction = ((originalSize - compressedSize) * 100.0) / originalSize;

        log.info("  💾  Taille originale     : {} bytes", originalSize);
        log.info("  💾  Taille compressée    : {} bytes", compressedSize);
        log.info("  ✅  Économie par embed   : {}%", (int) reduction);
        log.info("");

        // Projections
        long embedsPer1GB = (1024L * 1024L * 1024L) / compressedSize;
        log.info("  📈  Embeddings par GB    : ~{}", formatNumber(embedsPer1GB));
        log.info("  📈  Pour 1M embeddings   : ~{} GB", 
                 formatSize(1_000_000L * compressedSize));
        log.info("  📈  Pour 10M embeddings  : ~{} GB", 
                 formatSize(10_000_000L * compressedSize));
        log.info("");
        log.info("╚════════════════════════════════════════════════════════╝");
    }

    private long calculateCompressedSize(CompressionProperties props) {
        return switch (props.getMethod().toUpperCase()) {
            case "INT8" -> props.getDimensions(); // 1 byte
            case "INT16" -> props.getDimensions() * 2L; // 2 bytes
            default -> props.getDimensions() * 4L; // Float32
        };
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    private String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format("%.2f", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024L * 1024L) {
            return String.format("%.2f", bytes / (1024.0 * 1024.0));
        }
        return String.format("%.2f", bytes / 1024.0);
    }

    /**
     * Propriétés de configuration pour la compression.
     */
    @Data
    public static class CompressionProperties {
        
        /**
         * Active/désactive la compression.
         * Default: false (désactivé en dev)
         */
        private boolean enabled = false;

        /**
         * Méthode de compression.
         * Values: INT8, INT16, NONE
         * Default: INT8 (meilleur compromis)
         */
        private String method = "INT8";

        /**
         * Nombre de dimensions des embeddings.
         * OpenAI: 1536, Cohere: 1024, etc.
         * Default: 1536
         */
        private int dimensions = 1536;

        /**
         * Active la compression par batch (plus performant).
         * Default: true
         */
        private boolean batchCompression = true;

        /**
         * Seuil minimal de similarité cosinus après compression.
         * Si similarité < seuil, compression rejetée.
         * Default: 0.98 (98%)
         */
        private double qualityThreshold = 0.98;

        /**
         * Active le logging détaillé des compressions.
         * Default: false
         */
        private boolean verboseLogging = false;

        /**
         * Limite de mémoire pour le cache de compression (MB).
         * Default: 100 MB
         */
        private int cacheSizeMb = 100;
    }
}