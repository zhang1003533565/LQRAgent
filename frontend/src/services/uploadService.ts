import {
  deleteUploadTask,
  getFileUrl,
  getUploadConfig,
  getUploadStorage,
  listUploadTasks,
  retryParseUploadTask,
  updateUploadFileRelations,
  uploadFile as apiUploadFile,
  type UploadTask,
} from '@/api/student/upload'
import { generateResource } from '@/api/student/resources'
import { getCurrentPath } from '@/api/student/learningPath'
import { listKnowledgePointsByIds, searchKnowledgePoints } from '@/api/student/knowledge'
import { trackBehavior } from '@/utils/tracker'
import { usePathStore } from '@/utils/store/pathStore'
import {
  getUploadMetadata,
  persistUploadMetadata,
  removeUploadMetadata,
} from '@/utils/upload/uploadMetadataStorage'
import {
  UPLOAD_CONFIG,
  SUPPORTED_EXTENSIONS,
  SUPPORTED_MIME_HINTS,
  applyServerUploadConfig,
} from '@/utils/upload/uploadConstants'
import {
  computeUploadStats,
  isExtensionSupported,
  parseAnalysis,
  taskToParseResult,
  taskToUploadedFile,
} from '@/utils/upload/uploadMappers'
import type {
  FileParseResult,
  GenerateResourcePayload,
  KnowledgePointOption,
  LearningPathOption,
  StorageUsage,
  UpdateFileRelationsPayload,
  UploadedFile,
  UploadedFilesFilters,
  UploadedFilesPageResult,
  UploadFilePayload,
  UploadStats,
} from '@/utils/types/upload'
import type { ResourceType } from '@/utils/types/media-resource'

const FILE_SIZE_STORAGE_KEY = 'lqragent.upload.fileSizes'

function readSizeMap(): Record<string, number> {
  try {
    const raw = localStorage.getItem(FILE_SIZE_STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Record<string, number>) : {}
  } catch {
    return {}
  }
}

function persistFileSize(fileId: string, sizeBytes: number) {
  const map = readSizeMap()
  map[fileId] = sizeBytes
  localStorage.setItem(FILE_SIZE_STORAGE_KEY, JSON.stringify(map))
}

function getPersistedSize(fileId: string): number {
  return readSizeMap()[fileId] ?? 0
}

function clearPersistedSize(fileId: string) {
  const map = readSizeMap()
  if (!(fileId in map)) return
  delete map[fileId]
  localStorage.setItem(FILE_SIZE_STORAGE_KEY, JSON.stringify(map))
}

async function loadAllTasks(): Promise<UploadTask[]> {
  return listUploadTasks()
}

function tasksToFiles(tasks: UploadTask[]): UploadedFile[] {
  return tasks.map((t) => {
    const fileId = String(t.id)
    const persistedSize = t.fileSizeBytes ?? getPersistedSize(fileId)
    const file = taskToUploadedFile(t, persistedSize)
    const meta = getUploadMetadata(fileId)
    if (meta?.knowledgePointIds?.length && !t.manualKpIds) {
      file.relatedKnowledgePointIds = Array.from(
        new Set([...(file.relatedKnowledgePointIds || []), ...meta.knowledgePointIds]),
      )
    }
    if (!file.sizeBytes && getPersistedSize(fileId) > 0) {
      file.sizeBytes = getPersistedSize(fileId)
    }
    if (t.fileSizeBytes != null && getPersistedSize(fileId) > 0) {
      clearPersistedSize(fileId)
    }
    return file
  })
}

function sortFiles(list: UploadedFile[], sort?: UploadedFilesFilters['sort']): UploadedFile[] {
  const key = sort || 'uploadedAt'
  return [...list].sort((a, b) => {
    switch (key) {
      case 'name':
        return a.name.localeCompare(b.name, 'zh-CN')
      case 'size':
        return b.sizeBytes - a.sizeBytes
      case 'parseStatus':
        return (a.parseStatus || '').localeCompare(b.parseStatus || '')
      default:
        return new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime()
    }
  })
}

function filterFiles(list: UploadedFile[], params?: UploadedFilesFilters): UploadedFile[] {
  if (!params) return list
  let result = list

  if (params.keyword?.trim()) {
    const kw = params.keyword.trim().toLowerCase()
    result = result.filter(
      (f) =>
        f.name.toLowerCase().includes(kw) ||
        (f.tags || []).some((t) => t.toLowerCase().includes(kw)) ||
        (f.relatedKnowledgePointIds || []).some((id) => id.toLowerCase().includes(kw)),
    )
  }

  if (params.sourceType && params.sourceType !== 'all') {
    result = result.filter((f) => f.sourceType === params.sourceType)
  }

  if (params.parseStatus && params.parseStatus !== 'all') {
    result = result.filter((f) => f.parseStatus === params.parseStatus)
  }

  if (params.learningPathId && params.learningPathId !== 'all') {
    const currentKp = usePathStore.getState().selectedKpId
    if (params.learningPathId === 'current' && currentKp) {
      result = result.filter((f) => (f.relatedKnowledgePointIds || []).includes(currentKp))
    }
  }

  if (params.knowledgePointId) {
    result = result.filter((f) =>
      (f.relatedKnowledgePointIds || []).includes(params.knowledgePointId!),
    )
  }

  return result
}

