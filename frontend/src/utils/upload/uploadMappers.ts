import type { UploadTask, TaskStatus } from '@/api/student/upload'
import type {
  FileParseResult,
  ParseStatus,
  SourceType,
  UploadedFile,
  UploadedFileStatus,
  UploadStats,
} from '@/utils/types/upload'

export type ParsedAnalysis = {
  summary: string
  mappedKpIds: string[]
  matchedKnowledgePoints: Array<{ kpId: string; score: number | null }>
  extractedText?: string
  chapters?: FileParseResult['chapters']
  suggestedTags?: string[]
}

function extractExt(fileName: string): string {
  const parts = (fileName || '').split('.')
  return parts.length > 1 ? parts.pop()!.toLowerCase() : ''
}

function inferSourceType(ext: string): SourceType {
  if (['pdf', 'doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx', 'md', 'txt', 'rst'].includes(ext))
    return 'document'
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg'].includes(ext)) return 'image'
  if (['mp3', 'wav', 'ogg'].includes(ext)) return 'audio'
  if (['mp4', 'avi', 'mov'].includes(ext)) return 'video'
  if (['py', 'java', 'kt', 'js', 'ts', 'go', 'rs', 'c', 'cpp', 'h'].includes(ext)) return 'code'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'archive'
  return 'other'
}

function inferMimeType(ext: string): string {
  const map: Record<string, string> = {
    pdf: 'application/pdf',
    doc: 'application/msword',
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    ppt: 'application/vnd.ms-powerpoint',
    pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    txt: 'text/plain',
    md: 'text/markdown',
    png: 'image/png',
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    gif: 'image/gif',
    mp4: 'video/mp4',
    mp3: 'audio/mpeg',
  }
  return map[ext] || 'application/octet-stream'
}

function mapTaskStatus(status: TaskStatus): UploadedFileStatus {
  switch (status) {
    case 'PENDING':
      return 'processing'
    case 'PROCESSING':
      return 'processing'
    case 'COMPLETED':
      return 'parsed'
    case 'FAILED':
      return 'failed'
    default:
      return 'uploaded'
  }
}

function mapParseStatus(status: TaskStatus): ParseStatus {
  switch (status) {
    case 'PENDING':
      return 'pending'
    case 'PROCESSING':
      return 'processing'
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'failed'
    default:
      return 'pending'
  }
}

function normalizeScore(raw: unknown): number | null {
  if (typeof raw === 'number') {
    return Math.max(0, Math.min(100, Math.round(raw <= 1 ? raw * 100 : raw)))
  }
  if (typeof raw === 'string' && raw.trim() !== '' && !Number.isNaN(Number(raw))) {
    const n = Number(raw)
    return Math.max(0, Math.min(100, Math.round(n <= 1 ? n * 100 : n)))
  }
  return null
}

