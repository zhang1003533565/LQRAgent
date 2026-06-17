import { create } from 'zustand'
import type { ChatMessage, MessageContentType, QuizData } from '@/utils/types/chat'
import type { MultiCardBlock } from '@/utils/types/multi-card'
import type { RagSource } from '@/utils/types/artifact'
import { chatApi } from '@/api/student/chat'

interface ChatState {
  messages: ChatMessage[]
  sessionId: string | null
  isConnected: boolean
  loadingMessages: boolean
  autoLoaded: boolean
  addMessage: (msg: ChatMessage) => void
  appendToLastMessage: (chunk: string) => void
  setStreaming: (id: string, streaming: boolean) => void
  updateMessage: (id: string, patch: Partial<ChatMessage>) => void
  setSessionId: (id: string | null) => void
  setConnected: (connected: boolean) => void
  setAutoLoaded: (v: boolean) => void
  clearMessages: () => void
  loadMessages: (sessionId: string) => Promise<void>
}

// 用于批量处理 chunk 更新
let pendingChunk = ''
let rafId: number | null = null
let pendingSet: ((fn: (state: ChatState) => Partial<ChatState>) => void) | null = null

function flushChunk() {
  if (pendingChunk && pendingSet) {
    const chunk = pendingChunk
    pendingChunk = ''
    pendingSet((state: ChatState) => {
      const msgs = [...state.messages]
      if (msgs.length === 0) return state
      const last = { ...msgs[msgs.length - 1] }
      last.content += chunk
      msgs[msgs.length - 1] = last
      return { messages: msgs }
    })
  }
  rafId = null
}

export const useChatStore = create<ChatState>((set, get) => ({
  messages: [],
  sessionId: null,
  isConnected: false,
  loadingMessages: false,
  autoLoaded: false,

  addMessage: (msg) =>
    set((state) => ({ messages: [...state.messages, msg] })),

  appendToLastMessage: (chunk) => {
    pendingChunk += chunk
    pendingSet = set as any
    if (rafId === null) {
      rafId = requestAnimationFrame(flushChunk)
    }
  },

  setStreaming: (id, streaming) =>
    set((state) => ({
      messages: state.messages.map((m) =>
        m.id === id ? { ...m, streaming } : m,
      ),
    })),

  updateMessage: (id, patch) =>
    set((state) => ({
      messages: state.messages.map((m) =>
        m.id === id ? { ...m, ...patch } : m,
      ),
    })),

  setSessionId: (id) => set({ sessionId: id }),
  setConnected: (connected) => set({ isConnected: connected }),
  setAutoLoaded: (v) => set({ autoLoaded: v }),
  clearMessages: () => set({ messages: [] }),

  loadMessages: async (sessionId: string) => {
    set({ loadingMessages: true, sessionId: String(sessionId), messages: [] })
    try {
      const messages = await chatApi.getMessages(sessionId, 50)
      const reversed = [...messages].reverse()
      const formattedMessages: ChatMessage[] = reversed.map((msg) => {
        let metadataParsed: Record<string, unknown> = {}
        if (msg.metadata && typeof msg.metadata === 'string') {
          try { metadataParsed = JSON.parse(msg.metadata) } catch {}
        } else if (msg.metadata && typeof msg.metadata === 'object') {
          metadataParsed = msg.metadata as Record<string, unknown>
        }
        return {
          id: String(msg.id),
          role: msg.role as 'user' | 'assistant' | 'system',
          content: msg.content,
          contentType: (metadataParsed.contentType as MessageContentType) || (msg.contentType as MessageContentType | undefined),
          agentName: msg.agentName,
          imageUrl: (metadataParsed.imageUrl as string) || msg.imageUrl,
          videoUrl: (metadataParsed.videoUrl as string) || (msg as any).videoUrl,
          quizData: (metadataParsed.quizData as QuizData) || (msg as any).quizData,
          diagramCode: (metadataParsed.diagramCode as string) || msg.diagramCode,
          diagramFormat: (metadataParsed.diagramFormat as string) || msg.diagramFormat,
          cards: (metadataParsed.cards as MultiCardBlock[]) || msg.cards,
          ragSources: (metadataParsed.ragSources as RagSource[]) || msg.ragSources,
          metadata: metadataParsed,
          createdAt: new Date(msg.createdAt),
          streaming: false,
        }
      })
      set({ messages: formattedMessages })
    } catch (err) {
      console.error('Failed to load messages:', err)
    } finally {
      set({ loadingMessages: false })
    }
  },
}))
