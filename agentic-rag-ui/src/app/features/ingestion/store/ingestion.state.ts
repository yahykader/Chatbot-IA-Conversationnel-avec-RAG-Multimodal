// features/ingestion/store/ingestion.state.ts
import { AsyncResponse, IngestionResponse } from '../../../core/services/ingestion-api.service';

// ✅ MODIFIER: Ajouter 'rate-limited' au type status
export interface UploadFile {
  id: string;
  file: File;
  progress: number;
  status: 'pending' | 'uploading' | 'success' | 'error' | 'duplicate' | 'rate-limited';
  batchId?: string;
  response?: IngestionResponse;
  asyncResponse?: AsyncResponse;
  error?: string;
  message?: string;
  existingBatchId?: string;
  retryAfterSeconds?: number;
}

export interface IngestionState {
  uploads: UploadFile[];
  activeUploads: number;
  stats: {
    total: number;
    success: number;
    errors: number;
    duplicates: number;
    rateLimited: number;
  };
  strategies: any[];
  activeIngestions: any[];
  loading: boolean;
  error: string | null;
  uploadMode: 'sync' | 'async';
}

export const initialState: IngestionState = {
  uploads: [],
  activeUploads: 0,
  stats: {
    total: 0,
    success: 0,
    errors: 0,
    duplicates: 0,
    rateLimited: 0
  },
  strategies: [],
  activeIngestions: [],
  loading: false,
  error: null,
  uploadMode: 'async'
};