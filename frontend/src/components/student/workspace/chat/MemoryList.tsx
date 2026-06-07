import React from 'react'
import type { UserMemory } from '@/utils/types/memory'
import { MemoryItem } from './MemoryItem'

interface MemoryListProps {
  memories: UserMemory[]
  loading?: boolean
  onDelete?: (id: string) => void
  onEdit?: (memory: UserMemory) => void
}

/**
 * 记忆列表组件
 */
export const MemoryList: React.FC<MemoryListProps> = ({
  memories,
  loading = false,
  onDelete,
  onEdit,
}) => {
  if (loading) {
    return (
      <div className="flex items-center justify-center py-8">
        <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-500" />
      </div>
    )
  }

  if (memories.length === 0) {
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
            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
          />
        </svg>
        <p>暂无记忆</p>
        <p className="text-xs mt-1">系统会自动从对话中提取记忆</p>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {memories.map((memory) => (
        <MemoryItem
          key={memory.id}
          memory={memory}
          onDelete={onDelete}
          onEdit={onEdit}
        />
      ))}
    </div>
  )
}

export default MemoryList
