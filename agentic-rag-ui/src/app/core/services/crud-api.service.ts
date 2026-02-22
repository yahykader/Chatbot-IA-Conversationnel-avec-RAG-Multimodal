// core/services/crud-api.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface DeleteResponse {
  success: boolean;
  deletedCount: number;
  embeddingId?: string;
  batchId?: string;
  type?: string;
  message: string;
  timestamp?: Date;
}

export interface DuplicateCheckResponse {
  isDuplicate: boolean;
  filename: string;
  existingBatchId?: string;
  message: string;
}

export interface BatchInfoResponse {
  found: boolean;
  batchId: string;
  textEmbeddings: number;
  imageEmbeddings: number;
  totalEmbeddings: number;
  message: string;
}

@Injectable({
  providedIn: 'root'
})
export class CrudApiService {
  
  private apiUrl = `${environment.apiUrl}/v1/crud`;
  
  constructor(private http: HttpClient) {}
  
  /**
   * Supprimer un fichier par ID
   */
  deleteFile(
    embeddingId: string, 
    type: 'text' | 'image' = 'text'
  ): Observable<DeleteResponse> {
    return this.http.delete<DeleteResponse>(
      `${this.apiUrl}/file/${embeddingId}?type=${type}`
    );
  }
  
  /**
   * Supprimer tous les fichiers d'un batch
   */
  deleteBatch(batchId: string): Observable<DeleteResponse> {
    return this.http.delete<DeleteResponse>(
      `${this.apiUrl}/batch/${batchId}/files`
    );
  }
  
  /**
   * Supprimer plusieurs fichiers texte
   */
  deleteTextBatch(embeddingIds: string[]): Observable<DeleteResponse> {
    return this.http.request<DeleteResponse>(
      'DELETE',
      `${this.apiUrl}/files/text/batch`,
      { body: embeddingIds }
    );
  }
  
  /**
   * Supprimer plusieurs fichiers image
   */
  deleteImageBatch(embeddingIds: string[]): Observable<DeleteResponse> {
    return this.http.request<DeleteResponse>(
      'DELETE',
      `${this.apiUrl}/files/image/batch`,
      { body: embeddingIds }
    );
  }
  
  /**
   * Supprimer TOUS les fichiers (DANGER)
   */
  deleteAllFiles(confirmation: string): Observable<DeleteResponse> {
    return this.http.delete<DeleteResponse>(
      `${this.apiUrl}/files/all?confirmation=${confirmation}`
    );
  }
  
  /**
   * Vérifier si un fichier est un doublon
   */
  checkDuplicate(file: File): Observable<DuplicateCheckResponse> {
    const formData = new FormData();
    formData.append('file', file);
    
    return this.http.post<DuplicateCheckResponse>(
      `${this.apiUrl}/check-duplicate`,
      formData
    );
  }
  
  /**
   * Informations sur un batch
   */
  getBatchInfo(batchId: string): Observable<BatchInfoResponse> {
    return this.http.get<BatchInfoResponse>(
      `${this.apiUrl}/batch/${batchId}/info`
    );
  }
  
  /**
   * Statistiques système
   */
  getSystemStats(): Observable<any> {
    return this.http.get(`${this.apiUrl}/stats/system`);
  }
}