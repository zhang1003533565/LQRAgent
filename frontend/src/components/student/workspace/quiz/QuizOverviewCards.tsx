import { CheckSquare, Clock, FolderCheck, Target } from 'lucide-react'
import type { QuizOverview } from '@/utils/types/quiz'
import { ErrorState, LoadingSkeleton } from './shared'

type Props = {
  overview: QuizOverview | null
  loading?: boolean
  error?: string | null
  onRetry?: () => void
}

export default function QuizOverviewCards({ overview, loading, error, onRetry }: Props) {
  if (loading) {
    return (
      <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
        <LoadingSkeleton rows={1} />
      </section>
    )
  }

  if (error) {
    return (
      <section className="rounded-[18px] border border-[#E6EEFA] bg-white shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
        <ErrorState message={error} onRetry={onRetry} />
      </section>
    )
  }

  if (!overview) return null

  const stats = [
    { label: '总题目数', value: overview.totalQuestions, icon: FolderCheck, bg: 'bg-[#EAF3FF]', color: 'text-[#2563EB]' },
    { label: '已完成', value: overview.completedQuestions, icon: CheckSquare, bg: 'bg-[#DCFCE7]', color: 'text-[#16A34A]' },
    { label: '正确率', value: `${overview.accuracyRate}%`, icon: Target, bg: 'bg-[#EFF6FF]', color: 'text-[#3B82F6]' },
    { label: '累计练习时长', value: `${overview.totalPracticeDurationMinutes} 分钟`, icon: Clock, bg: 'bg-[#FFF7ED]', color: 'text-[#F59E0B]' },
  ]

  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white px-5 py-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="grid grid-cols-2 gap-0 md:grid-cols-4">
        {stats.map((item, index) => {
          const Icon = item.icon
          return (
            <div
              key={item.label}
              className={`flex items-center gap-3 px-2 py-2 ${index > 0 ? 'md:border-l md:border-[#EEF3FA]' : ''}`}
            >
              <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${item.bg}`}>
                <Icon className={`h-5 w-5 ${item.color}`} />
              </div>
              <div>
                <p className="text-sm font-semibold text-[#334155]">{item.label}</p>
                <p className="text-lg font-extrabold text-[#0F2A5F]">{item.value}</p>
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}
