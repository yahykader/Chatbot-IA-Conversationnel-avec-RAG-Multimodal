// ============================================================================
// COMPONENT - assistant.component.ts (VERSION ADAPTÉE ET OPTIMISÉE)
// ============================================================================
import { 
  Component, 
  OnInit, 
  ViewChild, 
  ElementRef, 
  AfterViewChecked, 
  OnDestroy 
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { Observable, Subject } from 'rxjs';
import { take, takeUntil } from 'rxjs/operators';
import { trigger, transition, style, animate } from '@angular/animations';

import * as AssistantActions from './store/assistant.actions';
import * as AssistantSelectors from './store/assistant.selectors';
import { Message, UploadedFile } from './store/assistant.models';
import { MarkdownModule } from 'ngx-markdown';
import { VoiceButtonComponent } from './voice-control/voice-button.component';
import { VoiceService } from './service/VoiceService';

@Component({
  selector: 'app-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule, MarkdownModule, VoiceButtonComponent],
  templateUrl: './assistant.component.html',
  styleUrls: ['./assistant.component.scss'],
  animations: [
    trigger('slideIn', [
      transition(':enter', [
        style({ 
          opacity: 0, 
          transform: 'translateY(20px)' 
        }),
        animate('300ms ease-out', style({ 
          opacity: 1, 
          transform: 'translateY(0)' 
        }))
      ]),
      transition(':leave', [
        animate('200ms ease-in', style({ 
          opacity: 0, 
          transform: 'translateX(-20px)' 
        }))
      ])
    ]),
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('400ms ease-out', style({ opacity: 1 }))
      ])
    ])
  ]
})
export class AssistantComponent implements OnInit, AfterViewChecked, OnDestroy {

    // ✅ ÉTAPE 3 : Ajouter les propriétés
  @ViewChild(VoiceButtonComponent) voiceButton?: VoiceButtonComponent;

  isVoiceEnabled = false;
  isRecording = false; // État d'enregistrement
  
  // ==================== VIEW CHILDREN ====================
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;
  @ViewChild('chatContainer') chatContainer!: ElementRef<HTMLDivElement>;
  @ViewChild('messageInput') messageInput!: ElementRef<HTMLTextAreaElement>;
  
  // ==================== OBSERVABLES FROM STORE ====================
  messages$: Observable<Message[]>;
  files$: Observable<UploadedFile[]>;
  loading$: Observable<boolean>;
  uploading$: Observable<boolean>;
  hasMessages$: Observable<boolean>;
  hasFiles$: Observable<boolean>;
  userId$: Observable<string>;
  messagesError$: Observable<string | null>;
  filesError$: Observable<string | null>;
  isStreaming$: Observable<boolean>;
  canSendMessage$: Observable<boolean>;
  uiState$: Observable<any>;
  
  // ==================== LOCAL STATE ====================
  dragOver = false;
  currentMessage = '';
  private shouldScrollToBottom = false;
  private lastMessageCount = 0;
  private destroy$ = new Subject<void>();
  
  constructor(private store: Store, private voiceService: VoiceService) {
    // ✅ CRITIQUE: Utiliser selectMessagesSorted pour garantir l'ordre
    this.messages$ = this.store.select(AssistantSelectors.selectMessagesSorted);
    this.files$ = this.store.select(AssistantSelectors.selectAllFiles);
    this.loading$ = this.store.select(AssistantSelectors.selectMessagesLoading);
    this.uploading$ = this.store.select(AssistantSelectors.selectFilesUploading);
    this.hasMessages$ = this.store.select(AssistantSelectors.selectHasMessages);
    this.hasFiles$ = this.store.select(AssistantSelectors.selectHasFiles);
    this.userId$ = this.store.select(AssistantSelectors.selectUserId);
    this.messagesError$ = this.store.select(AssistantSelectors.selectMessagesError);
    this.filesError$ = this.store.select(AssistantSelectors.selectFilesError);
    this.isStreaming$ = this.store.select(AssistantSelectors.selectIsStreaming);
    this.canSendMessage$ = this.store.select(AssistantSelectors.selectCanSendMessage);
    this.uiState$ = this.store.select(AssistantSelectors.selectUIState);
    
    console.log('✅ [Component] AssistantComponent initialisé');

    // ✅ AJOUTER - Vérifier support vocal
    this.isVoiceEnabled = this.voiceService.isRecordingSupported();
    console.log('✅ [Component] Support vocal:', this.isVoiceEnabled);
  }
  
