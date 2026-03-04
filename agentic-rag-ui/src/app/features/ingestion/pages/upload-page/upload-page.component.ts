// features/ingestion/pages/upload-page/upload-page.component.ts
import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';
import { take } from 'rxjs/operators';

import * as IngestionActions from '../../store/ingestion.actions';
import * as IngestionSelectors from '../../store/ingestion.selectors';
import * as ProgressActions from '../../store/progress.actions';
import * as ProgressSelectors from '../../store/progress.selectors';

import { UploadFile } from '../../store/ingestion.state';
import { UploadProgress } from '../../../../core/services/websocket-progress.service';

import { UploadZoneComponent } from '../../components/upload-zone/upload-zone.component';
import { UploadItemComponent } from '../../components/upload-item/upload-item.component';
import { ProgressPanelComponent } from '../../components/progress-panel/progress-panel.component';
import { DeleteAllButtonComponent } from '../../components/delete-all-button/delete-all-button.component';
import { selectIsRateLimited, selectRetryAfterSeconds } from '../../store/rate-limit/rate-limit.selectors';

@Component({
  selector: 'app-upload-page',
  standalone: true,
  imports: [
    CommonModule,
    UploadZoneComponent,
    UploadItemComponent,
    ProgressPanelComponent,
    DeleteAllButtonComponent,
  ],
  templateUrl: './upload-page.component.html',
  styleUrls: ['./upload-page.component.scss']
})
export class UploadPageComponent implements OnInit, OnDestroy {
  
  // Uploads
  pendingUploads$: Observable<UploadFile[]>;
  activeUploads$: Observable<UploadFile[]>;
  completedUploads$: Observable<UploadFile[]>;
  
  // Stats
  stats$: Observable<any>;
  uploadMode$: Observable<'sync' | 'async'>;
  
  // Progress
  activeProgress$: Observable<UploadProgress[]>;
  activeProgressCount$: Observable<number>;
  wsConnected$: Observable<boolean>;
  
  // Strategies
  strategies$: Observable<any[]>;

  // Rate limiting
  isRateLimited$: Observable<boolean>;
  retryAfterSeconds$: Observable<number>;
  
  constructor(private store: Store) {
    // Uploads
    this.pendingUploads$ = this.store.select(IngestionSelectors.selectPendingUploads);
    this.activeUploads$ = this.store.select(IngestionSelectors.selectActiveUploads);
    this.completedUploads$ = this.store.select(IngestionSelectors.selectCompletedUploads);
    
    // Stats
    this.stats$ = this.store.select(IngestionSelectors.selectStats);
    this.uploadMode$ = this.store.select(IngestionSelectors.selectUploadMode);
    
    // Progress
    this.activeProgress$ = this.store.select(ProgressSelectors.selectActiveProgress);
    this.activeProgressCount$ = this.store.select(ProgressSelectors.selectActiveProgressCount);
    this.wsConnected$ = this.store.select(ProgressSelectors.selectWebSocketConnected);
    
    // Strategies
    this.strategies$ = this.store.select(IngestionSelectors.selectStrategies);
  
    // Rate limiting
    this.isRateLimited$ = this.store.select(selectIsRateLimited);
    this.retryAfterSeconds$ = this.store.select(selectRetryAfterSeconds);
  }
  
  ngOnInit(): void {
    // Connecter WebSocket
    this.store.dispatch(ProgressActions.connectWebSocket());
    
    // Charger les stratégies
    this.store.dispatch(IngestionActions.loadStrategies());
  }
  
  ngOnDestroy(): void {
    // Déconnecter WebSocket
    this.store.dispatch(ProgressActions.disconnectWebSocket());
  }
  
  // File selection
  onFilesSelected(files: File[]): void {
    this.store.dispatch(IngestionActions.addFilesToUpload({ files }));
  }
  
  // Upload controls
  startUpload(fileId: string, file: File): void {
    this.uploadMode$.subscribe(mode => {
      if (mode === 'async') {
        this.store.dispatch(IngestionActions.uploadFileAsync({ fileId, file }));
      } else {
        this.store.dispatch(IngestionActions.uploadFile({ fileId, file }));
      }
    }).unsubscribe();
  }
  
  /**
   * ✅ CORRECTION: Uploader tous les fichiers en attente
  */
  startAllUploads(): void {
    // ✅ AJOUT: Vérifier rate limit AVANT d'uploader
    this.isRateLimited$.pipe(take(1)).subscribe(isRateLimited => {
      if (isRateLimited) {
        console.warn('⚠️ Rate limit actif - Upload bloqué');
        return;  // ✅ Arrêter immédiatement
      }
      
      this.pendingUploads$.pipe(take(1)).subscribe(uploads => {
        if (!uploads || uploads.length === 0) {
          console.warn('⚠️ Aucun fichier en attente');
          return;
        }
        
        console.log(`🚀 Starting ${uploads.length} uploads...`);
        
        uploads.forEach(upload => {
          this.uploadMode$.pipe(take(1)).subscribe(mode => {
            if (mode === 'async') {
              this.store.dispatch(
                IngestionActions.uploadFileAsync({ 
                  fileId: upload.id, 
                  file: upload.file 
                })
              );
            } else {
              this.store.dispatch(
                IngestionActions.uploadFile({ 
                  fileId: upload.id, 
                  file: upload.file 
                })
              );
            }
          });
        });
      });
    });
  }
  
  removeUpload(fileId: string): void {
    this.store.dispatch(IngestionActions.removeUpload({ fileId }));
  }
  
  clearCompleted(): void {
    this.store.dispatch(IngestionActions.clearCompletedUploads());
  }
  
  // Mode toggle
  toggleUploadMode(): void {
    this.store.dispatch(IngestionActions.toggleUploadMode());
  }
}