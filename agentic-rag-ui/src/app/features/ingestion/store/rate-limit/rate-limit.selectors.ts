import { createFeatureSelector, createSelector } from '@ngrx/store';
import { RateLimitState } from './rate-limit.state';

export const selectRateLimitState = createFeatureSelector<RateLimitState>('rateLimit');

export const selectIsRateLimited = createSelector(
  selectRateLimitState,
  (state) => state.isRateLimited
);

export const selectRetryAfterSeconds = createSelector(
  selectRateLimitState,
  (state) => state.retryAfterSeconds
);

export const selectRateLimitMessage = createSelector(
  selectRateLimitState,
  (state) => state.message
);

export const selectRemainingTokens = createSelector(
  selectRateLimitState,
  (state) => state.remainingTokens
);

export const selectUploadRemaining = createSelector(
  selectRateLimitState,
  (state) => state.remainingTokens.upload
);

export const selectRateLimitPercentage = createSelector(
  selectRateLimitState,
  (state) => {
    const upload = state.remainingTokens.upload;
    if (upload === null) return 100;
    return Math.round((upload / state.limits.upload) * 100);
  }
);