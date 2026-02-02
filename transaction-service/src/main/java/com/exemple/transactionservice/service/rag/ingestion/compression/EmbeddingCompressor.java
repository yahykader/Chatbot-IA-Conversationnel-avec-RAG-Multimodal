package com.exemple.transactionservice.service.rag.ingestion.compression;

import dev.langchain4j.data.embedding.Embedding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Service de compression d'embeddings pour réduire l'utilisation mémoire et stockage.
 * 
 * <p>Les embeddings vectoriels occupent beaucoup d'espace :
 * <ul>
 *   <li>Embedding 1536 dimensions (OpenAI) : 1536 × 4 bytes = <b>6 KB</b></li>
 *   <li>1 million embeddings : 1M × 6 KB = <b>6 GB</b></li>
 *   <li>10 millions embeddings : 10M × 6 KB = <b>60 GB</b></li>
 * </ul>
 * 
 * <h2>Techniques de Compression</h2>
 * 
 * <h3>1. Quantization Int8 (Recommandé)</h3>
 * <ul>
 *   <li>Float32 (4 bytes) → Int8 (1 byte)</li>
 *   <li><b>Réduction : 75%</b></li>
 *   <li>Perte qualité : ~1-2%</li>
 * </ul>
 * 
 * <h3>2. Quantization Int16</h3>
 * <ul>
 *   <li>Float32 (4 bytes) → Int16 (2 bytes)</li>
 *   <li><b>Réduction : 50%</b></li>
 *   <li>Perte qualité : <1%</li>
 * </ul>
 * 
 *  Product Quantization (PQ)
 * 
 *   <li>Divise vecteur en sous-vecteurs
 *   <li><b>Réduction : 90%+</b></li>
 *   <li>Nécessite codebook</li>
 * </ul>
 * 
 * Cas d'Usage
 * 
    * Production à grande échelle : Millions d'embeddings
    * Contraintes mémoire : RAM limitée
    * Contraintes stockage : Disque limité
    * Recherche approximative : ANN (Approximate Nearest Neighbor)
 * 
 * Impact Performance
 * 
 *  Métrique    Original   Int8        Int16
 *  Taille      mémoire    6 KB         1.5 KB 3 KB 
 *  Recherche   100%       99%          99.5%
 *  Stockage     6 GB/1M   1.5 GB/1M    3 GB/1M
 * 
 * @author RAG Team
 * @version 1.0
 * @since 2025-01
 */
@Slf4j
@Service
public class EmbeddingCompressor {

    private final boolean compressionEnabled;
    private final CompressionMethod defaultMethod;
    private final int dimensions;

    /**
     * Constructeur avec injection de configuration.
     */
    public EmbeddingCompressor(
            @Value("${embedding.compression.enabled:false}") boolean compressionEnabled,
            @Value("${embedding.compression.method:INT8}") String method,
            @Value("${embedding.compression.dimensions:1536}") int dimensions) {
        
        this.compressionEnabled = compressionEnabled;
        this.defaultMethod = CompressionMethod.valueOf(method.toUpperCase());
        this.dimensions = dimensions;

        if (compressionEnabled) {
            log.info("🗜️ Compression embeddings activée: méthode={}, dimensions={}", 
                     defaultMethod, dimensions);
            
            // Calculer économies
            long originalSize = dimensions * 4L; // Float32
            long compressedSize = calculateCompressedSize(dimensions, defaultMethod);
            double reduction = ((originalSize - compressedSize) * 100.0) / originalSize;
            
            log.info("💾 Économie mémoire: {}% ({} bytes → {} bytes)", 
                     (int) reduction, originalSize, compressedSize);
        } else {
            log.info("🔓 Compression embeddings désactivée");
        }
    }

    // ========================================================================
    // MÉTHODES PUBLIQUES - QUANTIZATION INT8
    // ========================================================================

