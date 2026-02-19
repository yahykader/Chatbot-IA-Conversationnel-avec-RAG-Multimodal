// features/chat/store/chat.reducer.ts

import { createReducer, on } from '@ngrx/store';
import * as ChatActions from './chat.actions';
import { 
  initialChatState, 
  conversationsAdapter, 
  Message, 
  Conversation, 
  Citation
} from './chat.state';
import { v4 as uuidv4 } from 'uuid';

export const chatReducer = createReducer(
  initialChatState,
  
  // ✅ Create Conversation - Utiliser adapter.addOne()
  on(ChatActions.createConversation, (state) => {
    const newConversation: Conversation = {
      id: uuidv4(),
      title: 'Nouvelle conversation',
      messages: [],
      createdAt: new Date(),
      updatedAt: new Date()
    };
    
    return {
      ...state,
      conversations: conversationsAdapter.addOne(
        newConversation, 
        state.conversations
      ),
      activeConversationId: newConversation.id
    };
  }),
  
  // ✅ Load Conversations - Utiliser adapter.setAll()
  on(ChatActions.loadConversationsSuccess, (state, { conversations }) => ({
    ...state,
    conversations: conversationsAdapter.setAll(
      conversations, 
      state.conversations
    ),
    loading: false
  })),
  
  // Set Active Conversation (pas de changement)
  on(ChatActions.setActiveConversation, (state, { conversationId }) => ({
    ...state,
    activeConversationId: conversationId
  })),
  
  // ✅ Delete Conversation - Utiliser adapter.removeOne()
  on(ChatActions.deleteConversation, (state, { conversationId }) => ({
    ...state,
    conversations: conversationsAdapter.removeOne(
      conversationId, 
      state.conversations
    ),
    activeConversationId: state.activeConversationId === conversationId 
      ? null 
      : state.activeConversationId
  })),
  
  // ✅ Add User Message - Utiliser adapter.updateOne() au lieu de map()
  on(ChatActions.addUserMessage, (state, { message }) => {
    // Accès O(1) au lieu de find() O(n)
    const activeConv = state.conversations.entities[state.activeConversationId!];
    
    if (!activeConv) return state;
    
    const updatedConv: Conversation = {
      ...activeConv,
      messages: [...activeConv.messages, message],
      title: activeConv.messages.length === 0 
        ? message.content.substring(0, 50) 
        : activeConv.title,
      updatedAt: new Date()
    };
    
    return {
      ...state,
      conversations: conversationsAdapter.updateOne(
        { id: activeConv.id, changes: updatedConv },
        state.conversations
      )
    };
  }),
  
  // ✅ Add Assistant Message - Utiliser adapter.updateOne()
  on(ChatActions.addAssistantMessage, (state, { messageId }) => {
    // Accès O(1)
    const activeConv = state.conversations.entities[state.activeConversationId!];
    
    if (!activeConv) return state;
    
    const assistantMessage: Message = {
      id: messageId,
      role: 'assistant',
      content: '',
      timestamp: new Date(),
      status: 'streaming'
    };
    
    const updatedConv: Conversation = {
      ...activeConv,
      messages: [...activeConv.messages, assistantMessage],
      updatedAt: new Date()
    };
    
    return {
      ...state,
      conversations: conversationsAdapter.updateOne(
        { id: activeConv.id, changes: updatedConv },
        state.conversations
      ),
      isStreaming: true,
      streamingMessageId: messageId
    };
  }),
  
  // Stream Connected (pas de changement)
  on(ChatActions.streamConnected, (state, { event }) => ({
    ...state,
    streamSessionId: event.sessionId || null
  })),
  
  // ✅ Stream Token - Utiliser adapter.updateOne()
  on(ChatActions.streamToken, (state, { event }) => {
    if (!state.streamingMessageId) return state;
    
    // Accès O(1)
    const activeConv = state.conversations.entities[state.activeConversationId!];
    if (!activeConv) return state;
    
    // Mettre à jour le message qui streame
    const updatedMessages = activeConv.messages.map(msg =>
      msg.id === state.streamingMessageId
        ? { ...msg, content: msg.content + (event.text || '') }
        : msg
    );
    
    return {
      ...state,
      conversations: conversationsAdapter.updateOne(
        { 
          id: activeConv.id, 
          changes: { ...activeConv, messages: updatedMessages } 
        },
        state.conversations
      )
    };
  }),

  // ✅ Stream Complete - AVEC LOGS
  on(ChatActions.streamComplete, (state, { event }) => {
    console.log('📊 streamComplete reducer');
    
    if (!state.streamingMessageId) return state;
    
    const activeConv = state.conversations.entities[state.activeConversationId!];
    if (!activeConv) return state;
    
    // ✅ Extraire les sources
    const sources = event.response?.sources || [];
    
    console.log('✅ Sources extracted:', sources);
    
    // Convertir sources en format Citation avec juste le nom de fichier
    const citations = sources.map((source: any, index: number) => ({
      index: index + 1,
      content: source.file,  // Juste le nom du fichier
      sourceFile: source.file,
      sourcePage: source.page ? parseInt(source.page) : null
    }));
    
    console.log('✅ Converted citations:', citations);
    
    const updatedMessages = activeConv.messages.map(msg =>
      msg.id === state.streamingMessageId
        ? {
            ...msg,
            status: 'complete' as const,
            citations: citations,
            metadata: {
              ...event.metadata,
              sources: sources
            }
          }
        : msg
    );
    
    return {
      ...state,
      conversations: conversationsAdapter.updateOne(
        { 
          id: activeConv.id, 
          changes: { 
            ...activeConv, 
            messages: updatedMessages,
            updatedAt: new Date()
          } 
        },
        state.conversations
      ),
      isStreaming: false,
      streamingMessageId: null,
      streamSessionId: null
    };
  }),
  
  // Stream Error (pas de changement)
  on(ChatActions.streamError, (state, { error }) => ({
    ...state,
    isStreaming: false,
    streamingMessageId: null,
    streamSessionId: null,
    error
  })),
  
  // Cancel Stream (pas de changement)
  on(ChatActions.cancelStreamSuccess, (state) => ({
    ...state,
    isStreaming: false,
    streamingMessageId: null,
    streamSessionId: null
  })),
  
  // Input Text (pas de changement)
  on(ChatActions.updateInputText, (state, { text }) => ({
    ...state,
    inputText: text
  })),
  
  on(ChatActions.clearInputText, (state) => ({
    ...state,
    inputText: ''
  })),
  
  // Clear Error (pas de changement)
  on(ChatActions.clearError, (state) => ({
    ...state,
    error: null
  }))
);

  // ✅ Fonction pour extraire les citations
  function extractCitations(text: string): { cleanText: string; citations: Citation[] } {
    const citations: Citation[] = [];
    
    const cleanText = text.replace(
      /<cite\s+index="(\d+)">(.+?)<\/cite>/g, 
      (match, index, content) => {
        citations.push({
          index: parseInt(index, 10),
          content: content.trim()
        });
        return '';  // Supprimer du texte
      }
    );
    
    return { 
      cleanText: cleanText.trim(), 
      citations 
    };
  }