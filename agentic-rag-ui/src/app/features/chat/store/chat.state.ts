// features/chat/store/chat.state.ts

import { EntityState, EntityAdapter, createEntityAdapter } from '@ngrx/entity';

export interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
  status: 'pending' | 'streaming' | 'complete' | 'error';
  citations?: Citation[],
  metadata?: {
    model?: string;
    tokens?: number;
    sources?: any[];
  };
}

export interface Citation {
  index: number;
  content: string;
  sourceFile?: string;
  sourcePage?: number;
}

export interface Conversation {
  id: string;
  title: string;
  messages: Message[];
  createdAt: Date;
  updatedAt: Date;
}

//  EntityState pour conversations
export interface ConversationsState extends EntityState<Conversation> {
  // EntityState ajoute automatiquement:
  // - ids: string[]
  // - entities: { [id: string]: Conversation }
}

//  Créer l'adapter
export const conversationsAdapter: EntityAdapter<Conversation> = createEntityAdapter<Conversation>({
  selectId: (conversation) => conversation.id,
  sortComparer: (a, b) => b.updatedAt.getTime() - a.updatedAt.getTime()  // Tri par date
});

//  State initial généré par l'adapter
const initialConversationsState: ConversationsState = conversationsAdapter.getInitialState();

//  ChatState principal
export interface ChatState {
  conversations: ConversationsState;  // Maintenant un EntityState
  activeConversationId: string | null;
  
  // Streaming
  isStreaming: boolean;
  streamingMessageId: string | null;
  streamSessionId: string | null;
  
  // UI
  inputText: string;
  
  // Loading & Errors
  loading: boolean;
  error: string | null;
}

export const initialChatState: ChatState = {
  conversations: initialConversationsState,  // Utiliser l'état initial de l'adapter
  activeConversationId: null,
  isStreaming: false,
  streamingMessageId: null,
  streamSessionId: null,
  inputText: '',
  loading: false,
  error: null
};