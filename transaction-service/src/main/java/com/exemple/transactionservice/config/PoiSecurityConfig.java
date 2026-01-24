package com.exemple.transactionservice.config;

import jakarta.annotation.PostConstruct;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PoiSecurityConfig {

    @PostConstruct
    public void configurePoiZipSecurity() {
        // Augmenter le seuil pour XLSX riches (images, charts, styles)
        ZipSecureFile.setMaxFileCount(10_000);

        // Laissez minInflateRatio par défaut en prod, sauf besoin explicite
        // ZipSecureFile.setMinInflateRatio(0.01);
    }
}