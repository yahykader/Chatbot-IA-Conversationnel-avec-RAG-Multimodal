package com.exemple.transactionservice.service.rag.ingestion.security;

import com.exemple.transactionservice.exception.AntivirusScanException;
import com.exemple.transactionservice.config.ClamAvProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Service de scan antivirus utilisant ClamAV via le protocole INSTREAM.
 * Ce service communique avec le daemon ClamAV (clamd) pour scanner les fichiers
 * avant leur ingestion dans le système RAG. Il utilise le protocole INSTREAM pour
 * envoyer le contenu des fichiers directement via socket TCP.
 * 
 * ClamAV installé et clamd en cours d'exécution
 *  Prérequis
 *   Port clamd accessible (par défaut 3310)
 *   Base de données de signatures à jour
 *  Installation ClamAV
 * # Ubuntu/Debian
    * sudo apt-get install clamav clamav-daemon
    * sudo systemctl start clamav-daemon
    * sudo freshclam  # Mise à jour signatures
 */
@Slf4j
public class AntivirusScanner {

    private final boolean enabled;
    private final String clamAvHost;
    private final int clamAvPort;
    private final int timeout;
    private final int chunkSize;
    private final long maxFileSize;

    public AntivirusScanner(ClamAvProperties props) {
        this.enabled = props.isEnabled();
        this.clamAvHost = props.getHost();
        this.clamAvPort = props.getPort();
        this.timeout = props.getTimeout();
        this.chunkSize = props.getChunkSize();
        this.maxFileSize = props.getMaxFileSize();

        log.info("📡 AntivirusScanner initialisé: enabled={}, host={}, port={}, timeoutMs={}, chunkSize={}, maxFileSize={}",
                enabled, clamAvHost, clamAvPort, timeout, chunkSize, maxFileSize);
    }

