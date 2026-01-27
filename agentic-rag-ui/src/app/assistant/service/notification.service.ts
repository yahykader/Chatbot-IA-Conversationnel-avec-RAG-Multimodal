// notification.service.ts
import { Injectable } from '@angular/core';
import { Actions, ofType } from '@ngrx/effects';
import { ToastrService } from 'ngx-toastr';
import * as AssistantActions from '../store/assistant.actions';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(
    private actions$: Actions,
    private toastr: ToastrService
  ) {
    this.actions$.pipe(
      ofType(AssistantActions.showNotification)
    ).subscribe(({ message, notificationType, duration }) => {
      const options = { timeOut: duration || 3000 };
      
      switch (notificationType) {
        case 'success':
          this.toastr.success(message, '', options);
          break;
        case 'error':
          this.toastr.error(message, '', options);
          break;
        case 'warning':
          this.toastr.warning(message, '', options);
          break;
        case 'info':
          this.toastr.info(message, '', options);
          break;
      }
    });
  }
}