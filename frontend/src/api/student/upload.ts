import http from '@/api/http'

export type KbScope = 'PERSONAL' | 'PUBLIC'
export type TaskStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface UploadTask {
  id: number
  userId: number
  fileName: string
  kbScope: KbScope
  status: TaskStatus
  errorMessage?: string
  statusMessage?: string
  progressPercent?: number
  analysisResult?: string
  mappedKpIds?: string
  vectorChunkCount?: number
  vectorTotalTokens?: number
  vectorIndexName?: string
  createdAt: string
  startedAt?: string
  finishedAt?: string
}

/** 上传文件，立即返回排队状态 */
export async function uploadFile(
  file: File,
  scope: KbScope = 'PERSONAL',
  onProgress?: (progress: number) => void,
): Promise<UploadTask> {
  const form = new FormData()
  form.append('file', file)
  form.append('scope', scope)
  const res = await http.post<{ data: UploadTask }>('/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    },
  })
  return res.data.data
}

/** 查询当前用户的所有上传任务 */
export async function listUploadTasks(): Promise<UploadTask[]> {
  const res = await http.get<{ data: UploadTask[] }>('/upload/tasks')
  return res.data.data
}

export async function deleteUploadTask(taskId: number): Promise<void> {
  await http.delete(`/upload/tasks/${taskId}`)
}

/** 获取文件下载链接 */
export async function getFileUrl(fileId: number): Promise<{ url: string; fileName: string }> {
  const res = await http.get<{ data: { url: string; fileName: string } }>(`/upload/file/${fileId}`)
  return res.data.data
}
