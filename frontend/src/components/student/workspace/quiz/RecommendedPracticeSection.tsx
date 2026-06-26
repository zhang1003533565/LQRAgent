import { Loader2, Sparkles } from 'lucide-react'
import type { RecommendedPractice, RecommendReasonType } from '@/utils/types/quiz'
import { difficultyLabel } from '@/utils/quiz/quizMappers'
import { EmptyState, ErrorState, LoadingSkeleton } from './shared'

const REASON_STYLE: Record<RecommendReasonType, string> = {
  learning_path: 'bg-[#DBEAFE] text-[#2563EB]',
  weak_point: 'bg-[#FFEDD5] text-[#EA580C]',
  wrong_questions: 'bg-[#FEE2E2] text-[#EF4444]',
  review: 'bg-[#DCFCE7] text-[#16A34A]',
  new_topic: 'bg-[#F5F3FF] text-[#8B5CF6]',
}

function RecommendedPracticeCard({
  item,
  loading,
  onStart,
}: {
  item: RecommendedPractice
  loading?: boolean
  onStart: () => void
}) {
  return (
    <article className="flex h-[110px] items-center gap-4 rounded-2xl border border-[#E6EEFA] bg-white p-4 transition-all hover:-translate-y-0.5 hover:shadow-[0_12px_32px_rgba(37,99,235,0.10)]">
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-[#EAF3FF]">
        <Sparkles className="h-6 w-6 text-[#2563EB]" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="mb-1 flex items-center gap-2">
          <h3 className="truncate text-sm font-bold text-[#0F2A5F]">{item.title}</h3>
          <span className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium ${REASON_STYLE[item.reasonType]}`}>
            {item.reason}
          </span>
        </div>
        <p className="truncate text-xs text-[#64748B]">{item.description || `${item.questionCount} 题 · ${difficultyLabel(item.difficulty)}`}</p>
      </div>
      <button
        type="button"
        onClick={onStart}
        disabled={loading}
        className="shrink-0 rounded-lg bg-[#2563EB] px-3 py-2 text-xs font-semibold text-white disabled:opacity-50"
      >
        {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : item.startPayload.sessionId ? '继续练习' : '开始练习'}
      </button>
    </article>
  )
}

export default function RecommendedPracticeSection({
  items,
  loading,
  error,
  startingId,
  onRetry,
  onStart,
}: {
  items: RecommendedPractice[]
  loading?: boolean
  error?: string | null
  startingId?: string | null
  onRetry?: () => void
  onStart: (item: RecommendedPractice) => void
}) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-4">
        <h2 className="text-lg font-bold text-[#0F2A5F]">AI 推荐练习</h2>
        <p className="text-xs text-[#64748B]">根据你的学习路径、薄弱知识点和错题情况动态推荐</p>
      </div>
      {loading ? <LoadingSkeleton rows={2} /> : null}
      {error ? <ErrorState message={error} onRetry={onRetry} /> : null}
      {!loading && !error && items.length === 0 ? (
        <EmptyState title="暂无推荐练习" description="可先从题库目录开始练习" />
      ) : null}
      {!loading && !error && items.length > 0 ? (
        <div className="grid gap-3 lg:grid-cols-3">
          {items.map((item) => (
            <RecommendedPracticeCard
              key={item.id}
              item={item}
              loading={startingId === item.id}
              onStart={() => onStart(item)}
            />
          ))}
        </div>
      ) : null}
    </section>
  )
}
