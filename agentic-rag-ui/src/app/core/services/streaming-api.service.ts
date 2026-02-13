// core/services/streaming-api.service.ts
import { Injectable, NgZone } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environements/environement';
import { HttpClient } from '@angular/common/http';


export interface StreamingRequest {
  query: string;
  conversationId?: string;
  options?: any;
}

export interface StreamEvent {
  type: 'connected' | 'token' | 'complete' | 'error';
  sessionId?: string;
  conversationId?: string;
  text?: string;
  index?: number;
  response?: any;
  metadata?: any;
  error?: string;
  code?: string;
}

@Injectable({
  providedIn: 'root'
})
export class StreamingApiService {
  
  private apiUrl = `${environment.apiUrl}/api/v1/assistant`;
  
  constructor(private zone: NgZone, private http: HttpClient) {}
  
  /**
   * Stream SSE principal
   */
  stream(request: StreamingRequest): Observable<StreamEvent> {
    return new Observable(observer => {
      
      // Créer EventSource pour SSE
      const eventSource = new EventSource(
        `${this.apiUrl}/stream`,
        { withCredentials: false }
      );
      
      // Envoyer la requête POST (limitation SSE)
      fetch(`${this.apiUrl}/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(request)
      });
      
      // Event: connected
      eventSource.addEventListener('connected', (event: any) => {
        this.zone.run(() => {
          const data = JSON.parse(event.data);
          observer.next({
            type: 'connected',
            sessionId: data.sessionId,
            conversationId: data.conversationId
          });
        });
      });
      
      // Event: token
      eventSource.addEventListener('token', (event: any) => {
        this.zone.run(() => {
          const data = JSON.parse(event.data);
          observer.next({
            type: 'token',
            text: data.text,
            index: data.index
          });
        });
      });
      
      // Event: complete
      eventSource.addEventListener('complete', (event: any) => {
        this.zone.run(() => {
          const data = JSON.parse(event.data);
          observer.next({
            type: 'complete',
            response: data.response,
            metadata: data.metadata
          });
          observer.complete();
          eventSource.close();
        });
      });
      
      // Event: error
      eventSource.addEventListener('error', (event: any) => {
        this.zone.run(() => {
          try {
            const data = JSON.parse(event.data);
            observer.next({
              type: 'error',
              error: data.message,
              code: data.code
            });
          } catch (e) {
            observer.next({
              type: 'error',
              error: 'Stream connection error'
            });
          }
          observer.error(event);
          eventSource.close();
        });
      });
      
      // Cleanup
      return () => {
        eventSource.close();
      };
    });
  }
  
  /**
   * Annuler un stream
   */
  cancelStream(sessionId: string): Observable<void> {
    return this.http.post<void>(
      `${this.apiUrl}/stream/${sessionId}/cancel`,
      {}
    );
  }
  
  /**
   * Health check streaming
   */
  healthCheck(): Observable<string> {
    return this.http.get(`${this.apiUrl}/stream/health`, {
      responseType: 'text'
    });
  }
  
}