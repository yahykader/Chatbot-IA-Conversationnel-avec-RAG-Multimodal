// ============================================================================
// SERVICE - assistant-api.service.ts
// ============================================================================
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environements/environement';
import { UploadResponse } from '../store/assistant.models';

@Injectable({
  providedIn: 'root'
})
export class AssistantApiService {
  
  private readonly API_URL = environment.apiUrl || 'http://localhost:8080/api/assistant';
  
  constructor(private http: HttpClient) {}

  /**
   * ✅ STREAMING SSE - Version cumulative (content complet à chaque fois)
   */
  sendMessageStream(userId: string, message: string): Observable<string> {
    return new Observable<string>(observer => {
      const url = `${this.API_URL}/chat/stream?userId=${encodeURIComponent(userId)}&message=${encodeURIComponent(message)}`;
      
      console.log('🚀 Connexion SSE:', url);
      
      const eventSource = new EventSource(url);
      let accumulatedContent = '';
      
      // ✅ Event "chunk" : on reçoit du texte par morceaux
      eventSource.addEventListener('chunk', (event: MessageEvent) => {
        try {
          const chunk = event.data;
          
          if (chunk && chunk !== '[DONE]') {
            accumulatedContent += chunk;
            
            // ✅ On envoie le contenu cumulé (pas juste le delta)
            observer.next(accumulatedContent);
          }
        } catch (error) {
          console.error('❌ Erreur parsing chunk:', error);
        }
      });
      
      // ✅ Event "final" : réponse complète (optionnel si déjà accumulée)
      eventSource.addEventListener('final', (event: MessageEvent) => {
        try {
          const finalContent = event.data;
          if (finalContent && finalContent !== '[DONE]') {
            observer.next(finalContent);
          }
        } catch (error) {
          console.error('❌ Erreur parsing final:', error);
        }
      });
      
      // ✅ Event "done" : fin du stream
      eventSource.addEventListener('done', () => {
        console.log('✅ Stream terminé');
        eventSource.close();
        observer.complete();
      });
      
      // ✅ Event "error" : gestion des erreurs
      eventSource.addEventListener('error', (event: MessageEvent) => {
        console.error('❌ Erreur SSE:', event.data);
        observer.error(new Error(event.data || 'Erreur de streaming'));
        eventSource.close();
      });
      
      // ✅ Erreur de connexion
      eventSource.onerror = (error) => {
        console.error('❌ Erreur connexion SSE:', error);
        observer.error(new Error('Erreur de connexion au serveur'));
        eventSource.close();
      };
      
      // ✅ Cleanup à la désinscription
      return () => {
        console.log('🔌 Fermeture SSE');
        eventSource.close();
      };
    });
  }

  /**
   * ✅ UPLOAD DE FICHIER
   */
  uploadFile(file: File): Observable<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    
    console.log('📤 Upload fichier:', file.name);
    
    return this.http.post<UploadResponse>(
      `${this.API_URL}/upload`,
      formData
    );
  }
}