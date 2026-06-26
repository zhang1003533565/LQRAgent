const METADATA_KEY = 'lqragent.upload.metadata'

export type UploadTaskMetadata = {
  learningPathId?: string
  knowledgePointIds?: string[]
  autoParse?: boolean
  autoGenerateResource?: boolean
}

type MetadataMap = Record<string, UploadTaskMetadata>

function readMap(): MetadataMap {
  try {
    const raw = localStorage.getItem(METADATA_KEY)
    return raw ? (JSON.parse(raw) as MetadataMap) : {}
  } catch {
    return {}
  }
}

function writeMap(map: MetadataMap) {
  localStorage.setItem(METADATA_KEY, JSON.stringify(map))
}

export function getUploadMetadata(taskId: string): UploadTaskMetadata | undefined {
  return readMap()[taskId]
}

export function persistUploadMetadata(taskId: string, metadata: UploadTaskMetadata) {
  const map = readMap()
  map[taskId] = {
    ...map[taskId],
    ...metadata,
    knowledgePointIds: metadata.knowledgePointIds?.filter(Boolean),
  }
  writeMap(map)
}

export function removeUploadMetadata(taskId: string) {
  const map = readMap()
  delete map[taskId]
  writeMap(map)
}
