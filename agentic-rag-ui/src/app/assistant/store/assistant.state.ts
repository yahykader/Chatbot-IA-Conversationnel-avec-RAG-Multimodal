// ============================================================================
// STATE - assistant.state.ts (VERSION ADAPTÉE)
// ============================================================================
import { EntityState, EntityAdapter, createEntityAdapter } from '@ngrx/entity';
import { Message, UploadedFile } from './assistant.models';

// ==================== MESSAGES STATE ====================

/**
 * ✅ State pour les messages avec EntityAdapter
 */
export interface MessagesState extends EntityState<Message> {
  loading: boolean;
  error: string | null;
}

/**
 * ✅ State pour les fichiers avec EntityAdapter
 */
export interface FilesState extends EntityState<UploadedFile> {
  uploading: boolean;
  error: string | null;
}

/**
 * ✅ State global de l'assistant
 */
export interface AssistantState {
  messages: MessagesState;
  files: FilesState;
  userId: string;
  currentMessage: string;
}

// ============================================================================
// ADAPTERS
// ============================================================================

/**
 * ✅ Adapter pour les messages
 * Tri par séquence pour garantir l'ordre chronologique
 */
export const messagesAdapter: EntityAdapter<Message> = createEntityAdapter<Message>({
  selectId: (message: Message) => message.id,
  sortComparer: (a: Message, b: Message) => {
    // Tri principal par séquence
    if (a.sequence !== b.sequence) {
      return a.sequence - b.sequence;
    }
    // Tri secondaire par timestamp si séquences égales
    return new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime();
  }
});

/**
 * ✅ Adapter pour les fichiers
 * Tri par date (plus récent en premier)
 */
export const filesAdapter: EntityAdapter<UploadedFile> = createEntityAdapter<UploadedFile>({
  selectId: (file: UploadedFile) => file.id,
  sortComparer: (a: UploadedFile, b: UploadedFile) => 
    new Date(b.uploadDate).getTime() - new Date(a.uploadDate).getTime()
});

// ============================================================================
// INITIAL STATES
// ============================================================================

export const initialMessagesState: MessagesState = messagesAdapter.getInitialState({
  loading: false,
  error: null
});

export const initialFilesState: FilesState = filesAdapter.getInitialState({
  uploading: false,
  error: null
});

export const initialAssistantState: AssistantState = {
  messages: initialMessagesState,
  files: initialFilesState,
  userId: generateUserId(),
  currentMessage: ''
};

// ============================================================================
// HELPERS
// ============================================================================

/**
 * ✅ Génère ou récupère l'userId depuis localStorage
 */
function generateUserId(): string {
  const STORAGE_KEY = 'assistant_userId';
  
  let userId = localStorage.getItem(STORAGE_KEY);
  
  if (!userId) {
    userId = 'user_' + Date.now() + '_' + Math.random().toString(36).substring(2, 15);
    localStorage.setItem(STORAGE_KEY, userId);
    console.log('✅ Nouvel userId généré:', userId);
  } else {
    console.log('✅ UserId récupéré:', userId);
  }
  
  return userId;
}