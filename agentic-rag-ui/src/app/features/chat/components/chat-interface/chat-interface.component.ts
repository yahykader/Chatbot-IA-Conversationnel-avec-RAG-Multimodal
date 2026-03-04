import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewChecked, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { Observable, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import * as ChatActions from '../../store/chat.actions';
import * as ChatSelectors from '../../store/chat.selectors';
import { Message } from '../../store/chat.state';

import { MessageItemComponent } from '../message-item/message-item.component';
import { MessageInputComponent } from '../message-input/message-input.component';

@Component({
  selector: 'app-chat-interface',
  standalone: true,
  imports: [
    CommonModule,
    MessageItemComponent,
    MessageInputComponent
  ],
  templateUrl: './chat-interface.component.html',
  styleUrls: ['./chat-interface.component.scss']
})
export class ChatInterfaceComponent implements OnInit, AfterViewChecked, OnDestroy {
  
  @ViewChild('messagesContainer') messagesContainer!: ElementRef;
  
  // Observables pour le template
  messages$: Observable<Message[]>;
  error$: Observable<string | null>;
  
  //  Propriétés synchrones (pas d'Observable)
  inputText: string = '';
  isStreaming: boolean = false;
  
  private shouldScrollToBottom = true;
  private destroy$ = new Subject<void>();
  
  constructor(private store: Store, private cdr: ChangeDetectorRef) {
    this.messages$ = this.store.select(ChatSelectors.selectActiveMessages);
    this.error$ = this.store.select(ChatSelectors.selectError);
    
    // ✅ Subscribe aux observables et stocker les valeurs
    this.store.select(ChatSelectors.selectInputText)
      .pipe(takeUntil(this.destroy$))
      .subscribe(text => this.inputText = text || '');
    
    this.store.select(ChatSelectors.selectIsStreaming)
      .pipe(takeUntil(this.destroy$))
      .subscribe(streaming => this.isStreaming = streaming ?? false);
  }
  
  ngOnInit(): void {
    this.store.select(ChatSelectors.selectActiveConversationId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(activeId => {
        if (!activeId) {
          //setTimeout(() => {
            this.store.dispatch(ChatActions.createConversation());
            this.cdr.detectChanges();
          //}, 0);
        }
      });
  }
  
  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
    }
  }
  
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
  
  onSendMessage(content: string): void {
    this.store.dispatch(ChatActions.sendMessage({ content }));
    this.shouldScrollToBottom = true;
  }
  
  onCancelStream(): void {
    this.store.dispatch(ChatActions.cancelStream());
  }
  
  onInputChange(text: string): void {
    this.store.dispatch(ChatActions.updateInputText({ text }));
  }
  
  trackByMessageId(index: number, message: Message): string {
    return message.id;
  }
  
  private scrollToBottom(): void {
    try {
      if (this.messagesContainer) {
        const element = this.messagesContainer.nativeElement;
        element.scrollTop = element.scrollHeight;
      }
    } catch (err) {
      console.error('Error scrolling to bottom:', err);
    }
  }
}