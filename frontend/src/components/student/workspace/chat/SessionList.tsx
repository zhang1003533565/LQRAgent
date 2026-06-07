import React from 'react'
import type { ChatSession } from '@/utils/types/chat'
import { SessionItem } from './SessionItem'

interface SessionListProps {
  sessions: ChatSession[]
  currentSessionId: string | null
  onSelectSession: (sessionId: string) => void
  onDeleteSession?: (sessionId: string) => void
  loading?: boolean
}

/**
 * 会话列表组件
 */
export const SessionList: React.FC<SessionListProps> = ({
  sessions,
  currentSessionId,
  onSelectSession,
  onDeleteSession,
  loading = false,
}) => {
  if (loading) {
    return (
      <div className="flex items-center justify-center py-8">
        <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-500" />
      </div>
    )
  }

  if (sessions.length === 0) {
    return (
      <div className="text-center py-8 text-gray-400 text-sm">
        <svg
          className="w-12 h-12 mx-auto mb-3 text-gray-300"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.5}
            d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
          />
        </svg>
        <p>暂无会话</p>
        <p className="text-xs mt-1">开始新对话吧</p>
      </div>
    )
  }

  return (
    <div className="space-y-1 px-2">
      {sessions.map((session) => (
        <SessionItem
          key={session.id}
          session={session}
          isActive={session.id === currentSessionId}
          onClick={() => onSelectSession(session.id)}
          onDelete={onDeleteSession ? () => onDeleteSession(session.id) : undefined}
        />
      ))}
    </div>
  )
}

export default SessionList
