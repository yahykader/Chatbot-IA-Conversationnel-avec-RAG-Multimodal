// ============================================================================
// SELECTORS - assistant.selectors.ts (VERSION v3.0 - Progression temps réel)
// ============================================================================
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AssistantState, messagesAdapter, filesAdapter, LIMITS } from './assistant.state';
import { UploadedFile } from './assistant.models';

// ==================== FEATURE SELECTOR ====================

export const selectAssistantState = createFeatureSelector<AssistantState>('assistant');

// ==================== MESSAGES SELECTORS ====================

export const selectMessagesState = createSelector(
  selectAssistantState,
  (state) => state.messages
);

const {
  selectAll: selectAllMessages,
  selectEntities: selectMessageEntities,
  selectIds: selectMessageIds,
  selectTotal: selectTotalMessages
} = messagesAdapter.getSelectors(selectMessagesState);

export { selectAllMessages, selectMessageEntities, selectMessageIds, selectTotalMessages };

export const selectMessagesSorted = createSelector(
  selectAllMessages,
  (messages) => [...messages].sort((a, b) => {
    if (a.sequence !== b.sequence) {
      return a.sequence - b.sequence;
    }
    return new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime();
  })
);

export const selectMessagesLoading = createSelector(
  selectMessagesState,
  (state) => state.loading
);

export const selectMessagesError = createSelector(
  selectMessagesState,
  (state) => state.error
);

export const selectStreamingMessageId = createSelector(
  selectMessagesState,
  (state) => state.streamingMessageId
);

export const selectIsStreaming = createSelector(
  selectStreamingMessageId,
  (messageId) => messageId !== null
);

export const selectStreamingMessage = createSelector(
  selectStreamingMessageId,
  selectMessageEntities,
  (messageId, entities) => messageId ? entities[messageId] : null
);

export const selectLastMessage = createSelector(
  selectMessagesSorted,
  (messages) => messages.length > 0 ? messages[messages.length - 1] : null
);

export const selectUserMessages = createSelector(
  selectMessagesSorted,
  (messages) => messages.filter(m => m.sender === 'user')
);

export const selectAssistantMessages = createSelector(
  selectMessagesSorted,
  (messages) => messages.filter(m => m.sender === 'assistant')
);

export const selectCanSendMessage = createSelector(
  selectIsStreaming,
  selectMessagesLoading,
  (isStreaming, isLoading) => !isStreaming && !isLoading
);

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

export const selectFilesState = createSelector(
  selectAssistantState,
  (state) => state.files
);

const {
  selectAll: selectAllFiles,
  selectEntities: selectFileEntities,
  selectIds: selectFileIds,
  selectTotal: selectTotalFiles
} = filesAdapter.getSelectors(selectFilesState);

export { selectAllFiles, selectFileEntities, selectFileIds, selectTotalFiles };

export const selectFileById = (id: string) => createSelector(
  selectFileEntities,
  (entities) => entities[id]
);

export const selectFilesUploading = createSelector(
  selectFilesState,
  (state) => state.uploading
);

export const selectFilesError = createSelector(
  selectFilesState,
  (state) => state.error
);

export const selectUploadStats = createSelector(
  selectFilesState,
  (state) => state.stats
);

export const selectUploadProgress = createSelector(
  selectFilesState,
  (state) => state.progress
);

export const selectUploadErrors = createSelector(
  selectFilesState,
  (state) => state.uploadErrors
);

export const selectPollingFileIds = createSelector(
  selectFilesState,
  (state) => state.pollingFileIds
);

export const selectShowDuplicateModal = createSelector(
  selectFilesState,
  (state) => state.showDuplicateModal
);

export const selectCurrentDuplicateFileId = createSelector(
  selectFilesState,
  (state) => state.currentDuplicateFileId
);

export const selectCurrentDuplicateFile = createSelector(
  selectCurrentDuplicateFileId,
  selectFileEntities,
  (fileId, entities) => fileId ? entities[fileId] : null
);

// ==================== FILES BY STATUS SELECTORS ====================

export const selectPendingFiles = createSelector(
  selectAllFiles,
  (files) => files.filter(f => f.status === 'pending')
);

export const selectUploadingFiles = createSelector(
  selectAllFiles,
  (files) => files.filter(f => f.status === 'uploading')
);

