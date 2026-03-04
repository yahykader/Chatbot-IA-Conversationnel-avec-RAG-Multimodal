// core/services/notification.service.ts

import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

export interface Toast {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message: string;
  duration?: number;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private toastSubject = new Subject<Toast>();
  public toasts$ = this.toastSubject.asObservable();
  
  success(title: string, message: string, duration: number = 5000): void {
    this.show('success', title, message, duration);
  }
  
  error(title: string, message: string, duration: number = 5000): void {
    this.show('error', title, message, duration);
  }
  
  warning(title: string, message: string, duration: number = 5000): void {
    this.show('warning', title, message, duration);
  }
  
  info(title: string, message: string, duration: number = 5000): void {
    this.show('info', title, message, duration);
  }
  
  private show(type: Toast['type'], title: string, message: string, duration: number): void {
    const toast: Toast = {
      id: Date.now().toString(),
      type,
      title,
      message,
      duration
    };
    this.toastSubject.next(toast);
  }
}