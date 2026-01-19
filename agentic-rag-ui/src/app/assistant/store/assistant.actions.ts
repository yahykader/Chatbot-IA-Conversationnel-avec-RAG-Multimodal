// ============================================================================
// ACTIONS - assistant.actions.ts (VERSION ADAPTÉE)
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

/**
 * ✅ ADAPTÉ : isDelta supprimé car le contenu est toujours cumulatif
 */
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

// ==================== FILE ACTIONS ====================

export const uploadFile = createAction(
  '[Assistant] Upload File',
  props<{ file: File }>()
);

export const uploadFileProgress = createAction(
  '[Assistant] Upload File Progress',
  props<{ fileId: string; progress: number }>()
);

export const uploadFileSuccess = createAction(
  '[Assistant] Upload File Success',
  props<{ file: UploadedFile }>()
);

export const uploadFileFailure = createAction(
  '[Assistant] Upload File Failure',
  props<{ fileId: string; error: string }>()
);

export const clearFiles = createAction(
  '[Assistant] Clear Files'
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