export const selectProcessingFiles = createSelector(
  selectAllFiles,
  (files) => files.filter(f => f.status === 'processing')
);

export const selectCompletedFiles = createSelector(
  selectAllFiles,
  (files) => files.filter(f => f.status === 'completed')
);

export const selectFailedFiles = createSelector(
  selectAllFiles,
  (files) => files.filter(f => f.status === 'failed')
);

export const selectDuplicateFiles = createSelector(
  selectAllFiles,
  (files) => files.filter(f => f.status === 'duplicate')
);

export const selectActiveFiles = createSelector(
  selectAllFiles,
  (files) => files.filter(f => 
    f.status === 'pending' || 
    f.status === 'uploading' || 
    f.status === 'processing'
  )
);

export const selectHasActiveFiles = createSelector(
  selectActiveFiles,
  (files) => files.length > 0
);

export const selectFilesInPolling = createSelector(
  selectAllFiles,
  selectPollingFileIds,
  (files, pollingIds) => files.filter(f => pollingIds.has(f.id))
);

// ==================== FILE STATISTICS SELECTORS ====================

/**
 * ✅ CORRIGÉ : Statistiques détaillées avec gestion NaN
 */
export const selectFileStats = createSelector(
  selectUploadStats,
  selectAllFiles,
  (stats, files) => {
    const totalSize = files.reduce((acc, f) => acc + (f.size || 0), 0);
    const completedSize = files
      .filter(f => f.status === 'completed')
      .reduce((acc, f) => acc + (f.size || 0), 0);
    
    return {
      ...stats,
      totalSize,
      totalSizeMB: totalSize > 0 
        ? (totalSize / (1024 * 1024)).toFixed(2) 
        : '0.00',
      completedSize,
      completedSizeMB: completedSize > 0
        ? (completedSize / (1024 * 1024)).toFixed(2)
        : '0.00'
    };
  }
);

/**
 * ✅ AMÉLIORÉ : Progression moyenne avec gestion progress undefined
 */
export const selectAverageProgress = createSelector(
  selectAllFiles,
  (files) => {
    if (files.length === 0) return 0;
    const totalProgress = files.reduce((sum, file) => sum + (file.progress ?? 0), 0);
    return Math.round(totalProgress / files.length);
  }
);

/**
 * ✅ NOUVEAU : Progression détaillée par fichier actif
 */
export const selectActiveFilesProgress = createSelector(
  selectActiveFiles,
  (files) => files.map(file => ({
    fileId: file.id,
    fileName: file.name,
    status: file.status,
    progress: file.progress ?? 0,
    size: file.size,
    sizeFormatted: formatFileSize(file.size)
  }))
);

/**
 * ✅ AMÉLIORÉ : Temps estimé restant avec progression réelle
 */
export const selectEstimatedTimeRemaining = createSelector(
  selectActiveFiles,
  selectAverageProgress,
  (activeFiles, avgProgress) => {
    if (activeFiles.length === 0 || avgProgress === 0) return null;
    
    // Estimation basée sur la progression moyenne
    const remainingProgress = 100 - avgProgress;
    const estimatedSeconds = (remainingProgress / avgProgress) * 30;
    
    return {
      seconds: Math.round(estimatedSeconds),
      formatted: formatTime(estimatedSeconds)
    };
  }
);

/**
 * ✅ NOUVEAU : Vitesse d'upload globale (bytes/sec)
 */
export const selectUploadSpeed = createSelector(
  selectActiveFiles,
  (files) => {
    // Calcul basique - pourrait être amélioré avec des timestamps
    const uploadingFiles = files.filter(f => f.status === 'uploading');
    if (uploadingFiles.length === 0) return 0;
    
    // Estimation simplifiée: 1MB/s par fichier en moyenne
    return uploadingFiles.length * 1024 * 1024;
  }
);

// ==================== UI SELECTORS ====================

export const selectUserId = createSelector(
  selectAssistantState,
  (state) => state.userId
);

export const selectCurrentMessage = createSelector(
  selectAssistantState,
  (state) => state.currentMessage
);

export const selectIsSidebarOpen = createSelector(
  selectAssistantState,
  (state) => state.isSidebarOpen
);

export const selectUploadConfig = createSelector(
  selectAssistantState,
  (state) => state.uploadConfig
);

