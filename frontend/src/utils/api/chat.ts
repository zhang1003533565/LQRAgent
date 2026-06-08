/**
 * 聊天相关 API 客户端
 */
import type { ChatSession, ChatMessage } from '@/utils/types/chat'
import type { UserMemory, MemoryType } from '@/utils/types/memory'
import type { TraceTimeline } from '@/utils/types/trace'

const API_BASE = import.meta.env.VITE_API_URL || '/api'

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${url}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  })
  
  if (!response.ok) {
    const error = await response.text()
    throw new Error(error || `Request failed: ${response.status}`)
  }
  
  // DELETE 等操作可能没有响应体
  const text = await response.text()
  if (!text) return undefined as T
  return JSON.parse(text)
}

/**
 * 会话管理 API
 */
export const chatApi = {
  /**
   * 获取用户的会话列表
   */
  getSessions: (userId: string, page = 0, size = 20) =>
    request<ChatSession[]>(`/chat/sessions?userId=${userId}&page=${page}&size=${size}`),

  /**
   * 创建新会话
   */
  createSession: (userId: string, title?: string) =>
    request<ChatSession>(`/chat/sessions?userId=${userId}`, {
      method: 'POST',
      body: JSON.stringify({ title }),
      headers: { 'Content-Type': 'application/json' },
    }),

  /**
   * 删除会话
   */
  deleteSession: (sessionId: string) =>
    request<void>(`/chat/sessions/${sessionId}`, { method: 'DELETE' }),

  /**
   * 归档会话
   */
  archiveSession: (sessionId: string) =>
    request<void>(`/chat/sessions/${sessionId}/archive`, { method: 'PUT' }),

  /**
   * 搜索会话
   */
  searchSessions: (userId: string, keyword: string) =>
    request<ChatSession[]>(`/chat/sessions/search?userId=${userId}&keyword=${encodeURIComponent(keyword)}`),

  /**
   * 获取会话的消息列表
   */
  getMessages: (sessionId: string, limit = 50) =>
    request<ChatMessage[]>(`/chat/sessions/${sessionId}/messages?limit=${limit}`),
}

/**
 * 记忆管理 API
 */
export const memoryApi = {
  /**
   * 获取用户的记忆列表
   */
  getMemories: (userId: string, type?: MemoryType) => {
    const params = new URLSearchParams({ userId })
    if (type) params.append('type', type)
    return request<UserMemory[]>(`/memory?${params}`)
  },

  /**
   * 添加记忆
   */
  addMemory: (userId: string, memory: Partial<UserMemory>) =>
    request<UserMemory>(`/memory?userId=${userId}`, {
      method: 'POST',
      body: JSON.stringify(memory),
    }),

  /**
   * 更新记忆
   */
  updateMemory: (memoryId: string, data: Partial<UserMemory>) =>
    request<UserMemory>(`/memory/${memoryId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  /**
   * 删除记忆
   */
  deleteMemory: (memoryId: string) =>
    request<void>(`/memory/${memoryId}`, { method: 'DELETE' }),

  /**
   * 搜索记忆
   */
  searchMemories: (userId: string, keyword: string) =>
    request<UserMemory[]>(`/memory/search?userId=${userId}&keyword=${encodeURIComponent(keyword)}`),

  /**
   * 获取用于 Prompt 的重要记忆
   */
  getMemoriesForPrompt: (userId: string, limit = 3) =>
    request<{ memories: string }>(`/memory/prompt?userId=${userId}&limit=${limit}`),
}

/**
 * 链路追踪 API
 */
export const traceApi = {
  /**
   * 获取追踪时间线
   */
  getTrace: (traceId: string) =>
    request<TraceTimeline>(`/trace/${traceId}`),
}

export default { chatApi, memoryApi, traceApi }
