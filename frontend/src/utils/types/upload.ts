export type UploadedFileStatus =
  | 'uploading'
  | 'uploaded'
  | 'processing'
  | 'parsed'
  | 'failed'
  | 'deleted'

export type ParseStatus = 'pending' | 'processing' | 'success' | 'failed'

export type SourceType =
  | 'document'
  | 'image'
  | 'audio'
  | 'video'
  | 'code'
  | 'archive'
  | 'other'

export type ClientUploadStatus = 'queued' | 'uploading' | 'success' | 'failed' | 'canceled'

export type SuggestedResourceType =
  | 'lecture_note'
  | 'exercise'
  | 'mind_map'
  | 'case'
  | 'reference'
  | 'other'

export interface StorageUsage {
  usedBytes: number
  totalBytes: number
  fileCount: number
  maxFileSizeBytes?: number
  supportedMimeTypes?: string[]
}

export interface UploadedFile {
  id: string
  name: string
  sizeBytes: number
  mimeType: string
  extension?: string
  uploadedAt: string
  updatedAt?: string
  uploaderId: string
  url?: string
  thumbnailUrl?: string
  status: UploadedFileStatus
  progress?: number
  parseStatus?: ParseStatus
  parseError?: string
  sourceType?: SourceType
  relatedResourceId?: string
  relatedLearningPathId?: string
  relatedKnowledgePointIds?: string[]
  tags?: string[]
}

export interface ClientUploadTask {
  clientId: string
  fileName: string
  fileSizeBytes: number
  mimeType: string
  status: ClientUploadStatus
  progress: number
  errorMessage?: string
  uploadedFileId?: string
  file?: File
}

export interface FileParseResult {
  fileId: string
  title?: string
  summary?: string
  extractedText?: string
  chapters?: Array<{
    title: string
    summary?: string
    order: number
  }>
  knowledgePoints?: Array<{
    id?: string
    name: string
    confidence?: number
  }>
  suggestedTags?: string[]
  suggestedResourceType?: SuggestedResourceType
  suggestedLearningPathNodeIds?: string[]
  status: ParseStatus
  errorMessage?: string
}

export interface LearningPathOption {
  id: string
  title: string
  currentNodeId?: string
  progressRate?: number
}

export interface KnowledgePointOption {
  id: string
  name: string
  parentId?: string
  masteryLevel?: number
}

export interface UploadStats {
  totalFiles: number
  parsedFiles: number
  processingFiles: number
  failedFiles: number
  totalSizeBytes: number
}

export type UploadedFileSort = 'uploadedAt' | 'name' | 'size' | 'parseStatus'

export interface UploadedFilesFilters {
  keyword?: string
  sourceType?: SourceType | 'all'
  parseStatus?: ParseStatus | 'all'
  learningPathId?: string
  knowledgePointId?: string
  sort?: UploadedFileSort
  page?: number
  pageSize?: number
}

export interface UploadedFilesPageResult {
  list: UploadedFile[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface UploadConfig {
  learningPathId?: string
  knowledgePointIds?: string[]
  autoParse: boolean
  autoGenerateResource: boolean
}

export interface UploadFilePayload {
  file: File
  learningPathId?: string
  knowledgePointIds?: string[]
  autoParse?: boolean
  autoGenerateResource?: boolean
  onProgress?: (progress: number) => void
}

export interface GenerateResourcePayload {
  fileId: string
  resourceType?: string
  learningPathId?: string
  knowledgePointIds?: string[]
}

export interface UpdateFileRelationsPayload {
  fileId: string
  learningPathId?: string
  knowledgePointIds?: string[]
  tags?: string[]
}
