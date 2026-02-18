// features/ingestion/store/ingestion.selectors.ts
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { IngestionState } from './ingestion.state';

export const selectIngestionState = createFeatureSelector<IngestionState>('ingestion');

export const selectUploads = createSelector(
  selectIngestionState,
  (state) => state.uploads
);

export const selectPendingUploads = createSelector(
  selectUploads,
  (uploads) => uploads.filter(u => u.status === 'pending')
);

export const selectActiveUploads = createSelector(
  selectUploads,
  (uploads) => uploads.filter(u => u.status === 'uploading')
);

/**
 * ✅ CORRECTION: Uploads complétés = success, error, duplicate
 */
export const selectCompletedUploads = createSelector(
  selectUploads,
  (uploads) => uploads.filter(u => 
    u.status === 'success' || 
    u.status === 'error' || 
    u.status === 'duplicate'
  )
);

export const selectStats = createSelector(
  selectIngestionState,
  (state) => state.stats
);

export const selectStrategies = createSelector(
  selectIngestionState,
  (state) => state.strategies
);

export const selectActiveIngestions = createSelector(
  selectIngestionState,
  (state) => state.activeIngestions
);

// ✅ AJOUTER: Upload mode selector
export const selectUploadMode = createSelector(
  selectIngestionState,
  (state) => state.uploadMode
);

export const selectRateLimitedUploads = createSelector(
  selectIngestionState,
  (state) => state.uploads.filter(u => u.status === 'rate-limited')
);

export const selectRateLimitedCount = createSelector(
  selectIngestionState,
  (state) => state.stats.rateLimited
);
