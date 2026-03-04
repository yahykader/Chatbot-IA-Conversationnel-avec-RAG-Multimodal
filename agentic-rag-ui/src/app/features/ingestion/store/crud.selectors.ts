import { createFeatureSelector, createSelector } from '@ngrx/store';
import { CrudState } from './crud.state';

/**
 * Feature selector
 */
export const selectCrudState = createFeatureSelector<CrudState>('crud');

/**
 * Loading state
 */
export const selectCrudLoading = createSelector(
  selectCrudState,
  (state) => state.loading
);

/**
 * Error state
 */
export const selectCrudError = createSelector(
  selectCrudState,
  (state) => state.error
);

/**
 * Delete operations
 */
export const selectDeleteOperations = createSelector(
  selectCrudState,
  (state) => state.deleteOperations
);

export const selectActiveDeleteOperations = createSelector(
  selectCrudState,
  (state) => state.activeDeleteOperations
);

export const selectPendingDeleteOperations = createSelector(
  selectDeleteOperations,
  (operations) => operations.filter(op => op.status === 'pending')
);

export const selectSuccessDeleteOperations = createSelector(
  selectDeleteOperations,
  (operations) => operations.filter(op => op.status === 'success')
);

export const selectErrorDeleteOperations = createSelector(
  selectDeleteOperations,
  (operations) => operations.filter(op => op.status === 'error')
);

/**
 * Duplicate checks
 */
export const selectDuplicateChecks = createSelector(
  selectCrudState,
  (state) => state.duplicateChecks
);

export const selectDuplicateCheckByFilename = (filename: string) => createSelector(
  selectDuplicateChecks,
  (checks) => checks[filename]
);

/**
 * Batch infos
 */
export const selectBatchInfos = createSelector(
  selectCrudState,
  (state) => state.batchInfos
);

export const selectBatchInfoById = (batchId: string) => createSelector(
  selectBatchInfos,
  (infos) => infos[batchId]
);

/**
 * System stats
 */
export const selectSystemStats = createSelector(
  selectCrudState,
  (state) => state.systemStats
);

export const selectSystemHealthy = createSelector(
  selectSystemStats,
  (stats) => stats.redisHealthy === true
);

export const selectTotalEmbeddings = createSelector(
  selectSystemStats,
  (stats) => stats.totalEmbeddings || 0
);