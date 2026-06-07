import React from 'react'
import type { ChatSession } from '@/utils/types/chat'

interface SessionItemProps {
  session: ChatSession
  isActive: boolean
  onClick: () => void
  onDelete?: () => void
}

/**
 * 单个会话项组件
 */
export const SessionItem: React.FC<SessionItemProps> = ({
  session,
  isActive,
  onClick,
  onDelete,
}) => {
  const formatDate = (date: Date) => {
    const d = new Date(date)
    const now = new Date()
    const diffMs = now.getTime() - d.getTime()
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

    if (diffDays === 0) {
      return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    } else if (diffDays === 1) {
      return '昨天'
    } else if (diffDays < 7) {
      return `${diffDays}天前`
    } else {
      return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
    }
  }

  return (
    <div
      className={`
        group flex items-center gap-3 px-3 py-2.5 rounded-lg cursor-pointer
        transition-colors duration-150
        ${isActive
          ? 'bg-blue-50 text-blue-700'
          : 'hover:bg-gray-100 text-gray-700'
        }
      `}
      onClick={onClick}
    >
      {/* 图标 */}
      <svg
        className="w-4 h-4 flex-shrink-0 opacity-50"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
        />
      </svg>

      {/* 内容 */}
      <div className="flex-1 min-w-0">
        <div className="text-sm font-medium truncate">
          {session.title || '新对话'}
        </div>
        <div className="text-xs text-gray-400 mt-0.5">
          {formatDate(session.updatedAt)}
        </div>
      </div>

      {/* 删除按钮 */}
      {onDelete && (
        <button
          className="opacity-0 group-hover:opacity-100 p-1 hover:bg-gray-200 rounded transition-opacity"
          onClick={(e) => {
            e.stopPropagation()
            onDelete()
          }}
          title="删除会话"
        >
          <svg className="w-3.5 h-3.5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
        </button>
      )}
    </div>
  )
}

export default SessionItem
