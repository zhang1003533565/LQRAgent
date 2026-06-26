import { ArrowLeft, Bookmark, Loader2 } from 'lucide-react'
import type { PracticeSession } from '@/utils/types/quiz'

type Props = {
  session: PracticeSession
  onBack: () => void
  onToggleFavorite: () => void
  onToggleMark: () => void
  favoriteLoading?: boolean
}

export default function QuizProgressHeader({
  session,
  onBack,
  onToggleFavorite,
  onToggleMark,
  favoriteLoading,
}: Props) {
  const current = session.questions[session.currentIndex]
  const pct =
    session.totalQuestions > 0
      ? Math.round(((session.currentIndex + 1) / session.totalQuestions) * 100)
      : 0

  return (
    <header className="flex h-[72px] items-center justify-between rounded-[18px] border border-[#E6EEFA] bg-white px-6 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="flex min-w-0 items-center gap-3">
        <button type="button" onClick={onBack} className="rounded-lg p-2 hover:bg-[#F8FBFF]">
          <ArrowLeft className="h-5 w-5 text-[#64748B]" />
        </button>
        <h1 className="truncate text-lg font-bold text-[#0F2A5F]">{session.title}</h1>
      </div>
      <div className="hidden flex-1 px-8 md:block">
        <div className="mb-1 flex justify-between text-xs text-[#64748B]">
          <span>
            第 {session.currentIndex + 1} / {session.totalQuestions} 题
          </span>
          <span>{pct}%</span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-[#E8EEF7]">
          <div className="h-full rounded-full bg-[#2563EB]" style={{ width: `${pct}%` }} />
        </div>
      </div>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={onToggleFavorite}
          disabled={favoriteLoading}
          className="rounded-lg border border-[#D8E4F5] p-2"
        >
          <Bookmark className={`h-4 w-4 ${current?.isFavorite ? 'fill-[#F59E0B] text-[#F59E0B]' : 'text-[#64748B]'}`} />
        </button>
        <button type="button" onClick={onToggleMark} className="rounded-lg border border-[#D8E4F5] px-3 py-2 text-xs font-semibold text-[#2563EB]">
          {current?.isMarked ? '取消标记' : '标记本题'}
        </button>
      </div>
    </header>
  )
}
