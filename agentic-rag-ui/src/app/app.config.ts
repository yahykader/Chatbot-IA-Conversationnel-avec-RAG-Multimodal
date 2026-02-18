// app.config.ts
import { ApplicationConfig, provideZoneChangeDetection, importProvidersFrom } from '@angular/core';
import { provideRouter } from '@angular/router';
import {   
  provideHttpClient,
  withInterceptors,
  withInterceptorsFromDi  } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';

import { routes } from './app.routes';

// Import Interceptor
import { duplicateInterceptor } from './core/interceptors/duplicate-interceptor';

// Reducers
import { ingestionReducer } from './features/ingestion/store/ingestion.reducer';
import { progressReducer } from './features/ingestion/store/progress.reducer';

// Effects
import { IngestionEffects } from './features/ingestion/store/ingestion.effects';
import { ProgressEffects } from './features/ingestion/store/progress.effects';
import { MaterialModule } from './material/material.module';
import { environment } from '../../environements/environement';
import { CrudApiService } from './core/services/crud-api.service';
import { StreamingApiService } from './core/services/streaming-api.service';
import { WebSocketProgressService } from './core/services/websocket-progress.service';
import { IngestionApiService } from './core/services/ingestion-api.service';
import { crudReducer } from './features/ingestion/store/crud.reducer';
import { CrudEffects } from './features/ingestion/store/crud.effects';
import { rateLimitInterceptor } from './core/interceptors/rate-limit.interceptor';
import { rateLimitReducer } from './features/ingestion/store/rate-limit/rate-limit.reducer';
import { rateLimitReset } from './features/ingestion/store/rate-limit/rate-limit.actions';
import { RateLimitEffects } from './features/ingestion/store/rate-limit/rate-limit.effects';

export const appConfig: ApplicationConfig = {
  providers: [

    importProvidersFrom(MaterialModule),
    // Zone.js optimization
    provideZoneChangeDetection({ eventCoalescing: true }),
    
    // Router
    provideRouter(routes),
    
    // HTTP avec Interceptor
    provideHttpClient(
      withInterceptors([duplicateInterceptor, rateLimitInterceptor])
    ),
    
    // Animations
    provideAnimations(),

        // ✅ Services globaux
    IngestionApiService,
    WebSocketProgressService,
    StreamingApiService,
    CrudApiService,
    
    // NgRx Store
    provideStore({
      ingestion: ingestionReducer,
      progress: progressReducer,
      crud: crudReducer,
      rateLimit: rateLimitReducer
    }),
    
    // NgRx Effects
    provideEffects([
      RateLimitEffects,
      IngestionEffects,
      ProgressEffects,
      CrudEffects
    ]),
    
    // NgRx DevTools
    provideStoreDevtools({
      maxAge: 25,
      logOnly: environment.production,
      autoPause: true,
      trace: false,
      traceLimit: 75
    })
  ]
};

