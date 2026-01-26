// ============================================================================
// EFFECTS - assistant.effects.ts (VERSION v3.2 - Avec Notifications)
// ============================================================================
import { Injectable, inject } from '@angular/core';
import { 
  HttpResponse, 
  HttpEvent, 
  HttpEventType, 
  HttpProgressEvent 
} from '@angular/common/http';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { of, concat, interval, EMPTY } from 'rxjs';
import { 
  catchError, 
  endWith, 
  exhaustMap, 
  map, 
  tap, 
  withLatestFrom, 
  switchMap,
  mergeMap,
  filter,
  takeUntil,
  take,
  delay
} from 'rxjs/operators';

import * as AssistantActions from './assistant.actions';
import { AssistantApiService } from '../service/assistant-api.service';
import { 
  selectUserId, 
  selectAllMessages, 
  selectAllFiles,
  selectFileById,
  selectPollingFileIds 
} from './assistant.selectors';
import { 
  generateFileId,
  UploadResponse
} from './assistant.models';
import { LIMITS } from './assistant.state';

@Injectable()
export class AssistantEffects {
  
  private actions$ = inject(Actions);
  private store = inject(Store);
  private apiService = inject(AssistantApiService);

  // ==================== SEND MESSAGE WITH STREAMING ====================
  
  /**
   * ✅ Gestion du streaming SSE
   */
  sendMessageStream$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.sendMessage),
      withLatestFrom(this.store.select(selectUserId)),
      exhaustMap(([action, userId]) => {
        console.log('💬 [Effects] Envoi message:', action.message);

        const userTimestamp = new Date();
        const assistantTimestamp = new Date(userTimestamp.getTime() + 1);
        
        const userMessageId = this.generateMessageId('user');
        const assistantMessageId = this.generateMessageId('assistant');

        const userMessage = {
          id: userMessageId,
          content: action.message,
          sender: 'user' as const,
          timestamp: userTimestamp,
          sequence: 0
        };

        const assistantMessage = {
          id: assistantMessageId,
          content: '',
          sender: 'assistant' as const,
          timestamp: assistantTimestamp,
          isLoading: true,
          isStreaming: false,
          sequence: 0
        };

        console.log('✅ [Effects] Messages créés:', {
          userMessageId,
          assistantMessageId
        });

        return concat(
          of(AssistantActions.addUserMessage({ message: userMessage })),
          of(AssistantActions.addAssistantMessage({ message: assistantMessage })),
          of(AssistantActions.startStreaming({ messageId: assistantMessageId })),

          this.apiService.sendMessageStream(userId, action.message).pipe(
            map(cumulativeContent => {
              console.log('📥 [Effects] Contenu reçu:', cumulativeContent.substring(0, 50) + '...');
              
              return AssistantActions.updateMessageContent({
                messageId: assistantMessageId,
                content: cumulativeContent
              });
            }),

            endWith(
              AssistantActions.stopStreaming({ messageId: assistantMessageId })
            ),

            catchError((error) => {
              console.error('❌ [Effects] Erreur streaming:', error);

              const errorMessage = this.getErrorMessage(error);

              return of(
                AssistantActions.updateMessageContent({
                  messageId: assistantMessageId,
                  content: `❌ Erreur: ${errorMessage}`
                }),
                AssistantActions.streamingError({ 
                  messageId: assistantMessageId, 
                  error: errorMessage 
                }),
                AssistantActions.stopStreaming({ messageId: assistantMessageId }),
                AssistantActions.sendMessageFailure({ error: errorMessage })
              );
            })
          )
        );
      })
    )
  );
  
  // ==================== FILE UPLOAD EFFECTS ====================
  
  /**
   * ✅ Upload avec progression HTTP temps réel
   */
  uploadFile$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.uploadFile),
      mergeMap((action) => {
        const fileId = generateFileId(action.file);
        console.log('📤 [Effects] Upload fichier:', action.file.name, 'ID:', fileId);
        
        return this.apiService.uploadFile(action.file, action.userId).pipe(
          tap((event: HttpEvent<UploadResponse>) => {
            if (event.type === HttpEventType.UploadProgress) {
              const progressEvent = event as HttpProgressEvent;
              if (progressEvent.total) {
                const progress = Math.round((100 * progressEvent.loaded) / progressEvent.total);
                console.log('📊 [Effects] Progression upload:', progress, '%');
                
                this.store.dispatch(AssistantActions.updateFileProgress({ 
                  fileId, 
                  progress 
                }));
              }
            }
          }),
          
          filter((event): event is HttpResponse<UploadResponse> => 
            event.type === HttpEventType.Response
          ),
          
          map(response => response.body!),
          
          map(responseBody => {
            console.log('📥 [Effects] Réponse upload:', responseBody);

            if (responseBody.duplicate && responseBody.duplicateInfo) {
              console.log('⚠️ [Effects] Duplicata détecté:', responseBody.duplicateInfo.jobId);
              
              return AssistantActions.uploadFileDuplicate({
                file: action.file,
                duplicateInfo: responseBody.duplicateInfo,
                existingJobId: responseBody.duplicateInfo.jobId
              });
            }

            console.log('✅ [Effects] Fichier uploadé, job ID:', responseBody.jobId);
            
            return AssistantActions.uploadFileSuccess({
              file: action.file,
              response: {
                jobId: responseBody.jobId,
                fileName: responseBody.fileName,
                fileSize: responseBody.fileSize,
                status: responseBody.status,
                duplicate: false
              }
            });
          }),
          
          catchError((error) => {
            console.error('❌ [Effects] Erreur upload:', error);
            return of(AssistantActions.uploadFileFailure({
              file: action.file,
              error: this.getErrorMessage(error)
            }));
          })
        );
      })
    )
  );

  /**
   * ✅ NOUVEAU : Notification upload réussi
   */
  notifyUploadFileSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.uploadFileSuccess),
      map(({ file }) => {
        console.log('🎉 [Effects] Notification upload réussi:', file.name);
        return AssistantActions.showNotification({
          message: `Fichier "${file.name}" uploadé avec succès`,
          notificationType: 'success',
          duration: 3000
        });
      })
    )
  );

  /**
   * ✅ NOUVEAU : Notification upload échoué
   */
  notifyUploadFileFailure$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.uploadFileFailure),
      map(({ file, error }) => {
        console.error('❌ [Effects] Notification échec upload:', file.name, error);
        return AssistantActions.showNotification({
          message: `Erreur lors de l'upload de "${file.name}": ${error}`,
          notificationType: 'error',
          duration: 5000
        });
      })
    )
  );

  /**
   * ✅ NOUVEAU : Notification duplicata détecté
   */
  notifyUploadFileDuplicate$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.uploadFileDuplicate),
      map(({ file }) => {
        console.warn('⚠️ [Effects] Notification duplicata:', file.name);
        return AssistantActions.showNotification({
          message: `Le fichier "${file.name}" a déjà été uploadé`,
          notificationType: 'warning',
          duration: 4000
        });
      })
    )
  );

  /**
   * ✅ Démarrer le polling après upload réussi
   */
  startPollingAfterUpload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.uploadFileSuccess),
      map(({ file, response }) => {
        const fileId = generateFileId(file);
        console.log('🔄 [Effects] Démarrage polling pour:', response.jobId);
        
        return AssistantActions.startPollingAfterUpload({ 
          fileId, 
          jobId: response.jobId 
        });
      })
    )
  );

  /**
   * ✅ Polling du statut avec progression temps réel
   */
