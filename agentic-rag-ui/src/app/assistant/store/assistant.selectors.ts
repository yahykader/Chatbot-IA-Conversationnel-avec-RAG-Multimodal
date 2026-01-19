// ============================================================================
// SELECTORS - assistant.selectors.ts (VERSION ADAPTÉE ET OPTIMISÉE)
// ============================================================================
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AssistantState, messagesAdapter, filesAdapter } from './assistant.state';

// ==================== FEATURE SELECTOR ====================

export const selectAssistantState = createFeatureSelector<AssistantState>('assistant');

// ==================== MESSAGES SELECTORS ====================

/**
 * ✅ Sélectionne l'état des messages
 */
export const selectMessagesState = createSelector(
  selectAssistantState,
  (state) => state.messages
);

/**
 * ✅ Sélecteurs de base de l'EntityAdapter
 */
const {
  selectAll: selectAllMessages,
  selectEntities: selectMessageEntities,
  selectIds: selectMessageIds,
  selectTotal: selectTotalMessages
} = messagesAdapter.getSelectors(selectMessagesState);

export { selectAllMessages, selectMessageEntities, selectMessageIds, selectTotalMessages };

/**
 * ✅ CRITIQUE: Messages triés par séquence + timestamp
 * L'adapter trie déjà, mais on s'assure du tri pour la sécurité
 */
export const selectMessagesSorted = createSelector(
  selectAllMessages,
  (messages) => [...messages].sort((a, b) => {
    // Tri principal par séquence
    if (a.sequence !== b.sequence) {
      return a.sequence - b.sequence;
    }
    // Tri secondaire par timestamp si séquences égales
    return new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime();
  })
);

/**
 * ✅ État de chargement des messages
 */
export const selectMessagesLoading = createSelector(
  selectMessagesState,
  (state) => state.loading
);

/**
 * ✅ Erreur des messages
 */
export const selectMessagesError = createSelector(
  selectMessagesState,
  (state) => state.error
);

/**
 * ✅ Y a-t-il un message en cours de streaming ?
 */
export const selectIsStreaming = createSelector(
  selectAllMessages,
  (messages) => messages.some(m => m.isStreaming)
);

/**
 * ✅ Dernier message
 */
export const selectLastMessage = createSelector(
  selectMessagesSorted,
  (messages) => messages.length > 0 ? messages[messages.length - 1] : null
);

/**
 * ✅ Messages de l'utilisateur uniquement
 */
export const selectUserMessages = createSelector(
  selectMessagesSorted,
  (messages) => messages.filter(m => m.sender === 'user')
);

/**
 * ✅ Messages de l'assistant uniquement
 */
export const selectAssistantMessages = createSelector(
  selectMessagesSorted,
  (messages) => messages.filter(m => m.sender === 'assistant')
);

/**
 * ✅ Peut-on envoyer un message ?
 * (pas de streaming en cours, pas de chargement)
 */
export const selectCanSendMessage = createSelector(
  selectIsStreaming,
  selectMessagesLoading,
  (isStreaming, isLoading) => !isStreaming && !isLoading
);

/**
 * ✅ Statistiques des messages
 */
export const selectMessageStats = createSelector(
  selectUserMessages,
  selectAssistantMessages,
  (userMessages, assistantMessages) => ({
    total: userMessages.length + assistantMessages.length,
    userCount: userMessages.length,
    assistantCount: assistantMessages.length,
    lastUserMessage: userMessages[userMessages.length - 1]?.content || '',
    lastAssistantMessage: assistantMessages[assistantMessages.length - 1]?.content || ''
  })
);

// ==================== FILES SELECTORS ====================

/**
 * ✅ Sélectionne l'état des fichiers
 */
export const selectFilesState = createSelector(
  selectAssistantState,
  (state) => state.files
);

/**
 * ✅ Sélecteurs de base de l'EntityAdapter
 */
const {
  selectAll: selectAllFiles,
  selectEntities: selectFileEntities,
  selectIds: selectFileIds,
  selectTotal: selectTotalFiles
} = filesAdapter.getSelectors(selectFilesState);

export { selectAllFiles, selectFileEntities, selectFileIds, selectTotalFiles };

/**
 * ✅ Upload en cours ?
 */
export const selectFilesUploading = createSelector(
  selectFilesState,
  (state) => state.uploading
);

/**
 * ✅ Erreur des fichiers
 */
export const selectFilesError = createSelector(
  selectFilesState,
  (state) => state.error
);

/**
 * ✅ Fichiers uploadés avec succès
 */
export const selectSuccessfulFiles = createSelector(
  selectAllFiles,
  (files) => files.filter(f => f.status === 'success')
);

/**
 * ✅ Fichiers en cours d'upload
 */
export const selectUploadingFiles = createSelector(
  selectAllFiles,
  (files) => files.filter(f => f.status === 'uploading')
);

/**
 * ✅ Fichiers en erreur
 */
export const selectFailedFiles = createSelector(
  selectAllFiles,
  (files) => files.filter(f => f.status === 'error')
);

/**
 * ✅ Statistiques des fichiers
 */
export const selectFileStats = createSelector(
  selectSuccessfulFiles,
  selectUploadingFiles,
  selectFailedFiles,
  (success, uploading, failed) => ({
    total: success.length + uploading.length + failed.length,
    successCount: success.length,
    uploadingCount: uploading.length,
    failedCount: failed.length,
    totalSize: success.reduce((acc, f) => acc + f.size, 0),
    totalSizeMB: (success.reduce((acc, f) => acc + f.size, 0) / (1024 * 1024)).toFixed(2)
  })
);

// ==================== UI SELECTORS ====================

/**
 * ✅ UserId de l'utilisateur
 */
export const selectUserId = createSelector(
  selectAssistantState,
  (state) => state.userId
);

/**
 * ✅ Message en cours de saisie
 */
export const selectCurrentMessage = createSelector(
  selectAssistantState,
  (state) => state.currentMessage
);

/**
 * ✅ Y a-t-il des messages ?
 */
export const selectHasMessages = createSelector(
  selectTotalMessages,
  (total) => total > 0
);

/**
 * ✅ Y a-t-il des fichiers ?
 */
export const selectHasFiles = createSelector(
  selectTotalFiles,
  (total) => total > 0
);

/**
 * ✅ État global de l'UI
 */
export const selectUIState = createSelector(
  selectMessagesLoading,
  selectIsStreaming,
  selectFilesUploading,
  selectCanSendMessage,
  selectHasMessages,
  (messagesLoading, isStreaming, filesUploading, canSend, hasMessages) => ({
    isLoading: messagesLoading || isStreaming || filesUploading,
    isStreaming,
    canSendMessage: canSend,
    hasMessages,
    showWelcome: !hasMessages,
    showSpinner: messagesLoading || filesUploading,
    showStreamingIndicator: isStreaming
  })
);

/**
 * ✅ Compte des messages en cours de chargement/streaming
 */
export const selectLoadingMessagesCount = createSelector(
  selectAllMessages,
  (messages) => messages.filter(m => m.isLoading || m.isStreaming).length
);

/**
 * ✅ Y a-t-il une activité en cours ?
 */
export const selectHasActivity = createSelector(
  selectMessagesLoading,
  selectIsStreaming,
  selectFilesUploading,
  (messagesLoading, isStreaming, filesUploading) => 
    messagesLoading || isStreaming || filesUploading
);