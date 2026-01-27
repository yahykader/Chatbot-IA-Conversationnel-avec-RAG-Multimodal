// ============================================================================
// COMPONENT - voice-button.component.ts (Angular 21 Pure - Whisper)
// ============================================================================
import { Component, OnInit, OnDestroy, Output, EventEmitter, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, interval } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { VoiceService, WhisperResponse } from '../service/voice.service';

/**
 * ✅ Composant bouton vocal avec OpenAI Whisper - Angular 21
 */
@Component({
  selector: 'app-voice-button',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './voice-button.component.html',
  styleUrls: ['./voice-button.component.scss']
})
export class VoiceButtonComponent implements OnInit, OnDestroy {
  
  // ==================== OUTPUTS ====================
  @Output() transcriptFinal = new EventEmitter<string>();
  @Output() recordingChange = new EventEmitter<boolean>();
  @Output() error = new EventEmitter<string>();
  
  // ==================== INPUTS ====================
  @Input() language: string = 'fr'; // Langue de transcription
  
  // ==================== STATE ====================
  isSupported = false;
  isRecording = false;
  isProcessing = false;
  errorMessage: string = '';
  
  private recordingStartMs: number | null = null;
  private recordingDuration: string = '';
  private durationInterval: any = null;
  private destroy$ = new Subject<void>();
  
  constructor(private voiceService: VoiceService) {}
  
  // ==================== LIFECYCLE ====================
  
  ngOnInit(): void {
    // Vérifier support
    this.isSupported = this.voiceService.isRecordingSupported();
    console.log('✅ [VoiceButton] Support enregistrement:', this.isSupported);
    
    // S'abonner aux erreurs
    this.voiceService.getErrors()
      .pipe(takeUntil(this.destroy$))
      .subscribe(error => {
        console.error('❌ [VoiceButton] Erreur:', error);
        this.handleError(error);
        this.isRecording = false;
        this.isProcessing = false;
        this.stopDurationTimer();
      });
  }
  
  ngOnDestroy(): void {
    this.stopDurationTimer();
    this.destroy$.next();
    this.destroy$.complete();
  }
  
  // ==================== PUBLIC METHODS ====================
  
  /**
   * ✅ Toggle enregistrement
   */
  async toggleRecording(): Promise<void> {
    if (this.isRecording) {
      await this.stopRecording();
    } else {
      await this.startRecording();
    }
  }
  
  /**
   * ✅ Démarre l'enregistrement
   */
  async startRecording(): Promise<void> {
    try {
      console.log('🎤 [VoiceButton] Démarrage enregistrement');
      
      await this.voiceService.startRecording();
      
      this.isRecording = true;
      this.recordingStartMs = Date.now();
      this.startDurationTimer();
      this.recordingChange.emit(true);
      
      console.log('✅ [VoiceButton] Enregistrement en cours');
      
    } catch (error: any) {
      console.error('❌ [VoiceButton] Erreur démarrage:', error);
      this.handleError(error.message || 'Erreur lors du démarrage');
    }
  }
  
  /**
   * ✅ Arrête l'enregistrement et lance la transcription
   */
  async stopRecording(): Promise<void> {
    try {
      console.log('🛑 [VoiceButton] Arrêt enregistrement');
      
      this.isRecording = false;
      this.isProcessing = true;
      this.stopDurationTimer();
      this.recordingChange.emit(false);
      
      // Arrêter l'enregistrement et obtenir l'audio
      const audioBlob = await this.voiceService.stopRecording();
      console.log('📊 [VoiceButton] Audio obtenu:', audioBlob.size, 'bytes');
      
      // Envoyer au backend pour transcription
      console.log('📤 [VoiceButton] Envoi à Whisper...');
      
      this.voiceService.transcribeWithWhisper(audioBlob, this.language)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (response: WhisperResponse) => {
            console.log('✅ [VoiceButton] Transcription reçue:', response);
            
            this.isProcessing = false;
            
            if (response.success && response.transcript) {
              console.log('📝 [VoiceButton] Texte:', response.transcript);
              this.transcriptFinal.emit(response.transcript);
            } else {
              const errorMsg = response.error || 'Aucune transcription reçue';
              console.error('❌ [VoiceButton] Erreur:', errorMsg);
              this.handleError(errorMsg);
            }
          },
          error: (error) => {
            console.error('❌ [VoiceButton] Erreur transcription:', error);
            this.isProcessing = false;
            
            const errorMsg = error.error?.error || error.message || 'Erreur lors de la transcription';
            this.handleError(errorMsg);
          }
        });
        
    } catch (error: any) {
      console.error('❌ [VoiceButton] Erreur arrêt:', error);
      this.isProcessing = false;
      this.handleError(error.message || 'Erreur lors de l\'arrêt');
    }
  }
  
  /**
   * ✅ Méthode publique pour arrêter depuis le parent
   */
  public stopRecognition(): void {
    if (this.isRecording) {
      this.stopRecording();
    }
  }
  
  // ==================== GETTERS ====================
  
  /**
   * ✅ Tooltip dynamique
   */
  getTooltip(): string {
    if (!this.isSupported) {
      return 'Microphone non disponible';
    }
    if (this.isProcessing) {
      return 'Transcription en cours...';
    }
    if (this.isRecording) {
      return 'Arrêter l\'enregistrement';
    }
    return 'Démarrer l\'enregistrement vocal';
  }
  
  /**
   * ✅ Durée d'enregistrement formatée
   */
  getRecordingDuration(): string {
    return this.recordingDuration;
  }
  
  /**
   * ✅ Badge text (optionnel)
   */
  getBadgeText(): string {
    if (this.isRecording) {
      return '●';
    }
    if (this.isProcessing) {
      return '...';
    }
    return '';
  }
  
  /**
   * ✅ Couleur du bouton selon l'état
   */
  getButtonClass(): string {
    if (this.isRecording) {
      return 'btn-danger';
    }
    if (this.isProcessing) {
      return 'btn-warning';
    }
    return 'btn-outline-secondary';
  }
  
  // ==================== PRIVATE METHODS ====================
  
  /**
   * ✅ Démarre le timer de durée
   */
  private startDurationTimer(): void {
    this.recordingDuration = '00:00';
    
    this.durationInterval = setInterval(() => {
      if (this.recordingStartMs === null) return;
      
      const totalSeconds = Math.floor((Date.now() - this.recordingStartMs) / 1000);
      const mm = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
      const ss = String(totalSeconds % 60).padStart(2, '0');
      this.recordingDuration = `${mm}:${ss}`;
    }, 1000);
  }
  
  /**
   * ✅ Arrête le timer de durée
   */
  private stopDurationTimer(): void {
    if (this.durationInterval) {
      clearInterval(this.durationInterval);
      this.durationInterval = null;
    }
    this.recordingDuration = '';
    this.recordingStartMs = null;
  }
  
  /**
   * ✅ Gère les erreurs
   */
  private handleError(message: string): void {
    this.errorMessage = message;
    this.error.emit(message);
    
    // Auto-clear après 5 secondes
    setTimeout(() => {
      this.errorMessage = '';
    }, 5000);
  }
}