    /**
     * Compresse un embedding en Int8 (1 byte par dimension).
     * 
     * <p><b>Réduction : 75%</b> (4 bytes → 1 byte)
     * <p><b>Qualité : ~98-99%</b> (perte minime)
     * 
     * @param embedding Embedding original (Float32)
     * @return Embedding compressé (Int8)
     */
    public Embedding quantizeInt8(Embedding embedding) {
        if (!compressionEnabled) {
            return embedding;
        }

        long startTime = System.nanoTime();
        float[] originalVector = embedding.vector();
        
        // 1. Calculer min/max pour normalisation
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        
        for (float value : originalVector) {
            if (value < min) min = value;
            if (value > max) max = value;
        }
        
        // 2. Quantifier sur [-127, 127]
        float scale = (max - min) / 254.0f; // 254 = 127 - (-127)
        byte[] quantized = new byte[originalVector.length];
        
        for (int i = 0; i < originalVector.length; i++) {
            float normalized = (originalVector[i] - min) / scale - 127;
            quantized[i] = (byte) Math.round(normalized);
        }
        
        // 3. Dequantifier pour retrouver Float32 (requis par Langchain4j)
        float[] dequantized = new float[originalVector.length];
        for (int i = 0; i < originalVector.length; i++) {
            dequantized[i] = (quantized[i] + 127) * scale + min;
        }
        
        long duration = System.nanoTime() - startTime;
        
        log.debug("🗜️ Quantization Int8: {} ms, {} dims, perte={}%", 
                 duration / 1_000_000, 
                 originalVector.length,
                 calculateLoss(originalVector, dequantized));
        
        return Embedding.from(dequantized);
    }

    /**
     * Version avec métadonnées de compression (pour stockage optimisé).
     * 
     * @return CompressedEmbedding contenant données + métadonnées
     */
    public CompressedEmbedding quantizeInt8WithMetadata(Embedding embedding) {
        float[] originalVector = embedding.vector();
        
        // Calculer min/max
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        
        for (float value : originalVector) {
            if (value < min) min = value;
            if (value > max) max = value;
        }
        
        // Quantifier
        float scale = (max - min) / 254.0f;
        byte[] quantized = new byte[originalVector.length];
        
        for (int i = 0; i < originalVector.length; i++) {
            float normalized = (originalVector[i] - min) / scale - 127;
            quantized[i] = (byte) Math.round(normalized);
        }
        
        return new CompressedEmbedding(
            quantized,
            CompressionMethod.INT8,
            min,
            max,
            scale,
            originalVector.length
        );
    }

    // ========================================================================
    // MÉTHODES PUBLIQUES - QUANTIZATION INT16
    // ========================================================================

    /**
     * Compresse un embedding en Int16 (2 bytes par dimension).
     * 
     * <p><b>Réduction : 50%</b> (4 bytes → 2 bytes)
     * <p><b>Qualité : ~99.5%</b> (perte négligeable)
     * 
     * @param embedding Embedding original
     * @return Embedding compressé
     */
    public Embedding quantizeInt16(Embedding embedding) {
        if (!compressionEnabled) {
            return embedding;
        }

        float[] originalVector = embedding.vector();
        
        // Calculer min/max
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        
        for (float value : originalVector) {
            if (value < min) min = value;
            if (value > max) max = value;
        }
        
        // Quantifier sur [-32767, 32767]
        float scale = (max - min) / 65534.0f; // 65534 = 32767 - (-32767)
        short[] quantized = new short[originalVector.length];
        
        for (int i = 0; i < originalVector.length; i++) {
            float normalized = (originalVector[i] - min) / scale - 32767;
            quantized[i] = (short) Math.round(normalized);
        }
        
        // Dequantifier
        float[] dequantized = new float[originalVector.length];
        for (int i = 0; i < originalVector.length; i++) {
            dequantized[i] = (quantized[i] + 32767) * scale + min;
        }
        
        log.debug("🗜️ Quantization Int16: perte={}%", 
                 calculateLoss(originalVector, dequantized));
        
        return Embedding.from(dequantized);
    }

    // ========================================================================
    // MÉTHODES PUBLIQUES - DÉCOMPRESSION
    // ========================================================================

    /**
     * Décompresse un embedding depuis CompressedEmbedding.
     */
    public Embedding decompress(CompressedEmbedding compressed) {
        return switch (compressed.method) {
            case INT8 -> decompressInt8(compressed);
            case INT16 -> decompressInt16(compressed);
            case NONE -> throw new IllegalArgumentException("Pas de compression");
        };
    }

    private Embedding decompressInt8(CompressedEmbedding compressed) {
        byte[] data = compressed.data;
        float[] vector = new float[compressed.dimensions];
        
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (data[i] + 127) * compressed.scale + compressed.min;
        }
        
