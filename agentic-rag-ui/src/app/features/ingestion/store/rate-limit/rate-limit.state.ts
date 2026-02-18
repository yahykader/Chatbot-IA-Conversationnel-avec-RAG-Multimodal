export interface RateLimitState {
  isRateLimited: boolean;
  retryAfterSeconds: number;
  message: string;
  remainingTokens: {
    upload: number | null;
    batch: number | null;
    delete: number | null;
    search: number | null;
    default: number | null;
  };
  limits: {
    upload: number;
    batch: number;
    delete: number;
    search: number;
    default: number;
  };
}

export const initialRateLimitState: RateLimitState = {
  isRateLimited: false,
  retryAfterSeconds: 0,
  message: '',
  remainingTokens: {
    upload: null,
    batch: null,
    delete: null,
    search: null,
    default: null,
  },
  limits: {
    upload: 10,      // req/min
    batch: 5,        // req/min
    delete: 20,      // req/min
    search: 50,      // req/min
    default: 30,     // req/min
  },
};