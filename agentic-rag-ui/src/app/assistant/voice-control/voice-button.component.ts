// ============================================================================
// COMPONENT - voice-button.component.ts (Version Inline Simplifiée)
// ============================================================================
import { Component, OnInit, OnDestroy, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { VoiceService, VoiceRecognitionResult } from '../service/VoiceService';

@Component({
  selector: 'app-voice-button',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './voice-button.component.html',
  styleUrls: ['./voice-button.component.scss'],
})
export class VoiceButtonComponent implements OnInit, OnDestroy {
  
  @Output() transcriptFinal = new EventEmitter<string>();
  @Output() transcriptInterim = new EventEmitter<string>();
  @Output() listeningChange = new EventEmitter<boolean>();
  
  isRecognitionSupported = false;
  isListening = false;
  showUnsupportedWarning = false; // Masqué par défaut
  
  private destroy$ = new Subject<void>();
  
  constructor(private voiceService: VoiceService) {}
  
  ngOnInit(): void {
    // Vérifier le support
    this.isRecognitionSupported = this.voiceService.isSpeechRecognitionSupported();
    
    // S'abonner aux résultats
    this.voiceService.getRecognitionResults()
      .pipe(takeUntil(this.destroy$))
      .subscribe(result => this.handleRecognitionResult(result));
    
    // S'abonner aux erreurs
    this.voiceService.getRecognitionErrors()
      .pipe(takeUntil(this.destroy$))
      .subscribe(error => {
        console.error('❌ [VoiceButton] Erreur:', error);
        this.isListening = false;
        this.listeningChange.emit(false);
      });
  }
  
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.voiceService.stopRecognition();
  }
  
  toggleRecognition(): void {
    if (this.isListening) {
      this.stopRecognition();
    } else {
      this.startRecognition();
    }
  }
  
  startRecognition(): void {
    console.log('🎤 [VoiceButton] Démarrage reconnaissance');
    this.isListening = true;
    this.listeningChange.emit(true);
    this.voiceService.startRecognition();
  }
  
  stopRecognition(): void {
    console.log('🛑 [VoiceButton] Arrêt reconnaissance');
    this.isListening = false;
    this.listeningChange.emit(false);
    this.voiceService.stopRecognition();
  }
  
  private handleRecognitionResult(result: VoiceRecognitionResult): void {
    // Émettre transcriptions intermédiaires
    if (!result.isFinal) {
      this.transcriptInterim.emit(result.transcript);
    } else {
      // Transcription finale
      console.log('✅ [VoiceButton] Transcription finale:', result.transcript);
      this.transcriptFinal.emit(result.transcript);
      
      // Arrêter automatiquement après transcription finale
      this.stopRecognition();
    }
  }
}