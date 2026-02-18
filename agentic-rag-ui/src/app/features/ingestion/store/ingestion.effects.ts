// features/ingestion/store/ingestion.effects.ts
import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { map, catchError, switchMap, mergeMap, filter, withLatestFrom } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

import * as IngestionActions from './ingestion.actions';
import * as ProgressActions from './progress.actions';
import * as IngestionSelectors from './ingestion.selectors';
import * as RateLimitActions from '../store/rate-limit/rate-limit.actions';
import { selectIsRateLimited }  from '../store/rate-limit/rate-limit.selectors';
import { IngestionApiService } from '../../../core/services/ingestion-api.service';
import { Store } from '@ngrx/store';
import { selectRateLimitedUploads } from './ingestion.selectors';

@Injectable()
export class IngestionEffects {
  
  private actions$ = inject(Actions);
  private store = inject(Store);
  private ingestionApi = inject(IngestionApiService);
 
  
// ========================================================================
// ASYNC UPLOAD ACTIONS
// ========================================================================

/**
   * Effect Upload Async
   * 
   * 1. Upload le fichier en async
   * 2. Reçoit batchId immédiatement
   * 3. Subscribe au WebSocket pour tracking
   * 4. Gère les erreurs et doublons
   */
  uploadFileAsync$ = createEffect(() =>
    this.actions$.pipe(
      ofType(IngestionActions.uploadFileAsync),
      mergeMap(({ fileId, file }) =>
        this.ingestionApi.uploadFileAsync(file).pipe(
          map(response => {
            // ✅ Vérifier si doublon dans la réponse
            if (response.duplicate) {
              console.warn(`⚠️ Doublon détecté: ${file.name}`);
              
              return IngestionActions.uploadFileDuplicate({
                fileId,
                batchId: response.batchId,
                existingBatchId: response.existingBatchId,  // ✅ AJOUTER
                message: response.message
              });
            }
            
            console.log(`✅ Upload async accepté: ${response.batchId}`);
            
            return IngestionActions.uploadFileAsyncAccepted({
              fileId,
              response
            });
          }),
          catchError(error => {
            console.error('❌ Upload async error:', error);

            // ✅ AJOUT: Gérer erreur 429 (Rate Limit)
            if (error.status === 429) {
              const data = error.error;
              const retryAfter = data?.retryAfterSeconds || 60;
              
              console.warn(`⏳ Rate limit atteint: retry après ${retryAfter}s`);
              
              return of(IngestionActions.uploadFileRateLimited({
                fileId,
                retryAfterSeconds: retryAfter,
                message: data?.message || 'Rate limit dépassé. Réessayez dans quelques instants.'
              }));
            }
            
            // ✅ Gérer erreur 409 (Duplicate)
            if (error.isDuplicate || error.status === 409) {
              const data = error.data || error.error;
              
              return of(IngestionActions.uploadFileDuplicate({
                fileId,
                batchId: data?.batchId || 'unknown',
                existingBatchId: data?.existingBatchId,  // ✅ AJOUTER
                message: data?.message || 'Fichier déjà uploadé'
              }));
            }
            
            return of(IngestionActions.uploadFileAsyncError({
              fileId,
              error: error.message || 'Async upload failed'
            }));
          })
        )
      )
    )
);


