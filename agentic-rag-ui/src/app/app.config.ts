import { 
  ApplicationConfig, 
  provideZoneChangeDetection,
  provideBrowserGlobalErrorListeners, 
  importProvidersFrom
} from '@angular/core'
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { MarkdownModule } from 'ngx-markdown';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';
import { assistantReducer } from './assistant/store/assistant.reducer';
import { AssistantEffects } from './assistant/store/assistant.effects';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(), 
    provideAnimations(),
    provideStore({
      assistant: assistantReducer
    }),
    provideEffects(AssistantEffects),
    provideStoreDevtools({
      maxAge: 25,
      logOnly: false,
      trace: true,
      traceLimit: 75
    }),
        // ✅ Configurer Markdown
    importProvidersFrom(MarkdownModule.forRoot())
  ]
};
