import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface IngestionResponse {
  success: boolean;
  batchId: string;
  filename: string;
  fileSize: number;
  textEmbeddings: number;
  imageEmbeddings: number;
  durationMs: number;
  streamingUsed: boolean;
  message: string;
  duplicate: boolean;
  existingBatchId?: string;
}

export interface AsyncResponse {
  accepted: boolean;
  batchId: string;
  filename: string;
  message: string;
  statusUrl: string;
  duplicate: boolean;
  existingBatchId?: string;
}

export interface BatchResponse {
  success: boolean;
  batchId: string;
  fileCount: number;
  filenames: string[];
  totalSize: number;
  message: string;
  statusUrl: string;
  duplicateCount?: number;
  duplicateFiles?: string[];
  duplicateInfo?: { [filename: string]: string };
}

export interface BatchDetailedResponse {
  accepted: boolean;
  success: boolean;
  batchId: string;
  fileCount: number;
  totalSize: number;
  message: string;
  statusUrl: string;
  resultUrl: string;
  duplicateCount?: number;
  duplicateFiles?: string[];
  duplicateInfo?: { [filename: string]: string };
}

export interface StatusResponse {
  found: boolean;
  batchId: string;
  textEmbeddings: number;
  imageEmbeddings: number;
  totalEmbeddings: number;
  message: string;
}

export interface StatsResponse {
  strategiesCount: number;
  activeIngestions: number;
  trackerBatches: number;
  trackerEmbeddings: number;
  filesInProgress: number;
}

@Injectable({
  providedIn: 'root'
})
export class IngestionApiService {
  
  private apiUrl = `${environment.apiUrl}/v1/ingestion`;
  
  constructor(private http: HttpClient) {}
  
  /**
   * Upload fichier synchrone
   */
  uploadFile(file: File, batchId?: string): Observable<IngestionResponse> {
    const formData = new FormData();
    formData.append('file', file);
    if (batchId) {
      formData.append('batchId', batchId);
    }
    
    return this.http.post<IngestionResponse>(
      `${this.apiUrl}/upload`,
      formData
    );
  }
  
  /**
   * Upload asynchrone avec WebSocket tracking
   */
  uploadFileAsync(file: File, batchId?: string): Observable<AsyncResponse> {
    const formData = new FormData();
    formData.append('file', file);
    if (batchId) {
      formData.append('batchId', batchId);
    }
    
    return this.http.post<AsyncResponse>(
      `${this.apiUrl}/upload/async`,
      formData
    );
  }

  /**
   * Upload batch asynchrone
   */
  uploadBatchAsync(files: File[], batchId?: string): Observable<AsyncResponse> {
    const formData = new FormData();
    files.forEach(file => formData.append('files', file));
    if (batchId) {
      formData.append('batchId', batchId);
    }
    
    return this.http.post<AsyncResponse>(
      `${this.apiUrl}/upload/batch/async`,
      formData
    );
  }
  
  /**
   * Upload batch de fichiers
   */
  uploadBatch(files: File[], batchId?: string): Observable<any> {
    const formData = new FormData();
    files.forEach(file => formData.append('files', file));
    if (batchId) {
      formData.append('batchId', batchId);
    }
    
    return this.http.post(
      `${this.apiUrl}/upload/batch`,
      formData
    );
  }
  
  /**
   * Upload batch détaillé
   */
  uploadBatchDetailed(
    files: File[], 
    batchId?: string
  ): Observable<BatchDetailedResponse> {
    const formData = new FormData();
    files.forEach(file => formData.append('files', file));
    if (batchId) {
      formData.append('batchId', batchId);
    }
    
    return this.http.post<BatchDetailedResponse>(
      `${this.apiUrl}/upload/batch/detailed`,
      formData
    );
  }
  
  /**
   * Statut d'un batch
   */
  getBatchStatus(batchId: string): Observable<StatusResponse> {
    return this.http.get<StatusResponse>(
      `${this.apiUrl}/status/${batchId}`
    );
  }
  
  /**
   * Rollback d'un batch
   */
  rollbackBatch(batchId: string): Observable<any> {
    return this.http.delete(
      `${this.apiUrl}/rollback/${batchId}`
    );
  }
  
  /**
   * Ingestions actives
   */
  getActiveIngestions(): Observable<any> {
    return this.http.get(`${this.apiUrl}/active`);
  }
  
  /**
   * Statistiques globales
   */
  getStats(): Observable<StatsResponse> {
    return this.http.get<StatsResponse>(`${this.apiUrl}/stats`);
  }
  
  /**
   * Health check détaillé
   */
  getDetailedHealth(): Observable<any> {
    return this.http.get(`${this.apiUrl}/health/detailed`);
  }
  
  /**
   * Liste des stratégies
   */
  getStrategies(): Observable<any> {
    return this.http.get(`${this.apiUrl}/strategies`);
  }
}