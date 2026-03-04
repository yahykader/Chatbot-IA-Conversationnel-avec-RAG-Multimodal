// // ============================================================================
// // SERVICE - assistant-api.service.ts (VERSION v2.1 - FIXED)
// // ============================================================================
// import { Injectable } from '@angular/core';
// import { 
//   HttpClient, 
//   HttpHeaders, 
//   HttpEvent, 
//   HttpEventType, 
//   HttpParams,
//   HttpResponse,
//   HttpProgressEvent 
// } from '@angular/common/http';
// import { Observable, Subject } from 'rxjs';
// import { map, filter, tap } from 'rxjs/operators';
// import { environment } from '../../../../environements/environement';
// import { UploadResponse, UploadStatusResponse } from '../store/assistant.models';

// /**
//  * ✅ NOUVEAU : Interface pour la progression d'upload
//  */
// export interface UploadProgressEvent {
//   file: File;
//   progress: number;
// }

// @Injectable({
//   providedIn: 'root'
// })
// export class AssistantApiService {
  
//   private readonly API_URL = environment.apiUrl || 'http://localhost:8090/api/assistant';
  
//   // ✅ NOUVEAU : Subject pour la progression des uploads
//   private uploadProgressSubject = new Subject<UploadProgressEvent>();
  
//   constructor(private http: HttpClient) {
//     console.log('✅ [ApiService] Initialisé avec URL:', this.API_URL);
//   }

//   // ==================== CHAT STREAMING ====================

//   /**
//    * ✅ STREAMING SSE - Version cumulative (content complet à chaque fois)
//    */
//   sendMessageStream(userId: string, message: string): Observable<string> {
//     return new Observable<string>(observer => {
//       const url = `${this.API_URL}/chat/stream?userId=${encodeURIComponent(userId)}&message=${encodeURIComponent(message)}`;
      
//       console.log('🚀 [ApiService] Connexion SSE:', url);
      
//       const eventSource = new EventSource(url);
//       let accumulatedContent = '';
      
//       // ✅ Event "chunk" : on reçoit du texte par morceaux
//       eventSource.addEventListener('chunk', (event: MessageEvent) => {
//         try {
//           const chunk = event.data;
          
//           if (chunk && chunk !== '[DONE]') {
//             accumulatedContent += chunk;
            
//             // ✅ On envoie le contenu cumulé (pas juste le delta)
//             observer.next(accumulatedContent);
//           }
//         } catch (error) {
//           console.error('❌ [ApiService] Erreur parsing chunk:', error);
//         }
//       });
      
//       // ✅ Event "final" : réponse complète (optionnel si déjà accumulée)
//       eventSource.addEventListener('final', (event: MessageEvent) => {
//         try {
//           const finalContent = event.data;
//           if (finalContent && finalContent !== '[DONE]') {
//             observer.next(finalContent);
//           }
//         } catch (error) {
//           console.error('❌ [ApiService] Erreur parsing final:', error);
//         }
//       });
      
//       // ✅ Event "done" : fin du stream
//       eventSource.addEventListener('done', () => {
//         console.log('✅ [ApiService] Stream terminé');
//         eventSource.close();
//         observer.complete();
//       });
      
//       // ✅ Event "error" : gestion des erreurs
//       eventSource.addEventListener('error', (event: MessageEvent) => {
//         console.error('❌ [ApiService] Erreur SSE:', event.data);
//         observer.error(new Error(event.data || 'Erreur de streaming'));
//         eventSource.close();
//       });
      
//       // ✅ Erreur de connexion
//       eventSource.onerror = (error) => {
//         console.error('❌ [ApiService] Erreur connexion SSE:', error);
//         observer.error(new Error('Erreur de connexion au serveur'));
//         eventSource.close();
//       };
      
//       // ✅ Cleanup à la désinscription
//       return () => {
//         console.log('🔌 [ApiService] Fermeture SSE');
//         eventSource.close();
//       };
//     });
//   }

//   // ==================== FILE UPLOAD ====================

