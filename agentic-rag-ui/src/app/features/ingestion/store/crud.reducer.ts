// features/crud/store/crud.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { initialCrudState, DeleteOperation } from './crud.state';
import * as CrudActions from './crud.actions';

export const crudReducer = createReducer(
  initialCrudState,
  
  // ========================================================================
  // DELETE FILE
  // ========================================================================
  
  on(CrudActions.deleteFile, (state, { embeddingId, fileType  }) => ({
    ...state,
    loading: true,
    activeDeleteOperations: state.activeDeleteOperations + 1,
    deleteOperations: [
      ...state.deleteOperations,
      {
        id: `file-${embeddingId}-${Date.now()}`,
        type : 'file',
        targetId: embeddingId,
        status: 'pending' as const,
        timestamp: new Date()
      }
    ]
  })),
  
  on(CrudActions.deleteFileSuccess, (state, { response }) => ({
    ...state,
    loading: false,
    activeDeleteOperations: Math.max(0, state.activeDeleteOperations - 1),
    deleteOperations: state.deleteOperations.map(op =>
      op.targetId === response.embeddingId
        ? { ...op, status: 'success' as const, deletedCount: response.deletedCount, message: response.message }
        : op
    ),
    error: null
  })),
  
  on(CrudActions.deleteFileError, (state, { embeddingId, error }) => ({
    ...state,
    loading: false,
    activeDeleteOperations: Math.max(0, state.activeDeleteOperations - 1),
    deleteOperations: state.deleteOperations.map(op =>
      op.targetId === embeddingId
        ? { ...op, status: 'error' as const, message: error }
        : op
    ),
    error
  })),
  
  // ========================================================================
  // DELETE BATCH
  // ========================================================================
  
  on(CrudActions.deleteBatch, (state, { batchId }) => ({
    ...state,
    loading: true,
    activeDeleteOperations: state.activeDeleteOperations + 1,
    deleteOperations: [
      ...state.deleteOperations,
      {
        id: `batch-${batchId}-${Date.now()}`,
        type: 'batch' as const,
        targetId: batchId,
        status: 'pending' as const,
        timestamp: new Date()
      }
    ]
  })),
  
  on(CrudActions.deleteBatchSuccess, (state, { response }) => ({
    ...state,
    loading: false,
    activeDeleteOperations: Math.max(0, state.activeDeleteOperations - 1),
    deleteOperations: state.deleteOperations.map(op =>
      op.targetId === response.batchId
        ? { ...op, status: 'success' as const, deletedCount: response.deletedCount, message: response.message }
        : op
    ),
    // Supprimer les infos du batch du cache
    batchInfos: Object.keys(state.batchInfos)
      .filter(key => key !== response.batchId)
      .reduce((acc, key) => ({ ...acc, [key]: state.batchInfos[key] }), {}),
    error: null
  })),
  
  on(CrudActions.deleteBatchError, (state, { batchId, error }) => ({
    ...state,
    loading: false,
    activeDeleteOperations: Math.max(0, state.activeDeleteOperations - 1),
    deleteOperations: state.deleteOperations.map(op =>
      op.targetId === batchId
        ? { ...op, status: 'error' as const, message: error }
        : op
    ),
    error
  })),
  
  // ========================================================================
  // DELETE TEXT BATCH
  // ========================================================================
  
  on(CrudActions.deleteTextBatch, (state, { embeddingIds }) => ({
    ...state,
    loading: true,
    activeDeleteOperations: state.activeDeleteOperations + 1,
    deleteOperations: [
      ...state.deleteOperations,
      {
        id: `text-batch-${Date.now()}`,
        type: 'text-batch' as const,
        targetId: embeddingIds.join(','),
        status: 'pending' as const,
        timestamp: new Date()
      }
    ]
  })),
  
  on(CrudActions.deleteTextBatchSuccess, (state, { response }) => ({
    ...state,
    loading: false,
    activeDeleteOperations: Math.max(0, state.activeDeleteOperations - 1),
    deleteOperations: state.deleteOperations.map(op =>
      op.type === 'text-batch' && op.status === 'pending'
        ? { ...op, status: 'success' as const, deletedCount: response.deletedCount, message: response.message }
        : op
    ),
    error: null
  })),
  
  on(CrudActions.deleteTextBatchError, (state, { error }) => ({
    ...state,
    loading: false,
    activeDeleteOperations: Math.max(0, state.activeDeleteOperations - 1),
    error
  })),
  
  // ========================================================================
  // DELETE IMAGE BATCH
  // ========================================================================
  
  on(CrudActions.deleteImageBatch, (state, { embeddingIds }) => ({
    ...state,
    loading: true,
    activeDeleteOperations: state.activeDeleteOperations + 1,
    deleteOperations: [
      ...state.deleteOperations,
      {
        id: `image-batch-${Date.now()}`,
        type: 'image-batch' as const,
        targetId: embeddingIds.join(','),
        status: 'pending' as const,
        timestamp: new Date()
      }
    ]
  })),
  
  on(CrudActions.deleteImageBatchSuccess, (state, { response }) => ({
    ...state,
    loading: false,
    activeDeleteOperations: Math.max(0, state.activeDeleteOperations - 1),
    deleteOperations: state.deleteOperations.map(op =>
      op.type === 'image-batch' && op.status === 'pending'
        ? { ...op, status: 'success' as const, deletedCount: response.deletedCount, message: response.message }
        : op
    ),
    error: null
  })),
  
  on(CrudActions.deleteImageBatchError, (state, { error }) => ({
    ...state,
    loading: false,
    activeDeleteOperations: Math.max(0, state.activeDeleteOperations - 1),
    error
  })),
  
  // ========================================================================
  // DELETE ALL FILES
  // ========================================================================
  
  on(CrudActions.deleteAllFiles, (state) => ({
    ...state,
    loading: true,
    activeDeleteOperations: state.activeDeleteOperations + 1,
    deleteOperations: [
      ...state.deleteOperations,
      {
        id: `all-${Date.now()}`,
        type: 'all' as const,
        targetId: 'all-files',
        status: 'pending' as const,
        timestamp: new Date()
      }
    ]
  })),
  
  on(CrudActions.deleteAllFilesSuccess, (state, { response }) => ({
    ...state,
    loading: false,
    activeDeleteOperations: Math.max(0, state.activeDeleteOperations - 1),
    deleteOperations: state.deleteOperations.map(op =>
      op.type === 'all' && op.status === 'pending'
        ? { ...op, status: 'success' as const, deletedCount: response.deletedCount, message: response.message }
        : op
    ),
    // Clear tous les caches
    batchInfos: {},
    duplicateChecks: {},
    error: null
  })),
  
  on(CrudActions.deleteAllFilesError, (state, { error }) => ({
    ...state,
    loading: false,
    activeDeleteOperations: Math.max(0, state.activeDeleteOperations - 1),
    error
  })),
  
  // ========================================================================
  // CHECK DUPLICATE
  // ========================================================================
  
  on(CrudActions.checkDuplicate, (state, { file }) => ({
    ...state,
    loading: true
  })),
  
  on(CrudActions.checkDuplicateSuccess, (state, { response }) => ({
    ...state,
    loading: false,
    duplicateChecks: {
      ...state.duplicateChecks,
      [response.filename]: {
        filename: response.filename,
        isDuplicate: response.isDuplicate,
        existingBatchId: response.existingBatchId,
        message: response.message,
        timestamp: new Date()
      }
    },
    error: null
  })),
  
  on(CrudActions.checkDuplicateError, (state, { filename, error }) => ({
    ...state,
    loading: false,
    error
  })),
  
  // ========================================================================
  // GET BATCH INFO
  // ========================================================================
  
  on(CrudActions.getBatchInfo, (state) => ({
    ...state,
    loading: true
  })),
  
  on(CrudActions.getBatchInfoSuccess, (state, { response }) => ({
    ...state,
    loading: false,
    batchInfos: {
      ...state.batchInfos,
      [response.batchId]: {
        batchId: response.batchId,
        found: response.found,
        textEmbeddings: response.textEmbeddings,
        imageEmbeddings: response.imageEmbeddings,
        totalEmbeddings: response.totalEmbeddings,
        message: response.message,
        timestamp: new Date()
      }
    },
    error: null
  })),
  
  on(CrudActions.getBatchInfoError, (state, { batchId, error }) => ({
    ...state,
    loading: false,
    error
  })),
  
  // ========================================================================
  // GET SYSTEM STATS
  // ========================================================================
  
  on(CrudActions.getSystemStats, (state) => ({
    ...state,
    loading: true
  })),
  
  on(CrudActions.getSystemStatsSuccess, (state, { stats }) => ({
    ...state,
    loading: false,
    systemStats: {
      ...stats,
      lastUpdate: new Date()
    },
    error: null
  })),
  
  on(CrudActions.getSystemStatsError, (state, { error }) => ({
    ...state,
    loading: false,
    error
  })),
  
  // ========================================================================
  // CLEAR
  // ========================================================================
  
  on(CrudActions.clearDeleteOperations, (state) => ({
    ...state,
    deleteOperations: []
  })),
  
  on(CrudActions.clearDuplicateChecks, (state) => ({
    ...state,
    duplicateChecks: {}
  })),
  
  on(CrudActions.clearBatchInfos, (state) => ({
    ...state,
    batchInfos: {}
  })),
  
  on(CrudActions.clearAll, () => initialCrudState)
);