import React, { useState, useEffect, useCallback } from 'react'
import type { ChatSession } from '@/utils/types/chat'
import { chatApi } from '@/api/student/chat'
import { useAuthStore } from '@/utils/store/authStore'
import { SessionList } from './SessionList'
import { SessionSearch } from './SessionSearch'
import { NewChatButton } from './NewChatButton'

interface ChatSidebarProps {
  currentSessionId: string | null
  onSelectSession: (sessionId: string) => void
  onNewSession: () => void
  className?: string
  refreshTrigger?: number
}

/**
 * 聊天侧边栏组件
 * 包含会话列表、搜索、新建聊天按钮
 */
export const ChatSidebar: React.FC<ChatSidebarProps> = ({
  currentSessionId,
  onSelectSession,
  onNewSession,
  className = '',
  refreshTrigger = 0,
}) => {
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [loading, setLoading] = useState(false)
  const [searchMode, setSearchMode] = useState(false)
  const [creating, setCreating] = useState(false)
  const userId = useAuthStore((s) => s.user?.userId != null ? String(s.user.userId) : null)

  // 加载会话列表
  const loadSessions = useCallback(async () => {
    if (!userId) return
    setLoading(true)
    try {
      const data = await chatApi.getSessions(userId)
      setSessions(data)
    } catch (error) {
      console.error('Failed to load sessions:', error)
    } finally {
      setLoading(false)
    }
  }, [userId])

  useEffect(() => {
    loadSessions()
  }, [loadSessions])

  // 外部触发刷新（如新会话创建后）
  useEffect(() => {
    if (refreshTrigger > 0) {
      loadSessions()
    }
  }, [refreshTrigger]) // eslint-disable-line react-hooks/exhaustive-deps

  // 搜索会话
  const handleSearch = useCallback(async (keyword: string) => {
    if (!userId) return
    setLoading(true)
    try {
      const data = await chatApi.searchSessions(userId, keyword)
      setSessions(data)
      setSearchMode(true)
    } catch (error) {
      console.error('Failed to search sessions:', error)
    } finally {
      setLoading(false)
    }
  }, [userId])

  // 清除搜索
  const handleClearSearch = useCallback(() => {
    setSearchMode(false)
    loadSessions()
  }, [loadSessions])

  // 删除会话
  const handleDeleteSession = useCallback(async (sessionId: string) => {
    if (!confirm('确定要删除这个会话吗？')) return
    
    try {
      await chatApi.deleteSession(sessionId)
      setSessions((prev) => prev.filter((s) => s.id !== sessionId))
      // 如果删除的是当前会话，触发新建
      if (String(sessionId) === String(currentSessionId)) {
        onNewSession()
      }
    } catch (error) {
      console.error('Failed to delete session:', error)
    }
  }, [currentSessionId, onNewSession])

  // 新建会话
  const handleNewChat = useCallback(async () => {
    setCreating(true)
    try {
      onNewSession()
    } finally {
      setCreating(false)
    }
  }, [onNewSession])

  return (
    <div className={`flex flex-col h-full bg-gray-50 border-r border-gray-200 ${className}`}>
      {/* 标题 */}
      <div className="px-4 py-3 border-b border-gray-200">
        <h2 className="text-sm font-semibold text-gray-700">会话历史</h2>
      </div>

      {/* 新建按钮 */}
      <div className="px-3 py-3">
        <NewChatButton onClick={handleNewChat} loading={creating} />
      </div>

      {/* 搜索框 */}
      <SessionSearch
        onSearch={handleSearch}
        onClear={handleClearSearch}
      />

      {/* 会话列表 */}
      <div className="flex-1 overflow-y-auto">
        <SessionList
          sessions={sessions}
          currentSessionId={currentSessionId}
          onSelectSession={onSelectSession}
          onDeleteSession={handleDeleteSession}
          loading={loading}
        />
      </div>

      {/* 底部信息 */}
      <div className="px-4 py-3 border-t border-gray-200 text-xs text-gray-400">
        共 {sessions.length} 个会话
      </div>
    </div>
  )
}

export default ChatSidebar
