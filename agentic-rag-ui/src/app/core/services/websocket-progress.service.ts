import { Injectable, NgZone } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { Client, StompSubscription } from '@stomp/stompjs';
import { environment } from '../../../environments/environment';

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
  _shouldClear?: boolean;
  _clearAt?: number;
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

  // ============================================================
  // URL helpers — tout passe par nginx, jamais de hostname hardcodé
  // ============================================================

  private getWsUrl(endpoint: string): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}${endpoint}`;
    // ex: ws://localhost/ws  →  nginx proxie vers backend:8090/ws
  }

  private getHttpUrl(endpoint: string): string {
    return `${window.location.protocol}//${window.location.host}${endpoint}`;
    // ex: http://localhost/ws  →  nginx proxie vers backend:8090/ws (SockJS)
  }

  // ============================================================
  // Connexion principale (WebSocket natif STOMP)
  // ============================================================

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {

      if (this.stompClient?.connected) {
        console.log('✅ WebSocket déjà connecté');
        resolve();
        return;
      }

      const wsUrl = this.getWsUrl(environment.wsProgressEndpoint);
      console.log(`🔌 Connecting to WebSocket: ${wsUrl}`);

      this.stompClient = new Client({
        brokerURL: wsUrl,

        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        reconnectDelay: 5000,

        onConnect: (frame) => {
          this.zone.run(() => {
            console.log('✅ WebSocket connecté (natif)', frame);
            resolve();
          });
        },

        onStompError: (frame) => {
          this.zone.run(() => {
            console.error('❌ STOMP error', frame);
            console.log('🔄 Tentative fallback SockJS...');
            this.connectWithSockJS().then(resolve).catch(reject);
          });
        },

        onWebSocketError: (event) => {
          this.zone.run(() => {
            console.error('❌ WebSocket error', event);
            console.log('🔄 Tentative fallback SockJS...');
            this.connectWithSockJS().then(resolve).catch(reject);
          });
        },

        onWebSocketClose: (event) => {
          this.zone.run(() => {
            console.log('🔌 WebSocket closed', event);
          });
        },

        debug: (_str) => {
          // console.log('DEBUG:', _str);
        }
      });

      this.stompClient.activate();
    });
  }

  // ============================================================
  // Fallback SockJS
  // ============================================================

  private connectWithSockJS(): Promise<void> {
    return new Promise((resolve, reject) => {

      // http://localhost/ws  →  nginx proxie vers backend:8090/ws
      const httpUrl = this.getHttpUrl(environment.wsProgressEndpoint);
      console.log(`🔌 Connecting with SockJS: ${httpUrl}`);

      import('sockjs-client').then((SockJS) => {

        this.stompClient = new Client({
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

          debug: (_str) => {
            // console.log('DEBUG (SockJS):', _str);
          }
        });

        this.stompClient.activate();

      }).catch((error) => {
        console.error('❌ Impossible de charger SockJS', error);
        reject(new Error('SockJS fallback failed'));
      });
    });
  }

  // ============================================================
  // Souscription aux progress d'un batch
  // ============================================================

  subscribeToProgress(batchId: string): Observable<UploadProgress> {
    return new Observable<UploadProgress>(observer => {

      if (!this.stompClient?.connected) {
        console.error('❌ WebSocket non connecté');
        observer.error(new Error('WebSocket not connected'));
        return;
      }

      const destination = `/topic/upload-progress/${batchId}`;
      console.log(`📡 Subscribe to: ${destination}`);

      const subscription = this.stompClient.subscribe(destination, (message) => {
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
      });

      this.subscriptions.set(batchId, subscription);

      // Teardown
      return () => {
        subscription.unsubscribe();
        this.subscriptions.delete(batchId);
        console.log(`🔌 Unsubscribed from: ${destination}`);
      };
    });
  }

  // ============================================================
  // Gestion des souscriptions
  // ============================================================

  unsubscribeFromProgress(batchId: string): void {
    const subscription = this.subscriptions.get(batchId);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(batchId);
      console.log(`🔌 Unsubscribed from batch: ${batchId}`);
    }
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

  isConnected(): boolean {
    return this.stompClient?.connected ?? false;
  }
}