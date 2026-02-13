// /* // ============================================================================
// // COMPONENT - assistant.component.ts (VERSION v3.0 - Progression temps réel)
// // ============================================================================
// import { 
//   Component, 
//   OnInit, 
//   ViewChild, 
//   ElementRef, 
//   AfterViewChecked, 
//   OnDestroy, 
//   HostListener
// } from '@angular/core';
// import { CommonModule } from '@angular/common';
// import { FormsModule } from '@angular/forms';
// import { Store } from '@ngrx/store';
// import { Observable, Subject } from 'rxjs';
// import { take, takeUntil, filter } from 'rxjs/operators';
// import { trigger, transition, style, animate } from '@angular/animations';

// import * as AssistantActions from './store/assistant.actions';
// import * as AssistantSelectors from './store/assistant.selectors';
// import { Message, UploadedFile } from './store/assistant.models';
// import { MarkdownModule } from 'ngx-markdown';
// import { VoiceButtonComponent } from './voice-control/voice-button.component';
// import { VoiceService } from './service/voice.service';
// import { NotificationService } from './service/notification.service';

// @Component({
//   selector: 'app-assistant',
//   standalone: true,
//   imports: [CommonModule, FormsModule, MarkdownModule, VoiceButtonComponent],
//   templateUrl: './assistant.component.html',
//   styleUrls: ['./assistant.component.scss'],
//   animations: [
//     trigger('slideIn', [
//       transition(':enter', [
//         style({ opacity: 0, transform: 'translateY(20px)' }),
//         animate('300ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))
//       ]),
//       transition(':leave', [
//         animate('200ms ease-in', style({ opacity: 0, transform: 'translateX(-20px)' }))
//       ])
//     ]),
//     trigger('fadeIn', [
//       transition(':enter', [
//         style({ opacity: 0 }),
//         animate('400ms ease-out', style({ opacity: 1 }))
//       ])
//     ]),
//     trigger('modalFade', [
//       transition(':enter', [
//         style({ opacity: 0 }),
//         animate('200ms ease-out', style({ opacity: 1 }))
//       ]),
//       transition(':leave', [
//         animate('150ms ease-in', style({ opacity: 0 }))
//       ])
//     ])
//   ]
// })
// export class AssistantComponent implements OnInit, AfterViewChecked, OnDestroy {

//   // ==================== VIEW CHILDREN ====================
//   @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;
//   @ViewChild('chatContainer') chatContainer!: ElementRef<HTMLDivElement>;
//   @ViewChild('messageInput') messageInput!: ElementRef<HTMLTextAreaElement>;
//   @ViewChild(VoiceButtonComponent) voiceButton?: VoiceButtonComponent;
  
//   // ==================== OBSERVABLES FROM STORE ====================
  
//   // Messages
//   messages$: Observable<Message[]>;
//   messagesLoading$: Observable<boolean>;
//   messagesError$: Observable<string | null>;
//   isStreaming$: Observable<boolean>;
//   streamingMessage$: Observable<Message | null | undefined>;
//   canSendMessage$: Observable<boolean>;
//   hasMessages$: Observable<boolean>;
//   messageStats$: Observable<any>;
  
//   // Files
//   files$: Observable<UploadedFile[]>;
//   filesUploading$: Observable<boolean>;
//   filesError$: Observable<string | null>;
//   hasFiles$: Observable<boolean>;
//   fileStats$: Observable<any>;
  
//   // Files par statut
//   pendingFiles$: Observable<UploadedFile[]>;
//   uploadingFiles$: Observable<UploadedFile[]>;
//   processingFiles$: Observable<UploadedFile[]>;
//   completedFiles$: Observable<UploadedFile[]>;
//   failedFiles$: Observable<UploadedFile[]>;
//   duplicateFiles$: Observable<UploadedFile[]>;
//   activeFiles$: Observable<UploadedFile[]>;
//   hasActiveFiles$: Observable<boolean>;
  
