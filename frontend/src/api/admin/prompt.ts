import http from '@/api/http'

export interface AgentPrompt {
  id: number
  agentId: string
  agentName: string
  promptContent: string
  defaultContent: string
  version: number
  updatedAt: string
}

/** 获取指定 Agent 的提示词 */
export async function getPrompt(agentId: string): Promise<AgentPrompt> {
  const res = await http.get<AgentPrompt>(`/admin/prompts/${encodeURIComponent(agentId)}`)
  return res.data
}

/** 更新提示词 */
export async function savePrompt(agentId: string, content: string, updatedBy = 'admin'): Promise<AgentPrompt> {
  const res = await http.put<AgentPrompt>(`/admin/prompts/${encodeURIComponent(agentId)}`, {
    content,
    updatedBy,
  })
  return res.data
}

/** 重置为默认提示词 */
export async function resetPrompt(agentId: string): Promise<AgentPrompt> {
  const res = await http.post<AgentPrompt>(`/admin/prompts/${encodeURIComponent(agentId)}/reset`)
  return res.data
}

/** 清除提示词缓存 */
export async function clearCache(): Promise<void> {
  await http.post('/admin/prompts/clear-cache')
}

/** 获取所有 Agent 提示词列表 */
export async function getAllPrompts(): Promise<AgentPrompt[]> {
  const res = await http.get<AgentPrompt[]>('/admin/prompts')
  return res.data
}
