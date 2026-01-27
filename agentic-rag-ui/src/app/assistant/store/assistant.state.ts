// ============================================================================
// STATE - assistant.state.ts (VERSION v3.0 - Progression temps réel)
// ============================================================================
import { EntityState, EntityAdapter, createEntityAdapter } from '@ngrx/entity';
import { Message, UploadedFile, UploadStats, UploadProgress, UploadConfig, UploadError, DEFAULT_UPLOAD_CONFIG } from './assistant.models';

// ==================== MESSAGES STATE ====================

/**
 * ✅ State pour les messages avec EntityAdapter
 */
export interface MessagesState extends EntityState<Message> {
  loading: boolean;
  error: string | null;
  streamingMessageId: string | null;
}

// ==================== FILES STATE ====================

/**
 * ✅ State pour les fichiers avec EntityAdapter
 */
export interface FilesState extends EntityState<UploadedFile> {
  uploading: boolean;
  error: string | null;
  
  // Statistiques
  stats: UploadStats;
  progress: UploadProgress;
  
  // Gestion duplicatas
  showDuplicateModal: boolean;
  currentDuplicateFileId: string | null;
  
  // Historique erreurs
  uploadErrors: UploadError[];
  
  // Files en cours de polling
  pollingFileIds: Set<string>;
  
  // ✅ NOUVEAU : Batch upload tracking
  batchUploadInProgress: boolean;
  currentBatchId: string | null;
}

// ==================== GLOBAL STATE ====================

/**
 * ✅ State global de l'assistant
 */
export interface AssistantState {
  messages: MessagesState;
  files: FilesState;
  
  // User & UI
  userId: string;
  currentMessage: string;
  isSidebarOpen: boolean;
  
  // Configuration
  uploadConfig: UploadConfig;
  
  // État global
  globalLoading: boolean;
  globalError: string | null;
  
  // ✅ NOUVEAU : Notifications
  notifications: NotificationState[];
}

// ✅ NOUVEAU : Type pour notifications (optionnel)
export interface NotificationState {
  id: string;
  message: string;
  type: 'success' | 'error' | 'warning' | 'info';
  timestamp: Date;
  duration?: number;
}


// ============================================================================
// ADAPTERS
// ============================================================================

export const messagesAdapter: EntityAdapter<Message> = createEntityAdapter<Message>({
  selectId: (message: Message) => message.id,
  sortComparer: (a: Message, b: Message) => {
    if (a.sequence !== b.sequence) {
      return a.sequence - b.sequence;
    }
    return new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime();
  }
});

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
  error: null,
  streamingMessageId: null
});

export const initialFilesState: FilesState = filesAdapter.getInitialState({
  uploading: false,
  error: null,
  stats: {
    total: 0,
    pending: 0,
    uploading: 0,
    processing: 0,
    completed: 0,
    failed: 0,
    duplicate: 0
  },
  progress: {
    totalFiles: 0,
    completedFiles: 0,
    failedFiles: 0,
    duplicateFiles: 0,
    overallProgress: 0
  },
  showDuplicateModal: false,
  currentDuplicateFileId: null,
  uploadErrors: [],
  pollingFileIds: new Set<string>(),
  batchUploadInProgress: false,
  currentBatchId: null
});

