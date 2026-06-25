import { create } from 'zustand'
import type { ChatMessage, MessageContentType, QuizData } from '@/utils/types/chat'
import type { MultiCardBlock } from '@/utils/types/multi-card'
import type { RagSource } from '@/utils/types/artifact'
import { chatApi } from '@/api/student/chat'
import { hydrateAgentSteps } from '@/utils/chat/messageAgentSteps'

interface ChatState {
  messages: ChatMessage[]
  sessionId: string | null
  isConnected: boolean
  loadingMessages: boolean
  autoLoaded: boolean
  sessionListVersion: number
  addMessage: (msg: ChatMessage) => void
  appendToLastMessage: (chunk: string) => void
  setStreaming: (id: string, streaming: boolean) => void
  updateMessage: (id: string, patch: Partial<ChatMessage>) => void
  setSessionId: (id: string | null) => void
  setConnected: (connected: boolean) => void
  setAutoLoaded: (v: boolean) => void
  bumpSessionList: () => void
  clearMessages: () => void
  loadMessages: (sessionId: string) => Promise<void>
  finalizeStuckStreaming: () => void
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

/** 在 done 等事件前刷掉尚未写入 store 的 chunk，避免误判为空内容 */
export function flushPendingChunks() {
  if (rafId !== null) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
  flushChunk()
}

export const useChatStore = create<ChatState>((set, get) => ({
  messages: [],
  sessionId: null,
  isConnected: false,
  loadingMessages: false,
  autoLoaded: false,
  sessionListVersion: 0,

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
  bumpSessionList: () => set((state) => ({ sessionListVersion: state.sessionListVersion + 1 })),
  clearMessages: () => set({ messages: [] }),

  finalizeStuckStreaming: () =>
    set((state) => {
      const msgs = [...state.messages]
      for (let i = msgs.length - 1; i >= 0; i--) {
        const m = msgs[i]
        if (m.role === 'assistant' && m.streaming) {
          msgs[i] = {
            ...m,
            streaming: false,
            content: m.content?.trim()
              ? m.content
              : '回答未完成或超时，请重试。',
          }
          break
        }
      }
      return { messages: msgs }
    }),

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
        const imageUrl = (metadataParsed.imageUrl as string) || msg.imageUrl
        const videoUrl = (metadataParsed.videoUrl as string) || (msg as any).videoUrl
        const contentType =
          (metadataParsed.contentType as MessageContentType) ||
          (msg.contentType as MessageContentType | undefined)
        let content = msg.content
        // 历史消息：有图片/视频 metadata 时不展示 prompt 工程的长英文
        if (imageUrl && contentType === 'image') {
          if (!content?.trim() || content.length > 120) {
            content = '图片已生成。'
          }
        } else if (videoUrl && contentType === 'video') {
          if (!content?.trim() || content.length > 120) {
            content = '视频已生成。'
          }
        }
        return {
          id: String(msg.id),
          role: msg.role as 'user' | 'assistant' | 'system',
          content,
          contentType,
          agentName: msg.agentName,
          imageUrl,
          videoUrl,
          quizData: (metadataParsed.quizData as QuizData) || (msg as any).quizData,
          diagramCode: (metadataParsed.diagramCode as string) || msg.diagramCode,
          diagramFormat: (metadataParsed.diagramFormat as string) || msg.diagramFormat,
          cards: (metadataParsed.cards as MultiCardBlock[]) || msg.cards,
          ragSources: (metadataParsed.ragSources as RagSource[]) || msg.ragSources,
          agentSteps: hydrateAgentSteps(metadataParsed.agentSteps) ?? [],
          agentStepsCollapsed: metadataParsed.agentSteps
            ? true
            : undefined,
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
