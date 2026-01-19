// ============================================================================
// SERVICE - voice.service.ts (Web Speech API)
// ============================================================================
import { Injectable } from '@angular/core';
import { Observable, Subject, fromEvent } from 'rxjs';

// ✅ Interface pour le support du navigateur
declare global {
  interface Window {
    webkitSpeechRecognition: any;
    SpeechRecognition: any;
  }
}

export interface VoiceRecognitionResult {
  transcript: string;
  isFinal: boolean;
  confidence: number;
}

export interface VoiceSynthesisOptions {
  lang?: string;
  rate?: number;  // Vitesse (0.1 à 10)
  pitch?: number; // Tonalité (0 à 2)
  volume?: number; // Volume (0 à 1)
}

@Injectable({
  providedIn: 'root'
})
export class VoiceService {
  
  private recognition: any;
  private speechSynthesis: SpeechSynthesis;
  private isRecognitionAvailable = false;
  private isSynthesisAvailable = false;
  
  private recognitionSubject = new Subject<VoiceRecognitionResult>();
  private recognitionErrorSubject = new Subject<string>();
  
  constructor() {
    this.checkBrowserSupport();
    this.initializeSpeechRecognition();
    this.speechSynthesis = window.speechSynthesis;
  }
  
  // ==================== BROWSER SUPPORT ====================
  
  private checkBrowserSupport(): void {
    // Vérifier Speech Recognition
    this.isRecognitionAvailable = !!(
      window.SpeechRecognition || 
      window.webkitSpeechRecognition
    );
    
    // Vérifier Speech Synthesis
    this.isSynthesisAvailable = 'speechSynthesis' in window;
    
    console.log('🎤 [Voice] Speech Recognition disponible:', this.isRecognitionAvailable);
    console.log('🔊 [Voice] Speech Synthesis disponible:', this.isSynthesisAvailable);
  }
  
  public isSpeechRecognitionSupported(): boolean {
    return this.isRecognitionAvailable;
  }
  
  public isSpeechSynthesisSupported(): boolean {
    return this.isSynthesisAvailable;
  }
  
  // ==================== SPEECH RECOGNITION (STT) ====================
  
  private initializeSpeechRecognition(): void {
    if (!this.isRecognitionAvailable) {
      console.warn('⚠️ [Voice] Speech Recognition non supporté');
      return;
    }
    
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    this.recognition = new SpeechRecognition();
    
    // Configuration
    this.recognition.lang = 'fr-FR';
    this.recognition.continuous = true;
    this.recognition.interimResults = true;
    this.recognition.maxAlternatives = 1;
    
    // ✅ Event: Résultat de reconnaissance
    this.recognition.onresult = (event: any) => {
      const result = event.results[event.results.length - 1];
      const transcript = result[0].transcript;
      const isFinal = result.isFinal;
      const confidence = result[0].confidence;
      
      console.log('🎤 [Voice] Transcription:', transcript, '(final:', isFinal, ')');
      
      this.recognitionSubject.next({
        transcript,
        isFinal,
        confidence
      });
    };
    
    // ✅ Event: Erreur
    this.recognition.onerror = (event: any) => {
      console.error('❌ [Voice] Erreur reconnaissance:', event.error);
      this.recognitionErrorSubject.next(event.error);
    };
    
    // ✅ Event: Fin automatique
    this.recognition.onend = () => {
      console.log('🛑 [Voice] Reconnaissance terminée');
    };
    
    console.log('✅ [Voice] Speech Recognition initialisé');
  }
  
  /**
   * ✅ Démarre la reconnaissance vocale
   */
  public startRecognition(): void {
    if (!this.isRecognitionAvailable) {
      this.recognitionErrorSubject.next('Speech Recognition non supporté');
      return;
    }
    
    try {
      this.recognition.start();
      console.log('🎤 [Voice] Reconnaissance démarrée');
    } catch (error) {
      console.error('❌ [Voice] Erreur démarrage:', error);
      // Si déjà démarré, on redémarre
      this.stopRecognition();
      setTimeout(() => this.recognition.start(), 100);
    }
  }
  
