import type { MultiCardBlock } from './multi-card'
import type { RagSource } from './artifact'

export type ChatRole = 'user' | 'assistant' | 'system'

export type MessageContentType = 'text' | 'multi_card' | 'diagram' | 'learning_path'

/**
 * 聊天会话
 */
export interface ChatSession {
  id: string
  userId: string
  title: string
  status: 'ACTIVE' | 'ARCHIVED' | 'DELETED'
  createdAt: Date
  updatedAt: Date
  messageCount?: number
  lastMessage?: string
}

/**
 * 聊天消息
 */
export interface ChatMessage {
  id: string
  sessionId?: string
  userId?: string
  role: ChatRole
  content: string
  contentType?: MessageContentType
  agentName?: string
  cards?: MultiCardBlock[]
  ragSources?: RagSource[]
  diagramCode?: string
  diagramFormat?: string
  metadata?: Record<string, any>
  streaming?: boolean
  createdAt: Date
}
