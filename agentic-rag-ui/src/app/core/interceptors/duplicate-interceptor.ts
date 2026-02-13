// core/interceptors/duplicate-interceptor.ts
import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/**
 * Interceptor pour gérer les erreurs 409 (Duplicate)
 */
export const duplicateInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      
      // Gérer spécifiquement les 409 Conflict (Duplicate)
      if (error.status === 409) {
        console.log('⚠️ Duplicate détecté (409):', error.error);
        
        // Transformer l'erreur en réponse valide
        const duplicateResponse = {
          success: false,
          duplicate: true,
          filename: error.error?.filename || 'Unknown',
          batchId: error.error?.batchId || null,
          existingBatchId: error.error?.existingBatchId || error.error?.batchId,
          message: error.error?.message || 'Ce fichier existe déjà',
          ...error.error
        };
        
        // Retourner une erreur enrichie
        return throwError(() => ({
          isDuplicate: true,
          status: 409,
          data: duplicateResponse,
          originalError: error
        }));
      }
      
      // Autres erreurs
      return throwError(() => error);
    })
  );
};