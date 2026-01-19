// ============================================================================
// EFFECTS - assistant.effects.ts (VERSION ADAPTÉE ET OPTIMISÉE)
// ============================================================================
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { of, concat } from 'rxjs';
import { catchError, endWith, exhaustMap, map, tap, withLatestFrom } from 'rxjs/operators';

import * as AssistantActions from './assistant.actions';
import { AssistantApiService } from '../assistant-api.service';
import { selectUserId, selectAllMessages, selectAllFiles } from './assistant.selectors';

@Injectable()
export class AssistantEffects {
  
  private actions$ = inject(Actions);
  private store = inject(Store);
  private apiService = inject(AssistantApiService);

  // ==================== SEND MESSAGE WITH STREAMING ====================
  
  /**
   * ✅ ADAPTÉ : Gestion du streaming SSE
   * - exhaustMap empêche les envois multiples pendant un streaming
   * - Le contenu est cumulatif (pas de delta)
   * - Gestion complète des erreurs
   */
  sendMessageStream$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.sendMessage),
      withLatestFrom(this.store.select(selectUserId)),
      exhaustMap(([action, userId]) => {
        console.log('💬 [Effects] Envoi message:', action.message);

        // ✅ Timestamps distincts pour user et assistant
        const userTimestamp = new Date();
        const assistantTimestamp = new Date(userTimestamp.getTime() + 1);
        
        // ✅ Génération des IDs
        const userMessageId = this.generateMessageId('user');
        const assistantMessageId = this.generateMessageId('assistant');

        // ✅ Message utilisateur
        const userMessage = {
          id: userMessageId,
          content: action.message,
          sender: 'user' as const,
          timestamp: userTimestamp,
          sequence: 0 // Sera assigné par le reducer
        };

        // ✅ Message assistant (placeholder avec loading)
        const assistantMessage = {
          id: assistantMessageId,
          content: '',
          sender: 'assistant' as const,
          timestamp: assistantTimestamp,
          isLoading: true,      // Placeholder visible
          isStreaming: false,   // Pas encore de streaming
          sequence: 0           // Sera assigné par le reducer
        };

        console.log('✅ [Effects] Messages créés:', {
          userMessageId,
          assistantMessageId
        });

        // ✅ Séquence d'actions
        return concat(
          // 1. Ajouter le message utilisateur
          of(AssistantActions.addUserMessage({ message: userMessage })),
          
          // 2. Ajouter le message assistant vide (loading)
          of(AssistantActions.addAssistantMessage({ message: assistantMessage })),
          
          // 3. Démarrer le streaming
          of(AssistantActions.startStreaming({ messageId: assistantMessageId })),

          // 4. Stream SSE du contenu
          this.apiService.sendMessageStream(userId, action.message).pipe(
            // ✅ Le contenu reçu est CUMULATIF
            map(cumulativeContent => {
              console.log('📥 [Effects] Contenu reçu:', cumulativeContent.substring(0, 50) + '...');
              
              return AssistantActions.updateMessageContent({
                messageId: assistantMessageId,
                content: cumulativeContent
              });
            }),

            // ✅ Arrêter le streaming à la fin
            endWith(
              AssistantActions.stopStreaming({ messageId: assistantMessageId })
            ),

            // ✅ Gestion des erreurs
            catchError((error) => {
              console.error('❌ [Effects] Erreur streaming:', error);

              const errorMessage = this.getErrorMessage(error);

              return of(
                // Afficher le message d'erreur
                AssistantActions.updateMessageContent({
                  messageId: assistantMessageId,
                  content: `❌ Erreur: ${errorMessage}`
                }),
                // Arrêter le streaming
                AssistantActions.stopStreaming({ messageId: assistantMessageId }),
                // Dispatch l'action d'échec
                AssistantActions.sendMessageFailure({ error: errorMessage })
              );
            })
          )
        );
      })
    )
  );
  
  // ==================== LOAD MESSAGES ====================
  
  /**
   * ✅ Charge les messages depuis localStorage au démarrage
   */
  loadMessages$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.loadMessagesFromStorage),
      map(() => {
        const STORAGE_KEY = 'assistant_messages';
        
        try {
          const stored = localStorage.getItem(STORAGE_KEY);
          
          if (stored) {
            const messages = JSON.parse(stored);
            
            // ✅ Validation des messages
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
  
  /**
   * ✅ Sauvegarde automatique des messages après chaque modification
   */
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
            // ✅ Ne sauvegarder que les messages complets (pas en streaming)
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
  
  // ==================== UPLOAD FILE ====================
  
  /**
   * ✅ Upload d'un fichier vers le backend
   */
  uploadFile$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AssistantActions.uploadFile),
      exhaustMap((action) => {
        const fileId = this.generateFileId();
        console.log('📤 [Effects] Upload fichier:', action.file.name);
        
        return this.apiService.uploadFile(action.file).pipe(
          map(response => {
            if (response.success) {
              console.log('✅ [Effects] Fichier uploadé:', response.filename);
              
              return AssistantActions.uploadFileSuccess({
                file: {
                  id: fileId,
                  name: response.filename,
                  size: response.size,
                  uploadDate: new Date(),
                  status: 'success',
                  progress: 100
                }
              });
            } else {
              return AssistantActions.uploadFileFailure({
                fileId,
                error: response.error || 'Erreur upload'
              });
            }
          }),
          catchError((error) => {
            console.error('❌ [Effects] Erreur upload:', error);
            return of(AssistantActions.uploadFileFailure({
              fileId,
              error: this.getErrorMessage(error)
            }));
          })
        );
      })
    )
  );
  
  // ==================== LOAD FILES ====================
  
  /**
   * ✅ Charge les fichiers depuis localStorage
   */
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
  
  /**
   * ✅ Sauvegarde automatique des fichiers
   */
  saveFiles$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(
          AssistantActions.uploadFileSuccess,
          AssistantActions.clearFiles
        ),
        withLatestFrom(this.store.select(selectAllFiles)),
        tap(([, files]) => {
          const STORAGE_KEY = 'assistant_files';
          
          try {
            // ✅ Ne sauvegarder que les fichiers réussis
            const successFiles = files.filter(f => f.status === 'success');
            
            localStorage.setItem(STORAGE_KEY, JSON.stringify(successFiles));
            console.log('💾 [Effects] Fichiers sauvegardés:', successFiles.length);
          } catch (error) {
            console.error('❌ [Effects] Erreur sauvegarde fichiers:', error);
          }
        })
      ),
    { dispatch: false }
  );
  
  // ==================== HELPERS ====================
  
  /**
   * ✅ Génère un ID unique pour un message
   */
  private generateMessageId(prefix: 'user' | 'assistant'): string {
    return `${prefix}_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`;
  }
  
  /**
   * ✅ Génère un ID unique pour un fichier
   */
  private generateFileId(): string {
    return 'file_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
  }
  
  /**
   * ✅ Extrait le message d'erreur
   */
  private getErrorMessage(error: any): string {
    if (typeof error === 'string') return error;
    if (error?.error?.message) return error.error.message;
    if (error?.message) return error.message;
    if (error?.statusText) return error.statusText;
    return 'Une erreur est survenue';
  }
}