  // ==================== LIFECYCLE HOOKS ====================
  
  ngOnInit(): void {
    console.log('🚀 [Component] ngOnInit - Chargement des données');
    
    // Charger les données au démarrage
    this.store.dispatch(AssistantActions.loadMessagesFromStorage());
    this.store.dispatch(AssistantActions.loadFilesFromStorage());
    
    // S'abonner aux messages avec gestion de l'ordre
    this.messages$
      .pipe(takeUntil(this.destroy$))
      .subscribe(messages => {
        console.log('📨 [Component] Messages reçus:', messages.length);
        
        // Debug: vérifier l'ordre (seulement en dev)
        if (messages.length > 0 && messages.length <= 10) {
          messages.forEach((msg, i) => {
            console.log(
              `  [${i}] ${msg.sender} (seq: ${msg.sequence}): ${msg.content.substring(0, 30)}...`
            );
          });
        }
        
        // Auto-scroll seulement si nouveau message
        if (messages.length > this.lastMessageCount) {
          this.shouldScrollToBottom = true;
          this.lastMessageCount = messages.length;
        }
      });
    
    // Gérer les erreurs de messages
    this.messagesError$
      .pipe(takeUntil(this.destroy$))
      .subscribe(error => {
        if (error) {
          console.error('❌ [Component] Message error:', error);
          this.showError('Erreur de message: ' + error);
        }
      });
    
    // Gérer les erreurs de fichiers
    this.filesError$
      .pipe(takeUntil(this.destroy$))
      .subscribe(error => {
        if (error) {
          console.error('❌ [Component] File error:', error);
          this.showError('Erreur de fichier: ' + error);
        }
      });
  }
  
  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      setTimeout(() => {
        this.scrollToBottom();
        this.shouldScrollToBottom = false;
      }, 100);
    }
  }
  
  ngOnDestroy(): void {
    console.log('🔌 [Component] ngOnDestroy - Nettoyage');
    this.destroy$.next();
    this.destroy$.complete();
  }
  
  // ==================== FILE UPLOAD ====================
  
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      
      // Validation de la taille
      const maxSize = 50 * 1024 * 1024; // 50MB
      if (file.size > maxSize) {
        this.showError(
          `Le fichier est trop volumineux. Maximum: ${this.formatFileSize(maxSize)}`
        );
        return;
      }
      
      console.log(
        '📤 [Component] Upload fichier:', 
        file.name, 
        this.formatFileSize(file.size)
      );
      
      this.store.dispatch(AssistantActions.uploadFile({ file }));
      input.value = ''; // Reset input
    }
  }
  
  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = true;
  }
  
  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = false;
  }
  
  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = false;
    
    if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
      const file = event.dataTransfer.files[0];
      
      const maxSize = 50 * 1024 * 1024;
      if (file.size > maxSize) {
        this.showError(
          `Le fichier est trop volumineux. Maximum: ${this.formatFileSize(maxSize)}`
        );
        return;
      }
      
      console.log('📤 [Component] Drop fichier:', file.name);
      this.store.dispatch(AssistantActions.uploadFile({ file }));
    }
  }
  
  triggerFileInput(): void {
    this.fileInput?.nativeElement?.click();
  }
  
  // ==================== CHAT ====================
  
  sendMessage(message?: string): void {
    const messageToSend = message !== undefined ? message : this.currentMessage;
    const trimmedMessage = messageToSend?.trim();
    
    if (!trimmedMessage) {
      console.warn('⚠️ [Component] Message vide, envoi annulé');
      return;
    }
    
    console.log(
      '📤 [Component] Envoi message:', 
      trimmedMessage.substring(0, 50) + (trimmedMessage.length > 50 ? '...' : '')
    );
    
    // Dispatch l'action sendMessage
    this.store.dispatch(AssistantActions.sendMessage({ 
      message: trimmedMessage 
    }));
    
    // Réinitialiser l'input
    this.currentMessage = '';
    this.updateCurrentMessage('');
    
    // Focus sur l'input après envoi
    setTimeout(() => this.focusInput(), 100);
  }
  
  updateCurrentMessage(message: string): void {
    this.currentMessage = message;
    this.store.dispatch(AssistantActions.setCurrentMessage({ message }));
  }
  
  /**
   * ✅ Modifier onKeyDown pour désactiver Enter pendant enregistrement
   */
  onKeyDown(event: KeyboardEvent): void {
    // Empêcher l'envoi pendant l'enregistrement
    if (this.isRecording) {
      event.preventDefault();
      return;
    }
    
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
    
    if (event.key === 'Escape') {
      this.currentMessage = '';
      this.updateCurrentMessage('');
    }
  }
  
  // ==================== ACTIONS ====================
  
  clearChat(): void {
    if (confirm('Voulez-vous vraiment effacer tout l\'historique de conversation ?')) {
      console.log('🗑️ [Component] Effacement de l\'historique');
      this.store.dispatch(AssistantActions.clearMessages());
      this.lastMessageCount = 0;
    }
  }
  
  clearFiles(): void {
    if (confirm('Voulez-vous vraiment supprimer tous les fichiers uploadés ?')) {
      console.log('🗑️ [Component] Suppression des fichiers');
      this.store.dispatch(AssistantActions.clearFiles());
    }
  }
  
  deleteMessage(messageId: string): void {
    if (confirm('Voulez-vous supprimer ce message ?')) {
      console.log('🗑️ [Component] Suppression du message:', messageId);
      this.store.dispatch(AssistantActions.removeMessage({ messageId }));
    }
  }
  
  regenerateLastResponse(): void {
    this.messages$
      .pipe(take(1))
      .subscribe(messages => {
        // Trouver le dernier message utilisateur
        const lastUserMessage = [...messages]
          .reverse()
          .find(m => m.sender === 'user');
        
        if (lastUserMessage) {
          console.log('🔄 [Component] Régénération de la réponse');
          this.store.dispatch(AssistantActions.sendMessage({ 
            message: lastUserMessage.content 
          }));
        } else {
          console.warn('⚠️ [Component] Aucun message utilisateur trouvé');
        }
      });
  }
  
  exportHistory(): void {
    this.messages$
      .pipe(take(1))
      .subscribe(messages => {
        const dataStr = JSON.stringify(messages, null, 2);
        const dataBlob = new Blob([dataStr], { type: 'application/json' });
        const url = URL.createObjectURL(dataBlob);
        
        const link = document.createElement('a');
        link.href = url;
        link.download = `chat-history-${new Date().toISOString()}.json`;
        link.click();
        
        URL.revokeObjectURL(url);
        console.log('✅ [Component] Historique exporté');
      });
  }
  
  // ==================== UTILITY METHODS ====================
  
  private scrollToBottom(): void {
    try {
      if (this.chatContainer?.nativeElement) {
        const element = this.chatContainer.nativeElement;
        element.scrollTo({
          top: element.scrollHeight,
          behavior: 'smooth'
        });
        console.log('📜 [Component] Scroll to bottom:', element.scrollHeight);
      }
    } catch (err) {
      console.error('❌ [Component] Erreur lors du scroll:', err);
    }
  }
  
  private showError(message: string): void {
    // Utiliser alert pour la simplicité, remplacer par une toast si disponible
    alert(message);
    console.error('❌ [Component]', message);
  }
  
  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
  }
  
  formatTime(date: Date | string): string {
    const d = new Date(date);
    return d.toLocaleTimeString('fr-FR', { 
      hour: '2-digit', 
      minute: '2-digit' 
    });
  }
  
  formatDate(date: Date | string): string {
    const d = new Date(date);
    return d.toLocaleDateString('fr-FR', { 
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
  
  getFileIcon(filename: string): string {
    const extension = filename.split('.').pop()?.toLowerCase();
    const icons: { [key: string]: string } = {
      'pdf': '📕',
      'doc': '📘',
      'docx': '📘',
      'txt': '📄',
      'md': '📝',
      'jpg': '🖼️',
      'jpeg': '🖼️',
      'png': '🖼️',
      'gif': '🖼️',
      'webp': '🖼️',
      'xlsx': '📊',
      'xls': '📊',
      'csv': '📊',
      'pptx': '📊',
      'ppt': '📊',
      'zip': '📦',
      'rar': '📦'
    };
    return icons[extension || ''] || '📁';
  }
  
  copyMessage(content: string): void {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(content)
        .then(() => {
          console.log('✅ [Component] Message copié');
          // Optionnel: afficher une notification
        })
        .catch(err => {
          console.error('❌ [Component] Erreur lors de la copie:', err);
        });
    } else {
      console.warn('⚠️ [Component] Clipboard API non disponible');
    }
  }
  
  focusInput(): void {
    this.messageInput?.nativeElement?.focus();
  }
  
  // ==================== TRACK BY FUNCTIONS ====================
  
  /**
   * ✅ IMPORTANT: TrackBy pour optimiser le rendu des messages
   */
  trackByMessageId(index: number, message: Message): string {
    return message.id;
  }
  
  /**
   * ✅ IMPORTANT: TrackBy pour optimiser le rendu des fichiers
   */
  trackByFileId(index: number, file: UploadedFile): string {
    return file.id;
  }

  // ==================== VOICE CONTROL HANDLERS ====================
  /**
   * ✅ Gère la transcription finale (auto-envoi)
   */
  onVoiceTranscriptFinal(transcript: string): void {
    console.log('🎤 [Component] Transcription Whisper reçue:', transcript);
    
    if (!transcript || !transcript.trim()) {
      console.warn('⚠️ [Component] Transcription vide');
      return;
    }
    
    // Mettre à jour le message et envoyer
    this.currentMessage = transcript.trim();
    this.sendMessage();
  }
  
  /**
   * ✅ Gère l'état d'enregistrement
   */
  onRecordingChange(isRecording: boolean): void {
    this.isRecording = isRecording;
    console.log('🎤 [Component] État enregistrement:', isRecording);
  }
  
  /**
   * ✅ Gère les erreurs vocales
   */
  onVoiceError(error: string): void {
    console.error('❌ [Component] Erreur vocale:', error);
    // Afficher l'erreur à l'utilisateur (toast, alert, etc.)
    alert(`Erreur: ${error}`);
  }
  
  /**
   * ✅ Arrête l'enregistrement
   */
  stopListening(): void {
    this.voiceButton?.stopRecognition();
    this.isRecording = false;
  }

  getPlaceholder(): string {
    if (this.isRecording) {
      return '🎤 Parlez maintenant...';
    }
    return 'Posez votre question ou utilisez le micro...';
}
}

// ============================================================================
// NOTES IMPORTANTES
// ============================================================================

/*
 * ✅ AVANTAGES de cette implémentation:
 * 
 * 1. INTERFACE COMPACTE
 *    - Bouton micro directement dans l'input-group
 *    - Pas de barre supplémentaire
 *    - Design épuré
 * 
 * 2. UX OPTIMALE
 *    - Indicateur visuel pendant l'écoute (alerte rouge)
 *    - Transcription en temps réel dans le textarea
 *    - Envoi automatique après transcription finale
 * 
 * 3. FEEDBACK CLAIR
 *    - Animation du bouton pendant l'écoute
 *    - Placeholder change pendant l'écoute
 *    - Bouton d'arrêt visible dans l'alerte
 * 
 * 4. ACCESSIBLE
 *    - Fonctionne avec le clavier
 *    - Support Chrome/Edge uniquement
 *    - Message clair si non supporté
 */