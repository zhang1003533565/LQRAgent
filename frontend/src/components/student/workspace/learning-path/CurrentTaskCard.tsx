import { CheckCircle, Code2, Play } from 'lucide-react'
import type { LearningPathNodeItem } from '@/utils/types/learning-path-ui'

type Props = {
  node: LearningPathNodeItem | null
  onStartLearning: () => void
  onGenerateNotes: () => void
  onGenerateQuiz: () => void
}

const DIFFICULTY_STYLE: Record<LearningPathNodeItem['difficulty'], string> = {
  简单: 'bg-[#DCFCE7] text-[#16A34A]',
  中等: 'bg-[#FEF3C7] text-[#D97706]',
  困难: 'bg-[#FEE2E2] text-[#EF4444]',
}

export default function CurrentTaskCard({
  node,
  onStartLearning,
  onGenerateNotes,
  onGenerateQuiz,
}: Props) {
  if (!node) {
    return (
      <section className="min-h-[280px] rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
        <div className="flex items-center gap-2">
          <svg viewBox="0 0 24 24" className="h-[18px] w-[18px] text-[#2563EB]" aria-hidden>
            <circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" strokeWidth="2" />
            <circle cx="12" cy="12" r="3" fill="currentColor" />
          </svg>
          <h2 className="text-lg font-bold text-[#0F2A5F]">当前学习任务</h2>
        </div>
        <p className="mt-8 text-center text-sm text-[#64748B]">选择学习节点后，这里会显示任务详情</p>
      </section>
    )
  }

  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-5 flex items-center gap-2">
        <svg viewBox="0 0 24 24" className="h-[18px] w-[18px] text-[#2563EB]" aria-hidden>
          <circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" strokeWidth="2" />
          <circle cx="12" cy="12" r="3" fill="currentColor" />
        </svg>
        <h2 className="text-lg font-bold text-[#0F2A5F]">当前学习任务</h2>
      </div>

      <div className="mb-5 flex items-start gap-4">
        <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-[18px] bg-gradient-to-br from-[#DBEAFE] to-[#EFF6FF]">
          <Code2 className="h-8 w-8 text-[#2563EB]" />
        </div>
        <div className="min-w-0">
          <h3 className="text-xl font-extrabold leading-snug text-[#0F2A5F]">{node.title}</h3>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${DIFFICULTY_STYLE[node.difficulty]}`}>
              {node.difficulty}
            </span>
            <span className="rounded-full border border-[#D8E4F5] bg-white px-2.5 py-1 text-xs font-medium text-[#64748B]">
              预计 {node.durationMinutes} 分钟
            </span>
          </div>
        </div>
      </div>

      <div className="mb-5">
        <h4 className="mb-3 text-[15px] font-bold text-[#0F2A5F]">学习目标</h4>
        <ul className="space-y-3">
          {node.objectives.map((item) => (
            <li key={item} className="flex items-start gap-2 text-sm text-[#475569]">
              <CheckCircle className="mt-0.5 h-4 w-4 shrink-0 text-[#22C55E]" />
              <span>{item}</span>
            </li>
          ))}
        </ul>
      </div>

      <div className="space-y-3">
        <button
          type="button"
          onClick={onStartLearning}
          className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-br from-[#3B82F6] to-[#2563EB] text-base font-bold text-white shadow-[0_8px_20px_rgba(37,99,235,0.18)] transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_12px_28px_rgba(37,99,235,0.24)]"
        >
          <Play className="h-4 w-4 fill-current" />
          开始学习
        </button>
        <div className="grid grid-cols-2 gap-3">
          <button
            type="button"
            onClick={onGenerateNotes}
            className="inline-flex h-[42px] items-center justify-center rounded-[10px] border border-[#BFD7FF] bg-white text-sm font-semibold text-[#2563EB] transition-all duration-200 hover:-translate-y-0.5 hover:bg-[#F8FBFF]"
          >
            生成讲义
          </button>
          <button
            type="button"
            onClick={onGenerateQuiz}
            className="inline-flex h-[42px] items-center justify-center rounded-[10px] border border-[#BFD7FF] bg-white text-sm font-semibold text-[#2563EB] transition-all duration-200 hover:-translate-y-0.5 hover:bg-[#F8FBFF]"
          >
            生成练习
          </button>
        </div>
      </div>
    </section>
  )
}