export const initialAssistantState: AssistantState = {
  messages: initialMessagesState,
  files: initialFilesState,
  userId: generateUserId(),
  currentMessage: '',
  isSidebarOpen: true,
  uploadConfig: DEFAULT_UPLOAD_CONFIG,
  globalLoading: false,
  globalError: null,
  notifications: []
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

/**
 * ✅ Calcule les statistiques depuis l'état des fichiers
 */
export function calculateStatsFromFiles(files: UploadedFile[]): UploadStats {
  return {
    total: files.length,
    pending: files.filter(f => f.status === 'pending').length,
    uploading: files.filter(f => f.status === 'uploading').length,
    processing: files.filter(f => f.status === 'processing').length,
    completed: files.filter(f => f.status === 'completed').length,
    failed: files.filter(f => f.status === 'failed').length,
    duplicate: files.filter(f => f.status === 'duplicate').length
  };
}

/**
 * ✅ AMÉLIORÉ : Calcule la progression globale avec gestion progress undefined
 */
export function calculateProgressFromFiles(files: UploadedFile[]): UploadProgress {
  const totalFiles = files.length;
  const completedFiles = files.filter(f => f.status === 'completed').length;
  const failedFiles = files.filter(f => f.status === 'failed').length;
  const duplicateFiles = files.filter(f => f.status === 'duplicate').length;
  
  // ✅ Gestion des fichiers sans progress défini
  const totalProgress = files.reduce((sum, file) => {
    const progress = file.progress ?? 0;
    return sum + progress;
  }, 0);
  
  const overallProgress = totalFiles > 0
    ? Math.round(totalProgress / totalFiles)
    : 0;
  
  return {
    totalFiles,
    completedFiles,
    failedFiles,
    duplicateFiles,
    overallProgress
  };
}

/**
 * ✅ Vérifie si un fichier doit être pollé
 */
export function shouldPollFile(file: UploadedFile): boolean {
  return file.status === 'processing' && !!file.jobId;
}

/**
 * ✅ Filtre les fichiers actifs (en cours)
 */
export function getActiveFiles(files: UploadedFile[]): UploadedFile[] {
  return files.filter(f => 
    f.status === 'pending' || 
    f.status === 'uploading' || 
    f.status === 'processing'
  );
}

/**
 * ✅ Filtre les fichiers terminés
 */
export function getCompletedFiles(files: UploadedFile[]): UploadedFile[] {
  return files.filter(f => f.status === 'completed');
}

/**
 * ✅ Filtre les fichiers échoués
 */
export function getFailedFiles(files: UploadedFile[]): UploadedFile[] {
  return files.filter(f => f.status === 'failed');
}

/**
 * ✅ Filtre les duplicatas
 */
export function getDuplicateFiles(files: UploadedFile[]): UploadedFile[] {
  return files.filter(f => f.status === 'duplicate');
}

/**
 * ✅ Obtient le message actuellement en streaming
 */
export function getStreamingMessage(state: MessagesState, entities: { [id: string]: Message }): Message | null {
  if (!state.streamingMessageId) {
    return null;
  }
  return entities[state.streamingMessageId] || null;
}

/**
 * ✅ Vérifie s'il y a des uploads actifs
 */
export function hasActiveUploads(files: UploadedFile[]): boolean {
  return files.some(f => 
    f.status === 'pending' || 
    f.status === 'uploading' || 
    f.status === 'processing'
  );
}

/**
 * ✅ Obtient le prochain numéro de séquence pour un message
 */
export function getNextMessageSequence(messages: Message[]): number {
  if (messages.length === 0) {
    return 0;
  }
  return Math.max(...messages.map(m => m.sequence)) + 1;
}

/**
 * ✅ Nettoie les anciennes erreurs (garde les 10 dernières)
 */
export function cleanupOldErrors(errors: UploadError[], maxErrors: number = 10): UploadError[] {
  if (errors.length <= maxErrors) {
    return errors;
  }
  return [...errors]
    .sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime())
    .slice(0, maxErrors);
}

/**
 * ✅ Crée un objet UploadError
 */
export function createUploadError(fileId: string, fileName: string, error: string): UploadError {
  return {
    fileId,
    fileName,
    error,
    timestamp: new Date()
  };
}

/**
 * ✅ NOUVEAU : Calcule le temps écoulé depuis un timestamp
 */
