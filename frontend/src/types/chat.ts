import type { MultiCardBlock } from './multi-card'

export type ChatRole = 'user' | 'assistant'

export type MessageContentType = 'text' | 'multi_card'

export interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  contentType?: MessageContentType
  cards?: MultiCardBlock[]
  streaming?: boolean
  createdAt: Date
}
