import { useState } from 'react'
import { ChevronDown, ChevronUp, Loader2, Lock } from 'lucide-react'
import type { QuizChapter, QuizSection, SectionStatus } from '@/utils/types/quiz'
import { difficultyLabel, questionTypeLabel } from '@/utils/quiz/quizMappers'
import { EmptyState, LoadingSkeleton } from './shared'

const STATUS_LABEL: Record<SectionStatus, string> = {
  completed: '已完成',
  in_progress: '进行中',
  not_started: '未开始',
  recommended: '建议练习',
  locked: '锁定',
}

const STATUS_STYLE: Record<SectionStatus, string> = {
  completed: 'bg-[#DCFCE7] text-[#16A34A]',
  in_progress: 'bg-[#DBEAFE] text-[#2563EB]',
  not_started: 'bg-[#F1F5F9] text-[#64748B]',
  recommended: 'bg-[#FEF3C7] text-[#D97706]',
  locked: 'bg-[#F1F5F9] text-[#94A3B8]',
}

const BTN_LABEL: Record<SectionStatus, string> = {
  completed: '再练一次',
  in_progress: '继续练习',
  not_started: '开始练习',
  recommended: '建议练习',
  locked: '未解锁',
}

function SectionRow({
  section,
  starting,
  onStart,
}: {
  section: QuizSection
  starting?: boolean
  onStart: (section: QuizSection) => void
}) {
  const locked = section.status === 'locked'
  return (
    <div className="flex min-h-[58px] items-center gap-3 rounded-[10px] px-3 py-2 transition-colors hover:bg-[#F8FBFF]">
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[#F1F5F9] text-xs font-bold text-[#64748B]">
        {section.order}
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-bold text-[#0F2A5F]">{section.title}</p>
        <p className="truncate text-xs text-[#64748B]">{section.description}</p>
      </div>
      <span className="hidden text-xs text-[#64748B] sm:inline">{section.questionCount} 题</span>
      <span className="hidden text-xs text-[#64748B] md:inline">
        {section.accuracyRate != null ? `${section.accuracyRate}%` : '—'}
      </span>
      <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${STATUS_STYLE[section.status]}`}>
        {STATUS_LABEL[section.status]}
      </span>
      <button
        type="button"
        disabled={locked || starting}
        onClick={() => onStart(section)}
        className="shrink-0 rounded-lg border border-[#BFD7FF] px-3 py-1.5 text-xs font-semibold text-[#2563EB] disabled:cursor-not-allowed disabled:opacity-50"
      >
        {starting ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : BTN_LABEL[section.status]}
      </button>
    </div>
  )
}

export default function QuizChapterAccordion({
  chapters,
  loading,
  defaultExpandedId,
  startingSectionId,
  onStartSection,
}: {
  chapters: QuizChapter[]
  loading?: boolean
  defaultExpandedId?: string | null
  startingSectionId?: string | null
  onStartSection: (section: QuizSection) => void
}) {
  const [expandedId, setExpandedId] = useState<string | null>(
    defaultExpandedId ?? chapters[0]?.id ?? null,
  )

  if (loading) return <LoadingSkeleton rows={4} />
  if (chapters.length === 0) {
    return <EmptyState title="暂无题库目录" description="请稍后刷新或从学习路径生成练习" />
  }

  return (
    <div className="space-y-3">
      {chapters.map((chapter) => {
        const expanded = expandedId === chapter.id
        return (
          <div
            key={chapter.id}
            className={`overflow-hidden rounded-[14px] border ${expanded ? 'border-[#BFD7FF]' : 'border-[#E6EEFA]'} bg-white`}
          >
            <button
              type="button"
              onClick={() => setExpandedId(expanded ? null : chapter.id)}
              className="flex h-[68px] w-full items-center justify-between px-4"
            >
              <div className="flex items-center gap-3 text-left">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#EAF3FF] text-sm font-bold text-[#2563EB]">
                  {chapter.isLocked ? <Lock className="h-4 w-4" /> : chapter.order}
                </div>
                <div>
                  <p className="text-sm font-bold text-[#0F2A5F]">{chapter.title}</p>
                  <p className="text-xs text-[#64748B]">{chapter.description}</p>
                </div>
              </div>
              <div className="flex items-center gap-3 text-xs font-semibold text-[#334155]">
                <span>{chapter.completedQuestions}/{chapter.totalQuestions}</span>
                {chapter.accuracyRate != null ? <span>{chapter.accuracyRate}%</span> : null}
                {expanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
              </div>
            </button>
            {expanded ? (
              <div className="space-y-1 border-t border-[#EEF3FA] px-2 pb-2 pt-1">
                {chapter.sections.map((section) => (
                  <SectionRow
                    key={section.id}
                    section={section}
                    starting={startingSectionId === section.id}
                    onStart={onStartSection}
                  />
                ))}
              </div>
            ) : null}
          </div>
        )
      })}
    </div>
  )
}

export function QuestionListTab({
  questions,
  loading,
  emptyTitle,
  onPractice,
}: {
  questions: Array<{ id: string; title: string; type: string; difficulty?: string }>
  loading?: boolean
  emptyTitle: string
  onPractice?: (id: string) => void
}) {
  if (loading) return <LoadingSkeleton />
  if (questions.length === 0) return <EmptyState title={emptyTitle} />
  return (
    <div className="space-y-2">
      {questions.map((q) => (
        <div key={q.id} className="flex items-center justify-between rounded-xl border border-[#E6EEFA] px-4 py-3 hover:bg-[#F8FBFF]">
          <div>
            <p className="text-sm font-semibold text-[#0F2A5F]">{q.title}</p>
            <p className="truncate text-xs text-[#64748B]">{q.type ? questionTypeLabel(q.type as import('@/utils/types/quiz').QuestionType) : ''}</p>
          </div>
          {onPractice ? (
            <button type="button" onClick={() => onPractice(q.id)} className="text-xs font-semibold text-[#2563EB]">
              练习
            </button>
          ) : null}
        </div>
      ))}
    </div>
  )
}
