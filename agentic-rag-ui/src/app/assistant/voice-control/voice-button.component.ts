// ============================================================================
// COMPONENT - voice-button.component.ts (ADAPTÉ POUR WHISPER)
// ============================================================================
import { Component, OnInit, OnDestroy, Output, EventEmitter, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { VoiceService, WhisperResponse } from '../service/VoiceService';

/**
 * ✅ Composant bouton vocal avec OpenAI Whisper
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
  
  private destroy$ = new Subject<void>();
  
  constructor(private voiceService: VoiceService) {}
  
  ngOnInit(): void {
    // Vérifier support
    this.isSupported = this.voiceService.isRecordingSupported();
    console.log('✅ [VoiceButton] Support enregistrement:', this.isSupported);
    
    // S'abonner aux erreurs
    this.voiceService.getErrors()
      .pipe(takeUntil(this.destroy$))
      .subscribe(error => {
        console.error('❌ [VoiceButton] Erreur:', error);
        this.error.emit(error);
        this.isRecording = false;
        this.isProcessing = false;
      });
  }
  
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
  
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
      this.recordingChange.emit(true);
      
      console.log('✅ [VoiceButton] Enregistrement en cours');
      
    } catch (error: any) {
      console.error('❌ [VoiceButton] Erreur démarrage:', error);
      this.error.emit(error.message || 'Erreur lors du démarrage');
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
              this.error.emit(errorMsg);
            }
          },
          error: (error) => {
            console.error('❌ [VoiceButton] Erreur transcription:', error);
            this.isProcessing = false;
            
            const errorMsg = error.error?.error || error.message || 'Erreur lors de la transcription';
            this.error.emit(errorMsg);
          }
        });
        
    } catch (error: any) {
      console.error('❌ [VoiceButton] Erreur arrêt:', error);
      this.isProcessing = false;
      this.error.emit(error.message || 'Erreur lors de l\'arrêt');
    }
  }
  
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
   * ✅ Méthode publique pour arrêter depuis le parent
   */
  public stopRecognition(): void {
    if (this.isRecording) {
      this.stopRecording();
    }
  }
}