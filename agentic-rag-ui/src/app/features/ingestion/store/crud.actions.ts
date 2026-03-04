import { createAction, props } from '@ngrx/store';
import { 
  DeleteResponse, 
  DuplicateCheckResponse, 
  BatchInfoResponse 
} from '../../../core/services/crud-api.service';

// ========================================================================
// DELETE FILE
// ========================================================================

export const deleteFile = createAction(
  '[CRUD] Delete File',
  props<{ embeddingId: string; fileType: 'text' | 'image' }>()
);

export const deleteFileSuccess = createAction(
  '[CRUD] Delete File Success',
  props<{ response: DeleteResponse }>()
);

export const deleteFileError = createAction(
  '[CRUD] Delete File Error',
  props<{ embeddingId: string; error: string }>()
);

// ========================================================================
// DELETE BATCH
// ========================================================================

export const deleteBatch = createAction(
  '[CRUD] Delete Batch',
  props<{ batchId: string }>()
);

export const deleteBatchSuccess = createAction(
  '[CRUD] Delete Batch Success',
  props<{ response: DeleteResponse }>()
);

export const deleteBatchError = createAction(
  '[CRUD] Delete Batch Error',
  props<{ batchId: string; error: string }>()
);

// ========================================================================
// DELETE TEXT BATCH
// ========================================================================

export const deleteTextBatch = createAction(
  '[CRUD] Delete Text Batch',
  props<{ embeddingIds: string[] }>()
);

export const deleteTextBatchSuccess = createAction(
  '[CRUD] Delete Text Batch Success',
  props<{ response: DeleteResponse }>()
);

export const deleteTextBatchError = createAction(
  '[CRUD] Delete Text Batch Error',
  props<{ error: string }>()
);

// ========================================================================
// DELETE IMAGE BATCH
// ========================================================================

export const deleteImageBatch = createAction(
  '[CRUD] Delete Image Batch',
  props<{ embeddingIds: string[] }>()
);

export const deleteImageBatchSuccess = createAction(
  '[CRUD] Delete Image Batch Success',
  props<{ response: DeleteResponse }>()
);

export const deleteImageBatchError = createAction(
  '[CRUD] Delete Image Batch Error',
  props<{ error: string }>()
);

// ========================================================================
// DELETE ALL FILES
// ========================================================================

export const deleteAllFiles = createAction(
  '[CRUD] Delete All Files',
  props<{ confirmation: string }>()
);

export const deleteAllFilesSuccess = createAction(
  '[CRUD] Delete All Files Success',
  props<{ response: DeleteResponse }>()
);

export const deleteAllFilesError = createAction(
  '[CRUD] Delete All Files Error',
  props<{ error: string }>()
);

// ========================================================================
// CHECK DUPLICATE
// ========================================================================

export const checkDuplicate = createAction(
  '[CRUD] Check Duplicate',
  props<{ file: File }>()
);

export const checkDuplicateSuccess = createAction(
  '[CRUD] Check Duplicate Success',
  props<{ response: DuplicateCheckResponse }>()
);

export const checkDuplicateError = createAction(
  '[CRUD] Check Duplicate Error',
  props<{ filename: string; error: string }>()
);

// ========================================================================
// GET BATCH INFO
// ========================================================================

export const getBatchInfo = createAction(
  '[CRUD] Get Batch Info',
  props<{ batchId: string }>()
);

export const getBatchInfoSuccess = createAction(
  '[CRUD] Get Batch Info Success',
  props<{ response: BatchInfoResponse }>()
);

export const getBatchInfoError = createAction(
  '[CRUD] Get Batch Info Error',
  props<{ batchId: string; error: string }>()
);

// ========================================================================
// GET SYSTEM STATS
// ========================================================================

export const getSystemStats = createAction(
  '[CRUD] Get System Stats'
);

export const getSystemStatsSuccess = createAction(
  '[CRUD] Get System Stats Success',
  props<{ stats: any }>()
);

export const getSystemStatsError = createAction(
  '[CRUD] Get System Stats Error',
  props<{ error: string }>()
);

// ========================================================================
// CLEAR DELETE OPERATIONS
// ========================================================================

export const clearDeleteOperations = createAction(
  '[CRUD] Clear Delete Operations'
);

export const clearDuplicateChecks = createAction(
  '[CRUD] Clear Duplicate Checks'
);

export const clearBatchInfos = createAction(
  '[CRUD] Clear Batch Infos'
);

export const clearAll = createAction(
  '[CRUD] Clear All'
);