    /**
     * Scanne un fichier pour détecter des virus ou malwares.
     * 
     * <p>Si le scan est désactivé, retourne toujours un résultat "clean".
     * Si le fichier dépasse la taille maximale, lève une exception.
     *
     * @param file Le fichier à scanner
     * @return Résultat du scan avec statut et détails
     * @throws AntivirusScanException Si le scan échoue ou si un virus est détecté
     * @throws IllegalArgumentException Si le fichier est null ou n'existe pas
     */
    public ScanResult scanFile(File file) throws AntivirusScanException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("Fichier invalide ou inexistant");
        }

        if (!enabled) {
            log.debug("🔓 Scan antivirus désactivé, fichier accepté: {}", file.getName());
            return ScanResult.clean(file.getName());
        }

        long fileSize = file.length();
        if (fileSize > maxFileSize) {
            throw new AntivirusScanException(
                String.format("Fichier trop volumineux pour le scan: %d bytes (max: %d bytes)",
                              fileSize, maxFileSize)
            );
        }

        log.info("🔍 Scan antivirus: {} ({} bytes)", file.getName(), fileSize);

        try {
            return scanWithClamAV(file);
        } catch (IOException e) {
            throw new AntivirusScanException("Erreur lors du scan antivirus: " + e.getMessage(), e);
        }
    }

    /**
     * Scanne un fichier temporaire depuis un Path.
     *
     * @param path Le chemin du fichier à scanner
     * @return Résultat du scan
     * @throws AntivirusScanException Si le scan échoue
     */
    public ScanResult scanFile(Path path) throws AntivirusScanException {
        return scanFile(path.toFile());
    }

    /**
     * Scanne des données en mémoire (byte array).
     * 
     * <p>Utile pour scanner du contenu sans écrire sur disque.
     *
     * @param data Les données à scanner
     * @param filename Nom du fichier (pour logging)
     * @return Résultat du scan
     * @throws AntivirusScanException Si le scan échoue
     */
    public ScanResult scanBytes(byte[] data, String filename) throws AntivirusScanException {

        if (data == null) {
            throw new IllegalArgumentException("Données null");
        }

        if (!enabled) {
            return ScanResult.clean(filename);
        }

        if (data.length > maxFileSize) {
            throw new AntivirusScanException(
                String.format("Données trop volumineuses: %d bytes (max: %d bytes)",
                              data.length, maxFileSize)
            );
        }

        log.info("🔍 Scan antivirus (bytes): {} ({} bytes)", filename, data.length);

        try {
            return scanBytesWithClamAV(data, filename);
        } catch (IOException e) {
            throw new AntivirusScanException("Erreur lors du scan antivirus: " + e.getMessage(), e);
        }
    }

    /**
     * Vérifie la disponibilité du service ClamAV.
     * Test robuste basé sur "PING\n" -> "PONG".
     * @return true si ClamAV est accessible et répond
     */
    public boolean isAvailable() {
        if (!enabled) {
            return true; // Si désactivé, considéré comme "disponible"
        }
        try (Socket socket = openSocket();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {

            writer.write("PING\n");
            writer.flush();

            String line = reader.readLine(); // attendu: PONG
            boolean ok = line != null && "PONG".equalsIgnoreCase(line.trim());

            log.debug("✓ ClamAV disponible: {} (réponse={})", ok, line);
            return ok;

        } catch (IOException e) {
            log.warn("✗ ClamAV non disponible: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Récupère la version de ClamAV.
     * @return Version de ClamAV ou null si non disponible
     */
    public String getVersion() {
        if (!enabled) {
            return "disabled";
        }

        try (Socket socket = openSocket();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {

            writer.write("VERSION\n");
            writer.flush();

            String line = reader.readLine();
            return line != null ? line.trim() : null;

        } catch (IOException e) {
            log.warn("Impossible de récupérer la version ClamAV: {}", e.getMessage());
            return null;
        }
    }

    // ============================================================================
    // MÉTHODES PRIVÉES - Communication ClamAV
    // ============================================================================

    private Socket openSocket() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(clamAvHost, clamAvPort), timeout);
        socket.setSoTimeout(timeout);
        return socket;
    }

    /**
     * Effectue le scan d'un fichier via le protocole INSTREAM de ClamAV.
     */
    private ScanResult scanWithClamAV(File file) throws IOException {
        try (Socket socket = openSocket();
             FileInputStream fis = new FileInputStream(file);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            socket.setSoTimeout(timeout);

            // Envoi commande INSTREAM
            out.write("zINSTREAM\0".getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Envoi du fichier par chunks
            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            long totalSent = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                // Format: 4 bytes (taille du chunk) + chunk data
                out.write(ByteBuffer.allocate(4).putInt(bytesRead).array());
                out.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
            }

            // fin de stream
            out.write(new byte[]{0, 0, 0, 0});
            out.flush();

            log.debug("📤 Envoyé à ClamAV: {} bytes", totalSent);

            // Lecture de la réponse
            String response = readResponse(in);
            return parseResponse(file.getName(), response);
        }
    }

    /**
     * Effectue le scan de données en mémoire via INSTREAM.
     */
    private ScanResult scanBytesWithClamAV(byte[] data, String filename) throws IOException {
        try (Socket socket = openSocket();
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            socket.setSoTimeout(timeout);

            // Envoi commande INSTREAM
            out.write("zINSTREAM\0".getBytes(StandardCharsets.UTF_8));
            out.flush();

            // Envoi des données par chunks
            int offset = 0;
            while (offset < data.length) {
                int chunkLen = Math.min(chunkSize, data.length - offset);
                
                // Taille du chunk (4 bytes big-endian)
                out.write(ByteBuffer.allocate(4).putInt(chunkLen).array());
                // Données du chunk
                out.write(data, offset, chunkLen);
                
                offset += chunkLen;
            }

            // Chunk de fin
            out.write(new byte[]{0, 0, 0, 0});
            out.flush();

            // Lecture réponse
            String response = readResponse(in);
            return parseResponse(filename, response);
        }
    }

    /**
     * Lit la réponse de ClamAV depuis le socket.
     */
    private String readResponse(InputStream in) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int bytesRead;

        while ((bytesRead = in.read(buffer)) != -1) {
            response.write(buffer, 0, bytesRead);
            // ClamAV termine sa réponse par un '\0'
            if (buffer[bytesRead - 1] == 0) {
                break;
            }
        }

        return response.toString(StandardCharsets.UTF_8).trim();
    }

    /**
     * Parse la réponse de ClamAV et crée un ScanResult.
     * 
     * <p>Formats attendus:
     * <ul>
     *   <li>OK: "stream: OK"</li>
     *   <li>INFECTED: "stream: Eicar-Test-Signature FOUND"</li>
     * </ul>
     */
    private ScanResult parseResponse(String filename, String response) {
        log.debug("📥 Réponse ClamAV: {}", response);

        if (response.contains("OK")) {
            log.info("✅ Fichier propre: {}", filename);
            return ScanResult.clean(filename);
        } 
        else if (response.contains("FOUND")) {
            // Extraction du nom du virus
            String virusName = extractVirusName(response);
            log.warn("⚠️ VIRUS DÉTECTÉ: {} dans {}", virusName, filename);
            return ScanResult.infected(filename, virusName);
        } 
        else if (response.contains("ERROR")) {
            log.error("❌ Erreur ClamAV: {}", response);
            return ScanResult.error(filename, response);
        }
        else {
            log.warn("⚠️ Réponse ClamAV inconnue: {}", response);
            return ScanResult.error(filename, "Réponse inconnue: " + response);
        }
    }

    /**
     * Extrait le nom du virus de la réponse ClamAV.
     * 
     * <p>Exemple: "stream: Eicar-Test-Signature FOUND" → "Eicar-Test-Signature"
     */
    private String extractVirusName(String response) {
        // Format: "stream: <VIRUS_NAME> FOUND"
        String[] parts = response.split(":");
        if (parts.length < 2) {
            return "Unknown";
        }

        String virusPart = parts[1].trim();
        int foundIndex = virusPart.lastIndexOf("FOUND");
        if (foundIndex > 0) {
            return virusPart.substring(0, foundIndex).trim();
        }

        return virusPart;
    }

    // ============================================================================
    // CLASSES INTERNES
    // ============================================================================

    /**
     * Résultat d'un scan antivirus.
     */
    public static class ScanResult {
        private final String filename;
        private final ScanStatus status;
        private final String virusName;
        private final String errorMessage;

        private ScanResult(String filename, ScanStatus status, String virusName, String errorMessage) {
            this.filename = filename;
            this.status = status;
            this.virusName = virusName;
            this.errorMessage = errorMessage;
        }

        public static ScanResult clean(String filename) {
            return new ScanResult(filename, ScanStatus.CLEAN, null, null);
        }

        public static ScanResult infected(String filename, String virusName) {
            return new ScanResult(filename, ScanStatus.INFECTED, virusName, null);
        }

        public static ScanResult error(String filename, String errorMessage) {
            return new ScanResult(filename, ScanStatus.ERROR, null, errorMessage);
        }

        public boolean isClean() {
            return status == ScanStatus.CLEAN;
        }

        public boolean isInfected() {
            return status == ScanStatus.INFECTED;
        }

        public boolean hasError() {
            return status == ScanStatus.ERROR;
        }

        // Getters
        public String getFilename() { return filename; }
        public ScanStatus getStatus() { return status; }
        public String getVirusName() { return virusName; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            return String.format("ScanResult{filename='%s', status=%s, virus='%s', error='%s'}",
                                 filename, status, virusName, errorMessage);
        }
    }

    /**
     * Statut du scan.
     */
    public enum ScanStatus {
        CLEAN,      // Fichier propre
        INFECTED,   // Virus détecté
        ERROR       // Erreur lors du scan
    }

}