//   // Upload stats & progress
//   uploadStats$: Observable<any>;
//   uploadProgress$: Observable<any>;
//   uploadErrors$: Observable<any[]>;
  
//   // Duplicatas
//   showDuplicateModal$: Observable<boolean>;
//   currentDuplicateFile$: Observable<UploadedFile | null | undefined>;
  
//   // UI state
//   uiState$: Observable<any>;
//   uploadState$: Observable<any>;
//   conversationState$: Observable<any>;
  
//   // ✅ NOUVEAU : Dashboard stats
//   dashboardStats$: Observable<any>;
//   canUploadMore$: Observable<boolean>;
  
//   // Global
//   userId$: Observable<string>;
//   isSidebarOpen$: Observable<boolean>;
//   globalLoading$: Observable<boolean>;
//   globalError$: Observable<string | null>;
  
//   // ==================== LOCAL STATE ====================
//   dragOver = false;
//   currentMessage = '';
//   isVoiceEnabled = false;
//   isRecording = false;
//   showFileDropdown = false;
  
//   private shouldScrollToBottom = false;

//   private lastMessageCount = 0;
//   private destroy$ = new Subject<void>();
  
//   // ✅ NOUVEAU : Progress tracking
//   private progressUpdateCount = 0;
  
//   constructor(
//     private store: Store,
//     private voiceService: VoiceService,
//     private notificationService: NotificationService

//   ) {
//     // Messages
//     this.messages$ = this.store.select(AssistantSelectors.selectMessagesSorted);
//     this.messagesLoading$ = this.store.select(AssistantSelectors.selectMessagesLoading);
//     this.messagesError$ = this.store.select(AssistantSelectors.selectMessagesError);
//     this.isStreaming$ = this.store.select(AssistantSelectors.selectIsStreaming);
//     this.streamingMessage$ = this.store.select(AssistantSelectors.selectStreamingMessage);
//     this.canSendMessage$ = this.store.select(AssistantSelectors.selectCanSendMessage);
//     this.hasMessages$ = this.store.select(AssistantSelectors.selectHasMessages);
//     this.messageStats$ = this.store.select(AssistantSelectors.selectMessageStats);
    
//     // Files
//     this.files$ = this.store.select(AssistantSelectors.selectAllFiles);
//     this.filesUploading$ = this.store.select(AssistantSelectors.selectFilesUploading);
//     this.filesError$ = this.store.select(AssistantSelectors.selectFilesError);
//     this.hasFiles$ = this.store.select(AssistantSelectors.selectHasFiles);
//     this.fileStats$ = this.store.select(AssistantSelectors.selectFileStats);
    
//     // Files par statut
//     this.pendingFiles$ = this.store.select(AssistantSelectors.selectPendingFiles);
//     this.uploadingFiles$ = this.store.select(AssistantSelectors.selectUploadingFiles);
//     this.processingFiles$ = this.store.select(AssistantSelectors.selectProcessingFiles);
//     this.completedFiles$ = this.store.select(AssistantSelectors.selectCompletedFiles);
//     this.failedFiles$ = this.store.select(AssistantSelectors.selectFailedFiles);
//     this.duplicateFiles$ = this.store.select(AssistantSelectors.selectDuplicateFiles);
//     this.activeFiles$ = this.store.select(AssistantSelectors.selectActiveFiles);
//     this.hasActiveFiles$ = this.store.select(AssistantSelectors.selectHasActiveFiles);
    
//     // Stats & progress
//     this.uploadStats$ = this.store.select(AssistantSelectors.selectUploadStats);
//     this.uploadProgress$ = this.store.select(AssistantSelectors.selectUploadProgress);
//     this.uploadErrors$ = this.store.select(AssistantSelectors.selectUploadErrors);
    
//     // Duplicatas
//     this.showDuplicateModal$ = this.store.select(AssistantSelectors.selectShowDuplicateModal);
//     this.currentDuplicateFile$ = this.store.select(AssistantSelectors.selectCurrentDuplicateFile);
    
