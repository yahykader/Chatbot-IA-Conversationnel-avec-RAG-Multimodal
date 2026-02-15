// features/crud/components/delete-all-modal/delete-all-modal.component.ts

import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-delete-all-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './delete-all-modal.component.html',
  styleUrls: ['./delete-all-modal.component.scss']


})
export class DeleteAllModalComponent {
  @Output() confirmed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();
  
  step: number = 1;
  confirmationText: string = '';
  isDeleting: boolean = false;
  
  getTitle(): string {
    switch (this.step) {
      case 1: return 'Suppression Globale - Étape 1/3';
      case 2: return 'Suppression Globale - Étape 2/3';
      case 3: return 'Suppression Globale - Étape 3/3';
      case 4: return 'Suppression en cours...';
      default: return 'Suppression Globale';
    }
  }
  
  nextStep(): void {
    if (this.step < 3) {
      this.step++;
    }
  }
  
  /**
   * ✅ METTRE ICI - Validation finale et déclenchement suppression
   */
  validateAndDelete(): void {
    // ✅ PAS de confirm() ici
    if (this.confirmationText === 'DELETE_ALL_FILES') {
      this.step = 4;
      this.isDeleting = true;
      this.confirmed.emit();  // ✅ Juste émettre l'event
    }
  }
  
  cancel(): void {
    this.reset();
    this.cancelled.emit();
    this.closeModal();
  }
  
  reset(): void {
    this.step = 1;
    this.confirmationText = '';
    this.isDeleting = false;
  }
  
  openModal(): void {
    this.reset();
    const modalElement = document.getElementById('deleteAllModal');
    if (modalElement) {
      const modal = new (window as any).bootstrap.Modal(modalElement);
      modal.show();
    }
  }
  
  closeModal(): void {
    const modalElement = document.getElementById('deleteAllModal');
    if (modalElement) {
      const modal = (window as any).bootstrap.Modal.getInstance(modalElement);
      if (modal) {
        modal.hide();
      }
    }
    this.cleanupModal();
  }


  private cleanupModal(): void {
    setTimeout(() => {
        // Supprimer tous les backdrops
        const backdrops = document.querySelectorAll('.modal-backdrop');
        backdrops.forEach(backdrop => backdrop.remove());
        
        // Restaurer le body
        document.body.classList.remove('modal-open');
        document.body.style.removeProperty('overflow');
        document.body.style.removeProperty('padding-right');
        
        // Nettoyer le modal
        const modalElement = document.getElementById('deleteAllModal');
        if (modalElement) {
        modalElement.classList.remove('show');
        modalElement.style.display = 'none';
        modalElement.setAttribute('aria-hidden', 'true');
        }
    }, 100);
    }
  
  // Méthode publique pour fermer après succès
  onDeleteSuccess(): void {
    this.reset();
    this.closeModal();
  }
}