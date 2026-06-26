import { sendChatWs } from '@/utils/chat/chatWebSocket'

/**
 * 发送消息（连接由 WorkspaceShell 的 useChatWebSocket 统一管理）
 */
export function useWebSocket() {
  return { send: sendChatWs }
}

export { useChatWebSocket, sendChatWs } from '@/utils/chat/chatWebSocket'
