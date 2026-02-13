// features/ingestion/store/progress.state.ts
import { UploadProgress } from '../../../core/services/websocket-progress.service';

export interface ProgressState {
  connected: boolean;
  connecting: boolean;
  error: string | null;
  progressByBatch: { [batchId: string]: UploadProgress };
  subscribedBatches: string[];  // ✅ AJOUTER
}

export const initialProgressState: ProgressState = {
  connected: false,
  connecting: false,
  error: null,
  progressByBatch: {},
  subscribedBatches: []
};