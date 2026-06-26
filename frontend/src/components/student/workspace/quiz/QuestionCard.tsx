import { useState } from 'react'
import { BookOpen, ChevronDown, ChevronUp, Loader2 } from 'lucide-react'
import type { Question, SubmitAnswerResult } from '@/utils/types/quiz'
import { difficultyLabel, questionTypeLabel } from '@/utils/quiz/quizMappers'
import OptionItem from './OptionItem'

type Props = {
  question: Question
  index: number
  draftAnswer: string | string[]
  submitting?: boolean
  submitResult?: SubmitAnswerResult | null
  onSelectOption: (value: string) => void
  onFillChange: (value: string) => void
  onSubmit: () => void
  onPrev: () => void
  onNext: () => void
  onMark: () => void
  isFirst: boolean
  isLast: boolean
}

export default function QuestionCard({
  question,
  index,
  draftAnswer,
  submitting,
  submitResult,
  onSelectOption,
  onFillChange,
  onSubmit,
  onPrev,
  onNext,
  onMark,
  isFirst,
  isLast,
}: Props) {
  const [showAnalysis, setShowAnalysis] = useState(false)
  const submitted = question.status === 'correct' || question.status === 'wrong' || Boolean(submitResult)
  const correctAnswer = String(submitResult?.correctAnswer || question.answer || '').toUpperCase()
  const userAnswer = Array.isArray(draftAnswer) ? draftAnswer.join(',') : draftAnswer

  const isChoice =
    question.type === 'single_choice' ||
    question.type === 'multiple_choice' ||
    question.type === 'true_false'

  return (
    <article className="mt-4 min-h-[520px] rounded-[18px] border border-[#E6EEFA] bg-white p-6 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-7 flex flex-wrap gap-2">
        <span className="rounded-full bg-[#EAF3FF] px-3 py-1 text-xs font-semibold text-[#2563EB]">
          {questionTypeLabel(question.type)}
        </span>
        {question.difficulty ? (
          <span className="rounded-full bg-[#F1F5F9] px-3 py-1 text-xs font-medium text-[#64748B]">
            {difficultyLabel(question.difficulty)}
          </span>
        ) : null}
        {question.knowledgePoints?.map((kp) => (
          <span key={kp.id} className="rounded-full bg-[#F8FBFF] px-3 py-1 text-xs text-[#64748B]">
            {kp.name}
          </span>
        ))}
      </div>

      <h2 className="mb-7 text-[22px] font-bold leading-relaxed text-[#0F172A]">
        {index + 1}. {question.content}
      </h2>

      {question.codeContent ? (
        <pre className="mb-6 overflow-x-auto rounded-[14px] bg-[#0F172A] p-4 text-sm text-[#E2E8F0]">
          {question.codeContent}
        </pre>
      ) : null}

      {isChoice && question.options ? (
        <div className="space-y-3.5">
          {question.options.map((opt) => {
            const selected = userAnswer.toUpperCase() === opt.id.toUpperCase() || userAnswer.toUpperCase() === opt.label.toUpperCase()
            const isCorrect = submitted && correctAnswer === opt.id.toUpperCase()
            const isWrong = submitted && selected && !isCorrect
            return (
              <OptionItem
                key={opt.id}
                option={opt}
                selected={selected}
                submitted={submitted}
                isCorrect={isCorrect}
                isWrong={isWrong}
                onSelect={() => onSelectOption(opt.id)}
              />
            )
          })}
        </div>
      ) : (
        <textarea
          className="min-h-[120px] w-full rounded-xl border border-[#D8E4F5] px-4 py-3 text-sm outline-none focus:border-[#93C5FD]"
          value={typeof draftAnswer === 'string' ? draftAnswer : draftAnswer.join('\n')}
          onChange={(e) => onFillChange(e.target.value)}
          placeholder="请输入答案"
          disabled={submitted}
        />
      )}

      <div className="mt-6 rounded-xl border border-[#E6EEFA] bg-[#F8FBFF] px-4 py-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-sm text-[#64748B]">
            <BookOpen className="h-4 w-4" />
            知识点：{question.knowledgePoints?.map((k) => k.name).join('、') || '—'}
          </div>
          <button
            type="button"
            onClick={() => setShowAnalysis((v) => !v)}
            disabled={!submitted}
            className="inline-flex items-center gap-1 text-sm font-medium text-[#2563EB] disabled:opacity-40"
          >
            查看解析
            {showAnalysis ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
          </button>
        </div>
        {showAnalysis && submitted ? (
          <div className="mt-3 rounded-xl border border-[#D8E8FF] bg-white p-4 text-sm leading-relaxed text-[#475569]">
            {correctAnswer ? <p className="mb-2"><strong>正确答案：</strong>{correctAnswer}</p> : null}
            <p>{submitResult?.analysis || question.analysis || '暂无解析'}</p>
          </div>
        ) : null}
      </div>

      <footer className="mt-8 flex flex-wrap items-center justify-between gap-3">
        <button
          type="button"
          onClick={onPrev}
          disabled={isFirst}
          className="h-12 w-32 rounded-xl border border-[#D8E4F5] text-sm font-semibold text-[#64748B] disabled:opacity-40"
        >
          上一题
        </button>
        <button type="button" onClick={onMark} className="h-12 w-40 rounded-xl border border-[#D8E4F5] text-sm font-semibold text-[#2563EB]">
          {question.isMarked ? '取消标记' : '标记本题'}
        </button>
        {!submitted ? (
          <button
            type="button"
            onClick={onSubmit}
            disabled={!userAnswer || submitting}
            className="h-12 min-w-[160px] rounded-xl bg-gradient-to-br from-[#3B82F6] to-[#2563EB] px-6 text-sm font-bold text-white disabled:opacity-50"
          >
            {submitting ? <Loader2 className="mx-auto h-5 w-5 animate-spin" /> : '提交本题'}
          </button>
        ) : (
          <button
            type="button"
            onClick={onNext}
            className="h-12 min-w-[160px] rounded-xl bg-gradient-to-br from-[#3B82F6] to-[#2563EB] px-6 text-sm font-bold text-white"
          >
            {isLast ? '提交练习' : '下一题'}
          </button>
        )}
      </footer>
    </article>
  )
}
