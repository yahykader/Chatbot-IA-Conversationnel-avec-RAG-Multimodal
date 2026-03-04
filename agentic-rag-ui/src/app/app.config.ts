// app.config.ts
import { ApplicationConfig, provideZoneChangeDetection, importProvidersFrom  } from '@angular/core';
import { provideRouter } from '@angular/router';
import {   
  provideHttpClient,
  withInterceptors,
  withInterceptorsFromDi  } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';
import { provideMarkdown } from 'ngx-markdown';

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

import { CrudApiService } from './core/services/crud-api.service';
import { StreamingApiService } from './core/services/streaming-api.service';
import { WebSocketProgressService } from './core/services/websocket-progress.service';
import { IngestionApiService } from './core/services/ingestion-api.service';
import { crudReducer } from './features/ingestion/store/crud.reducer';
import { CrudEffects } from './features/ingestion/store/crud.effects';
import { rateLimitInterceptor } from './core/interceptors/rate-limit.interceptor';
import { rateLimitReducer } from './features/ingestion/store/rate-limit/rate-limit.reducer';
import { RateLimitEffects } from './features/ingestion/store/rate-limit/rate-limit.effects';
import { chatReducer } from './features/chat/store/chat.reducer';
import { ChatEffects } from './features/chat/store/chat.effects';
import { environment } from '../environments/environment';

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

    // AJOUT: Markdown
    provideMarkdown(),
    
    // Animations
    provideAnimations(),

        // Services globaux
    IngestionApiService,
    WebSocketProgressService,
    StreamingApiService,
    CrudApiService,
    
    // NgRx Store
    provideStore({
      ingestion: ingestionReducer,
      progress: progressReducer,
      crud: crudReducer,
      rateLimit: rateLimitReducer,
      chat: chatReducer
    }),
    
    // NgRx Effects
    provideEffects([
      RateLimitEffects,
      IngestionEffects,
      ProgressEffects,
      CrudEffects,
      ChatEffects
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

