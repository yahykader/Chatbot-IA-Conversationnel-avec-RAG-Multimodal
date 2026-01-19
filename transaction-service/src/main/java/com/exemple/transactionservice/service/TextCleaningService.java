// ============================================================================
// SERVICE - TextCleaningService.java
// ============================================================================
package com.exemple.transactionservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service de nettoyage intelligent de texte
 * Supprime les espaces parasites provenant de l'extraction PDF/Word
 * 
 * @author Votre Nom
 * @version 3.0 - Hybride Intelligent
 */
@Slf4j
@Service
public class TextCleaningService {

    public TextCleaningService() {
        log.info("✅ TextCleaningService initialisé - Nettoyage hybride intelligent");
    }

    /**
     * ✅ VERSION HYBRIDE INTELLIGENTE - Combine patterns + dictionnaire
     * Gère les espaces irréguliers : "Yah ya oui", "Comp ét ences", "J 2 EE"
     */
    public String cleanExtractedText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        String cleaned = text;
        
        // ✅ PRÉ-TRAITEMENT : Normaliser les caractères Unicode
        cleaned = cleaned.replace("\u00A0", " "); // Espace insécable
        cleaned = cleaned.replace("\u200B", "");  // Zero-width space
        cleaned = cleaned.replace("\u2009", " "); // Thin space
        cleaned = cleaned.replace("\u202F", " "); // Narrow no-break space
        cleaned = cleaned.replace("\u2007", " "); // Figure space
        
        // ✅ PHASE 0 : CORRECTIONS SPÉCIFIQUES PRÉCOCES
        cleaned = correctKnownPatterns(cleaned);
        
        // ✅ PHASE 1 : RECONSTRUCTION DES MOTS LIGNE PAR LIGNE
        StringBuilder phase1Result = new StringBuilder();
        String[] lines = cleaned.split("\n");
        
        for (String line : lines) {
            String rebuiltLine = reconstructLine(line);
            phase1Result.append(rebuiltLine).append("\n");
        }
        
        cleaned = phase1Result.toString();
        
        // ✅ PHASE 2 : NETTOYAGE CARACTÈRE PAR CARACTÈRE SÉLECTIF
        StringBuilder result = new StringBuilder();
        char[] chars = cleaned.toCharArray();
        boolean previousWasSpace = false;
        
        for (int i = 0; i < chars.length; i++) {
            char current = chars[i];
            
            if (current == '\n' || current == '\r') {
                if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
                    result.append('\n');
                }
                previousWasSpace = false;
                continue;
            }
            
