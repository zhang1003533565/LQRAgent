import { create } from 'zustand'
import type { ChatMessage } from '@/utils/types/chat'
import { chatApi } from '@/utils/api/chat'

interface ChatState {
  messages: ChatMessage[]
  sessionId: string | null
  isConnected: boolean
  loadingMessages: boolean
  addMessage: (msg: ChatMessage) => void
  appendToLastMessage: (chunk: string) => void
  setStreaming: (id: string, streaming: boolean) => void
  updateMessage: (id: string, patch: Partial<ChatMessage>) => void
  setSessionId: (id: string | null) => void
  setConnected: (connected: boolean) => void
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
  clearMessages: () => set({ messages: [] }),

  /**
   * 加载指定会话的历史消息
   */
  loadMessages: async (sessionId: string) => {
    set({ loadingMessages: true, sessionId, messages: [] })
    try {
      const messages = await chatApi.getMessages(sessionId, 50)
      const reversed = [...messages].reverse()
      reversed.forEach((msg) => {
        get().addMessage({
          id: msg.id,
          role: msg.role as 'user' | 'assistant' | 'system',
          content: msg.content,
          agentName: msg.agentName,
          createdAt: new Date(msg.createdAt),
          streaming: false,
        })
      })
    } catch (err) {
      console.error('Failed to load messages:', err)
    } finally {
      set({ loadingMessages: false })
    }
  },
}))
