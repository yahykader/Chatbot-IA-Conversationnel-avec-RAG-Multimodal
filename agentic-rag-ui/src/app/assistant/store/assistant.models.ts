// ============================================================================
// MODELS - assistant.models.ts (VERSION v2.1 - FIXED)
// ============================================================================

/**
 * ✅ Message dans le chat
 */
export interface Message {
  id: string;
  content: string;
  sender: 'user' | 'assistant';
  timestamp: Date;
  sequence: number;
  isLoading?: boolean;      // true = message placeholder (avant streaming)
  isStreaming?: boolean;    // true = streaming en cours
  error?: string;           // ✅ NOUVEAU : message d'erreur
}

/**
 * ✅ Fichier uploadé - ENRICHI avec backend response
 */
export interface UploadedFile {
  id: string;                    // ID local (généré côté frontend)
  name: string;
  size: number;
  type?: string;                 // ✅ NOUVEAU : type MIME
  uploadDate: Date;
  status: 'pending' | 'uploading' | 'processing' | 'completed' | 'failed' | 'duplicate'; // ✅ MODIFIÉ
  progress: number;              // ✅ MODIFIÉ : obligatoire (0-100)
  
  // ✅ NOUVEAU : Métadonnées backend
  jobId?: string;                // Job ID du backend
  existingJobId?: string;        // Job ID existant (si duplicata)
  error?: string;                // Message d'erreur
  
  // ✅ NOUVEAU : Informations duplicata
  duplicateInfo?: DuplicateInfo;
  
  // ✅ NOUVEAU : Timestamps
  completedAt?: Date;
}

/**
 * ✅ NOUVEAU : Informations sur les duplicatas
 */
export interface DuplicateInfo {
  jobId: string;
  originalFileName: string;
  uploadedAt: string;            // ISO 8601 format
  fingerprint: string;
  fileSize: number;
}

/**
 * ✅ Request pour le chat (non utilisé avec SSE mais conservé pour compatibilité)
 */
export interface ChatRequest {
  userId: string;
  message: string;
}

/**
 * ✅ Response du chat classique (non utilisé avec SSE)
 */
export interface ChatResponse {
  success: boolean;
  response: string;
  userId: string;
  error?: string;
}

/**
 * ✅ Response de l'upload - COMPLÈTEMENT ADAPTÉ AU BACKEND
 */
export interface UploadResponse {
  jobId: string;
  fileName: string;
  status: 'processing' | 'completed' | 'failed' | 'duplicate';
  message: string;
  duplicate: boolean;            // ✅ MODIFIÉ : "duplicate" au lieu de "isDuplicate"
  existingJobId?: string;
  duplicateInfo?: DuplicateInfo;
  fileSize: number;
  fileSizeKB: number;
}

/**
 * ✅ NOUVEAU : Response du statut d'upload (polling)
 */
export interface UploadStatusResponse {
  jobId: string;
  filename: string;
  status: 'pending' | 'processing' | 'completed' | 'failed';
  progress: number;
  message: string;
  error?: string;
  createdAt: string;             // ISO 8601 format
  completedAt?: string;          // ISO 8601 format
}

/**
 * ✅ NOUVEAU : Liste des uploads
 */
export interface UploadListResponse {
  uploads: UploadStatusResponse[];
  totalCount: number;
}

/**
 * ✅ Chunk SSE - ENRICHI
 */
export interface ChatStreamChunk {
  content: string;
  done?: boolean;
  error?: string;                // ✅ NOUVEAU : erreur streaming
}

/**
 * ✅ NOUVEAU : Event SSE typé
 */
export interface SSEEvent {
  event: 'chunk' | 'final' | 'done' | 'error' | 'heartbeat';
  id: string;
  data: string;
}

/**
 * ✅ NOUVEAU : Statistiques d'upload
 */
export interface UploadStats {
  total: number;
  pending: number;
  uploading: number;
  processing: number;
  completed: number;
  failed: number;
  duplicate: number;
}

