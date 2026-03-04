// features/ingestion/components/progress-panel/progress-panel.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';

import * as ProgressSelectors from '../../store/progress.selectors';
import { UploadProgress } from '../../../../core/services/websocket-progress.service';

@Component({
  selector: 'app-progress-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './progress-panel.component.html',
  styleUrls: ['./progress-panel.component.scss']
})
export class ProgressPanelComponent implements OnInit {
  
  activeProgress$: Observable<UploadProgress[]>;
  recentlyCompleted$: Observable<UploadProgress[]>;
  wsConnected$: Observable<boolean>;
  
  constructor(private store: Store) {
    this.activeProgress$ = this.store.select(ProgressSelectors.selectActiveProgress);
    this.recentlyCompleted$ = this.store.select(ProgressSelectors.selectRecentlyCompleted);
    this.wsConnected$ = this.store.select(ProgressSelectors.selectWebSocketConnected);
  }
  
  ngOnInit(): void {}
}