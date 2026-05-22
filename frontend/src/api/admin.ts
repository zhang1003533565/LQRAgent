import http from './http'
import type { UploadTask } from './upload'

export interface SysConfigItem {
  id: number
  configKey: string
  configValue: string
  remark?: string
  updatedAt: string
}

export interface AdminStatus {
  serverPort: string
  aiServerBaseUrl: string
  aiServerWsUrl: string
  aiServerAutoStart: boolean
  aiServerReachable: boolean
  userCount: number
  uploadTaskCount: number
}

export interface AdminUser {
  id: number
  username: string
  displayName: string
  role: string
  enabled: boolean
}

/** 预置可配置项（便于在界面中快速添加） */
export interface ModelConfig {
  llmBinding: string
  llmModel: string
  llmApiKeyMasked: string
  llmApiKeySet: boolean
  llmHost: string
  llmApiVersion?: string
  embeddingBinding: string
  embeddingModel: string
  embeddingApiKeyMasked: string
  embeddingApiKeySet: boolean
  embeddingHost: string
}

export interface ModelConfigSaveRequest {
  llmBinding: string
  llmModel: string
  llmApiKey?: string
  llmHost: string
  llmApiVersion?: string
  embeddingBinding: string
  embeddingModel: string
  embeddingApiKey?: string
  embeddingHost: string
  syncToAiServer?: boolean
}

export async function getModelConfig(): Promise<ModelConfig> {
  const res = await http.get<{ data: ModelConfig }>('/admin/model-config')
  return res.data.data
}

export async function saveModelConfig(data: ModelConfigSaveRequest): Promise<ModelConfig> {
  const res = await http.put<{ data: ModelConfig }>('/admin/model-config', data)
  return res.data.data
}

export async function testLlmConfig(): Promise<{
  success: boolean
  message: string
  endpoint?: string
  model?: string
}> {
  const res = await http.post<{ data: { success: boolean; message: string; endpoint?: string; model?: string } }>(
    '/admin/model-config/test-llm',
  )
  return res.data.data
}

export const PRESET_CONFIG_KEYS = [
  { key: 'ai-server.base-url', label: 'AI 服务 HTTP 地址', placeholder: 'http://localhost:8001' },
  { key: 'ai-server.ws-url', label: 'AI WebSocket 地址', placeholder: 'ws://localhost:8001/api/v1/ws' },
  { key: 'ai-server.auto-start', label: '启动时自动拉起 AI', placeholder: 'true / false' },
  { key: 'upload.queue.worker-interval-ms', label: '上传队列轮询间隔(ms)', placeholder: '5000' },
  { key: 'upload.queue.max-concurrent', label: '上传最大并发', placeholder: '2' },
] as const

export async function getAdminStatus(): Promise<AdminStatus> {
  const res = await http.get<{ data: AdminStatus }>('/admin/status')
  return res.data.data
}

export async function listSysConfig(): Promise<SysConfigItem[]> {
  const res = await http.get<{ data: SysConfigItem[] }>('/admin/config')
  return res.data.data
}

export async function saveSysConfig(
  key: string,
  configValue: string,
  remark?: string,
): Promise<SysConfigItem> {
  const res = await http.put<{ data: SysConfigItem }>(`/admin/config/${encodeURIComponent(key)}`, {
    configValue,
    remark,
  })
  return res.data.data
}

export async function deleteSysConfig(key: string): Promise<void> {
  await http.delete(`/admin/config/${encodeURIComponent(key)}`)
}

export async function pingAiServer(): Promise<{ reachable: boolean; baseUrl: string }> {
  const res = await http.post<{ data: { reachable: boolean; baseUrl: string } }>('/admin/ai/ping')
  return res.data.data
}

export async function listAdminUsers(): Promise<AdminUser[]> {
  const res = await http.get<{ data: AdminUser[] }>('/admin/users')
  return res.data.data
}

export async function listAdminUploadTasks(limit = 50): Promise<UploadTask[]> {
  const res = await http.get<{ data: UploadTask[] }>('/admin/upload/tasks', { params: { limit } })
  return res.data.data
}

export async function processOneUpload(): Promise<boolean> {
  const res = await http.post<{ data: { processed: boolean } }>('/admin/upload/process')
  return res.data.data.processed
}
