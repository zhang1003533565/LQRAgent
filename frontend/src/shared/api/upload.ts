import http from './http'

export type KbScope = 'PERSONAL' | 'PUBLIC'
export type TaskStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface UploadTask {
  id: number
  userId: number
  fileName: string
  kbScope: KbScope
  status: TaskStatus
  errorMessage?: string
  analysisResult?: string
  mappedKpIds?: string
  createdAt: string
  startedAt?: string
  finishedAt?: string
}

/** 上传文件，立即返回排队状态 */
export async function uploadFile(
  file: File,
  scope: KbScope = 'PERSONAL',
): Promise<UploadTask> {
  const form = new FormData()
  form.append('file', file)
  form.append('scope', scope)
  const res = await http.post<{ data: UploadTask }>('/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data.data
}

/** 查询当前用户的所有上传任务 */
export async function listUploadTasks(): Promise<UploadTask[]> {
  const res = await http.get<{ data: UploadTask[] }>('/upload/tasks')
  return res.data.data
}