            if (Character.isWhitespace(current)) {
                if (!previousWasSpace && i > 0 && i < chars.length - 1) {
                    char before = chars[i - 1];
                    char after = findNextNonSpace(chars, i + 1);
                    
                    boolean keepSpace = shouldKeepSpace(chars, i, before, after);
                    
                    if (keepSpace) {
                        result.append(' ');
                    }
                    previousWasSpace = true;
                }
            } else {
                result.append(current);
                previousWasSpace = false;
            }
        }
        
        cleaned = result.toString();
        
        // ✅ PHASE 3 : CORRECTIONS FINALES
        cleaned = applyFinalCorrections(cleaned);
        
        // ✅ LOGGING
        logCleaningResults(text, cleaned);
        
        return cleaned;
    }

    /**
     * PHASE 0 : Corriger les patterns techniques et acronymes connus
     */
    private String correctKnownPatterns(String text) {
        String corrected = text;
        
        // ✅ ACRONYMES TECHNIQUES
        corrected = corrected.replaceAll("(?i)J\\s*2\\s*EE", "J2EE");
        corrected = corrected.replaceAll("(?i)J\\s*2\\s*SE", "J2SE");
        corrected = corrected.replaceAll("(?i)REST\\s*API", "REST API");
        corrected = corrected.replaceAll("(?i)Fast\\s*API", "FastAPI");
        corrected = corrected.replaceAll("(?i)Spring\\s*Boot", "Spring Boot");
        corrected = corrected.replaceAll("(?i)Spring\\s*AI", "Spring AI");
        corrected = corrected.replaceAll("(?i)Type\\s*Script", "TypeScript");
        corrected = corrected.replaceAll("(?i)Java\\s*Script", "JavaScript");
        
        // ✅ TECHNOLOGIES
        corrected = corrected.replaceAll("(?i)Lang\\s*Chain\\s*4\\s*j", "LangChain4j");
        corrected = corrected.replaceAll("(?i)Postgre\\s*SQL", "PostgreSQL");
        corrected = corrected.replaceAll("(?i)My\\s*SQL", "MySQL");
        corrected = corrected.replaceAll("(?i)Mongo\\s*DB", "MongoDB");
        corrected = corrected.replaceAll("(?i)Big\\s*Query", "BigQuery");
        corrected = corrected.replaceAll("(?i)Cloud\\s*Run", "Cloud Run");
        corrected = corrected.replaceAll("(?i)Cloud\\s*Storage", "Cloud Storage");
        corrected = corrected.replaceAll("(?i)Cloud\\s*Composer", "Cloud Composer");
        corrected = corrected.replaceAll("(?i)Air\\s*flow", "Airflow");
        corrected = corrected.replaceAll("(?i)Key\\s*cloak", "Keycloak");
        corrected = corrected.replaceAll("(?i)Ng\\s*Rx", "NgRx");
        corrected = corrected.replaceAll("(?i)Ar\\s*go\\s*CD", "ArgoCD");
        corrected = corrected.replaceAll("(?i)Git\\s*Lab", "GitLab");
        corrected = corrected.replaceAll("(?i)Git\\s*Hub", "GitHub");
        corrected = corrected.replaceAll("(?i)Git\\s*Action(?:s)?", "GitHub Actions");
        corrected = corrected.replaceAll("(?i)Son\\s*ar\\s*Q\\s*ube", "SonarQube");
        corrected = corrected.replaceAll("(?i)Prom\\s*etheus", "Prometheus");
        corrected = corrected.replaceAll("(?i)Graf\\s*ana", "Grafana");
        corrected = corrected.replaceAll("(?i)Open\\s*AI", "OpenAI");
        
        // ✅ MÉTHODOLOGIES
        corrected = corrected.replaceAll("(?i)Micro\\s*services", "Microservices");
        corrected = corrected.replaceAll("(?i)Micro\\s*frontend", "Microfrontend");
        corrected = corrected.replaceAll("(?i)Clean\\s*Code", "Clean Code");
        corrected = corrected.replaceAll("(?i)Code\\s*Review", "Code Review");
        corrected = corrected.replaceAll("SOL\\s*ID", "SOLID");
        corrected = corrected.replaceAll("T\\s*DD", "TDD");
        corrected = corrected.replaceAll("B\\s*DD", "BDD");
        corrected = corrected.replaceAll("(?i)S\\s*cr\\s*um", "Scrum");
        
        // ✅ SIGLES
        corrected = corrected.replaceAll("CI\\s*/\\s*CD", "CI/CD");
        corrected = corrected.replaceAll("EL\\s*K", "ELK");
        corrected = corrected.replaceAll("R\\s*AG", "RAG");
        corrected = corrected.replaceAll("G\\s*CP", "GCP");
        corrected = corrected.replaceAll("D\\s*bt", "Dbt");
        corrected = corrected.replaceAll("D\\s*ev\\s*Ops", "DevOps");
        
        return corrected;
    }

    /**
     * PHASE 1 : Reconstruire une ligne en analysant les tokens
     */
    private String reconstructLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return "";
        }
        
        line = line.replaceAll("\\s+", " ").trim();
        String[] tokens = line.split(" ");
        StringBuilder result = new StringBuilder();
        
        int i = 0;
        while (i < tokens.length) {
            String token = tokens[i];
            
            if (token.isEmpty()) {
                i++;
                continue;
            }
            
            if (isPartOfBrokenWord(token)) {
                StringBuilder word = new StringBuilder(token);
                int j = i + 1;
                
                while (j < tokens.length && isPartOfBrokenWord(tokens[j]) && 
                       shouldMergeTokens(token, tokens[j])) {
                    word.append(tokens[j]);
                    j++;
                }
                
                if (j > i + 1) {
                    String reconstructed = word.toString();
                    
                    if (isPlausibleFrenchWord(reconstructed)) {
                        result.append(reconstructed).append(" ");
                        i = j;
                        continue;
                    }
                }
            }
            
            result.append(token).append(" ");
            i++;
        }
        
        return result.toString().trim();
    }

    private boolean isPartOfBrokenWord(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        if (token.length() <= 4) {
            for (char c : token.toCharArray()) {
                if (!Character.isLetter(c) && !isAccented(c)) {
                    return false;
                }
            }
            return true;
        }
        
        return false;
    }

    private boolean shouldMergeTokens(String token1, String token2) {
        if (token1 == null || token2 == null) {
            return false;
        }
        
        char lastChar = token1.charAt(token1.length() - 1);
        char firstChar = token2.charAt(0);
        
        if (isVowel(Character.toLowerCase(lastChar)) && Character.isUpperCase(firstChar)) {
            return false;
        }
        
        if (token1.length() <= 3 && token2.length() <= 4) {
            return true;
        }
        
        return false;
    }

    private boolean isPlausibleFrenchWord(String word) {
        if (word == null || word.length() < 3) {
            return false;
        }
        
        int vowelCount = 0;
        int consonantCount = 0;
        
        for (char c : word.toLowerCase().toCharArray()) {
            if (isVowel(c) || isAccentedVowel(c)) {
                vowelCount++;
            } else if (Character.isLetter(c)) {
                consonantCount++;
            }
        }
        
        if (vowelCount == 0) {
            return false;
        }
        
        if (consonantCount > vowelCount * 3) {
            return false;
        }
        
        if (hasTooManyConsecutiveConsonants(word)) {
            return false;
        }
        
        return true;
    }

    private boolean hasTooManyConsecutiveConsonants(String word) {
        int consecutiveConsonants = 0;
        
        for (char c : word.toLowerCase().toCharArray()) {
            if (!isVowel(c) && !isAccentedVowel(c) && Character.isLetter(c)) {
                consecutiveConsonants++;
                if (consecutiveConsonants > 3) {
                    return true;
                }
            } else {
                consecutiveConsonants = 0;
            }
        }
        
        return false;
    }

    /**
     * PHASE 2 : Décider si un espace doit être conservé
     */
    private boolean shouldKeepSpace(char[] chars, int spaceIndex, char before, char after) {
        // 1. Après ponctuation forte
        if (before == '.' || before == '!' || before == '?' || 
            before == ':' || before == ';' || before == ',') {
            return true;
        }
        
        // 2. Entre nombre et lettre
        if ((Character.isDigit(before) && (Character.isLetter(after) || isAccented(after))) ||
            ((Character.isLetter(before) || isAccented(before)) && Character.isDigit(after))) {
            return true;
        }
        
        // 3. Séparateur de milliers
        if (Character.isDigit(before) && Character.isDigit(after)) {
            int digitsAfter = countDigitsAfter(chars, spaceIndex + 1);
            if (digitsAfter == 3) {
                return true;
            }
        }
        
        // 4. Symboles monétaires
        if (before == '€' || before == '%' || before == '°' || before == '$' ||
            after == '€' || after == '$') {
            return true;
        }
        
        // 5. Entre deux mots complets (au moins 5 lettres de chaque côté)
        int lettersBefore = countLettersBefore(chars, spaceIndex - 1);
        int lettersAfter = countLettersAfter(chars, spaceIndex + 1);
        
        if (lettersBefore >= 5 && lettersAfter >= 5) {
            return true;
        }
        
        // 6. Après minuscule avant majuscule
        if (Character.isLowerCase(before) && Character.isUpperCase(after)) {
            return true;
        }
        
        return false;
    }

    /**
     * PHASE 3 : Corrections finales
     */
    private String applyFinalCorrections(String text) {
        String corrected = text;
        
        corrected = corrected.replaceAll("\\s+([.,;:!?)])", "$1");
        corrected = corrected.replaceAll("([.,;:!?])([A-Za-zÀ-ÿ0-9])", "$1 $2");
        corrected = corrected.replaceAll("(\\d)\\s*,\\s*(\\d)", "$1,$2");
        corrected = corrected.replaceAll("(\\d)\\s*\\.\\s*(\\d)", "$1.$2");
        corrected = corrected.replaceAll("([dDlLnNmMtTsScCjJqQ])\\s*'\\s*", "$1'");
        corrected = corrected.replaceAll("\\s+'", "'");
        corrected = corrected.replaceAll("\\(\\s+", "(");
        corrected = corrected.replaceAll("\\s+\\)", ")");
        corrected = corrected.replaceAll("\\s*/\\s*", "/");
        corrected = corrected.replaceAll("\\s*-\\s*", "-");
        corrected = corrected.replaceAll("_\\s+", "_");
        corrected = corrected.replaceAll("\\s+_", "_");
        corrected = corrected.replaceAll("[ \\t]{2,}", " ");
        
        String[] lines = corrected.split("\n");
        StringBuilder finalResult = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                finalResult.append(trimmed).append("\n");
            }
        }
        
        return finalResult.toString().trim();
    }

    /**
     * MÉTHODES UTILITAIRES
     */

    private boolean isVowel(char c) {
        return "aeiouy".indexOf(Character.toLowerCase(c)) >= 0;
    }

    private boolean isAccentedVowel(char c) {
        return "àâäéèêëïîôöùûüÿ".indexOf(c) >= 0;
    }

    private boolean isAccented(char c) {
        return (c >= 'À' && c <= 'ÿ') && c != '×' && c != '÷';
    }

    private char findNextNonSpace(char[] chars, int startIndex) {
        for (int i = startIndex; i < chars.length; i++) {
            if (!Character.isWhitespace(chars[i])) {
                return chars[i];
            }
        }
        return ' ';
    }

    private int countDigitsAfter(char[] chars, int startIndex) {
        int count = 0;
        for (int i = startIndex; i < chars.length && Character.isDigit(chars[i]); i++) {
            count++;
        }
        return count;
    }

    private int countLettersBefore(char[] chars, int endIndex) {
        int count = 0;
        for (int i = endIndex; i >= 0; i--) {
            char c = chars[i];
            if (Character.isLetter(c) || isAccented(c)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private int countLettersAfter(char[] chars, int startIndex) {
        int count = 0;
        for (int i = startIndex; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isLetter(c) || isAccented(c)) {
                count++;
            } else if (Character.isWhitespace(c)) {
                continue;
            } else {
                break;
            }
        }
        return count;
    }

    private void logCleaningResults(String original, String cleaned) {
        if (!log.isDebugEnabled() || original.equals(cleaned)) {
            return;
        }
        
        int reduction = original.length() - cleaned.length();
        
        if (reduction > 50) {
            log.debug("🧹 Nettoyage: {} → {} chars (réduction: {})",
                original.length(), cleaned.length(), reduction);
        }
    }
}