pollUploadStatus$ = createEffect(() =>
  this.actions$.pipe(
    ofType(AssistantActions.startPollingAfterUpload),
    mergeMap(({ fileId, jobId }) => {
      console.log('🔄 [Effects] Polling statut pour job:', jobId);

      return interval(LIMITS.POLLING_INTERVAL).pipe(
        switchMap(() => 
          this.apiService.getUploadStatus(jobId).pipe(
            tap(status => {
              console.log('📊 [Effects] Statut polling:', status);
              
              if (status.progress !== undefined) {
                this.store.dispatch(AssistantActions.updateFileProgress({
                  fileId,
                  progress: status.progress
                }));
              }
            }),
            map(status => 
              AssistantActions.pollUploadStatusSuccess({
                fileId,
                jobId: status.jobId,
                status: status.status,
                progress: status.progress,
                message: status.message
              })
            ),
            catchError(error => {
              console.error('❌ [Effects] Erreur polling:', error);
              return of(AssistantActions.pollUploadStatusFailure({
                fileId,
                jobId,
                error: this.getErrorMessage(error)
              }));
            })
          )
        ),
        // ✅ Arrêter UNIQUEMENT sur completed/failed/error
        takeUntil(
          this.actions$.pipe(
            ofType(
              AssistantActions.pollUploadStatusSuccess,
              AssistantActions.pollUploadStatusFailure
            ),
            filter(action => 
              'jobId' in action && action.jobId === jobId &&
              (
                ('status' in action && (action.status === 'completed' || action.status === 'failed')) ||
                action.type === AssistantActions.pollUploadStatusFailure.type
              )
            )
          )
        )
      );
    })
  )
);

  /**
   * ✅ NOUVEAU : Notification traitement terminé
   */
  notifyPollUploadStatusCompleted$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.pollUploadStatusSuccess),
      filter(action => action.status === 'completed'),
      map(({ message }) => {
        console.log('✅ [Effects] Notification traitement terminé:', message);
        return AssistantActions.showNotification({
          message: message || 'Traitement du fichier terminé',
          notificationType: 'success',
          duration: 3000
        });
      })
    )
  );

  /**
   * ✅ NOUVEAU : Notification traitement échoué
   */
  notifyPollUploadStatusFailed$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.pollUploadStatusFailure),
      map(({ error }) => {
        console.error('❌ [Effects] Notification échec traitement:', error);
        return AssistantActions.showNotification({
          message: `Erreur de traitement: ${error}`,
          notificationType: 'error',
          duration: 5000
        });
      })
    )
  );

  /**
   * ✅ Forcer le re-upload d'un duplicata
   */
  forceReupload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.forceReupload),
      withLatestFrom(this.store),
      mergeMap(([{ fileId, userId }, state]) => {
        const file = selectFileById(fileId)(state);
        
        if (!file) {
          console.warn('⚠️ [Effects] Fichier non trouvé pour re-upload:', fileId);
          return of(AssistantActions.showNotification({
            message: 'Fichier introuvable',
            notificationType: 'error',
            duration: 3000
          }));
        }

        console.log('🔄 [Effects] Re-upload forcé:', file.name);
        
        return of(AssistantActions.showNotification({ 
          message: 'Veuillez re-sélectionner le fichier pour le re-uploader',
          notificationType: 'info',
          duration: 300000
        }));
      })
    )
  );

  /**
   * ✅ Upload multiple avec progression batch
   */
  uploadMultipleFiles$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.uploadMultipleFiles),
      mergeMap(({ files, userId }) => {
        console.log('📤 [Effects] Upload multiple:', files.length, 'fichiers');
        
        return concat(
          of(AssistantActions.showNotification({
            message: `Upload de ${files.length} fichier(s) en cours...`,
            notificationType: 'info',
            duration: 3000
          })),
          ...files.map(file => 
            of(AssistantActions.uploadFile({ file, userId }))
          )
        );
      })
    )
  );

  /**
   * ✅ Calculer la progression batch globale
   */
  updateBatchProgress$ = createEffect(() =>
    this.actions$.pipe(
      ofType(
        AssistantActions.uploadFileSuccess,
        AssistantActions.uploadFileFailure,
        AssistantActions.uploadFileDuplicate
      ),
      withLatestFrom(this.store.select(selectAllFiles)),
      map(([action, files]) => {
        const totalFiles = files.length;
        const completedFiles = files.filter(f => 
          f.status === 'completed' || 
          f.status === 'failed' || 
          f.status === 'duplicate'
        ).length;
        const failedFiles = files.filter(f => f.status === 'failed').length;
        const overallProgress = totalFiles > 0 
          ? Math.round((completedFiles / totalFiles) * 100) 
          : 0;

        return AssistantActions.updateBatchProgress({
          totalFiles,
          completedFiles,
          failedFiles,
          overallProgress
        });
      })
    )
  );

  // ==================== DUPLICATE MANAGEMENT EFFECTS ====================

  /**
   * ✅ Auto-afficher la modale de duplicata
   */
  showDuplicateModalAuto$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.uploadFileDuplicate),
      map(({ file }) => {
        const fileId = generateFileId(file);
        console.log('⚠️ [Effects] Affichage modale duplicata pour:', fileId);
        return AssistantActions.showDuplicateModal({ fileId });
      })
    )
  );

  /**
   * ✅ Utiliser un fichier duplicata existant
   */
  useDuplicateFile$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.useDuplicateFile),
      tap(({ fileId, existingJobId }) => {
        console.log('✅ [Effects] Utilisation fichier existant:', existingJobId);
      }),
      mergeMap(() => [
        AssistantActions.hideDuplicateModal(),
        AssistantActions.showNotification({
          message: 'Fichier existant utilisé avec succès',
          notificationType: 'success',
          duration: 3000
        })
      ])
    )
  );

  // ==================== RETRY FAILED UPLOAD ====================

  /**
   * ✅ Retry upload échoué
   */
  retryFailedUpload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.retryFailedUpload),
      map(({ file }) => {
        console.log('🔄 [Effects] Retry upload:', file.name);
        
        return AssistantActions.showNotification({ 
          message: 'Veuillez re-sélectionner le fichier pour réessayer',
          notificationType: 'info',
          duration: 4000
        });
      })
    )
  );

  // ==================== CHAT ERROR HANDLING ====================

  /**
   * ✅ NOUVEAU : Notification erreur de chat
   */
  notifyChatError$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.sendMessageFailure),
      map(({ error }) => {
        console.error('❌ [Effects] Notification erreur chat:', error);
        return AssistantActions.showNotification({
          message: `Erreur: ${error}`,
          notificationType: 'error',
          duration: 5000
        });
      })
    )
  );

  // ==================== LOAD MESSAGES ====================
  
  loadMessages$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.loadMessagesFromStorage),
      map(() => {
        const STORAGE_KEY = 'assistant_messages';
        
        try {
          const stored = localStorage.getItem(STORAGE_KEY);
          
          if (stored) {
            const messages = JSON.parse(stored);
            
            const validMessages = messages.filter((m: any) => 
              m && 
              m.id && 
              m.content !== undefined && 
              m.sender && 
              m.timestamp
            );
            
            console.log('📥 [Effects] Messages chargés:', validMessages.length);
            
            return AssistantActions.loadMessagesFromStorageSuccess({ 
              messages: validMessages 
            });
          }
        } catch (error) {
          console.error('❌ [Effects] Erreur chargement messages:', error);
        }
        
        return AssistantActions.loadMessagesFromStorageSuccess({ messages: [] });
      })
    )
  );
  
  // ==================== SAVE MESSAGES ====================
  
  saveMessages$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(
          AssistantActions.addUserMessage,
          AssistantActions.addAssistantMessage,
          AssistantActions.updateMessageContent,
          AssistantActions.stopStreaming,
          AssistantActions.removeMessage,
          AssistantActions.clearMessages
        ),
        withLatestFrom(this.store.select(selectAllMessages)),
        tap(([action, messages]) => {
          const STORAGE_KEY = 'assistant_messages';
          
          try {
            const messagesToSave = messages
              .filter(m => !m.isStreaming && !m.isLoading)
              .map(m => ({
                id: m.id,
                content: m.content,
                sender: m.sender,
                timestamp: m.timestamp,
                sequence: m.sequence
              }));
            
            localStorage.setItem(STORAGE_KEY, JSON.stringify(messagesToSave));
            console.log('💾 [Effects] Messages sauvegardés:', messagesToSave.length);
          } catch (error) {
            console.error('❌ [Effects] Erreur sauvegarde messages:', error);
          }
        })
      ),
    { dispatch: false }
  );
  
  // ==================== LOAD FILES ====================
  
  loadFiles$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.loadFilesFromStorage),
      map(() => {
        const STORAGE_KEY = 'assistant_files';
        
        try {
          const stored = localStorage.getItem(STORAGE_KEY);
          
          if (stored) {
            const files = JSON.parse(stored);
            console.log('📥 [Effects] Fichiers chargés:', files.length);
            return AssistantActions.loadFilesFromStorageSuccess({ files });
          }
        } catch (error) {
          console.error('❌ [Effects] Erreur chargement fichiers:', error);
        }
        
        return AssistantActions.loadFilesFromStorageSuccess({ files: [] });
      })
    )
  );
  
  // ==================== SAVE FILES ====================
  
  saveFiles$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(
          AssistantActions.uploadFileSuccess,
          AssistantActions.uploadFileDuplicate,
          AssistantActions.uploadFileFailure,
          AssistantActions.pollUploadStatusSuccess,
          AssistantActions.updateFileProgress,
          AssistantActions.removeFile,
          AssistantActions.clearFiles,
          AssistantActions.clearCompletedFiles
        ),
        withLatestFrom(this.store.select(selectAllFiles)),
        tap(([, files]) => {
          const STORAGE_KEY = 'assistant_files';
          
          try {
            const filesToSave = files
              .filter(f => f.status !== 'uploading' && f.status !== 'pending')
              .map(f => ({
                id: f.id,
                name: f.name,
                size: f.size,
                type: f.type,
                uploadDate: f.uploadDate,
                status: f.status,
                progress: f.progress,
                jobId: f.jobId,
                error: f.error,
                duplicateInfo: f.duplicateInfo,
                existingJobId: f.existingJobId
              }));
            
            localStorage.setItem(STORAGE_KEY, JSON.stringify(filesToSave));
            console.log('💾 [Effects] Fichiers sauvegardés:', filesToSave.length);
          } catch (error) {
            console.error('❌ [Effects] Erreur sauvegarde fichiers:', error);
          }
        })
      ),
    { dispatch: false }
  );
  
  // ==================== HELPERS ====================
  
  private generateMessageId(prefix: 'user' | 'assistant'): string {
    return `${prefix}_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  }
  
  private getErrorMessage(error: any): string {
    if (typeof error === 'string') return error;
    if (error?.error?.message) return error.error.message;
    if (error?.message) return error.message;
    if (error?.statusText) return error.statusText;
    return 'Une erreur est survenue';
  }
}