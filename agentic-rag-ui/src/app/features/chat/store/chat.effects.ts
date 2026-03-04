// features/chat/store/chat.effects.ts

import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { of } from 'rxjs';
import { 
  map, 
  switchMap, 
  catchError, 
  tap, 
  withLatestFrom 
} from 'rxjs/operators';
import { v4 as uuidv4 } from 'uuid';

import * as ChatActions from './chat.actions';
import { StreamingApiService } from '../../../core/services/streaming-api.service';
import { selectActiveConversation, selectActiveConversationId } from './chat.selectors';

@Injectable()
export class ChatEffects {

    private actions$= inject(Actions);
    private store= inject(Store<{ chat: any }>);
    private streamingApi= inject(StreamingApiService);
  
  // Send Message → Start Streaming
  sendMessage$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ChatActions.sendMessage),
      withLatestFrom(this.store.select(selectActiveConversationId)),
      switchMap(([{ content, conversationId: providedConvId }, activeConvId]) => {
        const conversationId = providedConvId || activeConvId;
        
        // Si pas de conversation active, en créer une
        if (!conversationId) {
          return of(ChatActions.createConversation()).pipe(
            tap(() => {
              // Re-dispatch sendMessage après création
              setTimeout(() => {
                this.store.dispatch(ChatActions.sendMessage({ content }));
              }, 100);
            }),
            map(() => ({ type: 'NO_OP' as const }))
          );
        }
        
        const userMessageId = uuidv4();
        const assistantMessageId = uuidv4();
        
        const userMessage = {
          id: userMessageId,
          role: 'user' as const,
          content,
          timestamp: new Date(),
          status: 'complete' as const
        };
        
        return [
          ChatActions.addUserMessage({ message: userMessage }),
          ChatActions.addAssistantMessage({ messageId: assistantMessageId }),
          ChatActions.clearInputText()
        ];
      })
    )
  );
  
  // ✅ CORRECTION: Utiliser selectActiveConversation au lieu de conversations
  startStreaming$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ChatActions.addAssistantMessage),
      withLatestFrom(
        this.store.select(selectActiveConversationId),
        this.store.select(selectActiveConversation)
      ),
      switchMap(([{ messageId }, conversationId, activeConv]) => {
        
        // Récupérer le dernier message user de la conversation active
        const lastUserMessage = this.getLastUserMessageFromConversation(activeConv);
        
        if (!lastUserMessage) {
          return of(ChatActions.streamError({ 
            error: 'Aucun message utilisateur trouvé' 
          }));
        }
        
        return this.streamingApi.stream({
          query: lastUserMessage,
          ...(conversationId ? { conversationId } : {})
        }).pipe(
          map(event => {
            switch (event.type) {
              case 'connected':
                return ChatActions.streamConnected({ event });
              case 'token':
                return ChatActions.streamToken({ event });
              case 'complete':
                return ChatActions.streamComplete({ event });
              case 'error':
                return ChatActions.streamError({ 
                  error: event.error || 'Stream error' 
                });
              default:
                return { type: 'NO_OP' as const };
            }
          }),
          catchError(error =>
            of(ChatActions.streamError({ error: error.message }))
          )
        )
      })
    )
  );
  
  // Cancel Stream
  cancelStream$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ChatActions.cancelStream),
      withLatestFrom(
        this.store.select(state => state.chat.streamSessionId)
      ),
      switchMap(([_, sessionId]) => {
        if (!sessionId) {
          return of(ChatActions.cancelStreamSuccess());
        }
        
        return this.streamingApi.cancelStream(sessionId).pipe(
          map(() => ChatActions.cancelStreamSuccess()),
          catchError(() => of(ChatActions.cancelStreamSuccess()))
        );
      })
    )
  );
  
  private getLastUserMessageFromConversation(conversation: any): string {
    if (!conversation || !conversation.messages) {
      return '';
    }
    
    const userMessages = conversation.messages.filter(
      (m: any) => m.role === 'user'
    );
    
    if (userMessages.length === 0) {
      return '';
    }
    
    return userMessages[userMessages.length - 1].content;
  }

}