//     // States combinés
//     this.uiState$ = this.store.select(AssistantSelectors.selectUIState);
//     this.uploadState$ = this.store.select(AssistantSelectors.selectUploadState);
//     this.conversationState$ = this.store.select(AssistantSelectors.selectConversationState);
    
//     // ✅ NOUVEAU : Dashboard stats
//     this.dashboardStats$ = this.store.select(AssistantSelectors.selectDashboardStats);
//     this.canUploadMore$ = this.store.select(AssistantSelectors.selectCanUploadMore);
    
//     // Global
//     this.userId$ = this.store.select(AssistantSelectors.selectUserId);
//     this.isSidebarOpen$ = this.store.select(AssistantSelectors.selectIsSidebarOpen);
//     this.globalLoading$ = this.store.select(AssistantSelectors.selectGlobalLoading);
//     this.globalError$ = this.store.select(AssistantSelectors.selectGlobalError);
    
//     console.log('✅ [Component] AssistantComponent v3.0 initialisé');

//     // Vérifier support vocal
//     this.isVoiceEnabled = this.voiceService.isRecordingSupported();
//     console.log('✅ [Component] Support vocal:', this.isVoiceEnabled);
//   }
  
//   // ==================== LIFECYCLE HOOKS ====================
  
//   ngOnInit(): void {
//     console.log('🚀 [Component] ngOnInit - Chargement des données');
    
//     // Charger les données au démarrage
//     this.store.dispatch(AssistantActions.loadMessagesFromStorage());
//     this.store.dispatch(AssistantActions.loadFilesFromStorage());
    
//     // S'abonner aux messages
//     this.messages$
//       .pipe(takeUntil(this.destroy$))
//       .subscribe(messages => {
//         console.log('📨 [Component] Messages reçus:', messages.length);
        
//         // Debug ordre (dev only)
//         if (messages.length > 0 && messages.length <= 10) {
//           messages.forEach((msg, i) => {
//             console.log(
//               `  [${i}] ${msg.sender} (seq: ${msg.sequence}): ${msg.content.substring(0, 30)}...`
//             );
//           });
//         }
        
//         // Auto-scroll si nouveau message
//         if (messages.length > this.lastMessageCount) {
//           this.shouldScrollToBottom = true;
//           this.lastMessageCount = messages.length;
//         }
//       });
    
//     // ✅ NOUVEAU : Observer progression uploads en temps réel
//     this.uploadProgress$
//       .pipe(
//         takeUntil(this.destroy$),
//         filter(progress => progress.overallProgress > 0)
//       )
//       .subscribe(progress => {
//         this.progressUpdateCount++;
        
//         // Log throttlé (tous les 10 updates)
//         if (this.progressUpdateCount % 10 === 0) {
//           console.log('📊 [Component] Progression globale:', progress.overallProgress + '%', {
//             total: progress.totalFiles,
//             completed: progress.completedFiles,
//             failed: progress.failedFiles
//           });
//         }
//       });
    
//     // ✅ AMÉLIORÉ : Observer fichiers actifs
//     this.activeFiles$
//       .pipe(takeUntil(this.destroy$))
//       .subscribe(files => {
//         if (files.length > 0) {
//           console.log('📤 [Component] Uploads actifs:', files.length, files.map(f => ({
//             name: f.name,
//             status: f.status,
//             progress: f.progress ?? 0
//           })));
//         }
//       });
    
//     // Gérer les erreurs
//     this.messagesError$
//       .pipe(
//         takeUntil(this.destroy$),
//         filter(error => error !== null)
//       )
//       .subscribe(error => {
//         console.error('❌ [Component] Message error:', error);
//         this.showError('Erreur de message: ' + error);
//       });
    
//     this.filesError$
//       .pipe(
//         takeUntil(this.destroy$),
//         filter(error => error !== null)
//       )
//       .subscribe(error => {
//         console.error('❌ [Component] File error:', error);
//         this.showError('Erreur de fichier: ' + error);
//       });

