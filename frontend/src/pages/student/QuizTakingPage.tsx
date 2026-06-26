import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  ErrorState,
  KnowledgeTagsCard,
  LoadingSkeleton,
  QuestionCard,
  QuestionNavigator,
  QuizProgressHeader,
  QuizResultModal,
  QuizToolsCard,
  TimerProgressCard,
} from '@/components/student/workspace/quiz'
import { createPracticeSession } from '@/services/quizService'
import { usePracticeSession } from '@/utils/hooks/usePracticeSession'
import { navigateToWorkspace } from '@/utils/navigation/workspaceNav'
import type { SubmitAnswerResult } from '@/utils/types/quiz'

export default function QuizTakingPage() {
  const { sessionId } = useParams<{ sessionId: string }>()
  const navigate = useNavigate()
  const {
    session,
    loading,
    error,
    submitting,
    result,
    submitAnswer,
    submitSession,
    goToIndex,
    toggleFavorite,
    toggleMark,
    refresh,
  } = usePracticeSession(sessionId)

  const [draftAnswers, setDraftAnswers] = useState<Record<string, string | string[]>>({})
  const [lastSubmit, setLastSubmit] = useState<Record<string, SubmitAnswerResult | null>>({})
  const [showResult, setShowResult] = useState(false)

  const currentQuestion = session?.questions[session.currentIndex]
  const draftAnswer = currentQuestion ? draftAnswers[currentQuestion.id] ?? currentQuestion.userAnswer ?? '' : ''
  const submitResult = currentQuestion ? lastSubmit[currentQuestion.id] : null

  useEffect(() => {
    if (result) setShowResult(true)
  }, [result])

  const handleSelectOption = useCallback(
    (value: string) => {
      if (!currentQuestion) return
      setDraftAnswers((prev) => ({ ...prev, [currentQuestion.id]: value }))
    },
    [currentQuestion],
  )

  const handleFillChange = useCallback(
    (value: string) => {
      if (!currentQuestion) return
      setDraftAnswers((prev) => ({ ...prev, [currentQuestion.id]: value }))
    },
    [currentQuestion],
  )

  const handleSubmitCurrent = useCallback(async () => {
    if (!currentQuestion || !sessionId) return
    const answer = draftAnswers[currentQuestion.id] ?? draftAnswer
    if (!answer) return
    const res = await submitAnswer(currentQuestion.id, answer)
    if (res) setLastSubmit((prev) => ({ ...prev, [currentQuestion.id]: res }))
  }, [currentQuestion, sessionId, draftAnswers, draftAnswer, submitAnswer])

  const handleNext = useCallback(async () => {
    if (!session) return
    const isLast = session.currentIndex >= session.totalQuestions - 1
    if (isLast) {
      await submitSession()
      return
    }
    goToIndex(session.currentIndex + 1)
  }, [session, goToIndex, submitSession])

  const handlePrev = useCallback(() => {
    if (!session || session.currentIndex <= 0) return
    goToIndex(session.currentIndex - 1)
  }, [session, goToIndex])

  const handleBack = useCallback(() => {
    if (session?.status === 'in_progress') {
      const ok = window.confirm('练习尚未提交，确定返回题库吗？进度已保存在本地。')
      if (!ok) return
    }
    navigate('/workspace/quiz')
  }, [navigate, session?.status])

  const handleRetry = useCallback(async () => {
    if (!session) return
    setShowResult(false)
    const next = await createPracticeSession({
      mode: session.mode,
      sectionId: session.sectionId,
      questionIds: session.questions.map((q) => Number(q.id)).filter((id) => id > 0),
      learningPathNodeId: session.kpId,
      title: session.title,
    })
    navigate(`/workspace/quiz/session/${next.id}`, { replace: true })
  }, [session, navigate])

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center bg-[#F6F9FE] p-8">
        <LoadingSkeleton rows={4} />
      </div>
    )
  }

  if (error || !session) {
    return (
      <div className="flex h-full items-center justify-center bg-[#F6F9FE] p-8">
        <ErrorState message={error || '练习会话不存在'} onRetry={refresh} />
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-0 overflow-hidden bg-[#F6F9FE] font-sans">
      <div className="flex min-w-0 flex-1 flex-col overflow-y-auto px-5 pb-8 pt-6">
        <QuizProgressHeader
          session={session}
          onBack={handleBack}
          onToggleFavorite={() => currentQuestion && toggleFavorite(currentQuestion.id, !currentQuestion.isFavorite)}
          onToggleMark={() => currentQuestion && toggleMark(currentQuestion.id, !currentQuestion.isMarked)}
        />

        {currentQuestion ? (
          <QuestionCard
            question={currentQuestion}
            index={session.currentIndex}
            draftAnswer={draftAnswer}
            submitting={submitting}
            submitResult={submitResult}
            onSelectOption={handleSelectOption}
            onFillChange={handleFillChange}
            onSubmit={handleSubmitCurrent}
            onPrev={handlePrev}
            onNext={handleNext}
            onMark={() => currentQuestion && toggleMark(currentQuestion.id, !currentQuestion.isMarked)}
            isFirst={session.currentIndex === 0}
            isLast={session.currentIndex >= session.totalQuestions - 1}
          />
        ) : null}
      </div>

      <aside className="hidden w-[280px] shrink-0 flex-col gap-4 overflow-y-auto pr-5 pt-6 xl:flex xl:w-[320px]">
        <TimerProgressCard session={session} />
        <QuestionNavigator session={session} onJump={goToIndex} />
        <KnowledgeTagsCard question={currentQuestion} />
        <QuizToolsCard
          onWrongBook={() => navigate('/workspace/quiz?tab=wrong')}
          onResources={() => navigateToWorkspace(navigate, '/workspace/resources', session.kpId)}
        />
      </aside>

      {result ? (
        <QuizResultModal
          open={showResult}
          result={result}
          onClose={() => setShowResult(false)}
          onViewWrong={() => navigate('/workspace/quiz')}
          onRetry={handleRetry}
          onBackCatalog={() => navigate('/workspace/quiz')}
          onGenerateReview={() => navigateToWorkspace(navigate, '/workspace/resources', session.kpId)}
        />
      ) : null}
    </div>
  )
}
