import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-delete-batch-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './delete-batch-modal.component.html',
  styleUrls: ['./delete-batch-modal.component.scss']

})
export class DeleteBatchModalComponent {
    
 @Input() modalId: string = 'deleteBatchModal';
  @Input() filename: string = '';
  @Input() batchId: string = '';
  @Input() isDuplicate: boolean = false;
  
  @Output() confirmed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();
  
  isDeleting: boolean = false;
  
  confirm(): void {
    this.isDeleting = true;
    this.confirmed.emit();
  }
  
  cancel(): void {
    this.cancelled.emit();
    this.closeModal();
  }
  
    openModal(): void {
    console.log(`🔓 Opening modal with ID: ${this.modalId}`, {
        filename: this.filename,
        batchId: this.batchId,
        isDuplicate: this.isDuplicate
    });
    
    this.isDeleting = false;
    const modalElement = document.getElementById(this.modalId);
    
    if (!modalElement) {
        console.error(`❌ Modal element not found: ${this.modalId}`);
        return;
    }
    
    console.log(`✅ Modal element found:`, modalElement);
    
    const modal = new (window as any).bootstrap.Modal(modalElement);
    modal.show();
    }
    
  closeModal(): void {
    const modalElement = document.getElementById(this.modalId);
    if (modalElement) {
      const modal = (window as any).bootstrap.Modal.getInstance(modalElement);
      if (modal) {
        modal.hide();
      }
    }
    
    this.cleanupModal();
    this.isDeleting = false;
  }
  
  private cleanupModal(): void {
    setTimeout(() => {
      const backdrops = document.querySelectorAll('.modal-backdrop');
      backdrops.forEach(backdrop => backdrop.remove());
      
      document.body.classList.remove('modal-open');
      document.body.style.removeProperty('overflow');
      document.body.style.removeProperty('padding-right');
      
      const modalElement = document.getElementById(this.modalId);  // ✅ Utiliser modalId
      if (modalElement) {
        modalElement.classList.remove('show');
        modalElement.style.display = 'none';
        modalElement.setAttribute('aria-hidden', 'true');
      }
    }, 100);
  }
  
  onDeleteSuccess(): void {
    this.closeModal();
  }
}