//     this.globalError$
//       .pipe(
//         takeUntil(this.destroy$),
//         filter(error => error !== null)
//       )
//       .subscribe(error => {
//         console.error('❌ [Component] Global error:', error);
//         this.showError(error!);
//       });
//   }
  
//   ngAfterViewChecked(): void {
//     if (this.shouldScrollToBottom) {
//       setTimeout(() => {
//         this.scrollToBottom();
//         this.shouldScrollToBottom = false;
//       }, 100);
//     }
//   }
  
//   ngOnDestroy(): void {
//     console.log('🔌 [Component] ngOnDestroy - Nettoyage');
//     this.destroy$.next();
//     this.destroy$.complete();
//   }
  
//   // ==================== FILE UPLOAD ====================
  
//   /**
//    * ✅ AMÉLIORÉ : Upload avec validation et reset input
//    */
//   onFileSelected(event: Event): void {
//     const input = event.target as HTMLInputElement;
//     if (!input.files || input.files.length === 0) {
//       return;
//     }
    
//     const files = Array.from(input.files);
    
//     console.log('📤 [Component] Fichiers sélectionnés:', files.length);
    
//     // Validation
//     const maxSize = 20 * 1024 * 1024; // 20MB
//     const invalidFiles = files.filter(f => f.size > maxSize);
    
//     if (invalidFiles.length > 0) {
//       this.showError(
//         `${invalidFiles.length} fichier(s) trop volumineux (max ${this.formatFileSize(maxSize)})`
//       );
//       input.value = ''; // Reset
//       return;
//     }
    
//     // ✅ Check limite uploads concurrents
//     this.canUploadMore$.pipe(take(1)).subscribe(canUpload => {
//       if (!canUpload) {
//         this.showError('Trop d\'uploads en cours. Veuillez patienter.');
//         input.value = ''; // Reset
//         return;
//       }
      
//       // Upload
//       if (files.length === 1) {
//         this.store.dispatch(AssistantActions.uploadFile({ file: files[0] }));
//       } else {
//         this.store.dispatch(AssistantActions.uploadMultipleFiles({ files }));
//       }
      
//       // ✅ IMPORTANT: Reset input pour permettre re-sélection du même fichier
//       input.value = '';
//     });
//   }
  
//   /**
//    * ✅ AMÉLIORÉ : Drop avec validation
//    */
//   onDrop(event: DragEvent): void {
//     event.preventDefault();
//     event.stopPropagation();
//     this.dragOver = false;
    
//     if (!event.dataTransfer?.files || event.dataTransfer.files.length === 0) {
//       return;
//     }
    
//     const files = Array.from(event.dataTransfer.files);
    
//     console.log('📤 [Component] Fichiers droppés:', files.length);
    
//     const maxSize = 20 * 1024 * 1024;
//     const invalidFiles = files.filter(f => f.size > maxSize);
    
//     if (invalidFiles.length > 0) {
//       this.showError(
//         `${invalidFiles.length} fichier(s) trop volumineux (max ${this.formatFileSize(maxSize)})`
//       );
//       return;
//     }
    
//     // Check limite
//     this.canUploadMore$.pipe(take(1)).subscribe(canUpload => {
//       if (!canUpload) {
//         this.showError('Trop d\'uploads en cours. Veuillez patienter.');
//         return;
//       }
      
//       if (files.length === 1) {
//         this.store.dispatch(AssistantActions.uploadFile({ file: files[0] }));
//       } else {
//         this.store.dispatch(AssistantActions.uploadMultipleFiles({ files }));
//       }
//     });
//   }
  
//   onDragOver(event: DragEvent): void {
//     event.preventDefault();
//     event.stopPropagation();
//     this.dragOver = true;
//   }
  
//   onDragLeave(event: DragEvent): void {
//     event.preventDefault();
//     event.stopPropagation();
//     this.dragOver = false;
//   }
  
//   /**
//    * ✅ AMÉLIORÉ : Trigger input seulement si possible
//    */
//   triggerFileInput(): void {
//     this.canUploadMore$.pipe(take(1)).subscribe(canUpload => {
//       if (!canUpload) {
//         this.showError('Trop d\'uploads en cours. Veuillez patienter.');
//         return;
//       }
      
