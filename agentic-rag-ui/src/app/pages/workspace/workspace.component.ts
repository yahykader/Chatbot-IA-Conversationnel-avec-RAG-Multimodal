// src/app/pages/workspace/workspace.component.ts

import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UploadPageComponent } from '../../features/ingestion/pages/upload-page/upload-page.component';
import { ChatPageComponent } from '../../features/chat/pages/chat-page/chat-page.component';

@Component({
  selector: 'app-workspace',
  standalone: true,
  imports: [
    CommonModule,
    UploadPageComponent,
    ChatPageComponent
  ],
  templateUrl: './workspace.component.html',
  styleUrls: ['./workspace.component.scss']
})
export class WorkspaceComponent {}