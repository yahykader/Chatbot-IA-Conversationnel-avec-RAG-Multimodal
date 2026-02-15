// features/crud/components/delete-all-button/delete-all-button.component.ts
import { Component, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { from, Observable } from 'rxjs';
import { filter, take } from 'rxjs/operators';


import * as CrudActions from '../../store/crud.actions';
import * as CrudSelectors from '../../store/crud.selectors';
import { DeleteAllModalComponent } from '../delete-all-modal/delete-all-modal.component';
import { NotificationService } from '../../../../core/services/notification.service';

@Component({
  selector: 'app-delete-all-button',
  standalone: true,
  imports: [CommonModule, DeleteAllModalComponent],
  templateUrl: './delete-all-button.component.html',
  styleUrls: ['./delete-all-button.component.scss']
  
})
export class DeleteAllButtonComponent {
    @ViewChild(DeleteAllModalComponent) modal!: DeleteAllModalComponent;

;
  
  isDeleting$: Observable<boolean>;
  
  constructor(private store: Store, private notificationService: NotificationService) {
    this.isDeleting$ = this.store.select(CrudSelectors.selectCrudLoading);
  }
 
  openDeleteModal(): void {
    setTimeout(() => {
      if (this.modal) {
        this.modal.openModal();
      }
    }, 0);
  }
  
onConfirmed(): void {
    console.warn('🚨 User confirmed DELETE ALL FILES via modal');
    
    this.store.dispatch(
      CrudActions.deleteAllFiles({ confirmation: 'DELETE_ALL_FILES' })
    );
    
    const subscription = this.store.select(CrudSelectors.selectCrudLoading).pipe(
      filter(loading => !loading),
      take(1)
    ).subscribe(() => {
      console.log('✅ Delete all completed');
      
      // Fermer le modal
      setTimeout(() => {
        if (this.modal) {
          this.modal.onDeleteSuccess();
        }
        
        // ✅ TOAST au lieu d'alert()
        this.notificationService.success(
          'Suppression Réussie',
          'Tous les fichiers ont été supprimés (PostgreSQL + Redis + Tracker)',
          5000
        );
        
        subscription.unsubscribe();
      }, 300);
    });
    
    // Timeout de sécurité
    setTimeout(() => {
      if (this.modal && this.modal.isDeleting) {
        console.warn('⚠️ Forcing modal close after timeout');
        this.modal.onDeleteSuccess();
        
        // ✅ Toast d'avertissement
        this.notificationService.warning(
          'Timeout',
          'La suppression a pris trop de temps',
          5000
        );
        
        subscription.unsubscribe();
      }
    }, 10000);
  }
  
  onCancelled(): void {
    console.log('ℹ️ User cancelled delete all operation');
  }
}
