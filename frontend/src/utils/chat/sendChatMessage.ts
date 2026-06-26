import { useChatStore } from '@/utils/store/chatStore'
import { sendChatWs } from '@/utils/chat/chatWebSocket'

/** 与 ChatComposer 一致：先写入用户/助手占位消息，再经 WebSocket 发送 */
export function submitChatMessage(content: string, onSent?: (text: string) => void) {
  const trimmed = content.trim()
  if (!trimmed) return

  useChatStore.getState().finalizeStuckStreaming()

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
  sendChatWs(trimmed)
}
