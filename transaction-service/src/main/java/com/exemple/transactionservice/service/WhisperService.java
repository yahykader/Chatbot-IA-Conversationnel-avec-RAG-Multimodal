package com.exemple.transactionservice.service;

// ✅ CORRECTION : Imports pour la version 0.18.2 du SDK
import com.theokanning.openai.audio.CreateTranscriptionRequest;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * ✅ Service de transcription audio avec OpenAI Whisper
 * Compatible avec openai-gpt3-java version 0.18.2
 */
@Slf4j
@Service
public class WhisperService {
    
    @Value("${openai.api.key}")
    private String apiKey;
    
    private OpenAiService openAiService;
    
    @PostConstruct
    public void init() {
        log.info("🎤 [Whisper] Initialisation du service Whisper");
        this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(30));
        log.info("✅ [Whisper] Service initialisé");
    }
    
    /**
     * ✅ Transcrit un fichier audio avec Whisper
     * 
     * @param audioBytes Données audio brutes
     * @param originalFilename Nom du fichier original
     * @param language Code langue (fr, en, es, etc.)
     * @return Texte transcrit
     */
    public String transcribeAudio(
        byte[] audioBytes, 
        String originalFilename,
        String language
    ) {
        File tempFile = null;
        
        try {
            log.info("🎤 [Whisper] Début transcription - Taille: {} bytes", audioBytes.length);
            
            // 1. Créer un fichier temporaire
            tempFile = createTempAudioFile(audioBytes, originalFilename);
            log.info("📁 [Whisper] Fichier temp créé: {}", tempFile.getAbsolutePath());
            
            // 2. Préparer la requête Whisper (version 0.18.2)
            CreateTranscriptionRequest request = CreateTranscriptionRequest.builder()
                .model("whisper-1")
                .language(language)
                .build();
            
            log.info("🌍 [Whisper] Langue spécifiée: {}", language);
            
            // 3. Appeler l'API OpenAI Whisper
            long startTime = System.currentTimeMillis();
            
            // ✅ CORRECTION : Méthode correcte pour version 0.18.2
            String transcript = openAiService.createTranscription(request, tempFile.getPath())
                .getText();
            
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("✅ [Whisper] Transcription réussie en {}ms", duration);
            log.info("📝 [Whisper] Résultat: {}", 
                     transcript.length() > 100 ? transcript.substring(0, 100) + "..." : transcript);
            
            return transcript;
            
        } catch (Exception e) {
            log.error("❌ [Whisper] Erreur transcription: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur lors de la transcription audio: " + e.getMessage(), e);
            
        } finally {
            // 4. Nettoyer le fichier temporaire
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                log.debug("🗑️ [Whisper] Fichier temp supprimé: {}", deleted);
            }
        }
    }
    
    /**
     * ✅ Crée un fichier temporaire pour l'audio
     */
    private File createTempAudioFile(byte[] audioBytes, String originalFilename) throws IOException {
        // Extraire l'extension du fichier
        String extension = getFileExtension(originalFilename);
        
        // Créer un fichier temporaire
        String tempFileName = "whisper_" + UUID.randomUUID().toString() + extension;
        File tempFile = new File(System.getProperty("java.io.tmpdir"), tempFileName);
        
        // Écrire les données audio
        FileUtils.writeByteArrayToFile(tempFile, audioBytes);
        
        return tempFile;
    }
    
    /**
     * ✅ Extrait l'extension du fichier
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return ".webm";
        }
        
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot);
        }
        
        return ".webm";
    }
    
    /**
     * ✅ Vérifie si le service est disponible
     */
    public boolean isAvailable() {
        return this.openAiService != null && this.apiKey != null && !this.apiKey.isEmpty();
    }
}