// ============================================================================
// REDUCER - assistant.reducer.ts (VERSION v3.1 - Avec Notifications)
// ============================================================================
import { createReducer, on } from '@ngrx/store';
import * as AssistantActions from './assistant.actions';
import {
  AssistantState,
  initialAssistantState,
  messagesAdapter,
  filesAdapter,
  calculateStatsFromFiles,
  calculateProgressFromFiles,
  createUploadError,
  cleanupOldErrors,
  getNextMessageSequence
} from './assistant.state';
import { createUploadedFile, generateFileId } from './assistant.models';

export const assistantReducer = createReducer(
  initialAssistantState,

  // ==================== NOTIFICATION REDUCERS ====================
  
  /**
   * ✅ NOUVEAU : Afficher une notification
   */
 /* on(AssistantActions.showNotification, (state, { message, notificationType, duration }) => {
    console.log('🔔 [Reducer] Notification:', notificationType, message);
    
    // Vous pouvez stocker la notification dans le state si vous voulez un système de toast
    // Pour l'instant, on log juste et on peut utiliser un service de notification externe
    
    return {
      ...state,
      // Si vous voulez stocker les notifications :
      // notifications: [...state.notifications, { message, notificationType, duration, timestamp: new Date() }]
    };
  }),*/
  on(AssistantActions.showNotification, (state, { message, notificationType, duration }) => {
  console.log('🔔 [Reducer] Notification:', notificationType, message);
  
  const notification = {
    id: `notif_${Date.now()}`,
    message,
    type: notificationType,
    timestamp: new Date(),
    duration
  };
  
  return {
    ...state,
    notifications: [...state.notifications, notification]
  };
}),

  // ==================== STREAMING REDUCERS ====================
  
  on(AssistantActions.updateMessageContent, (state, { messageId, content }) => ({
    ...state,
    messages: messagesAdapter.updateOne({
      id: messageId,
      changes: { content }
    }, state.messages)
  })),
  
  on(AssistantActions.startStreaming, (state, { messageId }) => ({
    ...state,
    messages: messagesAdapter.updateOne({
      id: messageId,
      changes: { 
        isStreaming: true, 
        isLoading: false 
      }
    }, {
      ...state.messages,
      streamingMessageId: messageId
    })
  })),
  
  on(AssistantActions.stopStreaming, (state, { messageId }) => ({
    ...state,
    messages: messagesAdapter.updateOne({
      id: messageId,
      changes: { 
        isStreaming: false,
        isLoading: false 
      }
    }, {
      ...state.messages,
      streamingMessageId: null
    })
  })),

  on(AssistantActions.streamingError, (state, { messageId, error }) => ({
    ...state,
    messages: messagesAdapter.updateOne({
      id: messageId,
      changes: { 
        isStreaming: false,
        isLoading: false,
        error
      }
    }, {
      ...state.messages,
      streamingMessageId: null,
      error
    })
  })),
  
  // ==================== MESSAGE REDUCERS ====================
  
  on(AssistantActions.sendMessage, (state) => ({
    ...state,
    messages: {
      ...state.messages,
      loading: true,
      error: null
    },
    globalLoading: true
  })),
  
  on(AssistantActions.addUserMessage, (state, { message }) => {
    const allMessages = Object.values(state.messages.entities).filter(m => m != null);
    const sequence = getNextMessageSequence(allMessages as any[]);
    
    return {
      ...state,
      messages: messagesAdapter.addOne(
        { ...message, sequence },
        state.messages
      ),
      currentMessage: ''
    };
  }),
  
  on(AssistantActions.addLoadingMessage, (state, { message }) => {
    const allMessages = Object.values(state.messages.entities).filter(m => m != null);
    const sequence = getNextMessageSequence(allMessages as any[]);
    
    return {
      ...state,
      messages: messagesAdapter.addOne(
        { ...message, sequence },
        state.messages
      )
    };
  }),
  
  on(AssistantActions.addAssistantMessage, (state, { message }) => {
    const allMessages = Object.values(state.messages.entities).filter(m => m != null);
    const sequence = getNextMessageSequence(allMessages as any[]);
    
    return {
      ...state,
      messages: messagesAdapter.addOne(
        { ...message, sequence },
        {
          ...state.messages,
          loading: false
        }
      ),
      globalLoading: false
    };
  }),
  
  on(AssistantActions.removeMessage, (state, { messageId }) => ({
    ...state,
    messages: messagesAdapter.removeOne(messageId, state.messages)
  })),
  
  on(AssistantActions.sendMessageFailure, (state, { error }) => ({
    ...state,
    messages: {
      ...state.messages,
      loading: false,
      error
    },
    globalLoading: false,
    globalError: error
  })),
  
  on(AssistantActions.clearMessages, (state) => ({
    ...state,
    messages: messagesAdapter.removeAll({
      ...state.messages,
      loading: false,
      error: null,
      streamingMessageId: null
    })
  })),
  
  on(AssistantActions.loadMessagesFromStorageSuccess, (state, { messages }) => {
    const sortedMessages = [...messages].sort((a, b) => 
      new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
    );
    
    const messagesWithSequence = sortedMessages.map((msg, index) => ({
      ...msg,
      sequence: index
    }));
    
    return {
      ...state,
      messages: messagesAdapter.setAll(messagesWithSequence, state.messages)
    };
  }),
  
  // ==================== FILE UPLOAD REDUCERS ====================
  
  /**
   * ✅ AMÉLIORÉ : Commence l'upload avec file complet
   */
  on(AssistantActions.uploadFile, (state, { file }) => {
    const uploadedFile = createUploadedFile(file, {
      status: 'uploading',
      progress: 0
    });
    
    const newFilesState = filesAdapter.addOne(uploadedFile, {
      ...state.files,
      uploading: true,
      error: null
    });
    
    const allFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    
    return {
      ...state,
      files: {
        ...newFilesState,
        stats: calculateStatsFromFiles(allFiles),
        progress: calculateProgressFromFiles(allFiles)
      }
    };
  }),
  
  /**
   * ✅ NOUVEAU : Mise à jour progression TEMPS RÉEL (HTTP + Polling)
   */
  on(AssistantActions.updateFileProgress, (state, { fileId, progress }) => {
    const file = state.files.entities[fileId];
    if (!file) return state;
    
    const newFilesState = filesAdapter.updateOne({
      id: fileId,
      changes: { progress }
    }, state.files);
    
    const allFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    
    return {
      ...state,
      files: {
        ...newFilesState,
        progress: calculateProgressFromFiles(allFiles)
      }
    };
  }),

  /**
   * ✅ DÉPRÉCIÉ : Gardé pour compatibilité
   */
  on(AssistantActions.uploadFileProgress, (state, { fileId, progress }) => {
    const file = state.files.entities[fileId];
    if (!file) return state;
    
    const newFilesState = filesAdapter.updateOne({
      id: fileId,
      changes: { progress }
    }, state.files);
    
    const allFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    
    return {
      ...state,
      files: {
        ...newFilesState,
        progress: calculateProgressFromFiles(allFiles)
      }
    };
  }),
  
  /**
   * ✅ AMÉLIORÉ : Upload réussi avec file et response
   */
  on(AssistantActions.uploadFileSuccess, (state, { file, response }) => {
    const fileId = generateFileId(file);
    
    const newFilesState = filesAdapter.updateOne({
      id: fileId,
      changes: { 
        status: 'processing',
        progress: 100,
        jobId: response.jobId,
        name: response.fileName,
        size: response.fileSize
      }
    }, {
      ...state.files,
      uploading: false
    });
    
    const allFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    
    return {
      ...state,
      files: {
        ...newFilesState,
        stats: calculateStatsFromFiles(allFiles),
        progress: calculateProgressFromFiles(allFiles),
        pollingFileIds: new Set([...state.files.pollingFileIds, fileId])
      }
    };
  }),

  /**
   * ✅ AMÉLIORÉ : Duplicata détecté avec file complet
   */
  on(AssistantActions.uploadFileDuplicate, (state, { file, duplicateInfo, existingJobId }) => {
    const fileId = generateFileId(file);
    
    const newFilesState = filesAdapter.updateOne({
      id: fileId,
      changes: { 
        status: 'duplicate',
        progress: 100,
        jobId: duplicateInfo.jobId,
        existingJobId,
        name: file.name,
        size: file.size,
        duplicateInfo
      }
    }, {
      ...state.files,
      uploading: false,
      showDuplicateModal: true,
      currentDuplicateFileId: fileId
    });
    
    const allFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    
    return {
      ...state,
      files: {
        ...newFilesState,
        stats: calculateStatsFromFiles(allFiles),
        progress: calculateProgressFromFiles(allFiles)
      }
    };
  }),
  
  /**
   * ✅ AMÉLIORÉ : Échec upload avec file complet
   */
  on(AssistantActions.uploadFileFailure, (state, { file, error }) => {
    const fileId = generateFileId(file);
    const uploadError = createUploadError(fileId, file.name, error);
    
    const newFilesState = filesAdapter.updateOne({
      id: fileId,
      changes: { 
        status: 'failed',
        error
      }
    }, {
      ...state.files,
      uploading: false,
      error
    });
    
    const allFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    const newErrors = cleanupOldErrors([...state.files.uploadErrors, uploadError]);
    
    return {
      ...state,
      files: {
        ...newFilesState,
        stats: calculateStatsFromFiles(allFiles),
        progress: calculateProgressFromFiles(allFiles),
        uploadErrors: newErrors
      }
    };
  }),

  // ==================== DUPLICATE MANAGEMENT REDUCERS ====================

  on(AssistantActions.showDuplicateModal, (state, { fileId }) => ({
    ...state,
    files: {
      ...state.files,
      showDuplicateModal: true,
      currentDuplicateFileId: fileId
    }
  })),

  on(AssistantActions.hideDuplicateModal, (state) => ({
    ...state,
    files: {
      ...state.files,
      showDuplicateModal: false,
      currentDuplicateFileId: null
    }
  })),

  /**
   * ✅ NOUVEAU : Fermer la modale
   */
  on(AssistantActions.closeDuplicateModal, (state) => ({
    ...state,
    files: {
      ...state.files,
      showDuplicateModal: false,
      currentDuplicateFileId: null
    }
  })),

  on(AssistantActions.useDuplicateFile, (state, { fileId, existingJobId }) => {
    const newFilesState = filesAdapter.updateOne({
      id: fileId,
      changes: { 
        status: 'completed',
        jobId: existingJobId,
        progress: 100
      }
    }, {
      ...state.files,
      showDuplicateModal: false,
      currentDuplicateFileId: null
    });
    
    const allFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    
    return {
      ...state,
      files: {
        ...newFilesState,
        stats: calculateStatsFromFiles(allFiles),
        progress: calculateProgressFromFiles(allFiles)
      }
    };
  }),

  on(AssistantActions.forceReupload, (state, { fileId }) => {
    const newFilesState = filesAdapter.updateOne({
      id: fileId,
      changes: { 
        status: 'pending',
        progress: 0,
        duplicateInfo: undefined,
        existingJobId: undefined,
        error: undefined
      }
    }, {
      ...state.files,
      showDuplicateModal: false,
      currentDuplicateFileId: null
    });
    
    return {
      ...state,
      files: newFilesState
    };
  }),

  on(AssistantActions.dismissDuplicate, (state, { fileId }) => {
    const newFilesState = filesAdapter.removeOne(fileId, {
      ...state.files,
      showDuplicateModal: false,
      currentDuplicateFileId: null
    });
    
    const allFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    
    return {
      ...state,
      files: {
        ...newFilesState,
        stats: calculateStatsFromFiles(allFiles),
        progress: calculateProgressFromFiles(allFiles)
      }
    };
  }),

  // ==================== POLLING REDUCERS ====================

  /**
   * ✅ NOUVEAU : Démarrer le polling
   */
  on(AssistantActions.startPollingAfterUpload, (state, { fileId, jobId }) => ({
    ...state,
    files: {
      ...state.files,
      pollingFileIds: new Set([...state.files.pollingFileIds, fileId])
    }
  })),

  on(AssistantActions.pollUploadStatus, (state, { fileId }) => ({
    ...state,
    files: {
      ...state.files,
      pollingFileIds: new Set([...state.files.pollingFileIds, fileId])
    }
  })),

  /**
   * ✅ AMÉLIORÉ : Mise à jour via polling avec progression
   */
  on(AssistantActions.pollUploadStatusSuccess, (state, { fileId, jobId, status, progress, message }) => {
    const file = state.files.entities[fileId];
    if (!file) return state;
    
    const changes: any = {};
    
    // ✅ Mettre à jour la progression si disponible
    if (progress !== undefined && progress !== null) {
      changes.progress = progress;
    }
    
    // ✅ Mapper le statut backend vers frontend
    if (status === 'completed') {
      changes.status = 'completed';
      changes.progress = 100;
      changes.completedAt = new Date();
    } else if (status === 'failed') {
      changes.status = 'failed';
      changes.error = message || 'Traitement échoué';
    } else if (status === 'processing') {
      changes.status = 'processing';
    }
    
    const newFilesState = filesAdapter.updateOne({
      id: fileId,
      changes
    }, state.files);
    
    const allFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    
    // ✅ Retirer du polling si terminé ou échoué
    const newPollingIds = new Set(state.files.pollingFileIds);
    if (status === 'completed' || status === 'failed') {
      newPollingIds.delete(fileId);
    }
    
    return {
      ...state,
      files: {
        ...newFilesState,
        stats: calculateStatsFromFiles(allFiles),
        progress: calculateProgressFromFiles(allFiles),
        pollingFileIds: newPollingIds
      }
    };
  }),

  on(AssistantActions.pollUploadStatusFailure, (state, { fileId, jobId, error }) => {
    const newPollingIds = new Set(state.files.pollingFileIds);
    newPollingIds.delete(fileId);
    
    const newFilesState = filesAdapter.updateOne({
      id: fileId,
      changes: { 
        status: 'failed',
        error
      }
    }, state.files);
    
    const allFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    
    return {
      ...state,
      files: {
        ...newFilesState,
        stats: calculateStatsFromFiles(allFiles),
        progress: calculateProgressFromFiles(allFiles),
        pollingFileIds: newPollingIds,
        error
      }
    };
  }),

  /**
   * ✅ NOUVEAU : Arrêter le polling manuellement
   */
  on(AssistantActions.stopPollingUploadStatus, (state, { fileId }) => {
    const newPollingIds = new Set(state.files.pollingFileIds);
    newPollingIds.delete(fileId);
    
    return {
      ...state,
      files: {
        ...state.files,
        pollingFileIds: newPollingIds
      }
    };
  }),

  // ==================== FILE MANAGEMENT REDUCERS ====================

  on(AssistantActions.removeFile, (state, { fileId }) => {
    // ✅ Retirer du polling si nécessaire
    const newPollingIds = new Set(state.files.pollingFileIds);
    newPollingIds.delete(fileId);
    
    const newFilesState = filesAdapter.removeOne(fileId, {
      ...state.files,
      pollingFileIds: newPollingIds
    });
    
    const allFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    
    return {
      ...state,
      files: {
        ...newFilesState,
        stats: calculateStatsFromFiles(allFiles),
        progress: calculateProgressFromFiles(allFiles)
      }
    };
  }),
  
  on(AssistantActions.clearFiles, (state) => ({
    ...state,
    files: filesAdapter.removeAll({
      ...initialAssistantState.files,
      uploading: false,
      error: null,
      pollingFileIds: new Set()
    })
  })),

  on(AssistantActions.clearCompletedFiles, (state) => {
    const allFiles = Object.values(state.files.entities).filter(f => f != null) as any[];
    const completedIds = allFiles
      .filter(f => f.status === 'completed')
      .map(f => f.id);
    
    const newFilesState = filesAdapter.removeMany(completedIds, state.files);
    const remainingFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    
    return {
      ...state,
      files: {
        ...newFilesState,
        stats: calculateStatsFromFiles(remainingFiles),
        progress: calculateProgressFromFiles(remainingFiles)
      }
    };
  }),

  /**
   * ✅ NOUVEAU : Retry failed upload
   */
  on(AssistantActions.retryFailedUpload, (state, { file }) => {
    const newFilesState = filesAdapter.updateOne({
      id: file.id,
      changes: {
        status: 'pending',
        progress: 0,
        error: undefined
      }
    }, state.files);
    
    return {
      ...state,
      files: newFilesState
    };
  }),
  
  on(AssistantActions.loadFilesFromStorageSuccess, (state, { files }) => {
    const newFilesState = filesAdapter.setAll(files, state.files);
    const allFiles = Object.values(newFilesState.entities).filter(f => f != null) as any[];
    
    return {
      ...state,
      files: {
        ...newFilesState,
        stats: calculateStatsFromFiles(allFiles),
        progress: calculateProgressFromFiles(allFiles)
      }
    };
  }),

  // ==================== BATCH UPLOAD REDUCERS ====================

  on(AssistantActions.uploadMultipleFiles, (state) => ({
    ...state,
    globalLoading: true
  })),

  /**
   * ✅ NOUVEAU : Progression batch temps réel
   */
  on(AssistantActions.updateBatchProgress, (state, { totalFiles, completedFiles, failedFiles, overallProgress }) => ({
    ...state,
    files: {
      ...state.files,
      progress: {
        ...state.files.progress,
        totalFiles,
        completedFiles,
        failedFiles,
        overallProgress
      }
    }
  })),

  on(AssistantActions.uploadMultipleFilesProgress, (state, { totalFiles, completedFiles, overallProgress }) => ({
    ...state,
    files: {
      ...state.files,
      progress: {
        ...state.files.progress,
        totalFiles,
        completedFiles,
        overallProgress
      }
    }
  })),

  on(AssistantActions.uploadMultipleFilesComplete, (state) => ({
    ...state,
    globalLoading: false
  })),
  
  // ==================== UI REDUCERS ====================
  
  on(AssistantActions.setCurrentMessage, (state, { message }) => ({
    ...state,
    currentMessage: message
  })),

  on(AssistantActions.toggleSidebar, (state) => ({
    ...state,
    isSidebarOpen: !state.isSidebarOpen
  })),

  /**
   * ✅ NOUVEAU : Set sidebar state
   */
  on(AssistantActions.setSidebarOpen, (state, { isOpen }) => ({
    ...state,
    isSidebarOpen: isOpen
  })),

  on(AssistantActions.setLoading, (state, { loading }) => ({
    ...state,
    globalLoading: loading
  })),

  // ==================== ERROR HANDLING REDUCERS ====================

  on(AssistantActions.clearError, (state) => ({
    ...state,
    globalError: null,
    messages: {
      ...state.messages,
      error: null
    },
    files: {
      ...state.files,
      error: null
    }
  })),

  /**
   * ✅ NOUVEAU : Clear global error
   */
  on(AssistantActions.clearGlobalError, (state) => ({
    ...state,
    globalError: null
  })),

  on(AssistantActions.setError, (state, { error }) => ({
    ...state,
    globalError: error
  })),

  /**
   * ✅ NOUVEAU : Ajouter erreur upload
   */
  on(AssistantActions.addUploadError, (state, { fileId, error }) => {
    const file = state.files.entities[fileId];
    if (!file) return state;
    
    const uploadError = createUploadError(fileId, file.name, error);
    const newErrors = cleanupOldErrors([...state.files.uploadErrors, uploadError]);
    
    return {
      ...state,
      files: {
        ...state.files,
        uploadErrors: newErrors
      }
    };
  }),

  /**
   * ✅ NOUVEAU : Clear upload errors
   */
  on(AssistantActions.clearUploadErrors, (state) => ({
    ...state,
    files: {
      ...state.files,
      uploadErrors: []
    }
  }))
);