export function getElapsedTime(startTime: Date): string {
  const elapsed = Date.now() - startTime.getTime();
  const seconds = Math.floor(elapsed / 1000);
  
  if (seconds < 60) {
    return `${seconds}s`;
  }
  
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}m ${remainingSeconds}s`;
}

/**
 * ✅ NOUVEAU : Estime le temps restant pour un upload
 */
export function estimateTimeRemaining(progress: number, startTime: Date): string {
  if (progress === 0) return 'Calcul...';
  if (progress >= 100) return 'Terminé';
  
  const elapsed = Date.now() - startTime.getTime();
  const estimatedTotal = (elapsed / progress) * 100;
  const remaining = estimatedTotal - elapsed;
  
  const seconds = Math.floor(remaining / 1000);
  
  if (seconds < 60) {
    return `~${seconds}s`;
  }
  
  const minutes = Math.floor(seconds / 60);
  return `~${minutes}m`;
}

/**
 * ✅ NOUVEAU : Formate la vitesse d'upload
 */
export function formatUploadSpeed(bytesPerSecond: number): string {
  if (bytesPerSecond < 1024) {
    return `${bytesPerSecond.toFixed(0)} B/s`;
  }
  if (bytesPerSecond < 1024 * 1024) {
    return `${(bytesPerSecond / 1024).toFixed(1)} KB/s`;
  }
  return `${(bytesPerSecond / (1024 * 1024)).toFixed(1)} MB/s`;
}

/**
 * ✅ Sauvegarde l'état dans localStorage
 */
export function saveStateToStorage(state: AssistantState): void {
  try {
    const toSave = {
      userId: state.userId,

      messages: Object.values(state.messages.entities)
        .filter((msg): msg is NonNullable<typeof msg> => !!msg)
        .filter(msg => !msg.isStreaming && !msg.isLoading),

      files: Object.values(state.files.entities)
        .filter((file): file is NonNullable<typeof file> => !!file)
        .map(file => ({
          ...file,
          // ✅ Marquer les fichiers en cours comme failed au reload
          ...(file.status === 'uploading' || file.status === 'processing'
            ? { status: 'failed' as const, error: 'Interrompu par rechargement' }
            : {})
        }))
    };

    localStorage.setItem(STORAGE_KEYS.STATE, JSON.stringify(toSave));
    console.log('💾 État sauvegardé:', {
      messages: toSave.messages.length,
      files: toSave.files.length
    });
  } catch (error) {
    console.error("❌ Erreur sauvegarde état:", error);
  }
}

/**
 * ✅ Charge l'état depuis localStorage
 */
export function loadStateFromStorage(): Partial<AssistantState> | null {
  try {
    const saved = localStorage.getItem(STORAGE_KEYS.STATE);
    if (!saved) {
      return null;
    }
    
    const parsed = JSON.parse(saved);
    console.log('📥 État chargé:', {
      userId: parsed.userId,
      messagesCount: parsed.messages?.length || 0,
      filesCount: parsed.files?.length || 0
    });
    
    return {
      userId: parsed.userId,
    };
  } catch (error) {
    console.error('❌ Erreur chargement état:', error);
    return null;
  }
}

/**
 * ✅ Efface l'état du localStorage
 */
export function clearStateFromStorage(): void {
  try {
    Object.values(STORAGE_KEYS).forEach(key => {
      localStorage.removeItem(key);
    });
    console.log('🗑️ État effacé du localStorage');
  } catch (error) {
    console.error('❌ Erreur effacement état:', error);
  }
}

// ============================================================================
// TYPE GUARDS
// ============================================================================

export function isDuplicateFile(file: UploadedFile): boolean {
  return file.status === 'duplicate';
}

export function isFailedFile(file: UploadedFile): boolean {
  return file.status === 'failed';
}

export function isCompletedFile(file: UploadedFile): boolean {
  return file.status === 'completed';
}

export function isStreamingMessage(message: Message): boolean {
  return message.isStreaming === true;
}

export function isLoadingMessage(message: Message): boolean {
  return message.isLoading === true;
}

/**
 * ✅ NOUVEAU : Vérifie si un fichier est en cours d'upload
 */
export function isUploadingFile(file: UploadedFile): boolean {
  return file.status === 'uploading';
}

/**
 * ✅ NOUVEAU : Vérifie si un fichier est en cours de traitement
 */
export function isProcessingFile(file: UploadedFile): boolean {
  return file.status === 'processing';
}

/**
 * ✅ NOUVEAU : Vérifie si un fichier est actif (en cours)
 */
export function isActiveFile(file: UploadedFile): boolean {
  return file.status === 'uploading' || 
         file.status === 'processing' || 
         file.status === 'pending';
}

// ============================================================================
// CONSTANTS
// ============================================================================

export const STORAGE_KEYS = {
  USER_ID: 'assistant_userId',
  STATE: 'assistant_state',
  MESSAGES: 'assistant_messages',
  FILES: 'assistant_files',
  CONFIG: 'assistant_config'
} as const;

export const LIMITS = {
  MAX_MESSAGES: 100,
  MAX_FILES: 50,
  MAX_ERRORS: 10,
  POLLING_INTERVAL: 2000,         // 2 secondes
  POLLING_MAX_ATTEMPTS: 150,      // ✅ 5 minutes (2s × 150 = 300s)
  STREAM_TIMEOUT: 120000,         // 2 minutes
  UPLOAD_TIMEOUT: 300000,         // 5 minutes
  MAX_CONCURRENT_UPLOADS: 3,
  PROGRESS_UPDATE_THROTTLE: 100
} as const;

/**
 * ✅ NOUVEAU : Status colors pour le UI
 */
export const STATUS_COLORS = {
  pending: '#6c757d',
  uploading: '#17a2b8',
  processing: '#007bff',
  completed: '#28a745',
  failed: '#dc3545',
  duplicate: '#ffc107'
} as const;

/**
 * ✅ NOUVEAU : Status icons
 */
export const STATUS_ICONS = {
  pending: 'bi-hourglass',
  uploading: 'bi-arrow-up-circle',
  processing: 'bi-arrow-repeat',
  completed: 'bi-check-circle',
  failed: 'bi-x-circle',
  duplicate: 'bi-exclamation-triangle'
} as const;

export const ASSISTANT_FEATURE_KEY = 'assistant';

// ============================================================================
// UTILITY TYPES
// ============================================================================

/**
 * ✅ NOUVEAU : Type pour les actions de batch
 */
export type BatchUploadStatus = {
  batchId: string;
  totalFiles: number;
  completedFiles: number;
  failedFiles: number;
  startTime: Date;
  estimatedCompletion?: Date;
};

/**
 * ✅ NOUVEAU : Type pour le tracking de progression
 */
export type ProgressTracker = {
  fileId: string;
  fileName: string;
  progress: number;
  speed: number;
  startTime: Date;
  estimatedTimeRemaining: string;
};