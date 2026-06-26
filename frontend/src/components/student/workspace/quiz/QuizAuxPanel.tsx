import { useEffect, useState } from 'react'
import { ChevronRight, Lightbulb } from 'lucide-react'
import type { PracticeRecord, QuizOverview } from '@/utils/types/quiz'
import { getQuestionTypeDistribution, getRecentPracticeRecords } from '@/services/quizService'
import { LoadingSkeleton } from './shared'

function ProgressRing({ percent, label }: { percent: number; label: string }) {
  const radius = 46
  const c = 2 * Math.PI * radius
  const offset = c - (percent / 100) * c
  return (
    <div className="relative mx-auto h-[110px] w-[110px]">
      <svg className="h-full w-full -rotate-90" viewBox="0 0 110 110">
        <circle cx="55" cy="55" r={radius} fill="none" stroke="#E8EEF7" strokeWidth="10" />
        <circle cx="55" cy="55" r={radius} fill="none" stroke="#2563EB" strokeWidth="10" strokeDasharray={c} strokeDashoffset={offset} strokeLinecap="round" />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
        <span className="text-xl font-extrabold text-[#0F2A5F]">{label}</span>
        <span className="text-[11px] text-[#64748B]">完成进度</span>
      </div>
    </div>
  )
}

export default function QuizAuxPanel({
  overview,
  overviewLoading,
  suggestion,
  onContinueRecord,
  onViewPath,
}: {
  overview: QuizOverview | null
  overviewLoading?: boolean
  suggestion?: string | null
  onContinueRecord?: (sessionId: string) => void
  onViewPath?: () => void
}) {
  const [distribution, setDistribution] = useState<Array<{ label: string; count: number; percent: number }>>([])
  const [records, setRecords] = useState<PracticeRecord[]>([])

  useEffect(() => {
    void getQuestionTypeDistribution().then(setDistribution)
    void getRecentPracticeRecords({ limit: 3 }).then(setRecords)
  }, [])

  const progressPercent =
    overview && overview.totalQuestions > 0
      ? Math.round((overview.completedQuestions / overview.totalQuestions) * 100)
      : 0

  return (
    <aside className="hidden w-[280px] shrink-0 flex-col gap-4 overflow-y-auto pr-5 pt-6 xl:flex xl:w-[320px]">
      <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
        <h2 className="mb-4 text-lg font-bold text-[#0F2A5F]">学习进度</h2>
        {overviewLoading ? (
          <LoadingSkeleton rows={2} />
        ) : overview ? (
          <>
            <ProgressRing
              percent={progressPercent}
              label={`${overview.completedQuestions}/${overview.totalQuestions}`}
            />
            <div className="mt-4 space-y-2 text-sm text-[#64748B]">
              <div className="flex justify-between">
                <span>正确率</span>
                <span className="font-semibold text-[#334155]">{overview.accuracyRate}%</span>
              </div>
              <div className="flex justify-between">
                <span>今日目标</span>
                <span className="font-semibold text-[#334155]">
                  {overview.todayCompletedCount ?? 0}/{overview.todayTargetCount ?? 0}
                </span>
              </div>
            </div>
          </>
        ) : null}
      </section>

      <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
        <h2 className="mb-4 text-lg font-bold text-[#0F2A5F]">题型分布</h2>
        {distribution.length === 0 ? (
          <p className="text-xs text-[#64748B]">暂无统计数据</p>
        ) : (
          <ul className="space-y-3">
            {distribution.map((item) => (
              <li key={item.label}>
                <div className="mb-1 flex justify-between text-xs text-[#64748B]">
                  <span>{item.label}</span>
                  <span>{item.count} · {item.percent}%</span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-[#E8EEF7]">
                  <div className="h-full rounded-full bg-[#2563EB]" style={{ width: `${item.percent}%` }} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
        <h2 className="mb-4 text-lg font-bold text-[#0F2A5F]">最近练习</h2>
        {records.length === 0 ? (
          <p className="text-xs text-[#64748B]">还没有练习记录</p>
        ) : (
          <ul className="space-y-2">
            {records.map((r) => (
              <li key={r.id}>
                <button
                  type="button"
                  onClick={() => r.canContinue && onContinueRecord?.(r.sessionId)}
                  className="w-full rounded-lg px-1 py-1 text-left hover:bg-[#F8FBFF]"
                >
                  <p className="truncate text-sm font-medium text-[#334155]">{r.title}</p>
                  <p className="text-xs text-[#64748B]">
                    {r.accuracyRate != null ? `${r.accuracyRate}% · ` : ''}
                    {r.canContinue ? '可继续' : '已完成'}
                  </p>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
        <div className="mb-3 flex items-center gap-2">
          <Lightbulb className="h-4 w-4 text-[#F59E0B]" />
          <h2 className="text-lg font-bold text-[#0F2A5F]">学习建议</h2>
        </div>
        <p className="text-sm leading-relaxed text-[#64748B]">
          {suggestion || '完成推荐练习或继续当前学习路径中的薄弱小节。'}
        </p>
        <button
          type="button"
          onClick={onViewPath}
          className="mt-4 inline-flex h-9 w-full items-center justify-center gap-1 rounded-[10px] bg-[#F8FBFF] text-sm font-medium text-[#2563EB]"
        >
          查看学习路径
          <ChevronRight className="h-4 w-4" />
        </button>
      </section>
    </aside>
  )
}
