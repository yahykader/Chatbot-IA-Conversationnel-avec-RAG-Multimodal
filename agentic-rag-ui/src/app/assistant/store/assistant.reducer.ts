// ============================================================================
// REDUCER - assistant.reducer.ts (VERSION ADAPTÉE)
// ============================================================================
import { createReducer, on } from '@ngrx/store';
import * as AssistantActions from './assistant.actions';
import {
  AssistantState,
  initialAssistantState,
  messagesAdapter,
  filesAdapter
} from './assistant.state';

// ✅ COMPTEUR DE SÉQUENCE GLOBAL
let messageSequenceCounter = 0;

export const assistantReducer = createReducer(
  initialAssistantState,

  // ==================== STREAMING REDUCERS ====================
  
  /**
   * ✅ ADAPTÉ : Met à jour le contenu du message (cumulatif)
   */
  on(AssistantActions.updateMessageContent, (state, { messageId, content }) => ({
    ...state,
    messages: messagesAdapter.updateOne({
      id: messageId,
      changes: { content }
    }, state.messages)
  })),
  
  /**
   * ✅ Démarre le streaming pour un message
   */
  on(AssistantActions.startStreaming, (state, { messageId }) => ({
    ...state,
    messages: messagesAdapter.updateOne({
      id: messageId,
      changes: { 
        isStreaming: true, 
        isLoading: false 
      }
    }, state.messages)
  })),
  
  /**
   * ✅ Arrête le streaming pour un message
   */
  on(AssistantActions.stopStreaming, (state, { messageId }) => ({
    ...state,
    messages: messagesAdapter.updateOne({
      id: messageId,
      changes: { 
        isStreaming: false,
        isLoading: false 
      }
    }, state.messages)
  })),
  
  // ==================== MESSAGE REDUCERS ====================
  
  /**
   * ✅ Commence l'envoi d'un message
   */
  on(AssistantActions.sendMessage, (state) => ({
    ...state,
    messages: {
      ...state.messages,
      loading: true,
      error: null
    }
  })),
  
  /**
   * ✅ Ajoute un message utilisateur
   */
  on(AssistantActions.addUserMessage, (state, { message }) => ({
    ...state,
    messages: messagesAdapter.addOne(
      { ...message, sequence: messageSequenceCounter++ },
      state.messages
    ),
    currentMessage: ''
  })),
  
  /**
   * ✅ Ajoute un message de chargement (placeholder)
   */
  on(AssistantActions.addLoadingMessage, (state, { message }) => ({
    ...state,
    messages: messagesAdapter.addOne(
      { ...message, sequence: messageSequenceCounter++ },
      state.messages
    )
  })),
  
  /**
   * ✅ Ajoute un message assistant
   */
  on(AssistantActions.addAssistantMessage, (state, { message }) => ({
    ...state,
    messages: messagesAdapter.addOne(
      { ...message, sequence: messageSequenceCounter++ },
      {
        ...state.messages,
        loading: false
      }
    )
  })),
  
  /**
   * ✅ Supprime un message
   */
  on(AssistantActions.removeMessage, (state, { messageId }) => ({
    ...state,
    messages: messagesAdapter.removeOne(messageId, state.messages)
  })),
  
  /**
   * ✅ Échec d'envoi de message
   */
  on(AssistantActions.sendMessageFailure, (state, { error }) => ({
    ...state,
    messages: {
      ...state.messages,
      loading: false,
      error
    }
  })),
  
  /**
   * ✅ Efface tous les messages
   */
  on(AssistantActions.clearMessages, (state) => {
    messageSequenceCounter = 0;
    return {
      ...state,
      messages: messagesAdapter.removeAll({
        ...state.messages,
        loading: false,
        error: null
      })
    };
  }),
  
  /**
   * ✅ Charge les messages depuis localStorage
   */
  on(AssistantActions.loadMessagesFromStorageSuccess, (state, { messages }) => {
    // Trier par timestamp avant d'assigner les séquences
    const sortedMessages = [...messages].sort((a, b) => 
      new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
    );
    
    // Assigner les séquences dans l'ordre
    const messagesWithSequence = sortedMessages.map((msg, index) => ({
      ...msg,
      sequence: index
    }));
    
    // Mettre à jour le compteur
    messageSequenceCounter = messagesWithSequence.length;
    
    return {
      ...state,
      messages: messagesAdapter.setAll(messagesWithSequence, state.messages)
    };
  }),
  
  // ==================== FILE REDUCERS ====================
  
  /**
   * ✅ Commence l'upload d'un fichier
   */
  on(AssistantActions.uploadFile, (state, { file }) => ({
    ...state,
    files: filesAdapter.addOne({
      id: generateFileId(),
      name: file.name,
      size: file.size,
      uploadDate: new Date(),
      status: 'uploading',
      progress: 0
    }, {
      ...state.files,
      uploading: true,
      error: null
    })
  })),
  
  /**
   * ✅ Met à jour la progression d'upload
   */
  on(AssistantActions.uploadFileProgress, (state, { fileId, progress }) => ({
    ...state,
    files: filesAdapter.updateOne({
      id: fileId,
      changes: { progress }
    }, state.files)
  })),
  
  /**
   * ✅ Upload réussi
   */
  on(AssistantActions.uploadFileSuccess, (state, { file }) => ({
    ...state,
    files: filesAdapter.upsertOne(file, {
      ...state.files,
      uploading: false
    })
  })),
  
  /**
   * ✅ Échec d'upload
   */
  on(AssistantActions.uploadFileFailure, (state, { fileId, error }) => ({
    ...state,
    files: filesAdapter.updateOne({
      id: fileId,
      changes: { status: 'error' }
    }, {
      ...state.files,
      uploading: false,
      error
    })
  })),
  
  /**
   * ✅ Efface tous les fichiers
   */
  on(AssistantActions.clearFiles, (state) => ({
    ...state,
    files: filesAdapter.removeAll({
      ...state.files,
      uploading: false,
      error: null
    })
  })),
  
  /**
   * ✅ Charge les fichiers depuis localStorage
   */
  on(AssistantActions.loadFilesFromStorageSuccess, (state, { files }) => ({
    ...state,
    files: filesAdapter.setAll(files, state.files)
  })),
  
  // ==================== UI REDUCERS ====================
  
  /**
   * ✅ Met à jour le message en cours de saisie
   */
  on(AssistantActions.setCurrentMessage, (state, { message }) => ({
    ...state,
    currentMessage: message
  }))
);

// ============================================================================
// HELPERS
// ============================================================================

/**
 * ✅ Génère un ID unique pour un fichier
 */
function generateFileId(): string {
  return 'file_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);
}

/**
 * ✅ Réinitialise le compteur de séquence (utile pour les tests)
 */
export function resetMessageSequence(): void {
  messageSequenceCounter = 0;
  console.log('✅ Compteur de séquence réinitialisé');
}