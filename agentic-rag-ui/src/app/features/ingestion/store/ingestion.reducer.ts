// features/ingestion/store/ingestion.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { initialState, UploadFile } from './ingestion.state';
import * as IngestionActions from './ingestion.actions';
import * as ProgressActions from './progress.actions';
import * as CrudActions from './crud.actions';


export const ingestionReducer = createReducer(
  initialState,
  


  // ========================================================================
  // ASYNC UPLOAD REDUCERS
  // ========================================================================
  
  // Upload async started
  on(IngestionActions.uploadFileAsync, (state, { fileId }) => ({
    ...state,
    uploads: state.uploads.map(upload =>
      upload.id === fileId
        ? { 
            ...upload, 
            status: 'uploading' as const, 
            progress: 0 
          }
        : upload
    ),
    activeUploads: state.activeUploads + 1
  })),
  
  // Upload async accepted (pas terminé, juste accepté)
  on(IngestionActions.uploadFileAsyncAccepted, (state, { fileId, response }) => ({
    ...state,
    uploads: state.uploads.map(upload =>
      upload.id === fileId
        ? { 
            ...upload, 
            status: 'uploading' as const,
            progress: 10, // Initial progress
            batchId: response.batchId,
            asyncResponse: response,
            message: response.message
          }
        : upload
    )
    // activeUploads reste inchangé car l'upload continue
  })),
  
  // ✅ SOLUTION 1: Changer le status après WebSocket COMPLETED
  on(ProgressActions.progressUpdate, (state, { progress }) => {
    
    // Si progress COMPLETED, mettre à jour le status
    if (progress.stage === 'COMPLETED') {
      return {
        ...state,
        uploads: state.uploads.map(upload =>
          upload.batchId === progress.batchId
            ? {
                ...upload,
                status: 'success' as const,  // ✅ Changer vers success
                progress: 100,
                response: {
                  success: true,
                  batchId: progress.batchId,
                  filename: progress.filename,
                  fileSize: upload.file.size,
                  textEmbeddings: progress.embeddingsCreated || 0,
                  imageEmbeddings: progress.imagesProcessed || 0,
                  durationMs: 0,
                  streamingUsed: false,
                  message: progress.message,
                  duplicate: false
                }
              }
            : upload
        ),
        activeUploads: Math.max(0, state.activeUploads - 1)
      };
    }
    
    // Si progress ERROR, mettre à jour le status
    if (progress.stage === 'ERROR') {
      return {
        ...state,
        uploads: state.uploads.map(upload =>
          upload.batchId === progress.batchId
            ? {
                ...upload,
                status: 'error' as const,  // ✅ Changer vers error
                progress: 0,
                error: progress.message
              }
            : upload
        ),
        activeUploads: Math.max(0, state.activeUploads - 1)
      };
    }  
    // Sinon, juste update progress
    return state;
  }),


  // Upload async error
  on(IngestionActions.uploadFileAsyncError, (state, { fileId, error }) => ({
    ...state,
    uploads: state.uploads.map(upload =>
      upload.id === fileId
        ? { ...upload, status: 'error' as const, error }
        : upload
    ),
    activeUploads: Math.max(0, state.activeUploads - 1),
    stats: {
      ...state.stats,
      errors: state.stats.errors + 1
    }
  })),
  
  // Batch async
  on(IngestionActions.uploadBatchAsync, (state) => ({
    ...state,
    loading: true
  })),
  
  on(IngestionActions.uploadBatchAsyncAccepted, (state, { batchId, fileCount }) => ({
    ...state,
    loading: false,
    // Les uploads individuels seront trackés via WebSocket
  })),
  
  on(IngestionActions.uploadBatchAsyncError, (state, { error }) => ({
    ...state,
    loading: false,
    error
  })),

  // ========================================================================
  // SYNCHRONE UPLOAD REDUCERS
  // ========================================================================
  // Add files
  on(IngestionActions.addFilesToUpload, (state, { files }) => {
    const newUploads: UploadFile[] = files.map((file, index) => ({
      id: `upload_${Date.now()}_${index}`,
      file,
      progress: 0,
      status: 'pending'
    }));
    
    return {
      ...state,
      uploads: [...state.uploads, ...newUploads],
      stats: {
        ...state.stats,
        total: state.stats.total + files.length
      }
    };
  }),
  
  // Upload file
  on(IngestionActions.uploadFile, (state, { fileId }) => ({
    ...state,
    uploads: state.uploads.map(upload =>
      upload.id === fileId
        ? { ...upload, status: 'uploading' as const, progress: 0 }
        : upload
    ),
    activeUploads: state.activeUploads + 1
  })),
  
  // Upload success
  on(IngestionActions.uploadFileSuccess, (state, { fileId, response }) => ({
    ...state,
    uploads: state.uploads.map(upload =>
      upload.id === fileId
        ? { 
            ...upload, 
            status: 'success' as const, 
            progress: 100,
            batchId: response.batchId,
            response
          }
        : upload
    ),
    activeUploads: Math.max(0, state.activeUploads - 1),
    stats: {
      ...state.stats,
      success: state.stats.success + 1
    }
  })),
  
  // Upload error
  on(IngestionActions.uploadFileError, (state, { fileId, error }) => ({
    ...state,
    uploads: state.uploads.map(upload =>
      upload.id === fileId
        ? { ...upload, 
            status: 'error' as const,
            progress: 0,
            error }
        : upload
    ),
    activeUploads: Math.max(0, state.activeUploads - 1),
    stats: {
      ...state.stats,
      errors: state.stats.errors + 1
    }
  })),

  // ✅ MISE À JOUR: Upload duplicate avec existingBatchId
  on(IngestionActions.uploadFileDuplicate, (state, { fileId, batchId, existingBatchId, message }) => ({
    ...state,
    uploads: state.uploads.map(upload =>
      upload.id === fileId
        ? { 
            ...upload, 
            status: 'duplicate' as const, 
            progress: 100,
            batchId,
            existingBatchId,  // ✅ AJOUTER
            message: message || '⚠️ Ce fichier a déjà été uploadé'
          }
        : upload
    ),
    activeUploads: Math.max(0, state.activeUploads - 1),
    stats: {
      ...state.stats,
      duplicates: state.stats.duplicates + 1
    }
  })),

  // Remove file
  on(IngestionActions.removeFile, (state, { fileId }) => ({
    ...state,
    uploads: state.uploads.filter(upload => upload.id !== fileId)
  })),
  
  // Clear all
  on(IngestionActions.clearAllFiles, () => initialState),
  
  // Load strategies success
  on(IngestionActions.loadStrategiesSuccess, (state, { strategies }) => ({
    ...state,
    strategies,
    loading: false
  })),
  
  // Load active ingestions success
  on(IngestionActions.loadActiveIngestionsSuccess, (state, { ingestions }) => ({
    ...state,
    activeIngestions: ingestions,
    loading: false
  })),

    //  AJOUTER: Remove upload
  on(IngestionActions.removeUpload, (state, { fileId }) => ({
    ...state,
    uploads: state.uploads.filter(u => u.id !== fileId)
  })),

  //  AJOUTER: Clear completed
  on(IngestionActions.clearCompletedUploads, (state) => ({
    ...state,
    uploads: state.uploads.filter(u => 
      u.status === 'pending' || u.status === 'uploading'
    )
  })),

  //  AJOUTER: Toggle mode
  on(IngestionActions.toggleUploadMode, (state) => ({
    ...state,
    uploadMode: state.uploadMode === 'sync' ? 'async' : 'sync'
  })),



  // ========================================================================
  // UPLOAD MANAGEMENT (existant)
  // ========================================================================
  
  on(IngestionActions.addUpload, (state, { upload }) => ({
    ...state,
    uploads: [...state.uploads, upload]
  })),
  
  on(IngestionActions.updateUploadStatus, (state, { fileId, status, batchId, existingBatchId, error }) => ({
    ...state,
    uploads: state.uploads.map(upload =>
      upload.id === fileId
        ? { ...upload, status, batchId, existingBatchId, error }
        : upload
    )
  })),
  
  on(IngestionActions.setUploadMode, (state, { mode }) => ({
    ...state,
    uploadMode: mode
  })),
  
  // ========================================================================
  // ✅ DELETE MANAGEMENT - VERSION OPTIMISTE
  // ========================================================================
  
  on(IngestionActions.removeUpload, (state, { fileId }) => {
    console.log(`🗑️ [Ingestion] Manual remove: ${fileId}`);
    return {
      ...state,
      uploads: state.uploads.filter(upload => upload.id !== fileId)
    };
  }),
  
  on(CrudActions.deleteBatch, (state, { batchId }) => {
    console.log(`🗑️ [Optimistic] Removing uploads for batch: ${batchId}`);
    
    return {
      ...state,
      uploads: state.uploads.filter(upload => {
        const uploadBatchId = upload.existingBatchId || upload.batchId;
        return uploadBatchId !== batchId;
      })
    };
  }),
  
  on(CrudActions.deleteBatchError, (state, { error }) => ({
    ...state,
    error
  })),
  
  on(CrudActions.deleteAllFilesSuccess, (state) => {
    console.log(`🗑️ [Ingestion] Clearing all uploads`);
    return {
      ...state,
      uploads: []
    };
  }),

// features/ingestion/store/ingestion.reducer.ts

  on(IngestionActions.uploadFileRateLimited, (state, { fileId, retryAfterSeconds, message }) => {
    console.log(`⏳ Upload ${fileId} rate limited - retry après ${retryAfterSeconds}s`);

      // ✅ Vérifier si ce fichier est déjà compté comme rate-limited
      const existingUpload = state.uploads.find(u => u.id === fileId);
      const isAlreadyRateLimited = existingUpload?.status === 'rate-limited';
    
    return {
      ...state,
      uploads: state.uploads.map(upload =>
        upload.id === fileId
          ? {
              ...upload,
              status: 'rate-limited' as const,
              error: message,
              retryAfterSeconds,
              progress: 0
            }
          : upload
      ),
      activeUploads: Math.max(0, state.activeUploads - 1),  // ✅ Décrémenter
      stats: {
        ...state.stats,
          // ✅ N'incrémenter que si ce n'était pas déjà rate-limited
        rateLimited: isAlreadyRateLimited 
          ? state.stats.rateLimited 
          : state.stats.rateLimited + 1
        }
    };
  })
);