//   /**
//    * ✅ CORRIGÉ : UPLOAD DE FICHIER - Retourne HttpEvent pour la progression
//    */
//   uploadFile(file: File, userId: number = 1): Observable<HttpEvent<UploadResponse>> {
//     const formData = new FormData();
//     formData.append('file', file);
//     formData.append('userId', userId.toString());
    
//     console.log('📤 [ApiService] Upload fichier:', file.name, 'User:', userId);
    
//     return this.http.post<UploadResponse>(
//       `${this.API_URL}/upload`,
//       formData,
//       {
//         reportProgress: true,
//         observe: 'events'
//       }
//     ).pipe(
//       tap((event: HttpEvent<UploadResponse>) => {
//         // Gérer la progression
//         if (event.type === HttpEventType.UploadProgress) {
//           const progressEvent = event as HttpProgressEvent;
//           if (progressEvent.total) {
//             const progress = Math.round((100 * progressEvent.loaded) / progressEvent.total);
//             console.log(`📊 [ApiService] Progression: ${progress}% - ${file.name}`);
            
//             // Émettre la progression
//             this.uploadProgressSubject.next({ file, progress });
//           }
//         }
        
//         // Log la réponse finale
//         if (event.type === HttpEventType.Response) {
//           console.log('✅ [ApiService] Upload terminé:', event.body);
//         }
//       })
//     );
//   }

//   /**
//    * ✅ NOUVEAU : Observable pour suivre la progression des uploads
//    */
//   getUploadProgress(): Observable<UploadProgressEvent> {
//     return this.uploadProgressSubject.asObservable();
//   }

//   /**
//    * ✅ NOUVEAU : Récupérer le statut d'un upload (polling)
//    */
//   getUploadStatus(jobId: string): Observable<UploadStatusResponse> {
//     console.log('🔄 [ApiService] Récupération statut:', jobId);
    
//     return this.http.get<UploadStatusResponse>(
//       `${this.API_URL}/upload/status/${jobId}`
//     );
//   }

//   /**
//    * ✅ NOUVEAU : Lister tous les uploads
//    */
//   listUploads(userId?: number): Observable<UploadStatusResponse[]> {
//     let params = new HttpParams();
//     if (userId !== undefined) {
//       params = params.set('userId', String(userId));
//     }

//     console.log('📋 [ApiService] Liste uploads', { userId });

//     return this.http.get<UploadStatusResponse[]>(
//       `${this.API_URL}/uploads`,
//       {
//         params,
//         responseType: 'json' as const,
//       }
//     );
//   }

//   /**
//    * ✅ CORRIGÉ : Upload multiple de fichiers
//    * Retourne UploadResponse[] (réponses finales uniquement)
//    */
//   uploadMultipleFiles(files: File[], userId: number = 1): Observable<UploadResponse[]> {
//     console.log('📤 [ApiService] Upload multiple:', files.length, 'fichiers');
    
//     if (files.length === 0) {
//       return new Observable<UploadResponse[]>(observer => {
//         observer.next([]);
//         observer.complete();
//       });
//     }
    
//     // Créer un observable pour chaque fichier qui retourne uniquement la réponse finale
//     const uploadObservables = files.map(file => 
//       this.uploadFile(file, userId).pipe(
//         // ✅ Filtrer pour ne garder que la réponse HTTP finale
//         filter((event): event is HttpResponse<UploadResponse> => 
//           event.type === HttpEventType.Response
//         ),
//         // ✅ Extraire le body de la réponse
//         map(event => event.body!)
//       )
//     );
    
//     // Retourner un observable qui émet chaque réponse individuellement
//     return new Observable<UploadResponse[]>(observer => {
//       const responses: UploadResponse[] = [];
//       let completedCount = 0;
      
//       uploadObservables.forEach((uploadObs, index) => {
//         uploadObs.subscribe({
//           next: (response) => {
//             responses[index] = response; // ✅ LIGNE 202 CORRIGÉE : response est maintenant UploadResponse
//             completedCount++;
            
//             console.log(`✅ [ApiService] Upload ${completedCount}/${files.length} terminé`);
            
//             // Émettre toutes les réponses collectées jusqu'à présent
//             observer.next([...responses]);
            