/**
 * ✅ NOUVEAU : Progression globale des uploads
 */
export interface UploadProgress {
  totalFiles: number;
  completedFiles: number;
  failedFiles: number;
  duplicateFiles: number;
  overallProgress: number;       // 0-100
}

/**
 * ✅ FIXED : Configuration upload (FUSION des deux définitions)
 */
export interface UploadConfig {
  maxFileSize: number;           // en bytes
  allowedExtensions: string[];   // Liste des extensions autorisées
  maxConcurrent: number;         // Nombre max d'uploads simultanés (alias)
  maxConcurrentUploads: number;  // Nombre max d'uploads simultanés
  autoRetry: boolean;            // Réessayer automatiquement en cas d'échec
  retryAttempts: number;         // Nombre de tentatives
  retryDelay: number;            // Délai entre les tentatives (ms)
  userId?: number;               // ID utilisateur (optionnel)
}

/**
 * ✅ NOUVEAU : Erreur d'upload
 */
export interface UploadError {
  fileId: string;
  fileName: string;
  error: string;
  timestamp: Date;
}

/**
 * ✅ NOUVEAU : State de l'assistant (pour le store)
 */
export interface AssistantState {
  // Messages
  messages: Message[];
  currentMessage: string;
  
  // Uploads
  uploadedFiles: UploadedFile[];
  uploadStats: UploadStats;
  uploadProgress: UploadProgress;
  
  // UI State
  isLoading: boolean;
  isSidebarOpen: boolean;
  showDuplicateModal: boolean;
  currentDuplicateFileId: string | null;
  
  // Errors
  error: string | null;
  uploadErrors: UploadError[];
  
  // Configuration
  uploadConfig: UploadConfig;
}

/**
 * ✅ NOUVEAU : Helper pour créer un message
 */
export function createMessage(
  content: string, 
  sender: 'user' | 'assistant', 
  sequence: number,
  options?: Partial<Message>
): Message {
  return {
    id: generateMessageId(),
    content,
    sender,
    timestamp: new Date(),
    sequence,
    isLoading: false,
    isStreaming: false,
    ...options
  };
}

/**
 * ✅ NOUVEAU : Helper pour créer un fichier uploadé
 */
export function createUploadedFile(
  file: File,
  options?: Partial<UploadedFile>
): UploadedFile {
  return {
    id: generateFileId(file),
    name: file.name,
    size: file.size,
    type: file.type,
    uploadDate: new Date(),
    status: 'pending',
    progress: 0,
    ...options
  };
}

/**
 * ✅ NOUVEAU : Helper pour générer ID message
 */
