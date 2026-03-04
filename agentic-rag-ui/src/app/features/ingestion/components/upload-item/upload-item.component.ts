// features/ingestion/components/upload-item/upload-item.component.ts
import { Component, Input, Output, EventEmitter, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';
import { map, filter, take } from 'rxjs/operators';

import { UploadFile } from '../../store/ingestion.state';
import { UploadProgress } from '../../../../core/services/websocket-progress.service';
import * as ProgressSelectors from '../../store/progress.selectors';
import * as IngestionActions from '../../store/ingestion.actions';
import * as CrudActions from '../../../ingestion/store/crud.actions';
import * as CrudSelectors from '../../../ingestion/store/crud.selectors';
import { DeleteBatchModalComponent } from '../delete-batch-modal/delete-batch-modal.component';
import { NotificationService } from '../../../../core/services/notification.service';

@Component({
  selector: 'app-upload-item',
  standalone: true,
  imports: [CommonModule, DeleteBatchModalComponent],
  templateUrl: './upload-item.component.html',
  styleUrls: ['./upload-item.component.scss']
})
export class UploadItemComponent implements OnInit {
  @ViewChild(DeleteBatchModalComponent) deleteModal!: DeleteBatchModalComponent;
  
  @Input() upload!: UploadFile;
  @Output() start = new EventEmitter<void>();
  
  progress$!: Observable<UploadProgress | undefined>;
  isDeleting$!: Observable<boolean>;
  
  constructor(private store: Store, private notificationService: NotificationService) {}
  
  ngOnInit() {
    // Progress pour ce batch
    if (this.upload.batchId) {
      this.progress$ = this.store.select(
        ProgressSelectors.selectProgressForBatch(this.upload.batchId)
      );
    }
    
    // Observer si une suppression est en cours
    this.isDeleting$ = this.store.select(CrudSelectors.selectActiveDeleteOperations).pipe(
      map(count => count > 0)
    );
  }
  
  /**
   * Démarrer l'upload
   */
  onStart(): void {
    this.start.emit();
  }
  
  /**
   * Supprimer un upload (avec modal pour success/duplicate)
   */
  onDelete(): void {
    const { status, batchId, existingBatchId, file } = this.upload;
    
    // CAS 1: Upload en cours ou en attente
    if (status === 'pending' || status === 'uploading') {
      console.log(`🗑️ Removing pending upload: ${this.upload.id}`);
      
      this.store.dispatch(
        IngestionActions.removeUpload({ fileId: this.upload.id })
      );
      
      this.notificationService.info(
        'Fichier Retiré',
        `${file.name} retiré de la liste`,
        3000
      );
      
      return;
    }
    
    // CAS 2: Upload terminé (success ou duplicate)
    if (status === 'success' || status === 'duplicate') {
      const targetBatchId = existingBatchId || batchId;
      
      if (!targetBatchId) {
        console.error('❌ No batchId found for deletion');
        
        this.notificationService.error(
          'Erreur',
          'Impossible de trouver le batch ID pour la suppression',
          5000
        );
        
        return;
      }
      
      // ✅ AMÉLIORER: Logguer pour debug
      console.log('🗑️ Opening modal for deletion:', {
        filename: file.name,
        batchId: targetBatchId,
        isDuplicate: status === 'duplicate',
        uploadId: this.upload.id
      });
      
      // Ouvrir le modal avec les bonnes données
      setTimeout(() => {
        if (this.deleteModal) {
          // ✅ Assigner les valeurs AVANT d'ouvrir
          this.deleteModal.filename = file.name;
          this.deleteModal.batchId = targetBatchId;
          this.deleteModal.isDuplicate = status === 'duplicate';
          
          // Ouvrir le modal
          this.deleteModal.openModal();
          
          // ✅ Log après pour vérifier
          console.log('✅ Modal opened with data:', {
            filename: this.deleteModal.filename,
            batchId: this.deleteModal.batchId,
            isDuplicate: this.deleteModal.isDuplicate
          });
        } else {
          console.error('❌ Modal reference not found');
        }
      }, 0);
    }
  }
  
  /**
   * ✅ Callback: Utilisateur a confirmé la suppression dans le modal
   */
  onDeleteConfirmed(): void {
    const { batchId, existingBatchId, file } = this.upload;
    const targetBatchId = existingBatchId || batchId;
    
    if (!targetBatchId) {
      console.error('❌ No batchId for deletion');
      return;
    }
    
    console.log(`🗑️ Deleting batch from system: ${targetBatchId}`);
    
    // Dispatch suppression batch
    this.store.dispatch(
      CrudActions.deleteBatch({ batchId: targetBatchId })
    );
    
    // Écouter le succès pour fermer le modal et afficher toast
    this.store.select(CrudSelectors.selectActiveDeleteOperations).pipe(
      filter(count => count === 0),
      take(1)
    ).subscribe(() => {
      setTimeout(() => {
        // Fermer le modal
        if (this.deleteModal) {
          this.deleteModal.onDeleteSuccess();
        }
        
        // ✅ Toast de succès
        this.notificationService.success(
          'Fichier Supprimé',
          `${file.name} a été supprimé du système`,
          4000
        );
      }, 300);
    });
  }
  
  /**
   * ✅ Callback: Utilisateur a annulé
   */
  onDeleteCancelled(): void {
    console.log('ℹ️ User cancelled delete operation');
    
    // ✅ Toast d'info (optionnel)
    this.notificationService.info(
      'Suppression Annulée',
      'Aucune modification effectuée',
      2000
    );
  }
  /**
   * Tooltip dynamique pour le bouton supprimer
   */
  getDeleteTooltip(): string {
    const { status } = this.upload;
    
    if (status === 'pending' || status === 'uploading') {
      return 'Retirer de la liste';
    }
    
    if (status === 'duplicate') {
      return 'Supprimer le doublon du système';
    }
    
    if (status === 'success') {
      return 'Supprimer du système';
    }
    
    return 'Supprimer';
  }
  
  // ========================================================================
  // Méthodes utilitaires (INCHANGÉES)
  // ========================================================================
  
  getStatusIcon(): string {
    switch (this.upload.status) {
      case 'pending': return 'bi-clock-history';
      case 'uploading': return 'bi-arrow-repeat';
      case 'success': return 'bi-check-circle-fill';
      case 'error': return 'bi-x-circle-fill';
      case 'duplicate': return 'bi-exclamation-triangle-fill';
      default: return 'bi-file-earmark';
    }
  }
  
  getStatusColor(): string {
    switch (this.upload.status) {
      case 'pending': return 'secondary';
      case 'uploading': return 'primary';
      case 'success': return 'success';
      case 'error': return 'danger';
      case 'duplicate': return 'warning';
      default: return 'secondary';
    }
  }
  
  getStatusText(): string {
    switch (this.upload.status) {
      case 'pending': return 'En attente';
      case 'uploading': return 'En cours';
      case 'success': return 'Terminé';
      case 'error': return 'Erreur';
      case 'duplicate': return 'Doublon détecté';
      default: return 'Inconnu';
    }
  }
  
  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
  }
  
  getStageIcon(stage: string): string {
    const icons: { [key: string]: string } = {
      'UPLOAD': 'bi-cloud-upload',
      'PROCESSING': 'bi-gear',
      'CHUNKING': 'bi-scissors',
      'EMBEDDING': 'bi-box-seam',
      'IMAGES': 'bi-image',
      'COMPLETED': 'bi-check-circle',
      'ERROR': 'bi-x-circle'
    };
    return icons[stage] || 'bi-circle';
  }
  
  getStageLabel(stage: string): string {
    const labels: { [key: string]: string } = {
      'UPLOAD': 'Upload',
      'PROCESSING': 'Traitement',
      'CHUNKING': 'Découpage',
      'EMBEDDING': 'Embeddings',
      'IMAGES': 'Images',
      'COMPLETED': 'Terminé',
      'ERROR': 'Erreur'
    };
    return labels[stage] || stage;
  }
}