import { createFeatureSelector, createSelector } from '@ngrx/store';
import { ChatState, conversationsAdapter, Conversation } from './chat.state';

export const selectChatState = createFeatureSelector<ChatState>('chat');

// ✅ Selectors générés automatiquement par l'adapter
const {
  selectAll: selectAllConversations,
  selectEntities: selectConversationEntities,
  selectIds: selectConversationIds,
  selectTotal: selectTotalConversations
} = conversationsAdapter.getSelectors(
  (state: ChatState) => state.conversations
);

// ✅ Exporter les selectors avec le bon scope + types
export const selectConversations = createSelector(
  selectChatState,
  (state: ChatState) => selectAllConversations(state)
);

export const selectConversationsDictionary = createSelector(
  selectChatState,
  (state: ChatState) => selectConversationEntities(state)
);

export const selectActiveConversationId = createSelector(
  selectChatState,
  (state: ChatState) => state.activeConversationId
);

// Sélectionner la conversation active depuis le dictionnaire (O(1))
export const selectActiveConversation = createSelector(
  selectConversationsDictionary,
  selectActiveConversationId,
  (entities: { [id: string]: Conversation | undefined }, activeId: string | null) =>
    activeId ? entities[activeId] : undefined
);

export const selectActiveMessages = createSelector(
  selectActiveConversation,
  (conversation: Conversation | undefined) => conversation?.messages || []
);

export const selectIsStreaming = createSelector(
  selectChatState,
  (state: ChatState) => state.isStreaming
);

export const selectInputText = createSelector(
  selectChatState,
  (state: ChatState) => state.inputText
);

export const selectError = createSelector(
  selectChatState,
  (state: ChatState) => state.error 
);

// ✅ BONUS: Selectors avancés avec types
export const selectConversationById = (id: string) => createSelector(
  selectConversationsDictionary,
  (entities: { [id: string]: Conversation | undefined }) => entities[id]
);

export const selectRecentConversations = createSelector(
  selectConversations,
  (conversations: Conversation[]) => conversations.slice(0, 5)
);