//       this.fileInput?.nativeElement?.click();
//     });
//   }
  
//   // ==================== FILE ACTIONS ====================
  
//   removeFile(fileId: string): void {
//     if (confirm('Supprimer ce fichier ?')) {
//       console.log('🗑️ [Component] Suppression fichier:', fileId);
//       this.store.dispatch(AssistantActions.removeFile({ fileId }));
//     }
//   }
  
//   /**
//    * ✅ AMÉLIORÉ : Retry avec vérification
//    */
//   retryFailedUpload(file: UploadedFile): void {
//     console.log('🔄 [Component] Retry upload:', file.name);
    
//     this.store.dispatch(AssistantActions.retryFailedUpload({ file }));
    
//     // Afficher message
//     setTimeout(() => {
//       this.showError('Veuillez re-sélectionner le fichier pour le ré-uploader');
//     }, 100);
//   }
  
//   clearFiles(): void {
//     this.fileStats$.pipe(take(1)).subscribe(stats => {
//       if (stats.total === 0) {
//         return;
//       }
      
//       const message = `Supprimer ${stats.total} fichier(s) ?`;
//       if (confirm(message)) {
//         console.log('🗑️ [Component] Suppression tous fichiers');
//         this.store.dispatch(AssistantActions.clearFiles());
//       }
//     });
//   }
  
//   clearCompletedFiles(): void {
//     this.completedFiles$.pipe(take(1)).subscribe(files => {
//       if (files.length === 0) {
//         this.showError('Aucun fichier terminé à supprimer');
//         return;
//       }
      
//       if (confirm(`Supprimer ${files.length} fichier(s) terminé(s) ?`)) {
//         console.log('🗑️ [Component] Suppression fichiers terminés');
//         this.store.dispatch(AssistantActions.clearCompletedFiles());
//       }
//     });
//   }
  
//   // ==================== DUPLICATE MANAGEMENT ====================
  
//   useDuplicateFile(fileId: string, existingJobId: string): void {
//     console.log('✅ [Component] Utilisation duplicata:', existingJobId);
//     this.store.dispatch(AssistantActions.useDuplicateFile({ 
//       fileId, 
//       existingJobId 
//     }));
//   }
  
//   forceReupload(fileId: string): void {
//     console.log('🔄 [Component] Force reupload:', fileId);
//     this.store.dispatch(AssistantActions.forceReupload({ fileId }));
//   }
  
//   dismissDuplicate(fileId: string): void {
//     console.log('✖️ [Component] Dismiss duplicata:', fileId);
//     this.store.dispatch(AssistantActions.dismissDuplicate({ fileId }));
//   }
  
//   closeDuplicateModal(): void {
//     this.store.dispatch(AssistantActions.closeDuplicateModal());
//   }
  
//   // ==================== CHAT ====================
  
//   sendMessage(message?: string): void {
//     const messageToSend = message !== undefined ? message : this.currentMessage;
//     const trimmedMessage = messageToSend?.trim();
    
//     if (!trimmedMessage) {
//       console.warn('⚠️ [Component] Message vide, envoi annulé');
//       return;
//     }
    
//     console.log(
//       '📤 [Component] Envoi message:', 
//       trimmedMessage.substring(0, 50) + (trimmedMessage.length > 50 ? '...' : '')
//     );
    
//     this.store.dispatch(AssistantActions.sendMessage({ 
//       message: trimmedMessage 
//     }));
    
//     this.currentMessage = '';
//     this.updateCurrentMessage('');
    
//     setTimeout(() => this.focusInput(), 100);
//   }
  
//   updateCurrentMessage(message: string): void {
//     this.currentMessage = message;
//     this.store.dispatch(AssistantActions.setCurrentMessage({ message }));
//   }
  
//   onKeyDown(event: KeyboardEvent): void {
//     if (this.isRecording) {
//       event.preventDefault();
//       return;
//     }
    
