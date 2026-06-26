import type { PracticeSession, Question } from '@/utils/types/quiz'

function navClass(q: Question, index: number, currentIndex: number) {
  if (index === currentIndex) return 'border-[#2563EB] bg-white text-[#2563EB]'
  if (q.status === 'correct') return 'bg-[#22C55E] text-white'
  if (q.status === 'wrong') return 'bg-[#FEE2E2] text-[#EF4444]'
  return 'bg-[#F1F5F9] text-[#64748B]'
}

export function QuestionNavigator({
  session,
  onJump,
}: {
  session: PracticeSession
  onJump: (index: number) => void
}) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h2 className="mb-4 text-lg font-bold text-[#0F2A5F]">题目导航</h2>
      <div className="grid grid-cols-5 gap-2.5">
        {session.questions.map((q, index) => (
          <button
            key={q.id}
            type="button"
            onClick={() => onJump(index)}
            className={`relative flex h-[38px] w-[38px] items-center justify-center rounded-[10px] border text-sm font-semibold ${navClass(q, index, session.currentIndex)}`}
          >
            {index + 1}
            {q.isMarked ? <span className="absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full bg-[#F59E0B]" /> : null}
          </button>
        ))}
      </div>
    </section>
  )
}

export function TimerProgressCard({ session }: { session: PracticeSession }) {
  const done = session.completedCount
  const total = session.totalQuestions
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h2 className="mb-4 text-lg font-bold text-[#0F2A5F]">答题进度</h2>
      <div className="space-y-2 text-sm text-[#64748B]">
        <div className="flex justify-between"><span>已完成</span><span className="font-semibold text-[#334155]">{done}</span></div>
        <div className="flex justify-between"><span>未完成</span><span className="font-semibold text-[#334155]">{Math.max(0, total - done)}</span></div>
        <div className="flex justify-between"><span>错误</span><span className="font-semibold text-[#EF4444]">{session.wrongCount}</span></div>
      </div>
    </section>
  )
}

export function KnowledgeTagsCard({ question }: { question: Question | undefined }) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h2 className="mb-3 text-lg font-bold text-[#0F2A5F]">知识点</h2>
      <div className="flex flex-wrap gap-2">
        {question?.knowledgePoints?.length ? (
          question.knowledgePoints.map((kp) => (
            <span key={kp.id} className="rounded-full bg-[#EAF3FF] px-3 py-1 text-xs font-medium text-[#2563EB]">
              {kp.name}
            </span>
          ))
        ) : (
          <span className="text-xs text-[#64748B]">暂无关联知识点</span>
        )}
      </div>
    </section>
  )
}

export function QuizToolsCard({
  onWrongBook,
  onResources,
}: {
  onWrongBook?: () => void
  onResources?: () => void
}) {
  const tools = [
    { label: '错题本', action: onWrongBook },
    { label: '查看资源', action: onResources },
  ]
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h2 className="mb-3 text-lg font-bold text-[#0F2A5F]">答题工具</h2>
      <div className="grid grid-cols-2 gap-2">
        {tools.map((t) => (
          <button
            key={t.label}
            type="button"
            onClick={t.action}
            className="rounded-xl border border-[#D8E4F5] py-2.5 text-xs font-semibold text-[#2563EB] hover:bg-[#F8FBFF]"
          >
            {t.label}
          </button>
        ))}
      </div>
    </section>
  )
}