/** GET /api/upload/storage */
export async function getStorageUsage(): Promise<StorageUsage> {
  try {
    const storage = await getUploadStorage()
    return {
      usedBytes: storage.usedBytes,
      totalBytes: storage.totalBytes,
      fileCount: storage.fileCount,
      maxFileSizeBytes: storage.maxFileSizeBytes,
      supportedMimeTypes: storage.supportedMimeTypes,
    }
  } catch {
    const tasks = await loadAllTasks()
    const files = tasksToFiles(tasks)
    const usedBytes = files.reduce((sum, f) => sum + f.sizeBytes, 0)
    return {
      usedBytes,
      totalBytes: UPLOAD_CONFIG.defaultTotalBytes,
      fileCount: files.length,
      maxFileSizeBytes: UPLOAD_CONFIG.defaultMaxFileSizeBytes,
      supportedMimeTypes: [...SUPPORTED_MIME_HINTS],
    }
  }
}

export async function getUploadStats(): Promise<UploadStats> {
  const tasks = await loadAllTasks()
  return computeUploadStats(tasksToFiles(tasks))
}

export async function getUploadedFiles(
  params?: UploadedFilesFilters,
): Promise<UploadedFilesPageResult> {
  const tasks = await loadAllTasks()
  const all = sortFiles(tasksToFiles(tasks), params?.sort)
  const filtered = filterFiles(all, params)
  const pageSize = params?.pageSize ?? UPLOAD_CONFIG.defaultPageSize
  const total = filtered.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const page = Math.min(Math.max(1, params?.page ?? 1), totalPages)
  const start = (page - 1) * pageSize

  return {
    list: filtered.slice(start, start + pageSize),
    total,
    page,
    pageSize,
    totalPages,
  }
}

export async function uploadFile(payload: UploadFilePayload): Promise<UploadedFile> {
  const { file, onProgress } = payload

  if (!isExtensionSupported(file.name)) {
    throw new Error('暂不支持该文件类型')
  }

  const storage = await getStorageUsage()
  if (file.size > (storage.maxFileSizeBytes ?? UPLOAD_CONFIG.defaultMaxFileSizeBytes)) {
    throw new Error('文件超过单文件大小限制')
  }
  const remaining = storage.totalBytes - storage.usedBytes
  if (remaining > 0 && file.size > remaining) {
    throw new Error('存储空间不足，请删除部分资料后再上传')
  }

  const task = await apiUploadFile(
    file,
    'PERSONAL',
    onProgress,
    {
      learningPathId: payload.learningPathId,
      knowledgePointIds: payload.knowledgePointIds,
    },
  )
  persistFileSize(String(task.id), file.size)
  if (payload.knowledgePointIds?.length || payload.learningPathId) {
    persistUploadMetadata(String(task.id), {
      knowledgePointIds: payload.knowledgePointIds,
      learningPathId: payload.learningPathId,
      autoParse: payload.autoParse,
      autoGenerateResource: payload.autoGenerateResource,
    })
  }
  trackBehavior({ kpId: payload.knowledgePointIds?.[0] || '', action: 'upload_file', extra: file.name })

  // 后端尚未接收 metadata 字段；本地持久化供筛选与关联展示
  if (payload.autoGenerateResource && payload.knowledgePointIds?.[0]) {
    try {
      await generateResource({
        kpId: payload.knowledgePointIds[0],
        resourceType: 'LESSON',
        prompt: `基于上传文件 ${file.name} 生成学习资源`,
      })
    } catch {
      // optional follow-up
    }
  }

  return taskToUploadedFile(task, file.size)
}

export async function uploadFiles(payload: {
  files: File[]
  learningPathId?: string
  knowledgePointIds?: string[]
  autoParse?: boolean
  autoGenerateResource?: boolean
  onFileProgress?: (clientId: string, progress: number) => void
  clientIds?: string[]
}): Promise<UploadedFile[]> {
  const results: UploadedFile[] = []
  for (let i = 0; i < payload.files.length; i += 1) {
    const file = payload.files[i]
    const clientId = payload.clientIds?.[i]
    try {
      const uploaded = await uploadFile({
        file,
        learningPathId: payload.learningPathId,
        knowledgePointIds: payload.knowledgePointIds,
        autoParse: payload.autoParse,
        autoGenerateResource: payload.autoGenerateResource,
        onProgress: clientId
          ? (p) => payload.onFileProgress?.(clientId, p)
          : undefined,
      })
      results.push(uploaded)
    } catch {
      // caller handles per-file errors
    }
  }
  return results
}

