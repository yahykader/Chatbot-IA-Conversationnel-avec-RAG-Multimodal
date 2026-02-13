import { Injectable, NgZone } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { Client, StompSubscription } from '@stomp/stompjs';
import { environment } from '../../../../environements/environement';

export interface UploadProgress {
 batchId: string;
  filename: string;
  stage: 'UPLOAD' | 'PROCESSING' | 'CHUNKING' | 'EMBEDDING' | 'IMAGES' | 'COMPLETED' | 'ERROR';
  progressPercentage: number;
  message: string;
  embeddingsCreated?: number;
  chunksCreated?: number;
  imagesProcessed?: number;
  timestamp?: string;
  
  // ✅ AJOUTER: Propriétés pour auto-clear
  _shouldClear?: boolean;  // Marqueur pour auto-clear
  _clearAt?: number;       // Timestamp pour auto-clear
}

@Injectable({
  providedIn: 'root'
})
export class WebSocketProgressService {
  
  private stompClient: Client | null = null;
  private subscriptions = new Map<string, StompSubscription>();
  private progressSubject = new Subject<UploadProgress>();
  
  public progress$ = this.progressSubject.asObservable();
  
  constructor(private zone: NgZone) {
    console.log('✅ WebSocketProgressService initialized');
  }
  
  /**
   * ✅ Connecter au WebSocket
   * Tente d'abord WebSocket natif, fallback sur SockJS si échec
   */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      
      if (this.stompClient?.connected) {
        console.log('✅ WebSocket déjà connecté');
        resolve();
        return;
      }
      
      // ========================================================================
      // OPTION 1: WebSocket Natif (Recommandé)
      // ========================================================================
      const wsUrl = `${environment.wsUrl}${environment.wsProgressEndpoint}`;
      
      console.log(`🔌 Connecting to WebSocket: ${wsUrl}`);
      
      this.stompClient = new Client({
        // ✅ WebSocket natif
        brokerURL: wsUrl,
        
        // Heartbeat
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        
        // Reconnexion automatique
        reconnectDelay: 5000,
        
        // Callbacks
        onConnect: (frame) => {
          this.zone.run(() => {
            console.log('✅ WebSocket connecté (natif)', frame);
            resolve();
          });
        },
        
        onStompError: (frame) => {
          this.zone.run(() => {
            console.error('❌ STOMP error', frame);
            
            // ✅ Fallback SockJS si erreur
            console.log('🔄 Tentative fallback SockJS...');
            this.connectWithSockJS()
              .then(resolve)
              .catch(reject);
          });
        },
        
        onWebSocketError: (event) => {
          this.zone.run(() => {
            console.error('❌ WebSocket error', event);
            
            // ✅ Fallback SockJS
            console.log('🔄 Tentative fallback SockJS...');
            this.connectWithSockJS()
              .then(resolve)
              .catch(reject);
          });
        },
        
        onWebSocketClose: (event) => {
          this.zone.run(() => {
            console.log('🔌 WebSocket closed', event);
          });
        },
        
        debug: (str) => {
          // console.log('DEBUG:', str);
        }
      });
      
      // Activer
      this.stompClient.activate();
    });
  }
  
  /**
   * ✅ Fallback SockJS (si WebSocket natif échoue)
   */
  private connectWithSockJS(): Promise<void> {
    return new Promise((resolve, reject) => {
      
      // Note: Nécessite SockJS installé
      // npm install sockjs-client
      
      const httpUrl = `${environment.apiUrl}${environment.wsProgressEndpoint}`;
      
      console.log(`🔌 Connecting with SockJS: ${httpUrl}`);
      
      // Import dynamique de SockJS
      import('sockjs-client').then((SockJS) => {
        
        this.stompClient = new Client({
          // ✅ SockJS fallback
          webSocketFactory: () => new SockJS.default(httpUrl),
          
          heartbeatIncoming: 10000,
          heartbeatOutgoing: 10000,
          reconnectDelay: 5000,
          
          onConnect: (frame) => {
            this.zone.run(() => {
              console.log('✅ WebSocket connecté (SockJS)', frame);
              resolve();
            });
          },
          
          onStompError: (frame) => {
            this.zone.run(() => {
              console.error('❌ STOMP error (SockJS)', frame);
              reject(new Error(frame.headers['message']));
            });
          },
          
          debug: (str) => {
            // console.log('DEBUG (SockJS):', str);
          }
        });
        
        this.stompClient.activate();
        
      }).catch((error) => {
        console.error('❌ Impossible de charger SockJS', error);
        reject(new Error('SockJS fallback failed'));
      });
    });
  }
  
  disconnect(): void {
    this.subscriptions.forEach((sub, batchId) => {
      sub.unsubscribe();
      console.log(`🔌 Unsubscribed from batch: ${batchId}`);
    });
    this.subscriptions.clear();
    
    if (this.stompClient) {
      this.stompClient.deactivate();
      this.stompClient = null;
      console.log('🔌 WebSocket déconnecté');
    }
  }
  
  subscribeToProgress(batchId: string): Observable<UploadProgress> {
    
    const progressObservable = new Observable<UploadProgress>(observer => {
      
      if (!this.stompClient?.connected) {
        console.error('❌ WebSocket non connecté');
        observer.error(new Error('WebSocket not connected'));
        return;
      }
      
      const destination = `/topic/upload-progress/${batchId}`;
      
      console.log(`📡 Subscribe to: ${destination}`);
      
      const subscription = this.stompClient.subscribe(
        destination,
        (message) => {
          this.zone.run(() => {
            try {
              const progress: UploadProgress = JSON.parse(message.body);
              
              console.log(`📊 Progress [${batchId}]: ${progress.progressPercentage}%`);
              
              observer.next(progress);
              this.progressSubject.next(progress);
              
              if (progress.stage === 'COMPLETED' || progress.stage === 'ERROR') {
                console.log(`✅ Upload ${progress.stage}: ${batchId}`);
                observer.complete();
              }
              
            } catch (e) {
              console.error('❌ Erreur parsing progress', e);
              observer.error(e);
            }
          });
        }
      );
      
      this.subscriptions.set(batchId, subscription);
      
      return () => {
        subscription.unsubscribe();
        this.subscriptions.delete(batchId);
        console.log(`🔌 Unsubscribed from: ${destination}`);
      };
    });
    
    return progressObservable;
  }
  
  unsubscribeFromProgress(batchId: string): void {
    const subscription = this.subscriptions.get(batchId);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(batchId);
      console.log(`🔌 Unsubscribed from batch: ${batchId}`);
    }
  }
  
  isConnected(): boolean {
    return this.stompClient?.connected ?? false;
  }
}