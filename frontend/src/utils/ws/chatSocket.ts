import { useAuthStore } from '@/utils/store/authStore'
import { useChatStore } from '@/utils/store/chatStore'
import { useAgentTraceStore } from '@/utils/store/agentTraceStore'

type EventHandler = (event: string, data: any) => void

/**
 * WebSocket 聊天连接管理器。
 *
 * 协议遵守后端 /ws/chat 规范：
 * 发送 → {type:"message", content:"...", session_id: "..."}
 * 接收 → session_created / agent_step / chunk / done / error / artifact
 *
 * 连接时在 query 中传 JWT token 完成认证。
 */
export class ChatSocket {
  private ws: WebSocket | null = null
  private url: string
  private eventHandler?: EventHandler
  private _isConnected = false
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private maxRetries = 5
  private retryCount = 0
  private sessionId: string | null = null

  constructor() {
    const token = useAuthStore.getState().user?.token
    const host = window.location.hostname
    const port = 8080
    this.url = `ws://${host}:${port}/ws/chat?token=${token || ''}`
  }

  get isConnected() { return this._isConnected }

  /** 建立 WS 连接 */
  connect() {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return
    }

    try {
      this.ws = new WebSocket(this.url)
    } catch (e) {
      console.error('[ChatSocket] 创建 WebSocket 失败', e)
      this.scheduleReconnect()
      return
    }

    this.ws.onopen = () => {
      console.log('[ChatSocket] 已连接')
      this._isConnected = true
      this.retryCount = 0
      useChatStore.getState().setConnected(true)
    }

    this.ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        this.handleMessage(msg)
      } catch (e) {
        console.warn('[ChatSocket] 消息解析失败', e)
      }
    }

    this.ws.onclose = (event) => {
      console.log('[ChatSocket] 连接关闭', event.code, event.reason)
      this._isConnected = false
      useChatStore.getState().setConnected(false)
      this.ws = null
      this.scheduleReconnect()
    }

    this.ws.onerror = (err) => {
      console.error('[ChatSocket] 连接错误', err)
    }
  }

  /** 断开连接 */
  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.retryCount = this.maxRetries // 阻止重连
    if (this.ws) {
      this.ws.onclose = null
      this.ws.close()
      this.ws = null
    }
    this._isConnected = false
    useChatStore.getState().setConnected(false)
  }

  /** 发送消息 */
  send(content: string) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.warn('[ChatSocket] 未连接，无法发送')
      return
    }

    const payload = JSON.stringify({
      type: 'message',
      content,
      session_id: this.sessionId || null,
    })

    this.ws.send(payload)

    // 添加 user 消息到 store
    useChatStore.getState().addMessage({
      id: crypto.randomUUID(),
      role: 'user',
      content,
      createdAt: new Date(),
    })

    // 添加占位 assistant 消息（streaming）
    const assistantId = crypto.randomUUID()
    useChatStore.getState().addMessage({
      id: assistantId,
      role: 'assistant',
      content: '',
      streaming: true,
      createdAt: new Date(),
    })
  }

  /** 注册外部事件回调（可选） */
  setEventHandler(handler: EventHandler) {
    this.eventHandler = handler
  }

  // ===== internal =====

  private handleMessage(msg: any) {
    const type = msg.type || ''

    switch (type) {
      case 'session_created':
        this.sessionId = msg.session_id
        // 更新 chatStore sessionId
        useChatStore.getState().setSessionId(msg.session_id)
        break

      case 'agent_step':
        // 更新 agentTraceStore
        useAgentTraceStore.getState().upsertStep({
          agent: msg.agent,
          label: msg.label,
          status: msg.status,  // 'running' | 'done' | 'failed', 与后端协议一致
          detail: msg.detail,
        })
        break

      case 'chunk':
        // 逐字追加到最后一个 assistant 消息
        useChatStore.getState().appendToLastMessage(msg.content || '')
        break

      case 'done':
        // 关闭最后一个消息的 streaming 状态
        {
          const msgs = useChatStore.getState().messages
          for (let i = msgs.length - 1; i >= 0; i--) {
            if (msgs[i].role === 'assistant') {
              useChatStore.getState().setStreaming(msgs[i].id, false)
              break
            }
          }
        }
        break

      case 'error':
        console.error('[ChatSocket] 服务端错误:', msg.content)
        // 关闭 streaming
        {
          const msgs = useChatStore.getState().messages
          for (let i = msgs.length - 1; i >= 0; i--) {
            if (msgs[i].role === 'assistant' && msgs[i].streaming) {
              useChatStore.getState().updateMessage(msgs[i].id, { content: msgs[i].content + '\n\n⚠️ ' + (msg.content || '发生错误'), streaming: false })
              break
            }
          }
        }
        break

      case 'artifact':
        // 更新最后一条 assistant 消息的 content_type
        {
          const msgs = useChatStore.getState().messages
          for (let i = msgs.length - 1; i >= 0; i--) {
            if (msgs[i].role === 'assistant') {
              useChatStore.getState().updateMessage(msgs[i].id, {
                contentType: 'multi_card',
                cards: msg.payload,
              })
              break
            }
          }
        }
        break
    }

    // 透传给外部 handler
    this.eventHandler?.(type, msg)
  }

  private scheduleReconnect() {
    if (this.retryCount >= this.maxRetries) return
    if (this.reconnectTimer) return

    const delay = Math.min(1000 * Math.pow(2, this.retryCount), 15000)
    this.retryCount++
    console.log(`[ChatSocket] ${delay}ms 后重连 (${this.retryCount}/${this.maxRetries})`)

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, delay)
  }
}

/** 创建单例 */
let instance: ChatSocket | null = null

export function getChatSocket(): ChatSocket {
  if (!instance) {
    instance = new ChatSocket()
  }
  return instance
}
