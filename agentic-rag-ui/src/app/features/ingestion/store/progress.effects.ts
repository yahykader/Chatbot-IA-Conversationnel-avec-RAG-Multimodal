import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of, timer } from 'rxjs';
import { 
  map, 
  catchError, 
  switchMap, 
  tap,
  filter,
  mergeMap,
  delay,
  withLatestFrom
} from 'rxjs/operators';
import * as ProgressActions from './progress.actions';
import * as IngestionActions from './ingestion.actions';
import { WebSocketProgressService } from '../../../core/services/websocket-progress.service';
import { Store } from '@ngrx/store';

@Injectable()
export class ProgressEffects {
  
  private actions$ = inject(Actions);
  private store = inject(Store);
  private wsProgress = inject(WebSocketProgressService);
  
  /**
   * Effect: Connect WebSocket
   */
  connectWebSocket$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProgressActions.connectWebSocket),
      switchMap(() =>
        this.wsProgress.connect().then(
          () => ProgressActions.connectWebSocketSuccess(),
          (error) => ProgressActions.connectWebSocketError({ 
            error: error.message 
          })
        )
      )
    )
  );
  
  /**
   * Effect: Disconnect WebSocket
   */
  disconnectWebSocket$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProgressActions.disconnectWebSocket),
      tap(() => this.wsProgress.disconnect())
    ),
    { dispatch: false }
  );
  
  /**
   * Effect: Subscribe to progress when file starts uploading
   */
  subscribeOnUpload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(IngestionActions.uploadFile),
      map(({ fileId, file }) => {
        // Generate batchId (ou utiliser celui fourni)
        const batchId = `batch_${Date.now()}`;
        
        return ProgressActions.subscribeToProgress({ batchId });
      })
    )
  );
  
  /**
   * Effect: Subscribe to batch progress
   */
  subscribeToProgress$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProgressActions.subscribeToProgress),
      mergeMap(({ batchId }) =>
        this.wsProgress.subscribeToProgress(batchId).pipe(
          map(progress => ProgressActions.progressUpdate({ progress })),
          catchError(error =>
            of(ProgressActions.progressError({ 
              batchId, 
              error: error.message 
            }))
          )
        )
      )
    )
  );
  
  /**
   * Effect: Unsubscribe from progress
   */
  unsubscribeFromProgress$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProgressActions.unsubscribeFromProgress),
      tap(({ batchId }) => this.wsProgress.unsubscribeFromProgress(batchId))
    ),
    { dispatch: false }
  );
  
  /**
   * Effect: Auto-clear completed progress after 5 seconds
   */
  autoClearCompleted$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProgressActions.progressCompleted),
      switchMap(({ batchId }) =>
        of(ProgressActions.clearProgress({ batchId })).pipe(
          // Delay 5 seconds
          tap(() => setTimeout(() => {}, 5000))
        )
      )
    )
  );


 /**
   * ✅ NOUVEAU: Auto-clear progress après COMPLETED/ERROR
   */
  autoClearCompletedProgress$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProgressActions.progressUpdate),
      // Filtrer seulement COMPLETED ou ERROR
      filter(({ progress }) => 
        progress.stage === 'COMPLETED' || 
        progress.stage === 'ERROR'
      ),
      tap(({ progress }) => {
        console.log(`🧹 Progress ${progress.stage} détecté: ${progress.batchId} - auto-clear dans 5s`);
      }),
      // Attendre 5 secondes puis clear
      switchMap(({ progress }) =>
        timer(5000).pipe(
          tap(() => {
            console.log(`🗑️ Clearing progress: ${progress.batchId}`);
            this.wsProgress.unsubscribeFromProgress(progress.batchId);
          }),
          map(() => ProgressActions.clearProgress({ 
            batchId: progress.batchId 
          }))
        )
      )
    )
  );
  
  /**
   * ✅ NOUVEAU: Clear progress pour les doublons aussi
   */
  clearDuplicateProgress$ = createEffect(() =>
    this.actions$.pipe(
      ofType(IngestionActions.uploadFileDuplicate),
      // Delay 5 secondes puis clear
      delay(5000),
      map(({ batchId }) => {
        console.log(`🧹 Clearing duplicate progress: ${batchId}`);
        return ProgressActions.clearProgress({ batchId });
      })
    )
  );
  
  /**
   * ✅ NOUVEAU: Unsubscribe du WebSocket après clear
   */
  unsubscribeAfterClear$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProgressActions.clearProgress),
      tap(({ batchId }) => {
        console.log(`🔌 Unsubscribing from progress: ${batchId}`);
        this.wsProgress.unsubscribeFromProgress(batchId);
      })
    ),
    { dispatch: false }
  );

  /**
   * ✅ NOUVEAU: Auto-clear progress quand upload status change
   */
  clearProgressOnStatusChange$ = createEffect(() =>
    this.actions$.pipe(
      ofType(
        IngestionActions.uploadFileSuccess,
        IngestionActions.uploadFileError,
        IngestionActions.uploadFileDuplicate,
        IngestionActions.uploadFileAsyncAccepted
      ),
      // Attendre 3 secondes puis clear
      switchMap(action => {
        const batchId = 'batchId' in action ? action.batchId : null;
        
        if (!batchId) return of();
        
        return timer(3000).pipe(
          tap(() => {
            console.log(`🧹 Auto-clearing progress for: ${batchId}`);
            this.wsProgress.unsubscribeFromProgress(batchId);
          }),
          map(() => ProgressActions.clearProgress({ batchId }))
        );
      })
    )
  );
  
  /**
   * ✅ NOUVEAU: Clear progress pour uploads terminés
   */
  clearCompletedProgress$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProgressActions.progressUpdate),
      filter(({ progress }) => 
        progress.stage === 'COMPLETED' || 
        progress.stage === 'ERROR'
      ),
      switchMap(({ progress }) =>
        timer(5000).pipe(
          tap(() => {
            console.log(`🧹 Auto-clearing completed progress: ${progress.batchId}`);
            this.wsProgress.unsubscribeFromProgress(progress.batchId);
          }),
          map(() => ProgressActions.clearProgress({ 
            batchId: progress.batchId 
          }))
        )
      )
    )
  );

}