//     if (event.key === 'Enter' && !event.shiftKey) {
//       event.preventDefault();
//       this.sendMessage();
//     }
    
//     if (event.key === 'Escape') {
//       this.currentMessage = '';
//       this.updateCurrentMessage('');
//     }
//   }
  
//   // ==================== ACTIONS ====================
  
//   clearChat(): void {
//     this.messageStats$.pipe(take(1)).subscribe(stats => {
//       if (stats.total === 0) {
//         return;
//       }
      
//       const message = `Effacer ${stats.total} message(s) ?`;
//       if (confirm(message)) {
//         console.log('🗑️ [Component] Effacement historique');
//         this.store.dispatch(AssistantActions.clearMessages());
//         this.lastMessageCount = 0;
//       }
//     });
//   }
  
//   deleteMessage(messageId: string): void {
//     if (confirm('Supprimer ce message ?')) {
//       console.log('🗑️ [Component] Suppression message:', messageId);
//       this.store.dispatch(AssistantActions.removeMessage({ messageId }));
//     }
//   }
  
//   regenerateLastResponse(): void {
//     this.messages$
//       .pipe(take(1))
//       .subscribe(messages => {
//         const lastUserMessage = [...messages]
//           .reverse()
//           .find(m => m.sender === 'user');
        
//         if (lastUserMessage) {
//           console.log('🔄 [Component] Régénération réponse');
//           this.store.dispatch(AssistantActions.sendMessage({ 
//             message: lastUserMessage.content 
//           }));
//         } else {
//           console.warn('⚠️ [Component] Aucun message utilisateur');
//         }
//       });
//   }
  
//   exportHistory(): void {
//     this.messages$
//       .pipe(take(1))
//       .subscribe(messages => {
//         if (messages.length === 0) {
//           this.showError('Aucun message à exporter');
//           return;
//         }
        
//         const dataStr = JSON.stringify(messages, null, 2);
//         const dataBlob = new Blob([dataStr], { type: 'application/json' });
//         const url = URL.createObjectURL(dataBlob);
        
//         const link = document.createElement('a');
//         link.href = url;
//         link.download = `chat-history-${new Date().toISOString()}.json`;
//         link.click();
        
//         URL.revokeObjectURL(url);
//         console.log('✅ [Component] Historique exporté');
//       });
//   }
  
//   toggleSidebar(): void {
//     this.store.dispatch(AssistantActions.toggleSidebar());
//   }
  
//   clearGlobalError(): void {
//     this.store.dispatch(AssistantActions.clearGlobalError());
//   }
  
//   // ==================== VOICE CONTROL ====================
  
//   onVoiceTranscriptFinal(transcript: string): void {
//     console.log('🎤 [Component] Transcription Whisper:', transcript);
    
//     if (!transcript || !transcript.trim()) {
//       console.warn('⚠️ [Component] Transcription vide');
//       return;
//     }
    
//     // Auto-remplir et envoyer
//     this.currentMessage = transcript.trim();
//     this.sendMessage();
//   }
  
//   onRecordingChange(isRecording: boolean): void {
//     this.isRecording = isRecording;
//     console.log('🎤 [Component] État enregistrement:', isRecording);
//   }
  
//   onVoiceError(error: string): void {
//     console.error('❌ [Component] Erreur vocale:', error);
//     this.showError(`Erreur vocale: ${error}`);
//   }
  
//   stopListening(): void {
//     if (this.voiceButton) {
//       this.voiceButton.stopRecognition();
//     }
//     this.isRecording = false;
//   }
  
//   // ==================== UTILITY METHODS ====================
  
//   private scrollToBottom(): void {
//     try {
//       if (this.chatContainer?.nativeElement) {
//         const element = this.chatContainer.nativeElement;
//         element.scrollTo({
//           top: element.scrollHeight,
//           behavior: 'smooth'
//         });
//         console.log('📜 [Component] Scroll to bottom');
//       }
//     } catch (err) {
//       console.error('❌ [Component] Erreur scroll:', err);
//     }
//   }
  
