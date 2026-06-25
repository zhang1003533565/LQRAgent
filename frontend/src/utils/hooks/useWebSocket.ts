import { useEffect, useRef, useCallback } from 'react'
import { useChatStore } from '@/utils/store/chatStore'
import { useAuthStore } from '@/utils/store/authStore'
import { dispatchWsMessage } from '@/utils/hooks/wsMessageDispatcher'
import type { WsRawMessage } from '@/utils/types/agent-events'

const MAX_RETRY = 8
const BASE_DELAY = 1000

/**
 * WebSocket 连接管理 hook
 * 只负责：连接、重连、发送、断开
 * 消息分发由 wsMessageDispatcher.ts 处理
 */
export function useWebSocket() {
  const wsRef = useRef<WebSocket | null>(null)
  const retryRef = useRef(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const mountedRef = useRef(true)
  const setConnected = useChatStore((s) => s.setConnected)
  const token = useAuthStore((s) => s.user?.token)

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
    const wsHost = window.location.port === '5173' ? 'localhost:8080' : window.location.host
    const url = `${protocol}://${wsHost}/ws/chat?token=${token}`
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
        const data = JSON.parse(event.data as string) as WsRawMessage
        dispatchWsMessage(data)
      } catch {
        // non-JSON frame — ignore
      }
    }
  }, [token, setConnected, cleanup])

  const send = useCallback((content: string) => {
    if (wsRef.current?.readyState !== WebSocket.OPEN) {
      console.warn('[WebSocket] not connected')
      return
    }
    const sessionId = useChatStore.getState().sessionId
    wsRef.current.send(
      JSON.stringify({ type: 'message', content, session_id: sessionId }),
    )
  }, [])

  const disconnect = useCallback(() => {
    mountedRef.current = false
    cleanup()
  }, [cleanup])

  useEffect(() => {
    mountedRef.current = true
    const timer = setTimeout(() => {
      if (mountedRef.current && token) connect()
    }, 100)
    return () => {
      mountedRef.current = false
      clearTimeout(timer)
      disconnect()
    }
  }, [token, connect, disconnect])

  return { send, connect, disconnect }
}
