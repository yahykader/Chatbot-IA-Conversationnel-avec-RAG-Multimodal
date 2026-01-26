// ============================================================================
// ACTIONS - assistant.actions.ts (VERSION v3.0 - Progression temps réel)
// ============================================================================
import { createAction, props } from '@ngrx/store';
import { Message, UploadedFile } from './assistant.models';

// ==================== MESSAGE ACTIONS ====================

export const sendMessage = createAction(
  '[Assistant] Send Message',
  props<{ message: string }>()
);

export const sendMessageSuccess = createAction(
  '[Assistant] Send Message Success',
  props<{ response: string }>()
);

export const sendMessageFailure = createAction(
  '[Assistant] Send Message Failure',
  props<{ error: string }>()
);

export const addUserMessage = createAction(
  '[Assistant] Add User Message',
  props<{ message: Message }>()
);

export const addAssistantMessage = createAction(
  '[Assistant] Add Assistant Message',
  props<{ message: Message }>()
);

export const addLoadingMessage = createAction(
  '[Assistant] Add Loading Message',
  props<{ message: Message }>()
);

export const removeMessage = createAction(
  '[Assistant] Remove Message',
  props<{ messageId: string }>()
);

export const clearMessages = createAction(
  '[Assistant] Clear Messages'
);

export const loadMessagesFromStorage = createAction(
  '[Assistant] Load Messages From Storage'
);

export const loadMessagesFromStorageSuccess = createAction(
  '[Assistant] Load Messages From Storage Success',
  props<{ messages: Message[] }>()
);

// ==================== STREAMING ACTIONS ====================

export const updateMessageContent = createAction(
  '[Assistant] Update Message Content',
  props<{ messageId: string; content: string }>()
);

export const startStreaming = createAction(
  '[Assistant] Start Streaming',
  props<{ messageId: string }>()
);

export const stopStreaming = createAction(
  '[Assistant] Stop Streaming',
  props<{ messageId: string }>()
);

export const streamingError = createAction(
  '[Assistant] Streaming Error',
  props<{ messageId: string; error: string }>()
);

// ==================== FILE UPLOAD ACTIONS ====================

/**
 * ✅ Upload initial d'un fichier
 */
export const uploadFile = createAction(
  '[Assistant] Upload File',
  props<{ file: File; userId?: number }>()
);

/**
 * ✅ NOUVEAU : Mise à jour de la progression en temps réel
 * Appelé par l'effect lors de HttpEventType.UploadProgress
 */
export const updateFileProgress = createAction(
  '[Assistant] Update File Progress',
  props<{ fileId: string; progress: number }>()
);

/**
 * ✅ ANCIEN : Déprécié en faveur de updateFileProgress
 * Gardé pour compatibilité
 */
export const uploadFileProgress = createAction(
  '[Assistant] Upload File Progress',
  props<{ fileId: string; progress: number }>()
);

/**
 * ✅ Upload réussi (response du backend)
 */
export const uploadFileSuccess = createAction(
  '[Assistant] Upload File Success',
  props<{ 
    file: File;
    response: {
      jobId: string;
      fileName: string;
      fileSize: number;
      status: string;
      duplicate: boolean;
      duplicateInfo?: {
        jobId: string;
        originalFileName: string;
        uploadedAt: string;
        fingerprint: string;
        fileSize: number;
      };
    };
  }>()
);

/**
 * ✅ NOUVEAU : Duplicata détecté
 */
export const uploadFileDuplicate = createAction(
  '[Assistant] Upload File Duplicate',
  props<{ 
    file: File;
    duplicateInfo: {
      jobId: string;
      originalFileName: string;
      uploadedAt: string;
      fingerprint: string;
      fileSize: number;
    };
    existingJobId: string;
  }>()
);

export const uploadFileFailure = createAction(
  '[Assistant] Upload File Failure',
  props<{ file: File; error: string }>()
);

// ==================== DUPLICATE MANAGEMENT ACTIONS ====================

export const showDuplicateModal = createAction(
  '[Assistant] Show Duplicate Modal',
  props<{ fileId: string }>()
);

export const hideDuplicateModal = createAction(
  '[Assistant] Hide Duplicate Modal'
);

export const closeDuplicateModal = createAction(
  '[Assistant] Close Duplicate Modal'
);

export const useDuplicateFile = createAction(
  '[Assistant] Use Duplicate File',
  props<{ fileId: string; existingJobId: string }>()
);

export const forceReupload = createAction(
  '[Assistant] Force Reupload',
  props<{ fileId: string; userId?: number }>()
);

export const dismissDuplicate = createAction(
  '[Assistant] Dismiss Duplicate',
  props<{ fileId: string }>()
);

// ==================== FILE STATUS POLLING ACTIONS ====================

/**
 * ✅ NOUVEAU : Démarrer le polling après upload réussi
 */
export const startPollingAfterUpload = createAction(
  '[Assistant] Start Polling After Upload',
  props<{ fileId: string; jobId: string }>()
);

