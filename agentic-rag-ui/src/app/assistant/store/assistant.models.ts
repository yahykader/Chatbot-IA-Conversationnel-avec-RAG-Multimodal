// ============================================================================
// MODELS - assistant.models.ts (VERSION ADAPTÉE)
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
}

/**
 * ✅ Fichier uploadé
 */
export interface UploadedFile {
  id: string;
  name: string;
  size: number;
  uploadDate: Date;
  status: 'uploading' | 'success' | 'error';
  progress?: number;
}

/**
 * ✅ Request pour le chat (non utilisé avec SSE)
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
 * ✅ Response de l'upload
 */
export interface UploadResponse {
  success: boolean;
  message: string;
  filename: string;
  size: number;
  error?: string;
}

/**
 * ✅ Chunk SSE (non utilisé car géré par EventSource)
 */
export interface ChatStreamChunk {
  content: string;
  done?: boolean;
}