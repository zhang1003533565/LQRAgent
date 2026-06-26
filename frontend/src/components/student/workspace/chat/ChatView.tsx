import { useCallback, useEffect } from 'react'
import { sendChatWs } from '@/utils/chat/chatWebSocket'
import { useChatStore } from '@/utils/store/chatStore'
import { useAuthStore } from '@/utils/store/authStore'
import { usePathStore } from '@/utils/store/pathStore'
import { chatApi } from '@/api/student/chat'
import { trackBehavior } from '@/utils/tracker'
import ChatMessageList from './ChatMessageList'
import ChatComposer from './ChatComposer'
import styles from './ChatView.module.css'

export default function ChatView() {
  const sessionId = useChatStore((s) => s.sessionId)
  const isConnected = useChatStore((s) => s.isConnected)
  const loadMessages = useChatStore((s) => s.loadMessages)
  const messages = useChatStore((s) => s.messages)
  const userId = useAuthStore((s) => s.user?.userId != null ? String(s.user.userId) : null)
  const selectedKpId = usePathStore((s) => s.selectedKpId)
  const autoLoaded = useChatStore((s) => s.autoLoaded)
  const setAutoLoaded = useChatStore((s) => s.setAutoLoaded)

  // 挂载时自动加载最近会话（仅首次）
  useEffect(() => {
    if (autoLoaded || !userId) return
    // 如果已有消息或已有sessionId，不自动加载
    if (messages.length > 0 || sessionId) {
      setAutoLoaded(true)
      return
    }
    const loadRecentSession = async () => {
      try {
        const sessions = await chatApi.getSessions(userId)
        if (sessions.length > 0) {
          await loadMessages(String(sessions[0].id))
        }
      } catch (err) {
        console.error('Failed to auto-load recent session:', err)
      } finally {
        setAutoLoaded(true)
      }
    }
    loadRecentSession()
  }, [userId, autoLoaded, loadMessages, messages.length, sessionId, setAutoLoaded])

  const trackSend = useCallback((content: string) => {
    if (selectedKpId) {
      trackBehavior({ kpId: selectedKpId, action: 'chat_send', extra: content.slice(0, 100) })
    }
    sendChatWs(content)
  }, [selectedKpId])

  return (
    <section className={styles.page}>
      <div className={styles.workspace}>
        <div className={styles.chatCard}>
          <div className={styles.connectionStatus}>
            <span className={`${styles.statusDot} ${isConnected ? styles.connected : styles.disconnected}`} />
            <span className={styles.statusText}>
              {isConnected ? '已连接' : '连接中'}
            </span>
          </div>
          <div className={styles.scrollBody}>
            <div className={styles.content}>
              <ChatMessageList />
            </div>
          </div>
          <ChatComposer onSend={trackSend} />
        </div>
      </div>
    </section>
  )
}