//             // Si tous les uploads sont terminés
//             if (completedCount === files.length) {
//               observer.complete();
//             }
//           },
//           error: (error) => {
//             console.error('❌ [ApiService] Erreur upload:', error);
//             completedCount++;
            
//             // Continuer même en cas d'erreur
//             if (completedCount === files.length) {
//               observer.complete();
//             }
//           }
//         });
//       });
//     });
//   }

//   // ==================== UTILITY METHODS ====================

//   /**
//    * ✅ NOUVEAU : Vérifier la santé du serveur
//    */
//   healthCheck(): Observable<any> {
//     console.log('🏥 [ApiService] Health check');
    
//     return this.http.get(`${this.API_URL}/health`, {
//       headers: new HttpHeaders({
//         'Content-Type': 'application/json'
//       })
//     });
//   }

//   /**
//    * ✅ NOUVEAU : Obtenir la configuration du serveur
//    */
//   getServerConfig(): Observable<any> {
//     console.log('⚙️ [ApiService] Récupération config serveur');
    
//     return this.http.get(`${this.API_URL}/config`);
//   }

//   /**
//    * ✅ NOUVEAU : Annuler un upload en cours (si supporté par le backend)
//    */
//   cancelUpload(jobId: string): Observable<any> {
//     console.log('🚫 [ApiService] Annulation upload:', jobId);
    
//     return this.http.delete(`${this.API_URL}/upload/${jobId}`);
//   }

//   /**
//    * ✅ NOUVEAU : Supprimer un fichier uploadé
//    */
//   deleteFile(jobId: string): Observable<any> {
//     console.log('🗑️ [ApiService] Suppression fichier:', jobId);
    
//     return this.http.delete(`${this.API_URL}/files/${jobId}`);
//   }

//   /**
//    * ✅ NOUVEAU : Forcer le re-processing d'un fichier
//    */
//   reprocessFile(jobId: string): Observable<any> {
//     console.log('🔄 [ApiService] Re-processing fichier:', jobId);
    
//     return this.http.post(`${this.API_URL}/upload/${jobId}/reprocess`, {});
//   }

//   /**
//    * ✅ NOUVEAU : Force le re-upload d'un fichier (bypass duplicate check)
//    */
//   forceReupload(file: File, userId: number = 1): Observable<HttpEvent<UploadResponse>> {
//     const formData = new FormData();
//     formData.append('file', file);
//     formData.append('userId', userId.toString());
//     formData.append('force', 'true'); // Flag pour forcer l'upload
    
//     console.log('🔄 [ApiService] Force re-upload:', file.name);
    
//     return this.http.post<UploadResponse>(
//       `${this.API_URL}/upload`,
//       formData,
//       {
//         reportProgress: true,
//         observe: 'events'
//       }
//     ).pipe(
//       tap((event: HttpEvent<UploadResponse>) => {
//         if (event.type === HttpEventType.UploadProgress) {
//           const progressEvent = event as HttpProgressEvent;
//           if (progressEvent.total) {
//             const progress = Math.round((100 * progressEvent.loaded) / progressEvent.total);
//             console.log(`📊 [ApiService] Re-upload: ${progress}% - ${file.name}`);
//             this.uploadProgressSubject.next({ file, progress });
//           }
//         }
//       })
//     );
//   }

//   // ==================== ERROR HANDLING ====================

//   /**
//    * ✅ NOUVEAU : Extraire le message d'erreur
//    */
//   private getErrorMessage(error: any): string {
//     if (typeof error === 'string') return error;
//     if (error?.error?.message) return error.error.message;
//     if (error?.message) return error.message;
//     if (error?.statusText) return error.statusText;
//     return 'Une erreur est survenue';
//   }

//   /**
//    * ✅ NOUVEAU : Logger les erreurs de manière cohérente
//    */
//   private logError(context: string, error: any): void {
//     console.error(`❌ [ApiService] ${context}:`, {
//       message: this.getErrorMessage(error),
//       status: error?.status,
//       statusText: error?.statusText,
//       error
//     });
//   }
// }