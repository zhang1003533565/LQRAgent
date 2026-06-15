import http from '@/api/http'
import type { ChatSession, ChatMessage } from '@/utils/types/chat'
import type { UserMemory, MemoryType } from '@/utils/types/memory'
import type { TraceTimeline } from '@/utils/types/trace'

/**
 * 聊天会话 API
 */
export const chatApi = {
  getSessions: (userId: string, page = 0, size = 20) =>
    http.get<ChatSession[]>(`/chat/sessions`, { params: { userId, page, size } }).then(r => r.data),

  createSession: (userId: string, title?: string) =>
    http.post<ChatSession>(`/chat/sessions`, { title }, { params: { userId } }).then(r => r.data),

  deleteSession: (sessionId: string) =>
    http.delete(`/chat/sessions/${sessionId}`),

  archiveSession: (sessionId: string) =>
    http.put(`/chat/sessions/${sessionId}/archive`),

  searchSessions: (userId: string, keyword: string) =>
    http.get<ChatSession[]>(`/chat/sessions/search`, { params: { userId, keyword } }).then(r => r.data),

  getMessages: (sessionId: string, limit = 50) =>
    http.get<ChatMessage[]>(`/chat/sessions/${sessionId}/messages`, { params: { limit } }).then(r => r.data),

  updateMessageMetadata: (messageId: string, metadata: Record<string, unknown>) =>
    http.put<ChatMessage>(`/chat/messages/${messageId}/metadata`, metadata).then(r => r.data),
}

/**
 * 记忆管理 API
 */
export const memoryApi = {
  getMemories: (userId: string, type?: MemoryType) =>
    http.get<UserMemory[]>(`/memory`, { params: { userId, type } }).then(r => r.data),

  addMemory: (userId: string, memory: Partial<UserMemory>) =>
    http.post<UserMemory>(`/memory`, memory, { params: { userId } }).then(r => r.data),

  updateMemory: (memoryId: string, data: Partial<UserMemory>) =>
    http.put<UserMemory>(`/memory/${memoryId}`, data).then(r => r.data),

  deleteMemory: (memoryId: string) =>
    http.delete(`/memory/${memoryId}`),

  searchMemories: (userId: string, keyword: string) =>
    http.get<UserMemory[]>(`/memory/search`, { params: { userId, keyword } }).then(r => r.data),

  getMemoriesForPrompt: (userId: string, limit = 3) =>
    http.get<{ memories: string }>(`/memory/prompt`, { params: { userId, limit } }).then(r => r.data),
}

/**
 * 链路追踪 API
 */
export const traceApi = {
  getTrace: (traceId: string) =>
    http.get<TraceTimeline>(`/trace/${traceId}`).then(r => r.data),
}
