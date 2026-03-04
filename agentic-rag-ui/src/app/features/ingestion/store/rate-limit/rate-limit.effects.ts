import { Injectable, inject} from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { interval, of } from 'rxjs';
import { map, switchMap, takeWhile, tap } from 'rxjs/operators';
import * as RateLimitActions from './rate-limit.actions';

@Injectable()
export class RateLimitEffects {

    private actions$ = inject(Actions);
    private store = inject(Store<{ rateLimit: any }>);

  startCountdown$ = createEffect(() =>
    this.actions$.pipe(
      ofType(RateLimitActions.rateLimitExceeded),
      switchMap(({ retryAfterSeconds }) =>
        interval(1000).pipe(
          takeWhile(() => retryAfterSeconds > 0),
          map(() => RateLimitActions.decrementCountdown())
        )
      )
    )
  );
  
  autoReset$ = createEffect(() =>
    this.actions$.pipe(
      ofType(RateLimitActions.decrementCountdown),
      tap((_) => {
        // Vérifier si countdown est à 0
        this.store.select(state => state.rateLimit.retryAfterSeconds).subscribe(seconds => {
          if (seconds === 0) {
            this.store.dispatch(RateLimitActions.rateLimitReset());
          }
        });
      })
    ),
    { dispatch: false }
  );
}