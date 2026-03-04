import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { 
  map, 
  catchError, 
  mergeMap,
  tap
} from 'rxjs/operators';

import * as CrudActions from './crud.actions';
import { CrudApiService } from '../../../core/services/crud-api.service';
import { NotificationService } from '../../../core/services/notification.service';

@Injectable()
export class CrudEffects {
  
  private actions$ = inject(Actions);
  private crudApi = inject(CrudApiService);
  private notificationService = inject(NotificationService);
  
  // ========================================================================
  // DELETE FILE
  // ========================================================================
  
  deleteFile$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.deleteFile),
      tap(({ embeddingId, fileType  }) => {
        console.log(`🗑️ [CRUD] Deleting file: ${embeddingId} (${fileType })`);
      }),
      mergeMap(({ embeddingId, fileType  }) =>
        this.crudApi.deleteFile(embeddingId, fileType ).pipe(
          tap(response => {
            console.log(`✅ [CRUD] File deleted:`, response);
          }),
          map(response => CrudActions.deleteFileSuccess({ response })),
          catchError(error => {
            console.error(`❌ [CRUD] Delete file error:`, error);
            return of(CrudActions.deleteFileError({
              embeddingId,
              error: error.message || 'Erreur suppression fichier'
            }));
          })
        )
      )
    )
  );
  
  // ========================================================================
  // DELETE BATCH
  // ========================================================================
  deleteBatch$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.deleteBatch),
      tap(({ batchId }) => {
        console.log(`🗑️ [CRUD] Deleting batch: ${batchId}`);
      }),
      mergeMap(({ batchId }) =>
        this.crudApi.deleteBatch(batchId).pipe(
          tap(response => {
            console.log(`✅ [CRUD] Batch deleted successfully:`, response);
          }),
          map(response => CrudActions.deleteBatchSuccess({ response })),
          catchError(error => {
            console.error(`❌ [CRUD] Delete batch error:`, error);
            return of(CrudActions.deleteBatchError({
              batchId,
              error: error.message || 'Erreur suppression batch'
            }));
          })
        )
      )
    )
  );
  
  /**
   * ✅ Toast d'erreur pour suppression batch
   */
  deleteBatchErrorNotification$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.deleteBatchError),
      tap(({ batchId, error }) => {
        this.notificationService.error(
          'Erreur de Suppression',
          `Impossible de supprimer le batch: ${error}`,
          5000
        );
      })
    ),
    { dispatch: false }
  );
  
  // ========================================================================
  // DELETE TEXT BATCH
  // ========================================================================
  
  deleteTextBatch$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.deleteTextBatch),
      tap(({ embeddingIds }) => {
        console.log(`🗑️ [CRUD] Deleting text batch: ${embeddingIds.length} items`);
      }),
      mergeMap(({ embeddingIds }) =>
        this.crudApi.deleteTextBatch(embeddingIds).pipe(
          tap(response => {
            console.log(`✅ [CRUD] Text batch deleted:`, response);
          }),
          map(response => CrudActions.deleteTextBatchSuccess({ response })),
          catchError(error => {
            console.error(`❌ [CRUD] Delete text batch error:`, error);
            return of(CrudActions.deleteTextBatchError({
              error: error.message || 'Erreur suppression batch texte'
            }));
          })
        )
      )
    )
  );
  
  // ========================================================================
  // DELETE IMAGE BATCH
  // ========================================================================
  
  deleteImageBatch$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.deleteImageBatch),
      tap(({ embeddingIds }) => {
        console.log(`🗑️ [CRUD] Deleting image batch: ${embeddingIds.length} items`);
      }),
      mergeMap(({ embeddingIds }) =>
        this.crudApi.deleteImageBatch(embeddingIds).pipe(
          tap(response => {
            console.log(`✅ [CRUD] Image batch deleted:`, response);
          }),
          map(response => CrudActions.deleteImageBatchSuccess({ response })),
          catchError(error => {
            console.error(`❌ [CRUD] Delete image batch error:`, error);
            return of(CrudActions.deleteImageBatchError({
              error: error.message || 'Erreur suppression batch image'
            }));
          })
        )
      )
    )
  );
  
  // ========================================================================
  // DELETE ALL FILES
  // ========================================================================
deleteAllFilesSuccess$ = createEffect(() =>
  this.actions$.pipe(
    ofType(CrudActions.deleteAllFilesSuccess),
    tap(({ response }) => {
      console.warn(`✅ ${response.message}`);
      
      if (response.deletedCount === 0) {
        // ✅ Toast info si rien supprimé
        this.notificationService.info(
          'Base de Données Vide',
          'Aucun embedding à supprimer. La base de données est déjà vide.',
          5000
        );
      } else {
        // ✅ Toast succès si suppression réussie
        this.notificationService.success(
          'Suppression Réussie',
          `${response.deletedCount} embeddings supprimés + cache Redis + tracker`,
          5000
        );
      }
    })
  ),
  { dispatch: false }
);

  deleteAllFilesError$ = createEffect(() =>
  this.actions$.pipe(
    ofType(CrudActions.deleteAllFilesError),
    tap(({ error }) => {
      console.error(`❌ Erreur: ${error}`);
      
      // ✅ Toast d'erreur
      this.notificationService.error(
        'Erreur de Suppression',
        error || 'Une erreur est survenue lors de la suppression',
        7000
      );
    })
  ),
  { dispatch: false }
);


