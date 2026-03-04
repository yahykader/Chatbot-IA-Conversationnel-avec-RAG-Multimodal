// features/ingestion/store/progress.actions.ts
import { createAction, props } from '@ngrx/store';
import { UploadProgress } from '../../../core/services/websocket-progress.service';

// WebSocket connection
export const connectWebSocket = createAction(
  '[Progress] Connect WebSocket'
);

export const connectWebSocketSuccess = createAction(
  '[Progress] Connect WebSocket Success'
);

export const connectWebSocketError = createAction(
  '[Progress] Connect WebSocket Error',
  props<{ error: string }>()
);

export const disconnectWebSocket = createAction(
  '[Progress] Disconnect WebSocket'
);

// Progress subscription
export const subscribeToProgress = createAction(
  '[Progress] Subscribe To Progress',
  props<{ batchId: string }>()
);

export const unsubscribeFromProgress = createAction(
  '[Progress] Unsubscribe From Progress',
  props<{ batchId: string }>()
);

// Progress updates
export const progressUpdate = createAction(
  '[Progress] Progress Update',
  props<{ progress: UploadProgress }>()
);

export const progressCompleted = createAction(
  '[Progress] Progress Completed',
  props<{ batchId: string }>()
);

export const progressError = createAction(
  '[Progress] Progress Error',
  props<{ batchId: string; error: string }>()
);

// Clear
export const clearProgress = createAction(
  '[Progress] Clear Progress',
  props<{ batchId: string }>()
);

export const clearAllProgress = createAction(
  '[Progress] Clear All Progress'
);