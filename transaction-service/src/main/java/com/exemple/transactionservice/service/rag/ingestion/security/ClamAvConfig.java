

/**
 * Configuration Spring pour le service antivirus ClamAV.
 * Activer/désactiver le scan antivirus via propriétés
 * Configurer les paramètres de connexion ClamAV
 * Surveiller la disponibilité du service (health check)
*/
package com.exemple.transactionservice.service.rag.ingestion.security;

import com.exemple.transactionservice.service.rag.ingestion.security.AntivirusScanner;
import com.exemple.transactionservice.config.ClamAvProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@EnableConfigurationProperties(ClamAvProperties.class)
public class ClamAvConfig {

    private final ClamAvProperties props;


    /**
     * Bean principal du scanner antivirus (configuré via application.yml).
     */
    @Bean
    public AntivirusScanner antivirusScanner() {

        AntivirusScanner scanner = new AntivirusScanner(props);

        // Vérification de disponibilité au démarrage
        if (props.isEnabled()) {
            if (scanner.isAvailable()) {
                log.info("✅ ClamAV connecté avec succès - Version: {}", scanner.getVersion());
            } else {
                log.warn("⚠️ ClamAV activé mais indisponible ({}:{})",
                        props.getHost(), props.getPort());
            }
        } else {
            log.info("🦠 ClamAV désactivé via configuration (antivirus.enabled=false)");
        }

        return scanner;
    }

    /**
     * Health check périodique (uniquement si enabled + health-check.enabled).
     *
     * ✅ FIX: on injecte le bean AntivirusScanner (pas de recréation via antivirusScanner()).
     */
    @Scheduled(fixedDelayString = "${antivirus.health-check.interval:60000}")
    @ConditionalOnProperty(prefix = "antivirus", name = {"enabled", "health-check.enabled"}, havingValue = "true")
    public void healthCheck() {
        try {

            AntivirusScanner scanner = antivirusScanner(); // ✅ récupère le bean Spring

            if (!scanner.isAvailable()) {
                log.warn("⚠️ [HEALTH CHECK] ClamAV est devenu indisponible !");
                log.warn("⚠️ Les fichiers ne seront PAS scannés jusqu'au rétablissement");
                 // TODO: Envoyer alerte monitoring (Prometheus, etc.)
            } else {
                log.debug("✓ [HEALTH CHECK] ClamAV opérationnel");
            }
        } catch (Exception e) {
            log.error("❌ [HEALTH CHECK] Erreur lors de la vérification ClamAV", e);
        }
    }
}