deleteAllFiles$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.deleteAllFiles),
      tap(({ confirmation }) => {
        console.warn(`🚨 [CRUD] Deleting ALL files with confirmation: ${confirmation}`);
      }),
      mergeMap(({ confirmation }) =>
        this.crudApi.deleteAllFiles(confirmation).pipe(
          tap(response => {
            console.warn(`✅ [CRUD] ALL files deleted:`, response);
          }),
          map(response => CrudActions.deleteAllFilesSuccess({ response })),
          catchError(error => {
            console.error(`❌ [CRUD] Delete all files error:`, error);
            return of(CrudActions.deleteAllFilesError({
              error: error.message || 'Erreur suppression globale'
            }));
          })
        )
      )
    )
  );
  
  // ✅ NOUVEAU: Fermer le modal après succès
  deleteAllFilesSuccessCloseModal$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.deleteAllFilesSuccess),
      tap(() => {
        console.log('🔔 Triggering modal close after success');
        // Le modal se fermera automatiquement via selectCrudLoading
      })
    ),
    { dispatch: false }
  );
  
  // ✅ NOUVEAU: Fermer le modal après erreur aussi
  deleteAllFilesErrorCloseModal$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.deleteAllFilesError),
      tap(() => {
        console.log('🔔 Triggering modal close after error');
        // Le modal se fermera automatiquement via selectCrudLoading
      })
    ),
    { dispatch: false }
  );
  // ========================================================================
  //  notificationService DELETE ALL FILES
  // ========================================================================
  
  /**
   * ✅ Toast de succès
   */
  deleteAllFilesSuccessNotification$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.deleteAllFilesSuccess),
      tap(({ response }) => {
        if (response.deletedCount === 0) {
          // Base vide
          this.notificationService.info(
            'Base de Données Vide',
            'Aucun embedding à supprimer. La base est déjà vide.',
            5000
          );
        } else {
          // Suppression réussie (géré dans le component)
          console.log(`✅ ${response.deletedCount} embeddings supprimés`);
        }
      })
    ),
    { dispatch: false }
  );
  
  /**
   * ✅ Toast d'erreur
   */
  deleteAllFilesErrorNotification$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.deleteAllFilesError),
      tap(({ error }) => {
        this.notificationService.error(
          'Erreur de Suppression',
          error || 'Une erreur est survenue lors de la suppression',
          7000
        );
      })
    ),
    { dispatch: false }
  );
  // ========================================================================
  // CHECK DUPLICATE
  // ========================================================================
  
  checkDuplicate$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.checkDuplicate),
      tap(({ file }) => {
        console.log(`🔍 [CRUD] Checking duplicate: ${file.name}`);
      }),
      mergeMap(({ file }) =>
        this.crudApi.checkDuplicate(file).pipe(
          tap(response => {
            if (response.isDuplicate) {
              console.warn(`⚠️ [CRUD] Duplicate found: ${file.name}`, response);
            } else {
              console.log(`✅ [CRUD] No duplicate: ${file.name}`);
            }
          }),
          map(response => CrudActions.checkDuplicateSuccess({ response })),
          catchError(error => {
            console.error(`❌ [CRUD] Check duplicate error:`, error);
            return of(CrudActions.checkDuplicateError({
              filename: file.name,
              error: error.message || 'Erreur vérification doublon'
            }));
          })
        )
      )
    )
  );
  
  // ========================================================================
  // GET BATCH INFO
  // ========================================================================
  
  getBatchInfo$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.getBatchInfo),
      tap(({ batchId }) => {
        console.log(`📊 [CRUD] Getting batch info: ${batchId}`);
      }),
      mergeMap(({ batchId }) =>
        this.crudApi.getBatchInfo(batchId).pipe(
          tap(response => {
            console.log(`✅ [CRUD] Batch info retrieved:`, response);
          }),
          map(response => CrudActions.getBatchInfoSuccess({ response })),
          catchError(error => {
            console.error(`❌ [CRUD] Get batch info error:`, error);
            return of(CrudActions.getBatchInfoError({
              batchId,
              error: error.message || 'Erreur récupération info batch'
            }));
          })
        )
      )
    )
  );
  
  // ========================================================================
  // GET SYSTEM STATS
  // ========================================================================
  
  getSystemStats$ = createEffect(() =>
    this.actions$.pipe(
      ofType(CrudActions.getSystemStats),
      tap(() => {
        console.log(`📊 [CRUD] Getting system stats`);
      }),
      mergeMap(() =>
        this.crudApi.getSystemStats().pipe(
          tap(stats => {
            console.log(`✅ [CRUD] System stats retrieved:`, stats);
          }),
          map(stats => CrudActions.getSystemStatsSuccess({ stats })),
          catchError(error => {
            console.error(`❌ [CRUD] Get system stats error:`, error);
            return of(CrudActions.getSystemStatsError({
              error: error.message || 'Erreur récupération stats système'
            }));
          })
        )
      )
    )
  );
}