  /**
   * ✅ Arrête la reconnaissance vocale
   */
  public stopRecognition(): void {
    if (!this.isRecognitionAvailable) {
      return;
    }
    
    try {
      this.recognition.stop();
      console.log('🛑 [Voice] Reconnaissance arrêtée');
    } catch (error) {
      console.error('❌ [Voice] Erreur arrêt:', error);
    }
  }
  
  /**
   * ✅ Observable des résultats de reconnaissance
   */
  public getRecognitionResults(): Observable<VoiceRecognitionResult> {
    return this.recognitionSubject.asObservable();
  }
  
  /**
   * ✅ Observable des erreurs de reconnaissance
   */
  public getRecognitionErrors(): Observable<string> {
    return this.recognitionErrorSubject.asObservable();
  }
  
  // ==================== SPEECH SYNTHESIS (TTS) ====================
  
  /**
   * ✅ Lit un texte à voix haute
   */
  public speak(
    text: string, 
    options: VoiceSynthesisOptions = {}
  ): Promise<void> {
    return new Promise((resolve, reject) => {
      if (!this.isSynthesisAvailable) {
        reject(new Error('Speech Synthesis non supporté'));
        return;
      }
      
      // Arrêter toute lecture en cours
      this.stopSpeaking();
      
      const utterance = new SpeechSynthesisUtterance(text);
      
      // Configuration
      utterance.lang = options.lang || 'fr-FR';
      utterance.rate = options.rate || 1.0;
      utterance.pitch = options.pitch || 1.0;
      utterance.volume = options.volume || 1.0;
      
      // Sélectionner une voix française
      const voices = this.speechSynthesis.getVoices();
      const frenchVoice = voices.find(voice => voice.lang.startsWith('fr'));
      if (frenchVoice) {
        utterance.voice = frenchVoice;
      }
      
      // Events
      utterance.onend = () => {
        console.log('✅ [Voice] Lecture terminée');
        resolve();
      };
      
      utterance.onerror = (event) => {
        console.error('❌ [Voice] Erreur lecture:', event);
        reject(event);
      };
      
      // Lancer la lecture
      console.log('🔊 [Voice] Lecture:', text.substring(0, 50) + '...');
      this.speechSynthesis.speak(utterance);
    });
  }
  
  /**
   * ✅ Arrête la lecture en cours
   */
  public stopSpeaking(): void {
    if (!this.isSynthesisAvailable) {
      return;
    }
    
    if (this.speechSynthesis.speaking) {
      this.speechSynthesis.cancel();
      console.log('🛑 [Voice] Lecture arrêtée');
    }
  }
  
  /**
   * ✅ Met en pause la lecture
   */
  public pauseSpeaking(): void {
    if (!this.isSynthesisAvailable) {
      return;
    }
    
    if (this.speechSynthesis.speaking && !this.speechSynthesis.paused) {
      this.speechSynthesis.pause();
      console.log('⏸️ [Voice] Lecture en pause');
    }
  }
  
  /**
   * ✅ Reprend la lecture
   */
  public resumeSpeaking(): void {
    if (!this.isSynthesisAvailable) {
      return;
    }
    
    if (this.speechSynthesis.paused) {
      this.speechSynthesis.resume();
      console.log('▶️ [Voice] Lecture reprise');
    }
  }
  
  /**
   * ✅ Vérifie si une lecture est en cours
   */
  public isSpeaking(): boolean {
    return this.isSynthesisAvailable && this.speechSynthesis.speaking;
  }
  
  /**
   * ✅ Liste les voix disponibles
   */
  public getAvailableVoices(): SpeechSynthesisVoice[] {
    if (!this.isSynthesisAvailable) {
      return [];
    }
    
    return this.speechSynthesis.getVoices();
  }
  
  /**
   * ✅ Obtient les voix françaises disponibles
   */
  public getFrenchVoices(): SpeechSynthesisVoice[] {
    return this.getAvailableVoices().filter(voice => 
      voice.lang.startsWith('fr')
    );
  }
}