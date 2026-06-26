import { X } from 'lucide-react'
import type { PracticeResult } from '@/utils/types/quiz'

type Props = {
  open: boolean
  result: PracticeResult
  onClose: () => void
  onViewWrong: () => void
  onRetry: () => void
  onBackCatalog: () => void
  onGenerateReview: () => void
  onViewProfile?: () => void
  onContinuePath?: () => void
  onViewGraph?: () => void
}

export default function QuizResultModal({
  open,
  result,
  onClose,
  onViewWrong,
  onRetry,
  onBackCatalog,
  onGenerateReview,
  onViewProfile,
  onContinuePath,
  onViewGraph,
}: Props) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[rgba(15,23,42,0.35)] p-4" onClick={onClose}>
      <div className="w-full max-w-[560px] rounded-[20px] bg-white p-7 shadow-[0_20px_60px_rgba(15,23,42,0.16)]" onClick={(e) => e.stopPropagation()}>
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-xl font-bold text-[#0F2A5F]">本次练习完成</h2>
          <button type="button" onClick={onClose}><X className="h-5 w-5 text-[#64748B]" /></button>
        </div>
        <div className="mb-6 grid grid-cols-2 gap-4 text-center">
          <div className="rounded-xl bg-[#F8FBFF] p-4">
            <p className="text-xs text-[#64748B]">得分</p>
            <p className="text-3xl font-extrabold text-[#2563EB]">{result.score ?? result.accuracyRate}</p>
          </div>
          <div className="rounded-xl bg-[#F8FBFF] p-4">
            <p className="text-xs text-[#64748B]">正确率</p>
            <p className="text-3xl font-extrabold text-[#22C55E]">{result.accuracyRate}%</p>
          </div>
          <div className="rounded-xl border border-[#E6EEFA] p-3 text-sm">
            <span className="text-[#64748B]">正确 </span>
            <strong className="text-[#16A34A]">{result.correctCount}</strong>
          </div>
          <div className="rounded-xl border border-[#E6EEFA] p-3 text-sm">
            <span className="text-[#64748B]">错误 </span>
            <strong className="text-[#EF4444]">{result.wrongCount}</strong>
          </div>
        </div>
        {result.weakKnowledgePoints.length > 0 ? (
          <p className="mb-3 text-sm text-[#64748B]">
            薄弱知识点：{result.weakKnowledgePoints.map((k) => k.name).join('、')}
          </p>
        ) : null}
        {result.aiSuggestion ? (
          <p className="mb-5 rounded-xl bg-[#F8FBFF] p-4 text-sm leading-relaxed text-[#475569]">{result.aiSuggestion}</p>
        ) : null}
        <div className="grid grid-cols-2 gap-3">
          <button type="button" onClick={onViewWrong} className="h-10 rounded-xl border border-[#D8E4F5] text-sm font-semibold text-[#2563EB]">查看错题</button>
          <button type="button" onClick={onRetry} className="h-10 rounded-xl border border-[#D8E4F5] text-sm font-semibold text-[#2563EB]">再练一次</button>
          <button type="button" onClick={onBackCatalog} className="h-10 rounded-xl bg-[#F8FBFF] text-sm font-semibold text-[#2563EB]">返回题库</button>
          <button type="button" onClick={onGenerateReview} className="h-10 rounded-xl bg-gradient-to-br from-[#3B82F6] to-[#2563EB] text-sm font-semibold text-white">生成复习资料</button>
          {onContinuePath ? (
            <button type="button" onClick={onContinuePath} className="h-10 rounded-xl border border-[#D8E4F5] text-sm font-semibold text-[#2563EB]">继续学习路径</button>
          ) : null}
          {onViewGraph ? (
            <button type="button" onClick={onViewGraph} className="h-10 rounded-xl border border-[#D8E4F5] text-sm font-semibold text-[#2563EB]">查看知识图谱</button>
          ) : null}
          {onViewProfile ? (
            <button type="button" onClick={onViewProfile} className="col-span-2 h-10 rounded-xl bg-[#EAF3FF] text-sm font-semibold text-[#2563EB]">查看学习画像</button>
          ) : null}
        </div>
      </div>
    </div>
  )
}
