import http from '../http'
import type { UploadTask } from '@/api/student/upload'

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
  videoBinding: string
  videoModel: string
  videoApiKeyMasked: string
  videoApiKeySet: boolean
  videoHost: string
  imageBinding: string
  imageModel: string
  imageApiKeyMasked: string
  imageApiKeySet: boolean
  imageHost: string
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
  videoBinding: string
  videoModel: string
  videoApiKey?: string
  videoHost: string
  imageBinding: string
  imageModel: string
  imageApiKey?: string
  imageHost: string
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

// ===== 画像 / 图谱 / 路径 / 资源 =====

export interface AdminProfile {
  id: number
  userId: number
  knowledgeLevel: string
  learningGoal: string | null
  cognitiveStyle: string | null
  commonErrors: string | null
  learningPace: string
  interestDirection: string | null
  preferredResourceType: string | null
}

export interface KnowledgeNode {
  id: number
  kpId: string
  title: string
  description: string
  chapter: string
}

export interface KnowledgeEdge {
  id: number
  fromKpId: string
  toKpId: string
  relationType: string
}

export interface KnowledgeGraphData {
  nodes: KnowledgeNode[]
  edges: KnowledgeEdge[]
  nodeCount: number
  edgeCount: number
}

export interface LearningPathItem {
  id: number
  userId: number
  goal: string
  createdAt: string
  stepCount: number
  completedCount: number
}

export interface AdminResourceItem {
  id: number
  kpId: string
  resourceType: string
  title: string
  content: string
}

export async function listAdminProfiles(): Promise<AdminProfile[]> {
  const res = await http.get<{ data: AdminProfile[] }>('/admin/profiles')
  return res.data.data
}

export async function getKnowledgeGraph(subject?: string): Promise<KnowledgeGraphData & { subjects?: string[] }> {
  const params = subject ? { subject } : {}
  const res = await http.get<{ data: KnowledgeGraphData & { subjects?: string[] } }>('/admin/knowledge-graph', { params })
  return res.data.data
}

export async function listAdminLearningPaths(): Promise<LearningPathItem[]> {
  const res = await http.get<{ data: LearningPathItem[] }>('/admin/learning-paths')
  return res.data.data
}

export interface ResourceListResponse {
  items: AdminResourceItem[]
  subjects: string[]
  total: number
}

export async function listAdminResources(type?: string, kpId?: string, subject?: string): Promise<ResourceListResponse> {
  const params: Record<string, string> = {}
  if (type) params.type = type
  if (kpId) params.kpId = kpId
  if (subject) params.subject = subject
  const res = await http.get<{ data: ResourceListResponse }>('/admin/resources', { params })
  return res.data.data
}

// ===== 智能体监控 =====

export interface AgentStatItem {
  agent: string
  total: number
  success: number
  failed: number
  successRate: string
  avgDurationMs: number
}

export interface AgentRunItem {
  id: number
  sessionId: string
  userId: number
  agent: string
  intent: string
  status: string
  durationMs: number | null
  errorMessage: string
  createdAt: string
}

export interface AgentRunsData {
  items: AgentRunItem[]
  total: number
  page: number
  size: number
}

export interface AgentStatsResponse {
  stats: AgentStatItem[]
  registeredAgents: string[]
  agentCount: number
}

export async function getAgentStats(): Promise<AgentStatsResponse> {
  const res = await http.get<{ data: AgentStatsResponse }>('/admin/agent-stats')
  return res.data.data
}

export async function getAgentRuns(page = 1, size = 20): Promise<AgentRunsData> {
  const res = await http.get<{ data: AgentRunsData }>('/admin/agent-runs', { params: { page, size } })
  return res.data.data
}

// ===== Agent 测试 =====

export interface QuizRecordItem {
  id: number
  userId: number
  kpId: string
  resourceId: number | null
  score: number
  isCorrect: boolean
  answer: string
  createdAt: string
}

export interface QuizRecordsData {
  items: QuizRecordItem[]
  total: number
  page: number
  size: number
}

export interface StudyBehaviorItem {
  id: number
  userId: number
  kpId: string | null
  action: string
  durationSec: number | null
  extra: string | null
  createdAt: string
}

export interface StudyBehaviorsData {
  items: StudyBehaviorItem[]
  total: number
  page: number
  size: number
}

export async function getQuizRecords(page = 1, size = 20): Promise<QuizRecordsData> {
  const res = await http.get<{ data: QuizRecordsData }>('/admin/quiz-records', { params: { page, size } })
  return res.data.data
}

export async function getStudyBehaviors(page = 1, size = 20): Promise<StudyBehaviorsData> {
  const res = await http.get<{ data: StudyBehaviorsData }>('/admin/study-behaviors', { params: { page, size } })
  return res.data.data
}

export interface AgentTestResult {
  success: boolean
  agentType: string
  data: Record<string, unknown>
  errorMessage: string
  durationMs: number
}

export async function testAgent(message: string): Promise<AgentTestResult> {
  const res = await http.post<{ data: AgentTestResult }>('/admin/agent-test', { message })
  return res.data.data
}

// ===== 上传管理（管理员） =====

/** 重试一条失败的上传任务 */
export async function retryUploadTask(taskId: number): Promise<{ message: string; taskId: number }> {
  const res = await http.post<{ data: { message: string; taskId: number } }>(`/admin/upload/process/${taskId}`)
  return res.data.data
}

/** 删除一条上传任务 */
export async function deleteAdminUploadTask(taskId: number): Promise<void> {
  await http.delete(`/admin/upload/tasks/${taskId}`)
}

/** 上传文件到公共知识库 */
export async function uploadPublic(file: File): Promise<UploadTask> {
  const form = new FormData()
  form.append('file', file)
  const res = await http.post<{ data: UploadTask }>('/admin/upload-public', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data.data
}
