import { Component, Output, EventEmitter, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { VoiceButtonComponent } from '../voice-control/voice-button.component';

@Component({
  selector: 'app-message-input',
  standalone: true,
  imports: [CommonModule, FormsModule, VoiceButtonComponent],
  templateUrl: './message-input.component.html',
  styleUrls: ['./message-input.component.scss']
})
export class MessageInputComponent {
  @Input() inputText: string = '';
  @Input() disabled: boolean = false;
  @Input() isStreaming: boolean = false;
  
  @Output() send = new EventEmitter<string>();
  @Output() cancel = new EventEmitter<void>();
  @Output() inputChange = new EventEmitter<string>();

  @Output() transcript = new EventEmitter<string>();
  @Output() recordingChange = new EventEmitter<boolean>();
  @Output() voiceError = new EventEmitter<string>();

  isVoiceRecording = false;
  
  onInputChange(text: string): void {
    this.inputChange.emit(text);
  }
  
  onSend(): void {
    if (this.inputText.trim() && !this.disabled) {
      this.send.emit(this.inputText.trim());
    }
  }
  
  onCancel(): void {
    this.cancel.emit();
  }
  
  onKeyDown(event: KeyboardEvent): void {
    // Ctrl/Cmd + Enter pour envoyer
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      this.onSend();
    }
  }

    // ==================== VOICE HANDLERS ====================

  onTranscript(text: string): void {
    this.inputText = this.inputText ? `${this.inputText} ${text}` : text;
    this.inputChange.emit(this.inputText);
  }

  onRecordingChange(isRecording: boolean): void {
    this.isVoiceRecording = isRecording;
  }

  onVoiceError(error: string): void {
    console.error('[MessageInput] Voice error:', error);
  }
}