export function parseAnalysis(task: UploadTask): ParsedAnalysis {
  let summary = ''
  let mappedFromJson: string[] = []
  let matchedKnowledgePoints: ParsedAnalysis['matchedKnowledgePoints'] = []
  let extractedText: string | undefined
  let chapters: FileParseResult['chapters'] | undefined
  let suggestedTags: string[] | undefined

  if (task.analysisResult) {
    try {
      const result = JSON.parse(task.analysisResult)
      if (typeof result?.summary === 'string') summary = result.summary
      if (typeof result?.extractedText === 'string') extractedText = result.extractedText
      if (Array.isArray(result?.chapters)) {
        chapters = result.chapters
          .map((ch: { title?: string; summary?: string; order?: number }, i: number) => ({
            title: ch.title || `章节 ${i + 1}`,
            summary: ch.summary,
            order: typeof ch.order === 'number' ? ch.order : i,
          }))
          .sort((a, b) => a.order - b.order)
      }
      if (Array.isArray(result?.suggestedTags)) {
        suggestedTags = result.suggestedTags.map((t: unknown) => String(t)).filter(Boolean)
      }
      if (Array.isArray(result?.mappedKpIds)) {
        mappedFromJson = result.mappedKpIds
          .map((item: unknown) => String(item || '').trim())
          .filter(Boolean)
      }
      if (Array.isArray(result?.matchedKnowledgePoints)) {
        matchedKnowledgePoints = result.matchedKnowledgePoints
          .map((item: unknown) => {
            if (!item || typeof item !== 'object') return null
            const point = item as Record<string, unknown>
            const kpId = String(
              point.kpId || point.id || point.knowledgePointId || '',
            ).trim()
            if (!kpId) return null
            const rawScore =
              point.score ??
              point.matchScore ??
              point.matchingScore ??
              point.similarity ??
              point.relevance ??
              point.confidence
            return { kpId, score: normalizeScore(rawScore) }
          })
          .filter((item): item is { kpId: string; score: number | null } => Boolean(item))
      } else if (result?.matchedKnowledgePoints && typeof result.matchedKnowledgePoints === 'object') {
        matchedKnowledgePoints = Object.entries(
          result.matchedKnowledgePoints as Record<string, unknown>,
        )
          .map(([kpId, rawScore]) => {
            const normalizedId = String(kpId || '').trim()
            if (!normalizedId) return null
            return { kpId: normalizedId, score: normalizeScore(rawScore) }
          })
          .filter((item): item is { kpId: string; score: number | null } => Boolean(item))
      }
    } catch {
      // malformed payload
    }
  }

  const mappedFromField = (task.mappedKpIds || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)

  return {
    summary,
    mappedKpIds: Array.from(new Set([...mappedFromJson, ...mappedFromField])),
    matchedKnowledgePoints,
    extractedText,
    chapters,
    suggestedTags,
  }
}

export function taskToUploadedFile(
  task: UploadTask,
  sizeBytes = 0,
): UploadedFile {
  const ext = extractExt(task.fileName)
  const analysis = parseAnalysis(task)
  return {
    id: String(task.id),
    name: task.fileName,
    sizeBytes,
    mimeType: inferMimeType(ext),
    extension: ext || undefined,
    uploadedAt: task.createdAt,
    updatedAt: task.finishedAt || task.startedAt || task.createdAt,
    uploaderId: String(task.userId),
    status: mapTaskStatus(task.status),
    progress: task.progressPercent,
    parseStatus: mapParseStatus(task.status),
    parseError: task.errorMessage,
    sourceType: inferSourceType(ext),
    relatedKnowledgePointIds: analysis.mappedKpIds,
    tags: analysis.suggestedTags,
  }
}

export function taskToParseResult(
  task: UploadTask,
  kpNames: Record<string, string> = {},
): FileParseResult {
  const analysis = parseAnalysis(task)
  return {
    fileId: String(task.id),
    title: task.fileName,
    summary: analysis.summary,
    extractedText: analysis.extractedText,
    chapters: analysis.chapters,
    knowledgePoints: analysis.mappedKpIds.map((kpId) => ({
      id: kpId,
      name: kpNames[kpId] || kpId,
      confidence: analysis.matchedKnowledgePoints.find((p) => p.kpId === kpId)?.score,
    })),
    suggestedTags: analysis.suggestedTags,
    suggestedLearningPathNodeIds: analysis.mappedKpIds,
    status: mapParseStatus(task.status),
    errorMessage: task.errorMessage,
  }
}

export function computeUploadStats(files: UploadedFile[]): UploadStats {
  return {
    totalFiles: files.length,
    parsedFiles: files.filter((f) => f.parseStatus === 'success').length,
    processingFiles: files.filter(
      (f) => f.parseStatus === 'processing' || f.parseStatus === 'pending',
    ).length,
    failedFiles: files.filter((f) => f.parseStatus === 'failed' || f.status === 'failed').length,
    totalSizeBytes: files.reduce((sum, f) => sum + (f.sizeBytes || 0), 0),
  }
}

export function isExtensionSupported(fileName: string): boolean {
  const ext = extractExt(fileName)
  if (!ext) return false
  const supported = new Set([
    'pdf', 'doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx', 'md', 'txt', 'rst',
    'py', 'java', 'kt', 'js', 'ts', 'go', 'rs', 'c', 'cpp', 'h',
    'png', 'jpg', 'jpeg', 'gif', 'webp', 'svg',
    'mp4', 'avi', 'mov', 'mp3', 'wav', 'ogg',
    'json', 'yaml', 'yml', 'toml', 'xml', 'csv',
  ])
  return supported.has(ext)
}
