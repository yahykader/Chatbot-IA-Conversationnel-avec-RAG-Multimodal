import { Routes } from '@angular/router';
import { AssistantComponent } from './assistant/assistant.component';

export const routes: Routes = [
  { 
    path: '', 
    redirectTo: '/assistant', 
    pathMatch: 'full' 
  },
  { 
    path: 'assistant', 
    component: AssistantComponent 
  }
];
