// features/chat/store/chat.actions.ts

import { createAction, props } from '@ngrx/store';
import { Message, Conversation } from './chat.state';
import { StreamEvent } from '../../../core/services/streaming-api.service';

// Conversations
export const createConversation = createAction(
  '[Chat] Create Conversation'
);

export const loadConversations = createAction(
  '[Chat] Load Conversations'
);

export const loadConversationsSuccess = createAction(
  '[Chat] Load Conversations Success',
  props<{ conversations: Conversation[] }>()
);

export const setActiveConversation = createAction(
  '[Chat] Set Active Conversation',
  props<{ conversationId: string }>()
);

export const deleteConversation = createAction(
  '[Chat] Delete Conversation',
  props<{ conversationId: string }>()
);

// Messages
export const sendMessage = createAction(
  '[Chat] Send Message',
  props<{ content: string; conversationId?: string }>()
);

export const addUserMessage = createAction(
  '[Chat] Add User Message',
  props<{ message: Message }>()
);

export const addAssistantMessage = createAction(
  '[Chat] Add Assistant Message',
  props<{ messageId: string }>()
);

// Streaming
export const streamConnected = createAction(
  '[Chat] Stream Connected',
  props<{ event: StreamEvent }>()
);

export const streamToken = createAction(
  '[Chat] Stream Token',
  props<{ event: StreamEvent }>()
);

export const streamComplete = createAction(
  '[Chat] Stream Complete',
  props<{ event: StreamEvent }>()
);

export const streamError = createAction(
  '[Chat] Stream Error',
  props<{ error: string }>()
);

export const cancelStream = createAction(
  '[Chat] Cancel Stream'
);

export const cancelStreamSuccess = createAction(
  '[Chat] Cancel Stream Success'
);

// Input
export const updateInputText = createAction(
  '[Chat] Update Input Text',
  props<{ text: string }>()
);

export const clearInputText = createAction(
  '[Chat] Clear Input Text'
);

// Errors
export const clearError = createAction(
  '[Chat] Clear Error'
);