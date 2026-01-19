package com.exemple.transactionservice.controller;

import com.exemple.transactionservice.service.WhisperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * ✅ Controller pour la transcription audio
 */
@Slf4j
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class VoiceController {
    
    private final WhisperService whisperService;
    
    /**
     * ✅ Endpoint de transcription audio
     * 
     * POST /api/voice/transcribe
     * 
     * @param audioFile Fichier audio (webm, mp3, wav, etc.)
     * @param language Code langue optionnel (fr, en, es, etc.)
     * @return JSON avec la transcription
     */
    @PostMapping("/transcribe")
    public ResponseEntity<Map<String, Object>> transcribe(
        @RequestParam("audio") MultipartFile audioFile,
        @RequestParam(value = "language", required = false, defaultValue = "fr") String language
    ) {
        try {
            log.info("📥 [Voice] Réception fichier audio");
            log.info("📁 [Voice] Nom: {}", audioFile.getOriginalFilename());
            log.info("📊 [Voice] Taille: {} bytes", audioFile.getSize());
            log.info("🎵 [Voice] Type: {}", audioFile.getContentType());
            log.info("🌍 [Voice] Langue: {}", language);
            
            // Vérifications
            if (audioFile.isEmpty()) {
                log.warn("⚠️ [Voice] Fichier audio vide");
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Le fichier audio est vide"));
            }
            
            if (audioFile.getSize() > 25 * 1024 * 1024) {  // 25 MB max
                log.warn("⚠️ [Voice] Fichier trop volumineux: {} bytes", audioFile.getSize());
                return ResponseEntity.badRequest()
                    .body(createErrorResponse("Le fichier audio dépasse 25 MB"));
            }
            
            // Transcription
            byte[] audioBytes = audioFile.getBytes();
            String transcript = whisperService.transcribeAudio(
                audioBytes, 
                audioFile.getOriginalFilename(),
                language
            );
            
            log.info("✅ [Voice] Transcription réussie: {} caractères", transcript.length());
            
            // Réponse
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("transcript", transcript);
            response.put("language", language);
            response.put("audioSize", audioFile.getSize());
            response.put("filename", audioFile.getOriginalFilename());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ [Voice] Erreur transcription", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Erreur lors de la transcription: " + e.getMessage()));
        }
    }
    
    /**
     * ✅ Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("whisperAvailable", whisperService.isAvailable());
        return ResponseEntity.ok(response);
    }
    
    /**
     * ✅ Crée une réponse d'erreur
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}