export const selectGlobalLoading = createSelector(
  selectAssistantState,
  (state) => state.globalLoading
);

export const selectGlobalError = createSelector(
  selectAssistantState,
  (state) => state.globalError
);

export const selectHasMessages = createSelector(
  selectTotalMessages,
  (total) => total > 0
);

export const selectHasFiles = createSelector(
  selectTotalFiles,
  (total) => total > 0
);

/**
 * ✅ AMÉLIORÉ : État global UI avec toutes les conditions
 */
export const selectUIState = createSelector(
  selectMessagesLoading,
  selectIsStreaming,
  selectFilesUploading,
  selectCanSendMessage,
  selectHasMessages,
  selectHasActiveFiles,
  selectGlobalLoading,
  selectShowDuplicateModal,
  selectGlobalError,
  (messagesLoading, isStreaming, filesUploading, canSend, hasMessages, hasActiveFiles, globalLoading, showDuplicateModal, globalError) => ({
    isLoading: messagesLoading || isStreaming || filesUploading || globalLoading,
    isStreaming,
    canSendMessage: canSend && !globalError,
    hasMessages,
    hasActiveFiles,
    showWelcome: !hasMessages,
    showSpinner: messagesLoading || filesUploading || globalLoading,
    showStreamingIndicator: isStreaming,
    showDuplicateModal,
    showFileUploadArea: true,
    showClearButton: hasMessages || hasActiveFiles,
    hasGlobalError: !!globalError,
    isDisabled: globalError !== null || !canSend
  })
);

export const selectLoadingMessagesCount = createSelector(
  selectAllMessages,
  (messages) => messages.filter(m => m.isLoading || m.isStreaming).length
);

export const selectHasActivity = createSelector(
  selectMessagesLoading,
  selectIsStreaming,
  selectFilesUploading,
  selectHasActiveFiles,
  (messagesLoading, isStreaming, filesUploading, hasActiveFiles) => 
    messagesLoading || isStreaming || filesUploading || hasActiveFiles
);

// ==================== COMBINED/COMPUTED SELECTORS ====================

export const selectOverview = createSelector(
  selectMessageStats,
  selectFileStats,
  selectUploadProgress,
  selectHasActivity,
  (messageStats, fileStats, uploadProgress, hasActivity) => ({
    messages: messageStats,
    files: fileStats,
    upload: uploadProgress,
    isActive: hasActivity
  })
);

export const selectConversationState = createSelector(
  selectMessagesSorted,
  selectIsStreaming,
  selectStreamingMessage,
  selectLastMessage,
  (messages, isStreaming, streamingMessage, lastMessage) => ({
    messages,
    totalMessages: messages.length,
    isStreaming,
    streamingMessage,
    lastMessage,
    isEmpty: messages.length === 0,
    canScroll: messages.length > 5
  })
);

/**
 * ✅ AMÉLIORÉ : État complet des uploads avec progression temps réel
 */
export const selectUploadState = createSelector(
  selectAllFiles,
  selectUploadStats,
  selectUploadProgress,
  selectActiveFiles,
  selectDuplicateFiles,
  selectFailedFiles,
  selectShowDuplicateModal,
  selectCurrentDuplicateFile,
  selectAverageProgress,
  selectEstimatedTimeRemaining,
  (allFiles, stats, progress, activeFiles, duplicates, failed, showModal, currentDuplicate, avgProgress, estimatedTime) => ({
    allFiles,
    stats,
    progress,
    activeFiles,
    duplicates,
    failed,
    showDuplicateModal: showModal,
    currentDuplicate,
    hasActiveUploads: activeFiles.length > 0,
    hasDuplicates: duplicates.length > 0,
    hasFailures: failed.length > 0,
    averageProgress: avgProgress,
    estimatedTimeRemaining: estimatedTime,
    uploadInProgress: activeFiles.length > 0
  })
);

/**
 * ✅ AMÉLIORÉ : Vue liste avec progression temps réel
 */
