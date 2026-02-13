// app.routes.ts
import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'upload',
    pathMatch: 'full'
  },
  {
    path: 'upload',
    children: [
      {
        path: '',
        loadComponent: () => 
          import('./features/ingestion/pages/upload-page/upload-page.component')
            .then(m => m.UploadPageComponent),
        outlet: 'sidebar' // ✅ Afficher dans la sidebar
      }
    ]
  },
  // {
  //   path: 'chat',
  //   loadComponent: () => 
  //     import('./features/chat/pages/chat-page/chat-page.component')
  //       .then(m => m.ChatPageComponent)
  // },
  // {
  //   path: 'management',
  //   loadComponent: () => 
  //     import('./features/management/pages/management-page/management-page.component')
  //       .then(m => m.ManagementPageComponent)
  // }
];