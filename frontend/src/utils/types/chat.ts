import type { MultiCardBlock } from './multi-card'
import type { RagSource } from './artifact'

export type ChatRole = 'user' | 'assistant' | 'system'

export type MessageContentType = 'text' | 'multi_card' | 'diagram' | 'learning_path' | 'image' | 'quiz' | 'video'

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
export interface QuizQuestion {
  id: number
  type: '选择题' | '判断题' | '填空题' | '简答题' | '编程题' | string
  stem: string
  options?: string[]
  answer?: string
  explanation?: string
  difficulty?: string
}

export interface QuizData {
  title?: string
  topic?: string
  difficulty?: string
  questions: QuizQuestion[]
}

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
  imageUrl?: string
  videoUrl?: string
  quizData?: QuizData
  diagramCode?: string
  diagramFormat?: string
  metadata?: Record<string, any>
  streaming?: boolean
  createdAt: Date
}
