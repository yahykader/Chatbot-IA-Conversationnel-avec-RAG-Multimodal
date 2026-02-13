// features/ingestion/store/ingestion.state.ts
import { AsyncResponse, IngestionResponse } from '../../../core/services/ingestion-api.service';

export interface UploadFile {
  id: string;
  file: File;
  progress: number;
  status: 'pending' | 'uploading' | 'success' | 'error' | 'duplicate';
  batchId?: string;
  response?: IngestionResponse;
  asyncResponse?: AsyncResponse;
  error?: string;
  message?: string;
  existingBatchId?: string;
}

export interface IngestionState {
  uploads: UploadFile[];
  activeUploads: number;
  stats: {
    total: number;
    success: number;
    errors: number;
    duplicates: number;
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
    duplicates: 0
  },
  strategies: [],
  activeIngestions: [],
  loading: false,
  error: null,
  uploadMode: 'async'
};