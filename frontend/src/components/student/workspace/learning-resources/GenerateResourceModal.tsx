import { Loader2, Sparkles, X } from 'lucide-react'
import { GENERATE_DIFFICULTIES, GENERATE_MATERIAL_TYPES } from '@/utils/types/learningResources'
import type { ResourceType } from '@/utils/types/media-resource'

type Props = {
  open: boolean
  knowledgePoint: string
  selectedTypes: ResourceType[]
  difficulty: string
  prompt: string
  generating?: boolean
  onClose: () => void
  onKnowledgeChange: (value: string) => void
  onToggleType: (type: ResourceType) => void
  onDifficultyChange: (value: string) => void
  onPromptChange: (value: string) => void
  onSubmit: () => void
}

export default function GenerateResourceModal({
  open,
  knowledgePoint,
  selectedTypes,
  difficulty,
  prompt,
  generating,
  onClose,
  onKnowledgeChange,
  onToggleType,
  onDifficultyChange,
  onPromptChange,
  onSubmit,
}: Props) {
  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-[rgba(15,23,42,0.35)] p-4 backdrop-blur-[2px]"
      role="dialog"
      aria-modal="true"
      aria-label="AI 生成学习资料"
      onClick={onClose}
    >
      <div
        className="w-full max-w-[520px] rounded-[18px] border border-[#E6EEFA] bg-white p-6 shadow-[0_20px_60px_rgba(15,23,42,0.16)]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-xl font-bold text-[#0F2A5F]">AI 生成学习资料</h2>
          <button
            type="button"
            aria-label="关闭"
            onClick={onClose}
            className="rounded-lg p-1.5 text-[#64748B] transition-colors hover:bg-[#F8FBFF]"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="space-y-4">
          <div>
            <label className="mb-1.5 block text-sm font-semibold text-[#334155]">知识点选择</label>
            <input
              className="h-11 w-full rounded-xl border border-[#D8E4F5] px-3 text-sm text-[#334155] outline-none focus:border-[#93C5FD]"
              value={knowledgePoint}
              onChange={(e) => onKnowledgeChange(e.target.value)}
              placeholder="Python 简介与环境搭建"
            />
          </div>

          <div>
            <label className="mb-2 block text-sm font-semibold text-[#334155]">资料类型</label>
            <div className="flex flex-wrap gap-2">
              {GENERATE_MATERIAL_TYPES.map((type) => {
                const active = selectedTypes.includes(type.id)
                return (
                  <button
                    key={type.id}
                    type="button"
                    onClick={() => onToggleType(type.id)}
                    className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
                      active
                        ? 'bg-[#EAF3FF] text-[#2563EB]'
                        : 'bg-[#F1F5F9] text-[#64748B] hover:bg-[#EAF3FF]'
                    }`}
                  >
                    {type.label}
                  </button>
                )
              })}
            </div>
          </div>

          <div>
            <label className="mb-2 block text-sm font-semibold text-[#334155]">难度</label>
            <div className="flex flex-wrap gap-2">
              {GENERATE_DIFFICULTIES.map((level) => (
                <button
                  key={level}
                  type="button"
                  onClick={() => onDifficultyChange(level)}
                  className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
                    difficulty === level
                      ? 'bg-[#EAF3FF] text-[#2563EB]'
                      : 'bg-[#F1F5F9] text-[#64748B] hover:bg-[#EAF3FF]'
                  }`}
                >
                  {level}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-semibold text-[#334155]">生成要求</label>
            <textarea
              className="min-h-[96px] w-full resize-none rounded-xl border border-[#D8E4F5] px-3 py-2.5 text-sm text-[#334155] outline-none focus:border-[#93C5FD]"
              value={prompt}
              onChange={(e) => onPromptChange(e.target.value)}
              placeholder="例如：帮我生成适合初学者的 Python 环境搭建讲义，包含步骤和常见问题"
            />
          </div>
        </div>

        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="inline-flex h-10 items-center rounded-[10px] border border-[#D8E4F5] px-4 text-sm font-semibold text-[#64748B]"
          >
            取消
          </button>
          <button
            type="button"
            onClick={onSubmit}
            disabled={generating || selectedTypes.length === 0}
            className="inline-flex h-10 items-center gap-2 rounded-[10px] bg-gradient-to-br from-[#3B82F6] to-[#2563EB] px-4 text-sm font-semibold text-white shadow-[0_8px_20px_rgba(37,99,235,0.18)] disabled:opacity-50"
          >
            {generating ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
            {generating ? '生成中...' : '立即生成'}
          </button>
        </div>
      </div>
    </div>
  )
}
