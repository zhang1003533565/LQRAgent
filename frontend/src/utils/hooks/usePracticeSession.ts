import { useCallback, useEffect, useState } from 'react'
import {
  createPracticeSession,
  getPracticeSession,
  persistPracticeSession,
  submitPracticeSession,
  submitQuestionAnswer,
  favoriteQuestion,
  markQuestion,
} from '@/services/quizService'
import { saveSession } from '@/utils/quiz/quizSessionStorage'
import type { CreateSessionPayload, PracticeResult, PracticeSession, SubmitAnswerResult } from '@/utils/types/quiz'

export function usePracticeSession(sessionId: string | undefined) {
  const [session, setSession] = useState<PracticeSession | null>(null)
  const [loading, setLoading] = useState(Boolean(sessionId))
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<PracticeResult | null>(null)

  const load = useCallback(async () => {
    if (!sessionId) return
    setLoading(true)
    setError(null)
    try {
      setSession(await getPracticeSession(sessionId))
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载练习失败')
      setSession(null)
    } finally {
      setLoading(false)
    }
  }, [sessionId])

  useEffect(() => {
    void load()
  }, [load])

  const submitAnswer = useCallback(
    async (questionId: string, answer: string | string[]): Promise<SubmitAnswerResult | null> => {
      if (!sessionId || submitting) return null
      setSubmitting(true)
      try {
        const res = await submitQuestionAnswer({ sessionId, questionId, answer })
        if (res.updatedSession) setSession(res.updatedSession)
        return res
      } catch (e) {
        setError(e instanceof Error ? e.message : '提交失败')
        return null
      } finally {
        setSubmitting(false)
      }
    },
    [sessionId, submitting],
  )

  const submitSession = useCallback(async () => {
    if (!sessionId) return null
    setSubmitting(true)
    try {
      const res = await submitPracticeSession(sessionId)
      setResult(res)
      setSession((prev) => (prev ? { ...prev, status: 'submitted' } : prev))
      return res
    } catch (e) {
      setError(e instanceof Error ? e.message : '提交练习失败')
      return null
    } finally {
      setSubmitting(false)
    }
  }, [sessionId])

  const goToIndex = useCallback((index: number) => {
    setSession((prev) => {
      if (!prev) return prev
      const updated = { ...prev, currentIndex: index }
      saveSession(updated)
      void persistPracticeSession(updated)
      return updated
    })
  }, [])

  const toggleFavorite = useCallback(async (questionId: string, favorite: boolean) => {
    await favoriteQuestion({ questionId, favorite })
    setSession((prev) => {
      if (!prev) return prev
      return {
        ...prev,
        questions: prev.questions.map((q) =>
          q.id === questionId ? { ...q, isFavorite: favorite } : q,
        ),
      }
    })
  }, [])

  const toggleMark = useCallback(async (questionId: string, marked: boolean) => {
    await markQuestion({ questionId, marked })
    setSession((prev) => {
      if (!prev) return prev
      return {
        ...prev,
        questions: prev.questions.map((q) =>
          q.id === questionId ? { ...q, isMarked: marked } : q,
        ),
      }
    })
  }, [])

  return {
    session,
    loading,
    error,
    submitting,
    result,
    refresh: load,
    submitAnswer,
    submitSession,
    goToIndex,
    toggleFavorite,
    toggleMark,
    setSession,
  }
}

export function useCreatePracticeSession() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const create = useCallback(async (payload: CreateSessionPayload) => {
    setLoading(true)
    setError(null)
    try {
      return await createPracticeSession(payload)
    } catch (e) {
      setError(e instanceof Error ? e.message : '创建练习失败')
      return null
    } finally {
      setLoading(false)
    }
  }, [])

  return { create, loading, error }
}
