import { useState, useCallback, useEffect } from 'react'
import { useWebSocket } from '@/utils/hooks/useWebSocket'
import { useChatStore } from '@/utils/store/chatStore'
import { useAuthStore } from '@/utils/store/authStore'
import { chatApi } from '@/utils/api/chat'
import ChatMessageList from './ChatMessageList'
import ChatComposer from './ChatComposer'
import AgentStepsBar from './AgentStepsBar'
import { ChatSidebar } from './ChatSidebar'
import styles from './ChatView.module.css'

export default function ChatView() {
  const { send } = useWebSocket()
  const sessionId = useChatStore((s) => s.sessionId)
  const setSessionId = useChatStore((s) => s.setSessionId)
  const clearMessages = useChatStore((s) => s.clearMessages)
  const loadMessages = useChatStore((s) => s.loadMessages)
  const messages = useChatStore((s) => s.messages)
  const userId = useAuthStore((s) => s.user?.userId != null ? String(s.user.userId) : null)
  const [showSidebar, setShowSidebar] = useState(true)
  const [autoLoaded, setAutoLoaded] = useState(false)
  const [sidebarRefresh, setSidebarRefresh] = useState(0)

  // 挂载时自动加载最近会话
  useEffect(() => {
    if (autoLoaded || !userId) return
    
    // 如果已有消息（从其他页面跳转过来保留的状态），不自动加载
    if (messages.length > 0) {
      setAutoLoaded(true)
      return
    }

    // 自动加载最近会话
    const loadRecentSession = async () => {
      try {
        const sessions = await chatApi.getSessions(userId)
        if (sessions.length > 0) {
          const recent = sessions[0]
          await loadMessages(recent.id)
        }
      } catch (err) {
        console.error('Failed to auto-load recent session:', err)
      } finally {
        setAutoLoaded(true)
      }
    }
    loadRecentSession()
  }, [userId, autoLoaded, loadMessages, messages.length])

  // sessionId 变化时（新会话创建），刷新侧边栏
  useEffect(() => {
    if (sessionId) {
      setSidebarRefresh((n) => n + 1)
    }
  }, [sessionId])

  // 选择历史会话
  const handleSelectSession = useCallback(async (id: string) => {
    await loadMessages(id)
  }, [loadMessages])

  // 新建会话
  const handleNewSession = useCallback(() => {
    setSessionId(null)
    clearMessages()
    setSidebarRefresh((n) => n + 1)
  }, [setSessionId, clearMessages])

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowSidebar((v) => !v)}
            className="p-2 rounded text-gray-400 hover:text-gray-600 hover:bg-gray-100"
            title={showSidebar ? '隐藏历史' : '显示历史'}
          >
            <svg width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24" strokeWidth="2">
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <h1 className={styles.title}>聊天学习</h1>
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        {/* 左侧：会话历史侧边栏 */}
        {showSidebar && (
          <div className="w-64 flex-shrink-0 border-r border-gray-200 bg-gray-50">
            <ChatSidebar
              currentSessionId={sessionId}
              onSelectSession={handleSelectSession}
              onNewSession={handleNewSession}
              refreshTrigger={sidebarRefresh}
            />
          </div>
        )}

        {/* 主聊天区 */}
        <div className="flex-1 flex flex-col overflow-hidden">
          <div className={styles.scrollBody}>
            <div className={styles.content}>
              <AgentStepsBar />
              <ChatMessageList />
            </div>
          </div>
          <ChatComposer onSend={send} />
        </div>
      </div>
    </section>
  )
}
