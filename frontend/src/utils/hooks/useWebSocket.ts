import { useEffect, useRef, useCallback } from 'react'
import { useChatStore } from '@/utils/store/chatStore'
import { useAuthStore } from '@/utils/store/authStore'
import { useAgentTraceStore } from '@/utils/store/agentTraceStore'
import { useArtifactStore } from '@/utils/store/artifactStore'
import { usePathStore } from '@/utils/store/pathStore'
import { useProfileStore } from '@/utils/store/profileStore'
import type { AgentId, AgentStepStatus } from '@/utils/types/agent-events'
import type { ArtifactKind } from '@/utils/types/artifact'
import type { WsRawMessage } from '@/utils/types/agent-events'
import type { LearningPathArtifactPayload } from '@/utils/types/artifact'
import type { MultiCardBlock } from '@/utils/types/multi-card'
import type { ProfileSummary } from '@/utils/types/profile'

const MAX_RETRY = 8
const BASE_DELAY = 1000

function handleArtifact(kind: ArtifactKind, payload: unknown) {
  const artifact = useArtifactStore.getState()
  artifact.setLastKind(kind)

  if (kind === 'learning_path') {
    const p = payload as LearningPathArtifactPayload
    if (p?.nodes) {
      usePathStore.getState().setPath({
        goal: p.goal ?? '',
        nodes: p.nodes,
        planDescription: p.planDescription ?? '',
      })
    }
    return
  }

  if (kind === 'multi_card' && Array.isArray(payload)) {
    artifact.setMultiCardBlocks(payload as MultiCardBlock[])
    const msgs = useChatStore.getState().messages
    const last = [...msgs].reverse().find((m) => m.role === 'assistant')
    if (last) {
      useChatStore.getState().updateMessage(last.id, {
        contentType: 'multi_card',
        cards: payload as MultiCardBlock[],
        streaming: false,
      })
    }
  }
}

/**
 * WebSocket：chunk / agent_step / artifact / profile_patch / session_created / done / error
 * 自动重连（指数退避，最多 8 次）
 */
export function useWebSocket() {
  const wsRef = useRef<WebSocket | null>(null)
  const retryRef = useRef(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const mountedRef = useRef(true)
  const {
    appendToLastMessage,
    setStreaming,
    setConnected,
    setSessionId,
    addMessage,
    updateMessage,
  } = useChatStore()
  const upsertStep = useAgentTraceStore((s) => s.upsertStep)
  const clearSteps = useAgentTraceStore((s) => s.clearSteps)
  const patchSummary = useProfileStore((s) => s.patchSummary)
  const token = useAuthStore((s) => s.user?.token)

  const dispatch = useCallback(
    (data: WsRawMessage) => {
      if (data.session_id) setSessionId(data.session_id)

      switch (data.type) {
        case 'session_created':
          if (data.session_id) setSessionId(data.session_id)
          break
        case 'chunk':
          appendToLastMessage(data.content ?? '')
          break
        case 'agent_step':
          if (data.agent && data.label && data.status) {
            upsertStep({
              agent: data.agent as AgentId,
              label: data.label,
              status: data.status as AgentStepStatus,
              detail: data.detail,
            })
          }
          break
        case 'artifact':
          if (data.kind) {
            handleArtifact(data.kind as ArtifactKind, data.payload)
          }
          break
        case 'profile_patch':
          if (data.payload && typeof data.payload === 'object') {
            patchSummary(data.payload as Partial<ProfileSummary>)
          }
          break
        case 'done': {
          const msgs = useChatStore.getState().messages
          const last = [...msgs].reverse().find((m) => m.role === 'assistant')
          if (last) setStreaming(last.id, false)
          break
        }
        case 'error': {
          const msgs = useChatStore.getState().messages
          const last = [...msgs].reverse().find((m) => m.role === 'assistant')
          if (last) {
            updateMessage(last.id, {
              content: (last.content || '') + `\n\n⚠️ ${data.content ?? '发生错误'}`,
              streaming: false,
            })
          } else {
            addMessage({
              id: crypto.randomUUID(),
              role: 'assistant',
              content: data.content ?? '发生错误',
              createdAt: new Date(),
            })
          }
          break
        }
        default:
          break
      }
    },
    [
      appendToLastMessage,
      setStreaming,
      setSessionId,
      upsertStep,
      patchSummary,
      addMessage,
      updateMessage,
    ],
  )

  const cleanup = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
    if (wsRef.current) {
      wsRef.current.onclose = null
      wsRef.current.onerror = null
      wsRef.current.close()
      wsRef.current = null
    }
  }, [])

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return
    if (!token) return

    cleanup()

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${protocol}://${window.location.host}/ws/chat?token=${token}`
    const ws = new WebSocket(url)
    wsRef.current = ws

    ws.onopen = () => {
      retryRef.current = 0
      setConnected(true)
    }

    ws.onclose = () => {
      setConnected(false)
      wsRef.current = null
      if (mountedRef.current && retryRef.current < MAX_RETRY) {
        const delay = Math.min(BASE_DELAY * 2 ** retryRef.current, 30000)
        retryRef.current++
        timerRef.current = setTimeout(connect, delay)
      }
    }

    ws.onerror = () => {
      setConnected(false)
    }

    ws.onmessage = (event) => {
      try {
        dispatch(JSON.parse(event.data as string) as WsRawMessage)
      } catch {
        console.warn('[WebSocket] non-JSON:', event.data)
      }
    }
  }, [token, dispatch, setConnected, cleanup])

  const send = useCallback((content: string) => {
    if (wsRef.current?.readyState !== WebSocket.OPEN) {
      console.warn('[WebSocket] not connected')
      return
    }
    clearSteps()
    const sessionId = useChatStore.getState().sessionId
    wsRef.current.send(
      JSON.stringify({ type: 'message', content, session_id: sessionId }),
    )
  }, [clearSteps])

  const disconnect = useCallback(() => {
    mountedRef.current = false
    cleanup()
  }, [cleanup])

  useEffect(() => {
    mountedRef.current = true
    if (token) connect()
    return () => disconnect()
  }, [token, connect, disconnect])

  return { send, connect, disconnect }
}