export const selectFileListView = createSelector(
  selectAllFiles,
  selectPollingFileIds,
  (files, pollingIds) => files.map(file => ({
    ...file,
    isPolling: pollingIds.has(file.id),
    statusColor: getStatusColor(file.status),
    statusIcon: getStatusIcon(file.status),
    statusLabel: getStatusLabel(file.status),
    progressPercent: `${file.progress ?? 0}%`,
    progressValue: file.progress ?? 0,
    sizeFormatted: formatFileSize(file.size),
    canRetry: file.status === 'failed',
    canRemove: file.status !== 'uploading' && file.status !== 'processing',
    showProgress: file.status === 'uploading' || file.status === 'processing',
    showLoader: file.status === 'uploading' || file.status === 'processing',
    isActive: file.status === 'uploading' || file.status === 'processing' || file.status === 'pending',
    hasError: file.status === 'failed' && !!file.error,
    isDuplicate: file.status === 'duplicate',
    isCompleted: file.status === 'completed'
  }))
);

/**
 * ✅ NOUVEAU : Notifications actives
 */
export const selectNotifications = createSelector(
  selectAssistantState,
  (state) => state.notifications || []
);

/**
 * ✅ NOUVEAU : Peut uploader de nouveaux fichiers ?
 */
export const selectCanUploadMore = createSelector(
  selectActiveFiles,
  selectUploadConfig,
  (activeFiles, config) => {
    const maxConcurrent = config.maxConcurrentUploads || LIMITS.MAX_CONCURRENT_UPLOADS;
    return activeFiles.length < maxConcurrent;
  }
);

/**
 * ✅ NOUVEAU : Limites atteintes ?
 */
export const selectLimitsReached = createSelector(
  selectTotalFiles,
  selectTotalMessages,
  (totalFiles, totalMessages) => ({
    filesLimitReached: totalFiles >= LIMITS.MAX_FILES,
    messagesLimitReached: totalMessages >= LIMITS.MAX_MESSAGES,
    shouldCleanup: totalFiles > LIMITS.MAX_FILES * 0.9 || totalMessages > LIMITS.MAX_MESSAGES * 0.9
  })
);

/**
 * ✅ NOUVEAU : Stats en temps réel pour le dashboard
 */
export const selectDashboardStats = createSelector(
  selectMessageStats,
  selectFileStats,
  selectUploadProgress,
  selectActiveFiles,
  selectHasActivity,
  selectAverageProgress,
  (messageStats, fileStats, uploadProgress, activeFiles, hasActivity, avgProgress) => ({
    messages: {
      total: messageStats.total,
      user: messageStats.userCount,
      assistant: messageStats.assistantCount
    },
    files: {
      total: fileStats.total,
      completed: fileStats.completed,
      failed: fileStats.failed,
      active: activeFiles.length,
      totalSizeMB: fileStats.totalSizeMB
    },
    progress: {
      overall: uploadProgress.overallProgress,
      average: avgProgress,
      activeUploads: activeFiles.length
    },
    status: {
      isActive: hasActivity,
      hasErrors: fileStats.failed > 0
    }
  })
);

// ==================== HELPER FUNCTIONS ====================

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function formatTime(seconds: number): string {
  if (seconds < 60) {
    return `${Math.round(seconds)}s`;
  } else if (seconds < 3600) {
    const minutes = Math.floor(seconds / 60);
    const secs = Math.round(seconds % 60);
    return `${minutes}m ${secs}s`;
  } else {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    return `${hours}h ${minutes}m`;
  }
}

function getStatusColor(status: UploadedFile['status']): string {
  const colors: Record<UploadedFile['status'], string> = {
    'pending': 'secondary',
    'uploading': 'info',
    'processing': 'primary',
    'completed': 'success',
    'failed': 'danger',
    'duplicate': 'warning'
  };
  return colors[status] || 'secondary';
}

function getStatusIcon(status: UploadedFile['status']): string {
  const icons: Record<UploadedFile['status'], string> = {
    'pending': '○',
    'uploading': '↑',
    'processing': '⟳',
    'completed': '✓',
    'failed': '✗',
    'duplicate': '⚠'
  };
  return icons[status] || '○';
}

function getStatusLabel(status: UploadedFile['status']): string {
  const labels: Record<UploadedFile['status'], string> = {
    'pending': 'En attente',
    'uploading': 'Upload en cours',
    'processing': 'Traitement',
    'completed': 'Terminé',
    'failed': 'Échec',
    'duplicate': 'Duplicata'
  };
  return labels[status] || status;
}

