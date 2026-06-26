import { useCallback, useEffect, useState } from 'react'
import { getRecommendedPractices } from '@/services/quizService'
import type { RecommendedPractice } from '@/utils/types/quiz'

export function useRecommendedPractices(limit = 3) {
  const [items, setItems] = useState<RecommendedPractice[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setItems(await getRecommendedPractices({ limit }))
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载推荐失败')
      setItems([])
    } finally {
      setLoading(false)
    }
  }, [limit])

  useEffect(() => {
    void refresh()
  }, [refresh])

  return { items, loading, error, refresh }
}
