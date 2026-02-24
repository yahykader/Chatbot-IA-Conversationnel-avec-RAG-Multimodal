package com.exemple.transactionservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
public class StorageInitializer {
    
    @Value("${document.images.storage-path}")
    private String imagesStoragePath;
    
    @Value("${app.pdf.generated-pdf-dir}")
    private String pdfStoragePath;
    
    /**
     * ✅ Créé automatiquement les dossiers au démarrage de l'app
     */
    @PostConstruct
    public void initializeStorage() {
        log.info("🚀 Initialisation du système de stockage...");
        
        // Créer dossier images
        createDirectoryIfNotExists(imagesStoragePath, "Images");
        
        // Créer dossier PDF
        createDirectoryIfNotExists(pdfStoragePath, "PDF");
        
        log.info("✅ Système de stockage initialisé avec succès");
    }
    
    private void createDirectoryIfNotExists(String pathString, String type) {
        try {
            Path path = Paths.get(pathString);
            
            if (!Files.exists(path)) {
                log.info("📁 Création du dossier {} : {}", type, path.toAbsolutePath());
                Files.createDirectories(path);
                log.info("✅ Dossier {} créé : {}", type, path.toAbsolutePath());
            } else {
                log.info("✅ Dossier {} existe déjà : {}", type, path.toAbsolutePath());
            }
            
            // Vérifier les permissions d'écriture
            if (!Files.isWritable(path)) {
                log.error("❌ Pas de permission d'écriture sur le dossier {} : {}", 
                    type, path.toAbsolutePath());
                throw new IllegalStateException(
                    "Pas de permission d'écriture sur : " + path.toAbsolutePath()
                );
            }
            
            log.debug("✅ Permissions OK pour dossier {} : {}", type, path.toAbsolutePath());
            
        } catch (IOException e) {
            log.error("❌ Erreur lors de la création du dossier {} : {}", 
                type, pathString, e);
            throw new IllegalStateException(
                "Impossible de créer le dossier " + type + " : " + pathString, e
            );
        }
    }
}