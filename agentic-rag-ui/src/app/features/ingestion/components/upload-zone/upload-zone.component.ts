import { Component, Output, EventEmitter, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { Store } from '@ngrx/store';
import { selectIsRateLimited } from '../../store/rate-limit/rate-limit.selectors';
@Component({
  selector: 'app-upload-zone',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './upload-zone.component.html',
  styleUrls: ['./upload-zone.component.scss']
})
export class UploadZoneComponent {
  
  @Output() filesSelected = new EventEmitter<File[]>();
  
  // Options configurables
  @Input() maxFileSize = 5 * 1024 * 1024 * 1024; // 5 GB par défaut
  @Input() acceptedFormats = '*/*'; // Tous formats par défaut
  @Input() multiple = true; // Multiple files par défaut
  @Input() disabled = false; // Désactiver la zone

  //Rate limiting Déclarer comme readonly (sera initialisé dans constructor)
  readonly isRateLimited$: Observable<boolean>;
  
  isDragging = false;

  constructor(private store: Store){
    this.isRateLimited$ = this.store.select(selectIsRateLimited);
  }
  
  /**
   * Gestion du drag over
   */
  onDragOver(event: DragEvent): void {
    if (this.disabled) return;
    
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = true;
  }
  
  /**
   * Gestion du drag leave
   */
  onDragLeave(event: DragEvent): void {
    if (this.disabled) return;
    
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;
  }
  
  /**
   * Gestion du drop
   */
  onDrop(event: DragEvent): void {
    if (this.disabled) return;
    
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;
    
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.handleFiles(files);
    }
  }
  
  /**
   * Gestion de la sélection via input
   */
  onFileSelected(event: Event): void {
    if (this.disabled) return;
    
    const input = event.target as HTMLInputElement;
    const files = input.files;
    
    if (files && files.length > 0) {
      this.handleFiles(files);
      input.value = '';
    }
  }
  
  /**
   * Traitement et validation des fichiers
   */
  private handleFiles(fileList: FileList): void {
    const files = Array.from(fileList);
    
    // Validation
    const validFiles = files.filter(file => {
      // Vérifier taille
      if (file.size === 0) {
        console.warn(`Fichier vide ignoré: ${file.name}`);
        return false;
      }
      
      if (file.size > this.maxFileSize) {
        console.warn(`Fichier trop gros ignoré: ${file.name} (${this.formatFileSize(file.size)})`);
        return false;
      }
      
      return true;
    });
    
    if (validFiles.length > 0) {
      this.filesSelected.emit(validFiles);
    }
  }
  
  /**
   * Trigger du click sur input
   */
  triggerFileInput(): void {
    if (this.disabled) return;
    
    const fileInput = document.getElementById('fileInput') as HTMLInputElement;
    if (fileInput) {
      fileInput.click();
    }
  }

  get isDisabled$(): Observable<boolean> {
    return this.isRateLimited$;
  }
  
  /**
   * Formater la taille de fichier
   */
  private formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
  }

    /**
     * Formater la taille max pour l'affichage
     */
    formatMaxSize(): string {
    return this.formatFileSize(this.maxFileSize);
    }
}