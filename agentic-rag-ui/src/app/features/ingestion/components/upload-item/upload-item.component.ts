// features/ingestion/components/upload-item/upload-item.component.ts
import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';

import { UploadFile } from '../../store/ingestion.state';
import { UploadProgress } from '../../../../core/services/websocket-progress.service';
import * as ProgressSelectors from '../../store/progress.selectors';

@Component({
  selector: 'app-upload-item',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './upload-item.component.html',
  styleUrls: ['./upload-item.component.scss']
})
export class UploadItemComponent implements OnInit {
  
  @Input() upload!: UploadFile;
  @Output() start = new EventEmitter<void>();
  @Output() remove = new EventEmitter<void>();
  
  progress$!: Observable<UploadProgress | undefined>;
  
  constructor(private store: Store) {}
  
  ngOnInit(): void {
    if (this.upload.batchId) {
      this.progress$ = this.store.select(
        ProgressSelectors.selectProgressForBatch(this.upload.batchId)
      );
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
  
  getStatusIcon(): string {
    switch (this.upload.status) {
      case 'pending': return 'bi-hourglass';
      case 'uploading': return 'bi-arrow-repeat spin';
      case 'success': return 'bi-check-circle-fill';
      case 'error': return 'bi-x-circle-fill';
      case 'duplicate': return 'bi-exclamation-triangle-fill';
      default: return 'bi-file';
    }
  }
  
  getStatusText(): string {
    switch (this.upload.status) {
      case 'pending': return 'En attente';
      case 'uploading': return 'Upload en cours...';
      case 'success': return 'Succès';
      case 'error': return 'Erreur';
      case 'duplicate': return 'Doublon détecté';
      default: return 'Inconnu';
    }
  }
  
  getStageLabel(stage: string): string {
    const labels: { [key: string]: string } = {
      'UPLOAD': 'Téléchargement',
      'PROCESSING': 'Traitement',
      'CHUNKING': 'Découpage',
      'EMBEDDING': 'Embeddings',
      'IMAGES': 'Images',
      'COMPLETED': 'Terminé',
      'ERROR': 'Erreur'
    };
    return labels[stage] || stage;
  }
  
  getStageIcon(stage: string): string {
    const icons: { [key: string]: string } = {
      'UPLOAD': 'bi-upload',
      'PROCESSING': 'bi-gear',
      'CHUNKING': 'bi-scissors',
      'EMBEDDING': 'bi-box-seam',
      'IMAGES': 'bi-image',
      'COMPLETED': 'bi-check-circle-fill',
      'ERROR': 'bi-x-circle-fill'
    };
    return icons[stage] || 'bi-circle';
  }
  
  formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
  }
}