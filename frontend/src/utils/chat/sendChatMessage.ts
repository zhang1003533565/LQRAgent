import { useChatStore } from '@/utils/store/chatStore'
import { sendChatWs } from '@/utils/chat/chatWebSocket'

/** 与 ChatComposer 一致：先写入用户/助手占位消息，再经 WebSocket 发送 */
export function submitChatMessage(content: string, onSent?: (text: string) => void) {
  const trimmed = content.trim()
  if (!trimmed) return

  const state = useChatStore.getState()
  const lastAssistant = [...state.messages].reverse().find((m) => m.role === 'assistant')
  if (lastAssistant?.streaming) return

  state.finalizeStuckStreaming()

  useChatStore.getState().addMessage({
    id: crypto.randomUUID(),
    role: 'user',
    content: trimmed,
    createdAt: new Date(),
  })
  useChatStore.getState().addMessage({
    id: crypto.randomUUID(),
    role: 'assistant',
    content: '',
    streaming: true,
    agentSteps: [],
    agentStepsCollapsed: false,
    createdAt: new Date(),
  })

  onSent?.(trimmed)
  const sent = sendChatWs(trimmed)
  if (!sent) {
    const msgs = useChatStore.getState().messages
    const last = [...msgs].reverse().find((m) => m.role === 'assistant')
    if (last?.streaming) {
      useChatStore.getState().updateMessage(last.id, {
        content: '连接未就绪，请稍候再试或刷新页面。',
        streaming: false,
      })
    }
  }
}