/**
 * ✅ Polling du statut
 */
export const pollUploadStatus = createAction(
  '[Assistant] Poll Upload Status',
  props<{ fileId: string; jobId: string }>()
);

/**
 * ✅ AMÉLIORÉ : Avec progression temps réel
 */
export const pollUploadStatusSuccess = createAction(
  '[Assistant] Poll Upload Status Success',
  props<{ 
    fileId: string;
    jobId: string;
    status: string;
    progress?: number;
    message?: string;
  }>()
);

export const pollUploadStatusFailure = createAction(
  '[Assistant] Poll Upload Status Failure',
  props<{ fileId: string; jobId: string; error: string }>()
);

/**
 * ✅ NOUVEAU : Arrêter le polling
 */
export const stopPollingUploadStatus = createAction(
  '[Assistant] Stop Polling Upload Status',
  props<{ fileId: string; jobId: string }>()
);

// ==================== FILE MANAGEMENT ACTIONS ====================

export const removeFile = createAction(
  '[Assistant] Remove File',
  props<{ fileId: string }>()
);

export const clearFiles = createAction(
  '[Assistant] Clear Files'
);

export const clearCompletedFiles = createAction(
  '[Assistant] Clear Completed Files'
);

export const retryFailedUpload = createAction(
  '[Assistant] Retry Failed Upload',
  props<{ file: UploadedFile }>()
);

export const loadFilesFromStorage = createAction(
  '[Assistant] Load Files From Storage'
);

export const loadFilesFromStorageSuccess = createAction(
  '[Assistant] Load Files From Storage Success',
  props<{ files: UploadedFile[] }>()
);

// ==================== UI ACTIONS ====================

export const setCurrentMessage = createAction(
  '[Assistant] Set Current Message',
  props<{ message: string }>()
);

export const toggleSidebar = createAction(
  '[Assistant] Toggle Sidebar'
);

export const setLoading = createAction(
  '[Assistant] Set Loading',
  props<{ loading: boolean }>()
);

export const setSidebarOpen = createAction(
  '[Assistant] Set Sidebar Open',
  props<{ isOpen: boolean }>()
);

// ==================== ERROR HANDLING ACTIONS ====================

export const clearError = createAction(
  '[Assistant] Clear Error'
);

export const setError = createAction(
  '[Assistant] Set Error',
  props<{ error: string }>()
);

export const clearGlobalError = createAction(
  '[Assistant] Clear Global Error'
);

export const addUploadError = createAction(
  '[Assistant] Add Upload Error',
  props<{ fileId: string; error: string }>()
);

export const clearUploadErrors = createAction(
  '[Assistant] Clear Upload Errors'
);

// ==================== BATCH ACTIONS ====================

/**
 * ✅ Upload multiple de fichiers
 */
export const uploadMultipleFiles = createAction(
  '[Assistant] Upload Multiple Files',
  props<{ files: File[]; userId?: number }>()
);

/**
 * ✅ NOUVEAU : Progression globale batch
 */
export const updateBatchProgress = createAction(
  '[Assistant] Update Batch Progress',
  props<{ 
    totalFiles: number;
    completedFiles: number;
    failedFiles: number;
    overallProgress: number;
  }>()
);

export const uploadMultipleFilesProgress = createAction(
  '[Assistant] Upload Multiple Files Progress',
  props<{ 
    totalFiles: number;
    completedFiles: number;
    overallProgress: number;
  }>()
);

export const uploadMultipleFilesComplete = createAction(
  '[Assistant] Upload Multiple Files Complete',
  props<{ 
    successCount: number;
    failureCount: number;
    duplicateCount: number;
  }>()
);

// ==================== NOTIFICATION ACTIONS ====================

/**
 * ✅ NOUVEAU : Notifications toast (optionnel)
 */
export const showNotification = createAction(
  '[Assistant] Show Notification',
  props<{
    message: string;
    notificationType: "error" | "info" | "success" | "warning";  // ✅ Renommé
    duration?: number;
  }>()
);

export const hideNotification = createAction(
  '[Assistant] Hide Notification'
);

// ==================== EXPORT/IMPORT ACTIONS ====================

/**
 * ✅ NOUVEAU : Export de l'historique
 */
export const exportHistory = createAction(
  '[Assistant] Export History'
);

export const exportHistorySuccess = createAction(
  '[Assistant] Export History Success'
);

export const exportHistoryFailure = createAction(
  '[Assistant] Export History Failure',
  props<{ error: string }>()
);

// ==================== LIFECYCLE ACTIONS ====================

/**
 * ✅ NOUVEAU : Initialisation de l'app
 */
export const initApp = createAction(
  '[Assistant] Init App'
);

export const initAppSuccess = createAction(
  '[Assistant] Init App Success'
);

export const initAppFailure = createAction(
  '[Assistant] Init App Failure',
  props<{ error: string }>()
);

/**
 * ✅ NOUVEAU : Cleanup au démontage
 */
export const cleanupApp = createAction(
  '[Assistant] Cleanup App'
);