import { createReducer, on } from '@ngrx/store';
import { initialProgressState } from './progress.state';
import * as ProgressActions from './progress.actions';

export const progressReducer = createReducer(
  initialProgressState,
  
  on(ProgressActions.connectWebSocket, (state) => ({
    ...state,
    connecting: true,
    error: null
  })),
  
  on(ProgressActions.connectWebSocketSuccess, (state) => ({
    ...state,
    connected: true,
    connecting: false,
    error: null
  })),
  
  on(ProgressActions.connectWebSocketError, (state, { error }) => ({
    ...state,
    connected: false,
    connecting: false,
    error
  })),
  
  on(ProgressActions.disconnectWebSocket, (state) => ({
    ...state,
    connected: false,
    connecting: false
  })),
  
  on(ProgressActions.subscribeToProgress, (state, { batchId }) => ({
    ...state,
    subscribedBatches: [...state.subscribedBatches, batchId]
  })),
  
  on(ProgressActions.unsubscribeFromProgress, (state, { batchId }) => ({
    ...state,
    subscribedBatches: state.subscribedBatches.filter(id => id !== batchId)
  })),
  
  // ✅ MISE À JOUR: Ajouter propriétés auto-clear
  on(ProgressActions.progressUpdate, (state, { progress }) => {
    
    // Si COMPLETED ou ERROR, marquer pour auto-clear
    if (progress.stage === 'COMPLETED' || progress.stage === 'ERROR') {
      return {
        ...state,
        progressByBatch: {
          ...state.progressByBatch,
          [progress.batchId]: {
            ...progress,
            _shouldClear: true,  // ✅ Marqueur
            _clearAt: Date.now() + 5000  // ✅ Timestamp (5 secondes)
          }
        }
      };
    }
    
    // Sinon, update normal
    return {
      ...state,
      progressByBatch: {
        ...state.progressByBatch,
        [progress.batchId]: progress
      }
    };
  }),
  
  on(ProgressActions.clearProgress, (state, { batchId }) => {
    const { [batchId]: removed, ...rest } = state.progressByBatch;
    return {
      ...state,
      progressByBatch: rest
    };
  }),
  
  on(ProgressActions.clearAllProgress, (state) => ({
    ...state,
    progressByBatch: {}
  }))
);