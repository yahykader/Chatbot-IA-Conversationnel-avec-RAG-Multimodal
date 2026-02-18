import { createReducer, on } from '@ngrx/store';
import * as RateLimitActions from './rate-limit.actions';
import { initialRateLimitState } from './rate-limit.state';

export const rateLimitReducer = createReducer(
  initialRateLimitState,
  
  on(RateLimitActions.rateLimitExceeded, (state, { message, retryAfterSeconds }) => ({
    ...state,
    isRateLimited: true,
    message,
    retryAfterSeconds,
  })),
  
  on(RateLimitActions.rateLimitReset, (state) => ({
    ...state,
    isRateLimited: false,
    message: '',
    retryAfterSeconds: 0,
  })),
  
  on(RateLimitActions.updateRemainingTokens, (state, { endpoint, remaining }) => ({
    ...state,
    remainingTokens: {
      ...state.remainingTokens,
      [endpoint]: remaining,
    },
  })),
  
  on(RateLimitActions.decrementCountdown, (state) => ({
    ...state,
    retryAfterSeconds: Math.max(0, state.retryAfterSeconds - 1),
  }))
);