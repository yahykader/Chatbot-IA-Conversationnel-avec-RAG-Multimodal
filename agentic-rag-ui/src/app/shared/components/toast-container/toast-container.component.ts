// shared/components/toast-container/toast-container.component.ts

import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationService, Toast } from '../../../core/services/notification.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './toast-container.component.html',
  styleUrls: ['./toast-container.component.scss' ]
})
export class ToastContainerComponent implements OnInit {
  toasts: Toast[] = [];
  
  constructor(private notificationService: NotificationService) {}
  
  ngOnInit() {
    this.notificationService.toasts$.subscribe(toast => {
      this.toasts.push(toast);
      
      // Auto-remove après la durée
      if (toast.duration) {
        setTimeout(() => {
          this.removeToast(toast.id);
        }, toast.duration);
      }
    });
  }
  
  removeToast(id: string): void {
    this.toasts = this.toasts.filter(t => t.id !== id);
  }
}