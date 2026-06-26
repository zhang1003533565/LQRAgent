import { useEffect, useState } from 'react'
import type { LearningPathOption, UploadedFile } from '@/utils/types/upload'

type Props = {
  open: boolean
  file?: UploadedFile | null
  pathOptions: LearningPathOption[]
  kpOptions: Array<{ id: string; name: string }>
  loading?: boolean
  onClose: () => void
  onSave: (payload: {
    learningPathId?: string
    knowledgePointIds?: string[]
    tags?: string[]
  }) => void
}

export default function FileRelationModal({
  open,
  file,
  pathOptions,
  kpOptions,
  loading,
  onClose,
  onSave,
}: Props) {
  const [learningPathId, setLearningPathId] = useState('')
  const [knowledgePointIds, setKnowledgePointIds] = useState<string[]>([])
  const [tags, setTags] = useState('')

  useEffect(() => {
    if (!file) return
    setLearningPathId(file.relatedLearningPathId || '')
    setKnowledgePointIds(file.relatedKnowledgePointIds || [])
    setTags((file.tags || []).join(', '))
  }, [file])

  if (!open || !file) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4" onClick={onClose}>
      <div
        className="w-full max-w-[520px] rounded-2xl border border-[#E6EEFA] bg-white p-6 shadow-[0_20px_60px_rgba(15,23,42,0.16)]"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-xl font-bold text-[#0F2A5F]">关联路径与知识点</h2>
        <p className="mt-1 truncate text-sm text-[#64748B]">{file.name}</p>

        <div className="mt-5 space-y-4">
          <div>
            <label className="mb-2 block text-xs font-semibold text-[#64748B]">学习路径</label>
            <select
              value={learningPathId}
              onChange={(e) => setLearningPathId(e.target.value)}
              className="h-10 w-full rounded-xl border border-[#E6EEFA] px-3 text-sm"
            >
              <option value="">不关联</option>
              {pathOptions.map((p) => (
                <option key={p.id} value={p.id}>{p.title}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="mb-2 block text-xs font-semibold text-[#64748B]">知识点（多选）</label>
            <select
              multiple
              value={knowledgePointIds}
              onChange={(e) =>
                setKnowledgePointIds(Array.from(e.target.selectedOptions).map((o) => o.value))
              }
              className="h-24 w-full rounded-xl border border-[#E6EEFA] px-3 text-sm"
            >
              {kpOptions.map((kp) => (
                <option key={kp.id} value={kp.id}>{kp.name}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="mb-2 block text-xs font-semibold text-[#64748B]">标签</label>
            <input
              value={tags}
              onChange={(e) => setTags(e.target.value)}
              placeholder="用逗号分隔多个标签"
              className="h-10 w-full rounded-xl border border-[#E6EEFA] px-3 text-sm"
            />
          </div>
        </div>

        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-[10px] border border-[#D8E4F5] px-4 py-2 text-sm font-semibold text-[#64748B]"
          >
            取消
          </button>
          <button
            type="button"
            disabled={loading}
            onClick={() =>
              onSave({
                learningPathId: learningPathId || undefined,
                knowledgePointIds,
                tags: tags
                  .split(',')
                  .map((t) => t.trim())
                  .filter(Boolean),
              })
            }
            className="rounded-[10px] bg-[#2563EB] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
          >
            保存
          </button>
        </div>
      </div>
    </div>
  )
}
