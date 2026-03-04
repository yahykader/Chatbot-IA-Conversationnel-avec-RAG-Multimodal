import { createAction, props } from '@ngrx/store';

export const rateLimitExceeded = createAction(
  '[Rate Limit] Exceeded',
  props<{ message: string; retryAfterSeconds: number }>()
);

export const rateLimitReset = createAction(
  '[Rate Limit] Reset'
);

export const updateRemainingTokens = createAction(
  '[Rate Limit] Update Remaining Tokens',
  props<{ 
    endpoint: 'upload' | 'batch' | 'delete' | 'search' | 'default';
    remaining: number;
  }>()
);

export const startCountdown = createAction(
  '[Rate Limit] Start Countdown',
  props<{ seconds: number }>()
);

export const decrementCountdown = createAction(
  '[Rate Limit] Decrement Countdown'
);