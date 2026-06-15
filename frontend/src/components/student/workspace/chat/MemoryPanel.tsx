import React, { useState, useEffect, useCallback } from 'react'
import type { UserMemory, MemoryType } from '@/utils/types/memory'
import { memoryApi } from '@/api/student/chat'
import { MemoryList } from './MemoryList'
import { MemoryEditor } from './MemoryEditor'

interface MemoryPanelProps {
  userId: string
  className?: string
}

const MEMORY_TYPE_OPTIONS: { value: MemoryType | ''; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'PREFERENCE', label: '偏好' },
  { value: 'LEARNING_PROGRESS', label: '学习进度' },
  { value: 'TOPIC_INTEREST', label: '兴趣' },
  { value: 'INTERACTION_STYLE', label: '交互风格' },
  { value: 'KNOWLEDGE_STATE', label: '知识状态' },
]

/**
 * 记忆面板组件
 * 显示用户记忆列表，支持筛选、添加、编辑、删除
 */
export const MemoryPanel: React.FC<MemoryPanelProps> = ({
  userId,
  className = '',
}) => {
  const [memories, setMemories] = useState<UserMemory[]>([])
  const [loading, setLoading] = useState(false)
  const [filterType, setFilterType] = useState<MemoryType | ''>('')
  const [showEditor, setShowEditor] = useState(false)
  const [editingMemory, setEditingMemory] = useState<UserMemory | null>(null)

  // 加载记忆
  const loadMemories = useCallback(async () => {
    setLoading(true)
    try {
      const data = await memoryApi.getMemories(
        userId,
        filterType || undefined
      )
      setMemories(data)
    } catch (error) {
      console.error('Failed to load memories:', error)
    } finally {
      setLoading(false)
    }
  }, [userId, filterType])

  useEffect(() => {
    loadMemories()
  }, [loadMemories])

  // 删除记忆
  const handleDelete = useCallback(async (id: string) => {
    if (!confirm('确定要删除这条记忆吗？')) return
    
    try {
      await memoryApi.deleteMemory(id)
      setMemories((prev) => prev.filter((m) => m.id !== id))
    } catch (error) {
      console.error('Failed to delete memory:', error)
    }
  }, [])

  // 编辑记忆
  const handleEdit = useCallback((memory: UserMemory) => {
    setEditingMemory(memory)
    setShowEditor(true)
  }, [])

  // 保存记忆
  const handleSave = useCallback(async (data: Partial<UserMemory>) => {
    if (editingMemory) {
      // 更新
      const updated = await memoryApi.updateMemory(editingMemory.id, data)
      setMemories((prev) =>
        prev.map((m) => (m.id === editingMemory.id ? updated : m))
      )
    } else {
      // 新增
      const newMemory = await memoryApi.addMemory(userId, data)
      setMemories((prev) => [newMemory, ...prev])
    }
    setShowEditor(false)
    setEditingMemory(null)
  }, [editingMemory, userId])

  // 取消编辑
  const handleCancel = useCallback(() => {
    setShowEditor(false)
    setEditingMemory(null)
  }, [])

  return (
    <div className={`flex flex-col h-full bg-white border-l border-gray-200 ${className}`}>
      {/* 标题栏 */}
      <div className="px-4 py-3 border-b border-gray-200">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-gray-700">用户记忆</h2>
          <button
            onClick={() => {
              setEditingMemory(null)
              setShowEditor(!showEditor)
            }}
            className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded"
            title={showEditor ? '关闭编辑器' : '添加记忆'}
          >
            {showEditor ? (
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            ) : (
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
            )}
          </button>
        </div>
      </div>

      {/* 编辑器 */}
      {showEditor && (
        <div className="p-3 border-b border-gray-200">
          <MemoryEditor
            memory={editingMemory}
            onSave={handleSave}
            onCancel={handleCancel}
          />
        </div>
      )}

      {/* 类型筛选 */}
      <div className="px-3 py-2 border-b border-gray-100">
        <div className="flex flex-wrap gap-1">
          {MEMORY_TYPE_OPTIONS.map((option) => (
            <button
              key={option.value}
              onClick={() => setFilterType(option.value as MemoryType | '')}
              className={`px-2 py-1 text-xs rounded-full transition-colors ${
                filterType === option.value
                  ? 'bg-blue-500 text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      {/* 记忆列表 */}
      <div className="flex-1 overflow-y-auto p-3">
        <MemoryList
          memories={memories}
          loading={loading}
          onDelete={handleDelete}
          onEdit={handleEdit}
        />
      </div>

      {/* 底部统计 */}
      <div className="px-4 py-2 border-t border-gray-200 text-xs text-gray-400">
        共 {memories.length} 条记忆
      </div>
    </div>
  )
}

export default MemoryPanel