export function generateMessageId(): string {
  return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * ✅ NOUVEAU : Helper pour générer ID fichier
 */
export function generateFileId(file: File): string {
  return `${file.name}-${file.size}-${file.lastModified}`;
}

/**
 * ✅ NOUVEAU : Helper pour formater la taille de fichier
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

/**
 * ✅ NOUVEAU : Helper pour vérifier si un fichier est valide
 */
export function isValidFile(file: File, config: UploadConfig): { valid: boolean; error?: string } {
  // Vérifier la taille
  if (file.size > config.maxFileSize) {
    const maxSizeMB = config.maxFileSize / (1024 * 1024);
    const fileSizeMB = file.size / (1024 * 1024);
    return {
      valid: false,
      error: `Fichier trop volumineux: ${fileSizeMB.toFixed(2)} MB (max: ${maxSizeMB.toFixed(2)} MB)`
    };
  }
  
  // Vérifier l'extension
  const extension = file.name.split('.').pop()?.toLowerCase();
  if (extension && !config.allowedExtensions.includes(`.${extension}`)) {
    return {
      valid: false,
      error: `Extension non autorisée: .${extension}`
    };
  }
  
  return { valid: true };
}

/**
 * ✅ NOUVEAU : Helper pour calculer les statistiques d'upload
 */
export function calculateUploadStats(files: UploadedFile[]): UploadStats {
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
 * ✅ NOUVEAU : Helper pour calculer la progression globale
 */
export function calculateOverallProgress(files: UploadedFile[]): UploadProgress {
  const totalFiles = files.length;
  const completedFiles = files.filter(f => f.status === 'completed').length;
  const failedFiles = files.filter(f => f.status === 'failed').length;
  const duplicateFiles = files.filter(f => f.status === 'duplicate').length;
  
  const overallProgress = totalFiles > 0
    ? Math.round(files.reduce((sum, file) => sum + file.progress, 0) / totalFiles)
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
 * ✅ NOUVEAU : Helper pour obtenir la couleur du statut
 */
export function getStatusColor(status: UploadedFile['status']): string {
  const colors: Record<UploadedFile['status'], string> = {
    'pending': 'secondary',
    'uploading': 'info',
    'processing': 'primary',
    'completed': 'success',
    'failed': 'danger',
    'duplicate': 'warning'
  };
  return colors[status] || 'secondary';
}

/**
 * ✅ NOUVEAU : Helper pour obtenir l'icône du statut
 */
export function getStatusIcon(status: UploadedFile['status']): string {
  const icons: Record<UploadedFile['status'], string> = {
    'pending': '○',
    'uploading': '↑',
    'processing': '⟳',
    'completed': '✓',
    'failed': '✗',
    'duplicate': '⚠'
  };
  return icons[status] || '○';
}

/**
 * ✅ NOUVEAU : Helper pour obtenir le label du statut
 */
export function getStatusLabel(status: UploadedFile['status']): string {
  const labels: Record<UploadedFile['status'], string> = {
    'pending': 'En attente',
    'uploading': 'Upload en cours',
    'processing': 'Traitement',
    'completed': 'Terminé',
    'failed': 'Échec',
    'duplicate': 'Duplicata'
  };
  return labels[status] || status;
}

/**
 * ✅ NOUVEAU : Type guards
 */
export function isUploadCompleted(file: UploadedFile): boolean {
  return file.status === 'completed';
}

export function isUploadFailed(file: UploadedFile): boolean {
  return file.status === 'failed';
}

export function isUploadDuplicate(file: UploadedFile): boolean {
  return file.status === 'duplicate';
}

export function isUploadActive(file: UploadedFile): boolean {
  return file.status === 'pending' || 
         file.status === 'uploading' || 
         file.status === 'processing';
}

/**
 * ✅ FIXED : Configuration par défaut (toutes les propriétés requises)
 */
export const DEFAULT_UPLOAD_CONFIG: UploadConfig = {
  maxFileSize: 10 * 1024 * 1024,        // 10MB en bytes
  allowedExtensions: [
    '.pdf', '.doc', '.docx', '.txt', 
    '.jpg', '.jpeg', '.png', '.gif',
    '.xls', '.xlsx', '.csv'
  ],
  maxConcurrent: 3,                     // Alias pour maxConcurrentUploads
  maxConcurrentUploads: 3,              // Nombre max d'uploads simultanés
  autoRetry: true,                      // Réessayer automatiquement
  retryAttempts: 3,                     // 3 tentatives max
  retryDelay: 10000,                     // 1 seconde entre les tentatives
  userId: undefined                     // Optionnel, sera défini à l'initialisation
};

/**
 * ✅ NOUVEAU : State initial
 */
export const INITIAL_ASSISTANT_STATE: AssistantState = {
  messages: [],
  currentMessage: '',
  uploadedFiles: [],
  uploadStats: {
    total: 0,
    pending: 0,
    uploading: 0,
    processing: 0,
    completed: 0,
    failed: 0,
    duplicate: 0
  },
  uploadProgress: {
    totalFiles: 0,
    completedFiles: 0,
    failedFiles: 0,
    duplicateFiles: 0,
    overallProgress: 0
  },
  isLoading: false,
  isSidebarOpen: true,
  showDuplicateModal: false,
  currentDuplicateFileId: null,
  error: null,
  uploadErrors: [],
  uploadConfig: DEFAULT_UPLOAD_CONFIG
};