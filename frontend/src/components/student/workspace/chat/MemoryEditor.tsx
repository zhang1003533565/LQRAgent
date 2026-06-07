import React, { useState, useEffect } from 'react'
import type { UserMemory, MemoryType } from '@/utils/types/memory'

interface MemoryEditorProps {
  memory?: UserMemory | null
  onSave: (data: Partial<UserMemory>) => Promise<void>
  onCancel: () => void
}

const MEMORY_TYPES: { value: MemoryType; label: string }[] = [
  { value: 'PREFERENCE', label: '偏好' },
  { value: 'LEARNING_PROGRESS', label: '学习进度' },
  { value: 'TOPIC_INTEREST', label: '兴趣' },
  { value: 'INTERACTION_STYLE', label: '交互风格' },
  { value: 'KNOWLEDGE_STATE', label: '知识状态' },
]

/**
 * 记忆编辑器组件
 * 用于创建或编辑记忆
 */
export const MemoryEditor: React.FC<MemoryEditorProps> = ({
  memory,
  onSave,
  onCancel,
}) => {
  const [content, setContent] = useState('')
  const [memoryType, setMemoryType] = useState<MemoryType>('PREFERENCE')
  const [source, setSource] = useState('user_setting')
  const [importance, setImportance] = useState(3)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (memory) {
      setContent(memory.content)
      setMemoryType(memory.memoryType)
      setSource(memory.source || 'user_setting')
      setImportance(memory.importance)
    }
  }, [memory])

  const handleSave = async () => {
    if (!content.trim()) return

    setSaving(true)
    try {
      await onSave({
        content: content.trim(),
        memoryType,
        source,
        importance,
      })
      onCancel()
    } catch (error) {
      console.error('Failed to save memory:', error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="p-4 bg-white rounded-lg border border-gray-200">
      <h4 className="text-sm font-medium text-gray-700 mb-3">
        {memory ? '编辑记忆' : '添加记忆'}
      </h4>

      {/* 内容输入 */}
      <div className="mb-3">
        <label className="block text-xs text-gray-500 mb-1">内容</label>
        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="输入记忆内容..."
          rows={3}
          className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg
            focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
        />
      </div>

      {/* 类型选择 */}
      <div className="mb-3">
        <label className="block text-xs text-gray-500 mb-1">类型</label>
        <select
          value={memoryType}
          onChange={(e) => setMemoryType(e.target.value as MemoryType)}
          className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg
            focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {MEMORY_TYPES.map((type) => (
            <option key={type.value} value={type.value}>
              {type.label}
            </option>
          ))}
        </select>
      </div>

      {/* 重要性滑块 */}
      <div className="mb-4">
        <label className="block text-xs text-gray-500 mb-1">
          重要性: {importance}
        </label>
        <input
          type="range"
          min={1}
          max={5}
          value={importance}
          onChange={(e) => setImportance(Number(e.target.value))}
          className="w-full"
        />
        <div className="flex justify-between text-xs text-gray-400 mt-1">
          <span>低</span>
          <span>高</span>
        </div>
      </div>

      {/* 操作按钮 */}
      <div className="flex gap-2">
        <button
          onClick={onCancel}
          disabled={saving}
          className="flex-1 px-3 py-2 text-sm text-gray-600 bg-gray-100 rounded-lg
            hover:bg-gray-200 transition-colors"
        >
          取消
        </button>
        <button
          onClick={handleSave}
          disabled={saving || !content.trim()}
          className="flex-1 px-3 py-2 text-sm text-white bg-blue-500 rounded-lg
            hover:bg-blue-600 disabled:opacity-50 transition-colors"
        >
          {saving ? '保存中...' : '保存'}
        </button>
      </div>
    </div>
  )
}

export default MemoryEditor
