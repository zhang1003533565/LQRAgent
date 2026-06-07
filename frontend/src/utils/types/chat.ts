import type { MultiCardBlock } from './multi-card'
import type { RagSource } from './artifact'

export type ChatRole = 'user' | 'assistant'

export type MessageContentType = 'text' | 'multi_card' | 'diagram'

export interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  contentType?: MessageContentType
  cards?: MultiCardBlock[]
  ragSources?: RagSource[]
  diagramCode?: string
  diagramFormat?: string
  streaming?: boolean
  createdAt: Date
}
