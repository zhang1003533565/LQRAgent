import { useEffect, useRef, useCallback } from 'react'
import { useChatStore } from '@/store/chatStore'
import { useAuthStore } from '@/store/authStore'

/**
 * WebSocket 封装 Hook。
 * 连接到 /ws/chat，处理流式消息事件。
 *
 * 消息协议（与 ai-server unified_ws 对齐）：
 *   发送：{ type: 'message', content: string, session_id?: string }
 *   接收：{ type: 'chunk' | 'done' | 'error', content?: string, session_id?: string }
 */
export function useWebSocket() {
  const wsRef = useRef<WebSocket | null>(null)
  const { addMessage, appendToLastMessage, setStreaming, setConnected, setSessionId } = useChatStore()
  const token = useAuthStore((s) => s.user?.token)

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return

    const url = `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws/chat?token=${token}`
    const ws = new WebSocket(url)
    wsRef.current = ws

    ws.onopen = () => {
      setConnected(true)
    }

    ws.onclose = () => {
      setConnected(false)
    }

    ws.onerror = (e) => {
      console.error('[WebSocket] error', e)
      setConnected(false)
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data as string)

        if (data.session_id) {
          setSessionId(data.session_id)
        }

        if (data.type === 'chunk') {
          appendToLastMessage(data.content ?? '')
        } else if (data.type === 'done') {
          // 找到最后一条 assistant 消息，标记流式结束
          const msgs = useChatStore.getState().messages
          const last = [...msgs].reverse().find((m) => m.role === 'assistant')
          if (last) setStreaming(last.id, false)
        } else if (data.type === 'error') {
          console.error('[WebSocket] server error:', data.content)
        }
      } catch {
        console.warn('[WebSocket] non-JSON message:', event.data)
      }
    }
  }, [token, addMessage, appendToLastMessage, setStreaming, setConnected, setSessionId])

  const send = useCallback((content: string) => {
    if (wsRef.current?.readyState !== WebSocket.OPEN) {
      console.warn('[WebSocket] not connected')
      return
    }
    const sessionId = useChatStore.getState().sessionId
    wsRef.current.send(JSON.stringify({ type: 'message', content, session_id: sessionId }))
  }, [])

  const disconnect = useCallback(() => {
    wsRef.current?.close()
    wsRef.current = null
  }, [])

  useEffect(() => {
    if (token) connect()
    return () => disconnect()
  }, [token, connect, disconnect])

  return { send, connect, disconnect }
}