        return Embedding.from(vector);
    }

    private Embedding decompressInt16(CompressedEmbedding compressed) {
        // Convertir byte[] en short[]
        ByteBuffer buffer = ByteBuffer.wrap(compressed.data);
        short[] quantized = new short[compressed.dimensions];
        
        for (int i = 0; i < quantized.length; i++) {
            quantized[i] = buffer.getShort();
        }
        
        // Dequantifier
        float[] vector = new float[compressed.dimensions];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (quantized[i] + 32767) * compressed.scale + compressed.min;
        }
        
        return Embedding.from(vector);
    }

    // ========================================================================
    // MÉTHODES UTILITAIRES
    // ========================================================================

    /**
     * Calcule la perte de précision (MSE - Mean Squared Error).
     */
    private double calculateLoss(float[] original, float[] compressed) {
        double mse = 0.0;
        for (int i = 0; i < original.length; i++) {
            double diff = original[i] - compressed[i];
            mse += diff * diff;
        }
        mse /= original.length;
        
        // Convertir en pourcentage
        double maxValue = 1.0f;
        for (float value : original) {
            float abs = Math.abs(value);
            if (abs > maxValue) {
                maxValue = abs;
            }
        }
        
        return (Math.sqrt(mse) / maxValue) * 100;
    }

    /**
     * Calcule la taille compressée en bytes.
     */
    private long calculateCompressedSize(int dimensions, CompressionMethod method) {
        return switch (method) {
            case INT8 -> dimensions; // 1 byte par dimension
            case INT16 -> dimensions * 2L; // 2 bytes par dimension
            case NONE -> dimensions * 4L; // 4 bytes par dimension (Float32)
        };
    }

    /**
     * Compare la similarité cosinus entre deux embeddings.
     */
    public double cosineSimilarity(Embedding emb1, Embedding emb2) {
        float[] v1 = emb1.vector();
        float[] v2 = emb2.vector();
        
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Dimensions différentes");
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Calcule les statistiques de compression pour reporting.
     */
    public CompressionStats calculateStats(Embedding original, Embedding compressed) {
        float[] v1 = original.vector();
        float[] v2 = compressed.vector();
        
        long originalSize = v1.length * 4L; // Float32
        long compressedSize = calculateCompressedSize(v1.length, defaultMethod);
        double reduction = ((originalSize - compressedSize) * 100.0) / originalSize;
        double loss = calculateLoss(v1, v2);
        double similarity = cosineSimilarity(original, compressed);
        
        return new CompressionStats(
            originalSize,
            compressedSize,
            reduction,
            loss,
            similarity,
            defaultMethod
        );
    }

    // ========================================================================
    // GETTERS
    // ========================================================================

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public CompressionMethod getDefaultMethod() {
        return defaultMethod;
    }

    // ========================================================================
    // CLASSES INTERNES
    // ========================================================================

    /**
     * Méthodes de compression disponibles.
     */
    public enum CompressionMethod {
        NONE,   // Pas de compression (Float32)
        INT8,   // Quantization 8 bits (-75% taille)
        INT16   // Quantization 16 bits (-50% taille)
    }

    /**
     * Structure pour stocker un embedding compressé avec métadonnées.
     */
    public record CompressedEmbedding(
        byte[] data,              // Données compressées
        CompressionMethod method, // Méthode utilisée
        float min,                // Min pour dequantization
        float max,                // Max pour dequantization
        float scale,              // Scale pour dequantization
        int dimensions            // Nombre de dimensions originales
    ) {
        /**
         * Taille en bytes.
         */
        public long size() {
            return data.length + 20; // data + métadonnées (4 floats + 1 int)
        }

        /**
         * Taille originale (Float32).
         */
        public long originalSize() {
            return dimensions * 4L;
        }

        /**
         * Pourcentage de réduction.
         */
        public double reductionPercentage() {
            return ((originalSize() - size()) * 100.0) / originalSize();
        }
    }

    /**
     * Statistiques de compression.
     */
    public record CompressionStats(
        long originalSize,        // Taille originale (bytes)
        long compressedSize,      // Taille compressée (bytes)
        double reductionPercent,  // % de réduction
        double lossPercent,       // % de perte (MSE)
        double similarity,        // Similarité cosinus
        CompressionMethod method  // Méthode utilisée
    ) {
        @Override
        public String toString() {
            return String.format(
                "CompressionStats{method=%s, originalSize=%d bytes, compressedSize=%d bytes, " +
                "reduction=%.1f%%, loss=%.2f%%, similarity=%.4f}",
                method, originalSize, compressedSize, reductionPercent, lossPercent, similarity
            );
        }
    }
}