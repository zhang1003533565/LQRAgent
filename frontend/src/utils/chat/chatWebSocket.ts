import { useEffect, useCallback } from 'react'
import { useChatStore } from '@/utils/store/chatStore'
import { useAuthStore } from '@/utils/store/authStore'
import { dispatchWsMessage } from '@/utils/hooks/wsMessageDispatcher'
import type { WsRawMessage } from '@/utils/types/agent-events'

const MAX_RETRY = 8
const BASE_DELAY = 1000

let ws: WebSocket | null = null
let retryCount = 0
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let mountCount = 0
let shouldReconnect = true

function cleanup() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.onclose = null
    ws.onerror = null
    ws.close()
    ws = null
  }
}

function connect() {
  if (ws?.readyState === WebSocket.OPEN) return

  const token = useAuthStore.getState().user?.token
  if (!token) return

  cleanup()

  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const wsHost = window.location.port === '5173' ? 'localhost:8080' : window.location.host
  const url = `${protocol}://${wsHost}/ws/chat?token=${token}`
  const socket = new WebSocket(url)
  ws = socket

  socket.onopen = () => {
    retryCount = 0
    useChatStore.getState().setConnected(true)
  }

  socket.onclose = () => {
    useChatStore.getState().setConnected(false)
    ws = null
    if (shouldReconnect && mountCount > 0 && retryCount < MAX_RETRY) {
      const delay = Math.min(BASE_DELAY * 2 ** retryCount, 30000)
      retryCount++
      reconnectTimer = setTimeout(connect, delay)
    }
  }

  socket.onerror = () => {
    useChatStore.getState().setConnected(false)
  }

  socket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data as string) as WsRawMessage
      dispatchWsMessage(data)
    } catch {
      // non-JSON frame — ignore
    }
  }
}

export function sendChatWs(content: string) {
  if (ws?.readyState !== WebSocket.OPEN) {
    console.warn('[WebSocket] not connected')
    return
  }
  const sessionId = useChatStore.getState().sessionId
  ws.send(JSON.stringify({ type: 'message', content, session_id: sessionId }))
}

/** 在工作台壳层挂载一次，避免多组件重复建连 */
export function useChatWebSocket() {
  const token = useAuthStore((s) => s.user?.token)

  const disconnect = useCallback(() => {
    shouldReconnect = false
    cleanup()
    useChatStore.getState().setConnected(false)
  }, [])

  useEffect(() => {
    shouldReconnect = true
    mountCount++
    const timer = setTimeout(() => {
      if (token) connect()
    }, 100)

    return () => {
      clearTimeout(timer)
      mountCount--
      if (mountCount <= 0) {
        disconnect()
        mountCount = 0
      }
    }
  }, [token, disconnect])

  return { send: sendChatWs, connect, disconnect }
}
