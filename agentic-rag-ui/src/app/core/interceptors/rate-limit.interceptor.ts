import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { catchError, tap, throwError } from 'rxjs';
import * as RateLimitActions from '../../features/ingestion/store/rate-limit/rate-limit.actions';

interface RateLimitError {
  error: string;
  message: string;
  retryAfterSeconds: number;
  timestamp: number;
}

export const rateLimitInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(Store);
  
  // Ajouter X-User-Id depuis localStorage si disponible
  const userId = localStorage.getItem('userId');
  if (userId) {
    req = req.clone({
      setHeaders: {
        'X-User-Id': userId
      }
    });
  }
  
  return next(req).pipe(
    tap((event: any) => {
      // Capturer les headers de réponse
      if (event.headers) {
        const remaining = event.headers.get('X-RateLimit-Remaining');
        
        if (remaining !== null) {
          // Déterminer le type d'endpoint
          const endpoint = getEndpointType(req.url);
          
          store.dispatch(RateLimitActions.updateRemainingTokens({
            endpoint,
            remaining: parseInt(remaining, 10)
          }));
        }
      }
    }),
    catchError((error: HttpErrorResponse) => {
      // Gérer erreur 429
      if (error.status === 429) {
        const errorData = error.error as RateLimitError;
        const retryAfter = errorData?.retryAfterSeconds || 60;
        
        store.dispatch(RateLimitActions.rateLimitExceeded({
          message: errorData?.message || 'Rate limit dépassé',
          retryAfterSeconds: retryAfter
        }));
      }
      
      return throwError(() => error);
    })
  );
};

function getEndpointType(url: string): 'upload' | 'batch' | 'delete' | 'search' | 'default' {
  if (url.includes('/upload/batch')) return 'batch';
  if (url.includes('/upload')) return 'upload';
  if (url.includes('/delete') || url.includes('DELETE')) return 'delete';
  if (url.includes('/search')) return 'search';
  return 'default';
}