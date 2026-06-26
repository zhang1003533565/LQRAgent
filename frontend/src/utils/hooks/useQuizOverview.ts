import { useCallback, useEffect, useState } from 'react'
import { getQuizOverview } from '@/services/quizService'
import type { QuizOverview } from '@/utils/types/quiz'

export function useQuizOverview() {
  const [data, setData] = useState<QuizOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await getQuizOverview())
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载概览失败')
      setData(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  return { data, loading, error, refresh }
}
