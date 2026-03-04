import { Component, OnInit, OnDestroy, Output, EventEmitter, Input, ChangeDetectorRef, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { VoiceService, WhisperResponse } from '../../../../core/services/voice.service';

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
  @Input() language: string = 'fr';
  @Input() disabled: boolean = false;

  // ==================== STATE ====================
  isSupported = false;
  isRecording = false;
  isProcessing = false;
  errorMessage: string = '';

  private recordingStartMs: number | null = null;
  recordingDuration: string = '';
  private durationInterval: any = null;
  private destroy$ = new Subject<void>();

  constructor(
    private voiceService: VoiceService,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone
  ) {}

  // ==================== LIFECYCLE ====================

  ngOnInit(): void {
    this.isSupported = this.voiceService.isRecordingSupported();
    console.log('✅ [VoiceButton] Support enregistrement:', this.isSupported);

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

  async toggleRecording(): Promise<void> {
    if (this.isRecording) {
      await this.stopRecording();
    } else {
      await this.startRecording();
    }
  }

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

  async stopRecording(): Promise<void> {
    try {
      console.log('🛑 [VoiceButton] Arrêt enregistrement');

      this.isRecording = false;
      this.isProcessing = true;
      this.stopDurationTimer();
      this.recordingChange.emit(false);

      const audioBlob = await this.voiceService.stopRecording();
      console.log('📊 [VoiceButton] Audio obtenu:', audioBlob.size, 'bytes');
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
            this.cdr.detectChanges();
          },
          error: (error) => {
            console.error('❌ [VoiceButton] Erreur transcription:', error);
            this.isProcessing = false;
            const errorMsg = error.error?.error || error.message || 'Erreur lors de la transcription';
            this.handleError(errorMsg);
            this.cdr.detectChanges();
          }
        });

    } catch (error: any) {
      console.error('❌ [VoiceButton] Erreur arrêt:', error);
      this.isProcessing = false;
      this.handleError(error.message || 'Erreur lors de l\'arrêt');
    }
  }

  public stopRecognition(): void {
    if (this.isRecording) {
      this.stopRecording();
    }
  }

  // ==================== GETTERS ====================

  getTooltip(): string {
    if (!this.isSupported) return 'Microphone non disponible';
    if (this.isProcessing) return 'Transcription en cours...';
    if (this.isRecording) return 'Arrêter l\'enregistrement';
    return 'Démarrer l\'enregistrement vocal';
  }

  getButtonClass(): string {
    if (this.isRecording) return 'btn-danger';
    if (this.isProcessing) return 'btn-warning';
    return 'btn-outline-secondary';
  }

  // ==================== PRIVATE METHODS ====================

  private startDurationTimer(): void {
    this.recordingDuration = '00:00';

    // ✅ NgZone.runOutsideAngular → setInterval hors zone
    // puis re-entre dans la zone pour trigger la CD
    this.ngZone.runOutsideAngular(() => {
      this.durationInterval = setInterval(() => {
        if (this.recordingStartMs === null) return;

        const totalSeconds = Math.floor((Date.now() - this.recordingStartMs) / 1000);
        const mm = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
        const ss = String(totalSeconds % 60).padStart(2, '0');

        this.ngZone.run(() => {
          this.recordingDuration = `${mm}:${ss}`;
          this.cdr.detectChanges(); // ✅ force le re-render
        });
      }, 1000);
    });
  }

  private stopDurationTimer(): void {
    if (this.durationInterval) {
      clearInterval(this.durationInterval);
      this.durationInterval = null;
    }
    this.recordingDuration = '';
    this.recordingStartMs = null;
  }

  private handleError(message: string): void {
    this.errorMessage = message;
    this.error.emit(message);
    setTimeout(() => {
      this.errorMessage = '';
      this.cdr.detectChanges();
    }, 5000);
  }
}