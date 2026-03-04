// app.routes.ts
import { Routes } from '@angular/router';
import { ChatResolver } from './features/chat/resolvers/chat.resolver';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/workspace',
    pathMatch: 'full'
  },
  {
    path: 'workspace',
    loadComponent: () =>
      import('./pages/workspace/workspace.component')
        .then(m => m.WorkspaceComponent)
  },
/*   {
    path: 'management',
    loadComponent: () => 
      import('./features/management/pages/management-page/management-page.component')
        .then(m => m.ManagementPageComponent)
  } */
  {
    path: '**',
    redirectTo: '/workspace'
  }
];