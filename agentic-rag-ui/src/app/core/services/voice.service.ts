// ============================================================================
// SERVICE - voice.service.ts (ADAPTÉ POUR WHISPER)
// ============================================================================
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';


/**
 * ✅ Service vocal avec OpenAI Whisper
 * Enregistrement audio → Backend → Whisper API → Transcription
 */
@Injectable({
  providedIn: 'root'
})
export class VoiceService {

  private readonly apiUrl = `${environment.apiUrl}/v1/voice`;

  
  // ==================== ENREGISTREMENT AUDIO ====================
  
  private mediaRecorder: MediaRecorder | null = null;
  private audioChunks: Blob[] = [];
  private recordingSubject = new Subject<boolean>();
  private errorSubject = new Subject<string>();
  
  constructor(private http: HttpClient) {}
  
  /**
   * ✅ Vérifie si l'enregistrement audio est supporté
   */
  isRecordingSupported(): boolean {
    return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
  }
  
  /**
   * ✅ Démarre l'enregistrement audio
   */
  async startRecording(): Promise<void> {
    try {
      console.log('🎤 [VoiceService] Demande accès microphone');
      
      const stream = await navigator.mediaDevices.getUserMedia({ 
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          sampleRate: 16000
        } 
      });
      
      console.log('✅ [VoiceService] Accès microphone accordé');
      
      // Créer le MediaRecorder
      const options = { mimeType: 'audio/webm' };
      this.mediaRecorder = new MediaRecorder(stream, options);
      this.audioChunks = [];
      
      // Collecter les données audio
      this.mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          this.audioChunks.push(event.data);
          console.log('📊 [VoiceService] Chunk audio reçu:', event.data.size, 'bytes');
        }
      };
      
      // Démarrer l'enregistrement
      this.mediaRecorder.start(1000); // Collecte toutes les secondes
      this.recordingSubject.next(true);
      
      console.log('🎤 [VoiceService] Enregistrement démarré');
      
    } catch (error: any) {
      console.error('❌ [VoiceService] Erreur accès microphone:', error);
      this.errorSubject.next(error.message || 'Erreur accès microphone');
      throw error;
    }
  }
  
  /**
   * ✅ Arrête l'enregistrement et retourne le blob audio
   */
  async stopRecording(): Promise<Blob> {
    return new Promise((resolve, reject) => {
      if (!this.mediaRecorder) {
        reject(new Error('Aucun enregistrement en cours'));
        return;
      }
      
      console.log('🛑 [VoiceService] Arrêt enregistrement');
      
      this.mediaRecorder.onstop = () => {
        // Créer le blob audio
        const audioBlob = new Blob(this.audioChunks, { type: 'audio/webm' });
        console.log('✅ [VoiceService] Enregistrement arrêté:', audioBlob.size, 'bytes');
        
        // Arrêter tous les tracks du microphone
        if (this.mediaRecorder?.stream) {
          this.mediaRecorder.stream.getTracks().forEach(track => {
            track.stop();
            console.log('🔇 [VoiceService] Track audio arrêté');
          });
        }
        
        this.recordingSubject.next(false);
        resolve(audioBlob);
      };
      
      this.mediaRecorder.onerror = (error: any) => {
        console.error('❌ [VoiceService] Erreur MediaRecorder:', error);
        this.errorSubject.next('Erreur lors de l\'enregistrement');
        reject(error);
      };
      
      this.mediaRecorder.stop();
    });
  }
  
  /**
   * ✅ Transcrit l'audio avec Whisper via le backend
   * 
   * @param audioBlob Blob audio à transcrire
   * @param language Code langue (fr, en, es, etc.)
   * @returns Observable de la réponse API
   */
  transcribeWithWhisper(audioBlob: Blob, language: string = 'fr'): Observable<WhisperResponse> {
    const formData = new FormData();
    formData.append('audio', audioBlob, 'recording.webm');
    formData.append('language', language);
    
    console.log('📤 [VoiceService] Envoi audio à Whisper');
    console.log('📊 [VoiceService] Taille:', audioBlob.size, 'bytes');
    console.log('🌍 [VoiceService] Langue:', language);
    
    return this.http.post<WhisperResponse>(
      this.apiUrl + `/transcribe`,
      formData
    );
  }
  
  /**
   * ✅ Observable de l'état d'enregistrement
   */
  getRecordingState(): Observable<boolean> {
    return this.recordingSubject.asObservable();
  }
  
  /**
   * ✅ Observable des erreurs
   */
  getErrors(): Observable<string> {
    return this.errorSubject.asObservable();
  }
  
  /**
   * ✅ Vérifie si en cours d'enregistrement
   */
  isRecording(): boolean {
    return this.mediaRecorder?.state === 'recording';
  }
}

/**
 * ✅ Interface de réponse Whisper
 */
export interface WhisperResponse {
  success: boolean;
  transcript: string;
  language: string;
  audioSize: number;
  filename: string;
  transcriptLength?: number;
  error?: string;
}