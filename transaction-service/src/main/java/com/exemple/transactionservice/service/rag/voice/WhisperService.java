package com.exemple.transactionservice.service.rag.voice;

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
            
            // ==================== VALIDATION ====================
            if (audioBytes == null || audioBytes.length == 0) {
                throw new RuntimeException("Données audio vides");
            }
            
            if (audioBytes.length < 1000) {
                log.warn("⚠️ [Whisper] Audio très court: {} bytes — possible silence", audioBytes.length);
            }
            
            // ==================== FICHIER TEMP ====================
            tempFile = createTempAudioFile(audioBytes, originalFilename);
            log.info("📁 [Whisper] Fichier temp: {} ({} bytes)", 
                    tempFile.getName(), tempFile.length());
            
            // ==================== REQUEST WHISPER ====================
            CreateTranscriptionRequest request = CreateTranscriptionRequest.builder()
                .model("whisper-1")
                .language(language)
               // .responseFormat("text") // ← plus simple, retourne du texte brut
                .build();
            
            log.info("🌍 [Whisper] Langue: {} | Fichier: {}", language, tempFile.getName());
            
            // ==================== APPEL API ====================
            long startTime = System.currentTimeMillis();
            
            String transcript = openAiService
                .createTranscription(request, tempFile.getPath())
                .getText();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("⏱️ [Whisper] Durée API: {}ms", duration);
            
            // ==================== VALIDATION RÉSULTAT ====================
            log.info("📝 [Whisper] Transcript brut: '{}'", transcript);
            
            if (transcript == null || transcript.isBlank()) {
                log.warn("⚠️ [Whisper] Transcription vide — silence ou audio non reconnu");
                throw new RuntimeException("Aucune transcription reçue — vérifiez que l'audio contient de la parole");
            }
            
            String result = transcript.trim();
            log.info("✅ [Whisper] Transcription réussie en {}ms — {} caractères: '{}'",
                    duration,
                    result.length(),
                    result.length() > 100 ? result.substring(0, 100) + "..." : result);
            
            return result;
            
        } catch (RuntimeException e) {
            // Re-throw directement sans wrapper
            throw e;
            
        } catch (Exception e) {
            log.error("❌ [Whisper] Erreur inattendue: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur lors de la transcription audio: " + e.getMessage(), e);
            
        } finally {
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                log.debug("🗑️ [Whisper] Temp supprimé: {}", deleted);
            }
        }
    }
    
    /**
     * ✅ Crée un fichier temporaire pour l'audio
     */
    private File createTempAudioFile(byte[] audioBytes, String originalFilename) throws IOException {
        
        // ✅ Toujours forcer .webm si l'extension est absente ou inconnue
        String extension = getFileExtension(originalFilename);
        if (extension.equals(".webm") || extension.isEmpty()) {
            extension = ".webm"; // Whisper accepte webm nativement
        }
        
        String tempFileName = "whisper_" + UUID.randomUUID() + extension;
        File tempFile = new File(System.getProperty("java.io.tmpdir"), tempFileName);
        FileUtils.writeByteArrayToFile(tempFile, audioBytes);
        
        log.info("📁 [Whisper] Temp file: {} ({} bytes)", tempFile.getName(), audioBytes.length);
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