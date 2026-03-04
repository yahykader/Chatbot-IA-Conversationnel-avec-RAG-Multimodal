import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';

import * as ChatActions from '../../store/chat.actions';
import * as ChatSelectors from '../../store/chat.selectors';
import { Conversation } from '../../store/chat.state';

import { ChatInterfaceComponent } from '../../components/chat-interface/chat-interface.component';

@Component({
  selector: 'app-chat-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ChatInterfaceComponent
  ],
  templateUrl: './chat-page.component.html',
  styleUrls: ['./chat-page.component.scss']
})
export class ChatPageComponent implements OnInit {
  
  conversations$: Observable<Conversation[]>;
  activeConversationId$: Observable<string | null>;
  
  sidebarCollapsed = false;
  
  constructor(private store: Store) {
    this.conversations$ = this.store.select(ChatSelectors.selectConversations);
    this.activeConversationId$ = this.store.select(ChatSelectors.selectActiveConversationId);
  }
  
  ngOnInit(): void {
    // Charger les conversations (si vous avez une API backend)
    // this.store.dispatch(ChatActions.loadConversations());
  }
  
  onNewConversation(): void {
    this.store.dispatch(ChatActions.createConversation());
  }
  
  onSelectConversation(conversationId: string): void {
    this.store.dispatch(ChatActions.setActiveConversation({ conversationId }));
  }
  
  onDeleteConversation(conversationId: string, event: Event): void {
    event.stopPropagation();
    
    if (confirm('Supprimer cette conversation ?')) {
      this.store.dispatch(ChatActions.deleteConversation({ conversationId }));
    }
  }
  
  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }
  
  trackByConversationId(index: number, conversation: Conversation): string {
    return conversation.id;
  }
}