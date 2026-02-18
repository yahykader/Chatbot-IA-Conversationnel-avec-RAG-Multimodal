import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';
import {
  selectUploadRemaining,
  selectRateLimitPercentage
} from '../../store/rate-limit/rate-limit.selectors';

@Component({
  selector: 'app-rate-limit-indicator',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './rate-limit-indicator.component.html',
  styleUrls: ['./rate-limit-indicator.component.scss']
})
export class RateLimitIndicatorComponent {
  remaining$: Observable<number | null>;
  percentage$: Observable<number>;
  
  constructor(private store: Store) {
    this.remaining$ = this.store.select(selectUploadRemaining);
    this.percentage$ = this.store.select(selectRateLimitPercentage);
  }
}