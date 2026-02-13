import { createFeatureSelector, createSelector } from '@ngrx/store';
import { ProgressState } from './progress.state';

export const selectProgressState = createFeatureSelector<ProgressState>('progress');

export const selectWebSocketConnected = createSelector(
  selectProgressState,
  (state) => state.connected
);

export const selectWebSocketConnecting = createSelector(
  selectProgressState,
  (state) => state.connecting
);

export const selectProgressByBatch = createSelector(
  selectProgressState,
  (state) => state.progressByBatch
);

export const selectProgressForBatch = (batchId: string) => createSelector(
  selectProgressByBatch,
  (progressByBatch) => progressByBatch[batchId]
);

export const selectAllProgress = createSelector(
  selectProgressByBatch,
  (progressByBatch) => Object.values(progressByBatch)
);

/**
 * ✅ CORRIGÉ: Sélectionner seulement les progress actifs
 */
export const selectActiveProgress = createSelector(
  selectAllProgress,
  (allProgress) => allProgress.filter(p => 
    p && 
    p.stage !== 'COMPLETED' && 
    p.stage !== 'ERROR' &&
    !p._shouldClear  // ✅ Propriété existe maintenant
  )
);

/**
 * ✅ CORRIGÉ: Sélectionner les progress complétés récemment
 */
export const selectRecentlyCompleted = createSelector(
  selectAllProgress,
  (allProgress) => {
    const now = Date.now();
    return allProgress.filter(p => 
      p &&
      (p.stage === 'COMPLETED' || p.stage === 'ERROR') &&
      p._clearAt && 
      p._clearAt > now  // ✅ Propriété existe maintenant
    );
  }
);

export const selectActiveProgressCount = createSelector(
  selectActiveProgress,
  (activeProgress) => activeProgress.length
);

export const selectWebSocketError = createSelector(
  selectProgressState,
  (state) => state.error
);
