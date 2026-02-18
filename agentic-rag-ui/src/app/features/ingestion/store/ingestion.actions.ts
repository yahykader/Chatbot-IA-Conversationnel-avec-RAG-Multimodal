// features/ingestion/store/ingestion.actions.ts
import { createAction, props } from '@ngrx/store';
import { AsyncResponse, IngestionResponse } from '../../../core/services/ingestion-api.service';

import { UploadFile } from './ingestion.state';
// ========================================================================
// ASYNC UPLOAD ACTIONS
// ========================================================================

/**
 * Upload asynchrone - Retourne immédiatement avec batchId
 */
export const uploadFileAsync = createAction(
  '[Ingestion] Upload File Async',
  props<{ fileId: string; file: File }>()
);

export const uploadFileAsyncAccepted = createAction(
  '[Ingestion] Upload File Async Accepted',
  props<{ fileId: string; response: AsyncResponse }>()
);

export const uploadFileAsyncError = createAction(
  '[Ingestion] Upload File Async Error',
  props<{ fileId: string; error: string }>()
);

/**
 * Upload batch asynchrone
 */
export const uploadBatchAsync = createAction(
  '[Ingestion] Upload Batch Async',
  props<{ files: File[] }>()
);

export const uploadBatchAsyncAccepted = createAction(
  '[Ingestion] Upload Batch Async Accepted',
  props<{ batchId: string; fileCount: number }>()
);

export const uploadBatchAsyncError = createAction(
  '[Ingestion] Upload Batch Async Error',
  props<{ error: string }>()
);

export const uploadFileRateLimited = createAction(
  '[Ingestion] Upload File Rate Limited',
  props<{ 
    fileId: string; 
    retryAfterSeconds: number;
    message: string;
  }>()
);


// ========================================================================
// SYNCHRONE UPLOAD ACTIONS
// ========================================================================
export const addFilesToUpload = createAction(
  '[Ingestion] Add Files To Upload',
  props<{ files: File[] }>()
);

export const uploadFile = createAction(
  '[Ingestion] Upload File',
  props<{ fileId: string; file: File }>()
);

export const uploadFileSuccess = createAction(
  '[Ingestion] Upload File Success',
  props<{ fileId: string; response: IngestionResponse }>()
);

export const uploadFileError = createAction(
  '[Ingestion] Upload File Error',
  props<{ fileId: string; error: string }>()
);


/**
 * Ajouter un fichier à la liste des uploads
 */
export const addUpload = createAction(
  '[Ingestion] Add Upload',
  props<{ upload: UploadFile }>()
);

/**
 * Mettre à jour le statut d'un upload
 */
export const updateUploadStatus = createAction(
  '[Ingestion] Update Upload Status',
  props<{
    fileId: string;
    status: 'pending' | 'uploading' | 'success' | 'error' | 'duplicate';
    batchId?: string;
    existingBatchId?: string;
    error?: string;
  }>()
);

/**
 * Changer le mode d'upload (sync/async)
 */
export const setUploadMode = createAction(
  '[Ingestion] Set Upload Mode',
  props<{ mode: 'sync' | 'async' }>()
);

export const uploadFileDuplicate = createAction(
  '[Ingestion] Upload File Duplicate',
  props<{ fileId: string; batchId: string; existingBatchId?: string; message?: string }>()
);

export const removeFile = createAction(
  '[Ingestion] Remove File',
  props<{ fileId: string }>()
);

export const clearAllFiles = createAction(
  '[Ingestion] Clear All Files'
);

// Batch actions
export const uploadBatch = createAction(
  '[Ingestion] Upload Batch',
  props<{ files: File[] }>()
);

export const uploadBatchSuccess = createAction(
  '[Ingestion] Upload Batch Success',
  props<{ batchId: string; count: number }>()
);

export const uploadBatchError = createAction(
  '[Ingestion] Upload Batch Error',
  props<{ error: string }>()
);

// Load actions
export const loadStrategies = createAction(
  '[Ingestion] Load Strategies'
);

export const loadStrategiesSuccess = createAction(
  '[Ingestion] Load Strategies Success',
  props<{ strategies: any[] }>()
);

export const loadActiveIngestions = createAction(
  '[Ingestion] Load Active Ingestions'
);

export const loadActiveIngestionsSuccess = createAction(
  '[Ingestion] Load Active Ingestions Success',
  props<{ ingestions: any[] }>()
);

export const loadStats = createAction(
  '[Ingestion] Load Stats'
);

export const loadStatsSuccess = createAction(
  '[Ingestion] Load Stats Success',
  props<{ stats: any }>()
);

//   Remove upload
export const removeUpload = createAction(
  '[Ingestion] Remove Upload',
  props<{ fileId: string }>()
);

// AJOUTER: Clear completed
export const clearCompletedUploads = createAction(
  '[Ingestion] Clear Completed Uploads'
);

// AJOUTER: Toggle mode
export const toggleUploadMode = createAction(
  '[Ingestion] Toggle Upload Mode'
);
