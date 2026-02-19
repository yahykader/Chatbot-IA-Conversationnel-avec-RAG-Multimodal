// app.routes.ts
import { Routes } from '@angular/router';
import { ChatResolver } from './features/chat/resolvers/chat.resolver';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/',
    pathMatch: 'full'
  },

  // Upload → outlet sidebar
  {
    path: 'upload',
    outlet: 'sidebar',
    loadComponent: () =>
      import('./features/ingestion/pages/upload-page/upload-page.component')
        .then(m => m.UploadPageComponent)
  },

  // Chat → outlet chat
  {
    path: 'chat',
    outlet: 'chat',
    resolve: { chat: ChatResolver },
    loadComponent: () =>
      import('./features/chat/pages/chat-page/chat-page.component')
        .then(m => m.ChatPageComponent)
  },

  {
    path: '**',
    redirectTo: '/'
  },
/*   {
     path: 'management',
     loadComponent: () => 
       import('./features/management/pages/management-page/management-page.component')
         .then(m => m.ManagementPageComponent)
  } */
];