  /**
   * ✅ NOUVEAU: Synchroniser progress COMPLETED avec upload status
   */
  syncProgressToUploadStatus$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProgressActions.progressUpdate),
      filter(({ progress }) => 
        progress.stage === 'COMPLETED' || 
        progress.stage === 'ERROR'
      ),
      withLatestFrom(this.store.select(IngestionSelectors.selectUploads)),
      map(([{ progress }, uploads]) => {
        
        // Trouver l'upload correspondant au batchId
        const upload = uploads.find(u => u.batchId === progress.batchId);
        
        if (!upload) {
          console.warn(`Upload not found for batchId: ${progress.batchId}`);
          return { type: 'NO_OP' };
        }
        
        // Si COMPLETED
        if (progress.stage === 'COMPLETED') {
          console.log(`✅ Upload completed: ${upload.file.name}`);
          
          return IngestionActions.uploadFileSuccess({
            fileId: upload.id,
            response: {
              success: true,
              batchId: progress.batchId,
              filename: progress.filename,
              fileSize: upload.file.size,
              textEmbeddings: progress.embeddingsCreated || 0,
              imageEmbeddings: progress.imagesProcessed || 0,
              durationMs: 0,
              streamingUsed: false,
              message: progress.message,
              duplicate: false
            }
          });
        }
        
        // Si ERROR
        if (progress.stage === 'ERROR') {
          console.error(`❌ Upload error: ${upload.file.name}`);
          
          return IngestionActions.uploadFileError({
            fileId: upload.id,
            error: progress.message
          });
        }
        
        return { type: 'NO_OP' };
      })
    )
  );


  /**
   * ✅ NOUVEAU: Synchroniser progress COMPLETED → upload success
   */
  syncProgressCompleted$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProgressActions.progressUpdate),
      filter(({ progress }) => progress.stage === 'COMPLETED'),
      withLatestFrom(this.store.select(IngestionSelectors.selectUploads)),
      map(([{ progress }, uploads]) => {
        
        // Trouver l'upload correspondant
        const upload = uploads.find(u => u.batchId === progress.batchId);
        
        if (!upload) {
          console.warn(`⚠️ Upload not found for batchId: ${progress.batchId}`);
          return { type: 'NO_OP' };
        }
        
        console.log(`✅ Progress COMPLETED → uploadFileSuccess: ${upload.file.name}`);
        
        // Dispatch uploadFileSuccess
        return IngestionActions.uploadFileSuccess({
          fileId: upload.id,
          response: {
            success: true,
            batchId: progress.batchId,
            filename: progress.filename,
            fileSize: upload.file.size,  // ✅ Depuis upload.file
            textEmbeddings: progress.embeddingsCreated || 0,
            imageEmbeddings: progress.imagesProcessed || 0,
            durationMs: 0,
            streamingUsed: false,
            message: progress.message,
            duplicate: false
          }
        });
      })
    )
  );
  
  /**
   * ✅ NOUVEAU: Synchroniser progress ERROR → upload error
   */
  syncProgressError$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProgressActions.progressUpdate),
      filter(({ progress }) => progress.stage === 'ERROR'),
      withLatestFrom(this.store.select(IngestionSelectors.selectUploads)),
      map(([{ progress }, uploads]) => {
        
        // Trouver l'upload correspondant
        const upload = uploads.find(u => u.batchId === progress.batchId);
        
        if (!upload) {
          console.warn(`⚠️ Upload not found for batchId: ${progress.batchId}`);
          return { type: 'NO_OP' };
        }
        
        console.error(`❌ Progress ERROR → uploadFileError: ${upload.file.name}`);
        
        // Dispatch uploadFileError
        return IngestionActions.uploadFileError({
          fileId: upload.id,
          error: progress.message
        });
      })
    )
  );
  /**
   * ✅ NOUVEAU: Auto-subscribe au WebSocket après upload async accepté
   */
  subscribeAfterAsyncUpload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(IngestionActions.uploadFileAsyncAccepted),
      map(({ response }) => {
        console.log(`📡 Auto-subscribe to progress: ${response.batchId}`);
        
        return ProgressActions.subscribeToProgress({
          batchId: response.batchId
        });
      })
    )
  );
  
  /**
   * ✅ NOUVEAU: Effect Upload Batch Async
   */
  uploadBatchAsync$ = createEffect(() =>
    this.actions$.pipe(
      ofType(IngestionActions.uploadBatchAsync),
      switchMap(({ files }) =>
        this.ingestionApi.uploadBatchAsync(files).pipe(
          map(response => {
            console.log(`✅ Batch async accepté: ${response.batchId}`);
            
            return IngestionActions.uploadBatchAsyncAccepted({
              batchId: response.batchId,
              fileCount: files.length
            });
          }),
          catchError(error =>
            of(IngestionActions.uploadBatchAsyncError({
              error: error.message || 'Batch async upload failed'
            }))
          )
        )
      )
    )
  );
  
  /**
   * ✅ NOUVEAU: Auto-subscribe batch WebSocket
   */
  subscribeAfterBatchAsync$ = createEffect(() =>
    this.actions$.pipe(
      ofType(IngestionActions.uploadBatchAsyncAccepted),
      map(({ batchId }) => {
        console.log(`📡 Auto-subscribe to batch progress: ${batchId}`);
        
        return ProgressActions.subscribeToProgress({ batchId });
      })
    )
  );

  // ========================================================================
  // SYNCHRONE UPLOAD ACTIONS
  // ========================================================================
  uploadFile$ = createEffect(() =>
    this.actions$.pipe(
      ofType(IngestionActions.uploadFile),
      mergeMap(({ fileId, file }) =>
        this.ingestionApi.uploadFile(file).pipe(
          map(response => {
            // Vérifier doublon
            if (response.duplicate) {
              return IngestionActions.uploadFileDuplicate({
                fileId,
                batchId: response.batchId,
                existingBatchId: response.existingBatchId,  // ✅ AJOUTER
                message: response.message
              });
            }
            
            return IngestionActions.uploadFileSuccess({
              fileId,
              response
            });
          }),
          catchError(error => {
            // Gérer 409
            if (error.isDuplicate || error.status === 409) {
              const data = error.data || error.error;
              
              return of(IngestionActions.uploadFileDuplicate({
                fileId,
                batchId: data?.batchId || 'unknown',
                existingBatchId: data?.existingBatchId,  // ✅ AJOUTER
                message: data?.message || 'Fichier déjà uploadé'
              }));
            }
            
            return of(IngestionActions.uploadFileError({
              fileId,
              error: error.message || 'Upload failed'
            }));
          })
        )
      )
    )
  );
  
  uploadBatch$ = createEffect(() =>
    this.actions$.pipe(
      ofType(IngestionActions.uploadBatch),
      switchMap(({ files }) =>
        this.ingestionApi.uploadBatchDetailed(files).pipe(
          map(response =>
            IngestionActions.uploadBatchSuccess({
              batchId: response.batchId,
              count: response.fileCount
            })
          ),
          catchError(error =>
            of(IngestionActions.uploadBatchError({
              error: error.message || 'Batch upload failed'
            }))
          )
        )
      )
    )
  );
  
  loadStrategies$ = createEffect(() =>
    this.actions$.pipe(
      ofType(IngestionActions.loadStrategies),
      switchMap(() =>
        this.ingestionApi.getStrategies().pipe(
          map(response =>
            IngestionActions.loadStrategiesSuccess({
              strategies: response.strategies
            })
          ),
          catchError(() => of({ type: 'NO_OP' }))
        )
      )
    )
  );
  
  loadActiveIngestions$ = createEffect(() =>
    this.actions$.pipe(
      ofType(IngestionActions.loadActiveIngestions),
      switchMap(() =>
        this.ingestionApi.getActiveIngestions().pipe(
          map(response =>
            IngestionActions.loadActiveIngestionsSuccess({
              ingestions: response.ingestions
            })
          ),
          catchError(() => of({ type: 'NO_OP' }))
        )
      )
    )
  );

/*   autoRetryRateLimited$ = createEffect(() =>
  this.actions$.pipe(
    ofType(RateLimitActions.rateLimitReset),
    withLatestFrom(this.store.select(selectRateLimitedUploads)),
    filter(([_, uploads]) => uploads.length > 0),
    mergeMap(([_, uploads]) =>
      uploads.map(upload =>
        IngestionActions.uploadFileAsync({
          fileId: upload.id,
          file: upload.file
        })
      )
    )
  )
);*/
} 