export async function deleteUploadedFile(fileId: string): Promise<void> {
  await deleteUploadTask(Number(fileId))
  removeUploadMetadata(fileId)
}

/** POST /api/upload/tasks/{id}/retry-parse */
export async function retryParseFile(fileId: string): Promise<FileParseResult> {
  const task = await retryParseUploadTask(Number(fileId))
  return taskToParseResult(task)
}

export async function getFileParseResult(fileId: string): Promise<FileParseResult> {
  const tasks = await loadAllTasks()
  const task = tasks.find((t) => String(t.id) === fileId)
  if (!task) throw new Error('文件不存在')

  const analysis = parseAnalysis(task)
  let kpNames: Record<string, string> = {}
  if (analysis.mappedKpIds.length > 0) {
    try {
      const items = await listKnowledgePointsByIds(analysis.mappedKpIds)
      kpNames = items.reduce<Record<string, string>>((acc, item) => {
        acc[item.kpId] = item.title
        return acc
      }, {})
    } catch {
      // keep ids as names
    }
  }
  return taskToParseResult(task, kpNames)
}

const RESOURCE_TYPE_MAP: Record<string, ResourceType> = {
  lecture_note: 'LESSON',
  exercise: 'QUIZ',
  mind_map: 'ILLUSTRATION',
  case: 'CODE_CASE',
  reference: 'LESSON',
  other: 'LESSON',
}

export async function generateResourceFromFile(
  payload: GenerateResourcePayload,
): Promise<{ resourceId: string }> {
  const parseResult = await getFileParseResult(payload.fileId)
  const kpId =
    payload.knowledgePointIds?.[0] ||
    parseResult.knowledgePoints?.[0]?.id ||
  parseResult.suggestedLearningPathNodeIds?.[0] ||
    usePathStore.getState().selectedKpId ||
    ''

  if (!kpId) throw new Error('请先关联知识点后再生成资源')

  const resourceType =
    RESOURCE_TYPE_MAP[payload.resourceType || parseResult.suggestedResourceType || 'lecture_note'] ||
    'LESSON'

  const resource = await generateResource({
    kpId,
    resourceType,
    prompt: parseResult.summary || undefined,
  })

  return { resourceId: String(resource.id ?? payload.fileId) }
}

export async function getLearningPathOptions(): Promise<LearningPathOption[]> {
  const path = await getCurrentPath()
  if (!path) return []

  const completed = path.nodes.filter((n) => n.completed).length
  const progressRate =
    path.nodes.length > 0 ? Math.round((completed / path.nodes.length) * 100) : 0

  return [
    {
      id: path.goal,
      title: path.goal,
      currentNodeId: usePathStore.getState().selectedKpId || path.nodes.find((n) => n.status === 'ACTIVE')?.kpId,
      progressRate,
    },
  ]
}

export async function getKnowledgePointOptions(params?: {
  keyword?: string
  learningPathId?: string
}): Promise<KnowledgePointOption[]> {
  if (params?.keyword?.trim()) {
    const items = await searchKnowledgePoints(params.keyword.trim())
    return items.map((item) => ({
      id: item.kpId,
      name: item.title,
    }))
  }

  const path = await getCurrentPath()
  if (!path) return []

  return path.nodes.map((node) => ({
    id: node.kpId,
    name: node.title,
    masteryLevel: node.completed ? 100 : node.status === 'ACTIVE' ? 50 : 0,
  }))
}

/** PATCH /api/upload/files/{id}/relations */
export async function updateFileRelations(
  payload: UpdateFileRelationsPayload,
): Promise<UploadedFile> {
  const task = await updateUploadFileRelations(Number(payload.fileId), {
    learningPathId: payload.learningPathId,
    knowledgePointIds: payload.knowledgePointIds,
    tags: payload.tags,
  })
  persistUploadMetadata(payload.fileId, {
    knowledgePointIds: payload.knowledgePointIds,
    learningPathId: payload.learningPathId,
  })
  return taskToUploadedFile(task, getPersistedSize(payload.fileId))
}

export async function loadUploadConfigFromServer(): Promise<void> {
  try {
    const config = await getUploadConfig()
    applyServerUploadConfig(config)
  } catch {
    // keep local defaults
  }
}

export async function pruneLegacyFileSizes(): Promise<void> {
  try {
    const tasks = await loadAllTasks()
    for (const task of tasks) {
      if (task.fileSizeBytes != null) {
        clearPersistedSize(String(task.id))
      }
    }
  } catch {
    // optional cleanup
  }
}

export async function getFileDownloadUrl(fileId: string): Promise<string> {
  const res = await getFileUrl(Number(fileId))
  return res.url
}

export function getSupportedExtensionList(): readonly string[] {
  return SUPPORTED_EXTENSIONS
}
