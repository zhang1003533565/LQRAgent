import React from 'react'
import type { UserMemory, MemoryType } from '@/utils/types/memory'

interface MemoryItemProps {
  memory: UserMemory
  onDelete?: (id: string) => void
  onEdit?: (memory: UserMemory) => void
}

const MEMORY_TYPE_LABELS: Record<MemoryType, string> = {
  PREFERENCE: '偏好',
  LEARNING_PROGRESS: '学习进度',
  TOPIC_INTEREST: '兴趣',
  INTERACTION_STYLE: '交互风格',
  KNOWLEDGE_STATE: '知识状态',
}

const MEMORY_TYPE_COLORS: Record<MemoryType, string> = {
  PREFERENCE: 'bg-purple-100 text-purple-700',
  LEARNING_PROGRESS: 'bg-blue-100 text-blue-700',
  TOPIC_INTEREST: 'bg-green-100 text-green-700',
  INTERACTION_STYLE: 'bg-yellow-100 text-yellow-700',
  KNOWLEDGE_STATE: 'bg-red-100 text-red-700',
}

/**
 * 记忆项组件
 */
export const MemoryItem: React.FC<MemoryItemProps> = ({
  memory,
  onDelete,
  onEdit,
}) => {
  const formatDate = (date: Date | string) => {
    const d = new Date(date)
    return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  }

  return (
    <div className="p-3 bg-white rounded-lg border border-gray-200 hover:border-gray-300 transition-colors">
      {/* 头部：类型标签 + 操作按钮 */}
      <div className="flex items-center justify-between mb-2">
        <span
          className={`px-2 py-0.5 text-xs font-medium rounded-full ${
            MEMORY_TYPE_COLORS[memory.memoryType] || 'bg-gray-100 text-gray-700'
          }`}
        >
          {MEMORY_TYPE_LABELS[memory.memoryType] || memory.memoryType}
        </span>

        <div className="flex items-center gap-1">
          {onEdit && (
            <button
              onClick={() => onEdit(memory)}
              className="p-1 text-gray-400 hover:text-gray-600 rounded"
              title="编辑"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
            </button>
          )}
          {onDelete && (
            <button
              onClick={() => onDelete(memory.id)}
              className="p-1 text-gray-400 hover:text-red-500 rounded"
              title="删除"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* 内容 */}
      <p className="text-sm text-gray-700 leading-relaxed">{memory.content}</p>

      {/* 底部：元信息 */}
      <div className="flex items-center gap-3 mt-2 text-xs text-gray-400">
        <span>{formatDate(memory.createdAt)}</span>
        <span className="flex items-center gap-1">
          <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
          </svg>
          {memory.accessCount}
        </span>
        {memory.source && (
          <span className="text-gray-300">来源: {memory.source}</span>
        )}
      </div>
    </div>
  )
}

export default MemoryItem
