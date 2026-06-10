import http from '@/api/http'

export interface MediaResult {
  success: boolean
  imageUrl?: string
  videoUrl?: string
  taskId?: string
  status?: string
  prompt?: string
  duration?: number
  error?: string
}

export interface PromptResult {
  prompt: string
  mediaType: string
  reason: string
  success: boolean
}

/**
 * 为知识点生成示意图
 */
export async function generateImage(kpId: string, prompt?: string): Promise<MediaResult> {
  const params: Record<string, string> = { kpId }
  if (prompt) params.prompt = prompt
  const res = await http.post<{ data: MediaResult }>('/media/generate', null, { params })
  return res.data.data
}

/**
 * 为知识点生成视频（同步返回 taskId）
 */
export async function generateVideo(kpId: string, prompt?: string): Promise<MediaResult> {
  const params: Record<string, string> = { kpId }
  if (prompt) params.prompt = prompt
  const res = await http.post<{ data: MediaResult }>('/media/generate-video', null, { params })
  return res.data.data
}

/**
 * 提交异步视频生成任务
 */
export async function submitVideoTask(prompt: string, duration = 5): Promise<{ taskId: string; status: string }> {
  const res = await http.post<{ data: { taskId: string; status: string } }>('/media/test-video/submit', {
    prompt,
    duration,
  })
  return res.data.data
}

/**
 * 查询异步视频任务状态
 */
export async function getVideoTaskStatus(taskId: string): Promise<MediaResult> {
  const res = await http.get<{ data: MediaResult }>(`/media/test-video/${taskId}/status`)
  return res.data.data
}

/**
 * 按ID获取已生成的Media资源
 */
export async function getMedia(id: number): Promise<MediaResult> {
  const res = await http.get<{ data: MediaResult }>(`/media/${id}`)
  return res.data.data
}

/**
 * 同步测试图片生成
 */
export async function testImage(prompt: string): Promise<MediaResult> {
  const res = await http.post<{ data: MediaResult }>('/media/test-image', { prompt })
  return res.data.data
}

/**
 * 同步测试视频生成
 */
export async function testVideo(prompt: string, duration?: number): Promise<MediaResult> {
  const res = await http.post<{ data: MediaResult }>('/media/test-video', { prompt, duration: duration ?? 5 })
  return res.data.data
}

/**
 * 生成提示词
 */
export async function generatePrompt(intent: string, mediaType = 'image'): Promise<PromptResult> {
  const res = await http.post<{ data: PromptResult }>('/media/generate-prompt', { intent, mediaType })
  return res.data.data
}