//   private showError(message: string): void {
//     // TODO: Remplacer par un toast/notification
//     alert(message);
//     console.error('❌ [Component]', message);
//   }
  
//   formatFileSize(bytes: number): string {
//     if (bytes === 0) return '0 B';
//     const k = 1024;
//     const sizes = ['B', 'KB', 'MB', 'GB'];
//     const i = Math.floor(Math.log(bytes) / Math.log(k));
//     return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
//   }
  
//   formatTime(date: Date | string): string {
//     const d = new Date(date);
//     return d.toLocaleTimeString('fr-FR', { 
//       hour: '2-digit', 
//       minute: '2-digit' 
//     });
//   }
  
//   formatDate(date: Date | string): string {
//     const d = new Date(date);
//     return d.toLocaleDateString('fr-FR', { 
//       day: '2-digit',
//       month: 'short',
//       year: 'numeric',
//       hour: '2-digit',
//       minute: '2-digit'
//     });
//   }
  
//   getFileIcon(filename: string): string {
//     const extension = filename.split('.').pop()?.toLowerCase();
//     const icons: { [key: string]: string } = {
//       'pdf': '📕',
//       'doc': '📘',
//       'docx': '📘',
//       'txt': '📄',
//       'md': '📝',
//       'jpg': '🖼️',
//       'jpeg': '🖼️',
//       'png': '🖼️',
//       'gif': '🖼️',
//       'webp': '🖼️',
//       'xlsx': '📊',
//       'xls': '📊',
//       'csv': '📊',
//       'pptx': '📊',
//       'ppt': '📊',
//       'zip': '📦',
//       'rar': '📦'
//     };
//     return icons[extension || ''] || '📁';
//   }
  
//   getStatusIcon(status: string): string {
//     const icons: { [key: string]: string } = {
//       'pending': '○',
//       'uploading': '↑',
//       'processing': '⟳',
//       'completed': '✓',
//       'failed': '✗',
//       'duplicate': '⚠'
//     };
//     return icons[status] || '○';
//   }
  
//   getStatusColor(status: string): string {
//     const colors: { [key: string]: string } = {
//       'pending': 'secondary',
//       'uploading': 'info',
//       'processing': 'primary',
//       'completed': 'success',
//       'failed': 'danger',
//       'duplicate': 'warning'
//     };
//     return colors[status] || 'secondary';
//   }
  
//   copyMessage(content: string): void {
//     if (navigator.clipboard) {
//       navigator.clipboard.writeText(content)
//         .then(() => {
//           console.log('✅ [Component] Message copié');
//           // TODO: Afficher toast success
//         })
//         .catch(err => {
//           console.error('❌ [Component] Erreur copie:', err);
//           this.showError('Erreur lors de la copie');
//         });
//     } else {
//       console.warn('⚠️ [Component] Clipboard API non disponible');
//       this.showError('Copie non supportée par votre navigateur');
//     }
//   }
  
//   focusInput(): void {
//     this.messageInput?.nativeElement?.focus();
//   }
  
//   getPlaceholder(): string {
//     if (this.isRecording) {
//       return '🎤 Enregistrement en cours...';
//     }
//     return 'Posez votre question ou utilisez le micro...';
//   }
  
//   // ==================== TRACK BY FUNCTIONS ====================
  
//   trackByMessageId(index: number, message: Message): string {
//     return message.id;
//   }
  
//   trackByFileId(index: number, file: UploadedFile): string {
//     return file.id;
//   }

//   // =======================
//     toggleFileDropdown(): void {
//     this.showFileDropdown = !this.showFileDropdown;
//   }
  
//   closeFileDropdown(): void {
//     this.showFileDropdown = false;
//   }
  
//   // Fermer dropdown au clic extérieur
//   @HostListener('document:click', ['$event'])
//   onDocumentClick(event: Event): void {
//     const target = event.target as HTMLElement;
//     if (!target.closest('.file-dropdown')) {
//       this.showFileDropdown = false;
//     }
//   }
// } */