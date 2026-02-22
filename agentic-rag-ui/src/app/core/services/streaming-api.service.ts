// core/services/streaming-api.service.ts

import { Injectable, NgZone } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';


export interface StreamingRequest {
  query: string;
  conversationId?: string | null;
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
  
  private apiUrl = `${environment.apiUrl}/v1/assistant`;
  
  constructor(private zone: NgZone, private http: HttpClient) {}
  
  stream(request: StreamingRequest): Observable<StreamEvent> {
    return new Observable(observer => {
      
      let params = new HttpParams().set('query', request.query);
      
      if (request.conversationId) {
        params = params.set('conversationId', request.conversationId);
      }
      
      const url = `${this.apiUrl}/stream?${params.toString()}`;
      
      console.log('📡 Opening EventSource:', url);
      
      const eventSource = new EventSource(url, { 
        withCredentials: false 
      });
      
      // Event: connected
      eventSource.addEventListener('connected', (event: MessageEvent) => {
        this.zone.run(() => {
          try {
            console.log('✅ Connected raw:', event.data);
            const data = this.parseEventData(event.data);
            
            observer.next({
              type: 'connected',
              sessionId: data.sessionId,
              conversationId: data.conversationId
            });
          } catch (error) {
            console.error('❌ Error parsing connected event:', error);
          }
        });
      });
      
      // ✅ Event: token - Nettoyer les <cite>
      eventSource.addEventListener('token', (event: MessageEvent) => {
        this.zone.run(() => {
          try {
            const data = this.parseEventData(event.data);
            
            // Nettoyer les citations du texte streamé
            const cleanText = this.removeCitations(data.text || '');
            
            observer.next({
              type: 'token',
              text: cleanText,
              index: data.count || 0
            });
          } catch (error) {
            console.error('❌ Error parsing token event:', error);
          }
        });
      });
      
      // Event: complete
    eventSource.addEventListener('complete', (event: MessageEvent) => {
      this.zone.run(() => {
        try {
          console.log('🟢🟢🟢 COMPLETE EVENT RECEIVED');
          
          const data = this.parseEventData(event.data);
          
          observer.next({
            type: 'complete',
            response: data.response,
            metadata: data.metadata
          });
          
          console.log('✅ Stream complete emitted');
          
          observer.complete();
          eventSource.close();
        } catch (error) {
          console.error('❌ Error in complete event:', error);
          observer.complete();
          eventSource.close();
        }
      });
    });
      
      // Event: error
      eventSource.addEventListener('error', (event: MessageEvent) => {
        this.zone.run(() => {
          try {
            const data = this.parseEventData(event.data);
            
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
      
      eventSource.onerror = (error) => {
        this.zone.run(() => {
          console.error('❌ EventSource onerror:', error);
          if (eventSource.readyState !== EventSource.CLOSED) {
            observer.error(error);
          }
          eventSource.close();
        });
      };
      
      return () => {
        console.log('🛑 Closing EventSource');
        eventSource.close();
      };
    });
  }

  // ✅ AJOUT: Méthode pour supprimer les citations
  private removeCitations(text: string): string {
    // Supprimer <cite index="X">...</cite>
    return text.replace(/<cite\s+index="\d+">.*?<\/cite>/g, '');
  }

  private parseEventData(rawData: string): any {
    try {
      const parsed = JSON.parse(rawData);
      
      if (parsed.data && typeof parsed.data === 'object') {
        return parsed.data;
      }
      
      return parsed;
      
    } catch (firstError) {
      try {
        const lines = rawData.split('\n');
        
        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const jsonStr = line.substring(6);
            const parsed = JSON.parse(jsonStr);
            
            if (parsed.data && typeof parsed.data === 'object') {
              return parsed.data;
            }
            
            return parsed;
          }
        }
        
        throw new Error('No data field found in SSE event');
        
      } catch (secondError) {
        console.error('❌ Failed to parse event data:', rawData);
        throw secondError;
      }
    }
  }
  
  cancelStream(sessionId: string): Observable<void> {
    return this.http.post<void>(
      `${this.apiUrl}/stream/${sessionId}/cancel`,
      {}
    );
  }
  
  healthCheck(): Observable<string> {
    return this.http.get(`${this.apiUrl}/stream/health`, {
      responseType: 'text'
    });
  }
}