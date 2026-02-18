import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';
import {
  selectIsRateLimited,
  selectRetryAfterSeconds,
  selectRateLimitMessage
} from '../../store/rate-limit/rate-limit.selectors';

@Component({
  selector: 'app-rate-limit-toast',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './rate-limit-toast.component.html',
  styleUrls: ['./rate-limit-toast.component.scss']
})
export class RateLimitToastComponent {
  isRateLimited$: Observable<boolean>;
  retryAfterSeconds$: Observable<number>;
  message$: Observable<string>;
  
  constructor(private store: Store) {
    this.isRateLimited$ = this.store.select(selectIsRateLimited);
    this.retryAfterSeconds$ = this.store.select(selectRetryAfterSeconds);
    this.message$ = this.store.select(selectRateLimitMessage);
  }
}