/**
 * Représente une opération de suppression en cours
 */
export interface DeleteOperation {
  id: string;
  type: 'file' | 'batch' | 'text-batch' | 'image-batch' | 'all';
  targetId: string; // embeddingId ou batchId
  status: 'pending' | 'success' | 'error';
  message?: string;
  deletedCount?: number;
  timestamp: Date;
}

/**
 * Représente une vérification de doublon
 */
export interface DuplicateCheck {
  filename: string;
  isDuplicate: boolean;
  existingBatchId?: string;
  message: string;
  timestamp: Date;
}

/**
 * Informations sur un batch
 */
export interface BatchInfo {
  batchId: string;
  found: boolean;
  textEmbeddings?: number;
  imageEmbeddings?: number;
  totalEmbeddings?: number;
  message?: string;
  timestamp: Date;
}

/**
 * State global du CRUD
 */
export interface CrudState {
  // Opérations de suppression
  deleteOperations: DeleteOperation[];
  activeDeleteOperations: number;
  
  // Vérifications de doublons
  duplicateChecks: { [filename: string]: DuplicateCheck };
  
  // Informations des batchs
  batchInfos: { [batchId: string]: BatchInfo };
  
  // Stats système
  systemStats: {
    totalStrategies?: number;
    activeIngestions?: number;
    trackedBatches?: number;
    totalEmbeddings?: number;
    filesInProgress?: number;
    redisHealthy?: boolean;
    systemStatus?: string;
    lastUpdate?: Date;
  };
  
  // État global
  loading: boolean;
  error: string | null;
}

/**
 * State initial
 */
export const initialCrudState: CrudState = {
  deleteOperations: [],
  activeDeleteOperations: 0,
  duplicateChecks: {},
  batchInfos: {},
  systemStats: {},
  loading: false,
  error: null
};