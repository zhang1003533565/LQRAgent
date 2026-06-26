import { useCallback, useEffect, useState } from 'react'
import { getLearningPathOptions } from '@/services/uploadService'
import type { LearningPathOption } from '@/utils/types/upload'

export function useLearningPathOptions() {
  const [options, setOptions] = useState<LearningPathOption[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setOptions(await getLearningPathOptions())
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载学习路径失败')
      setOptions([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  return { options, loading, error, refresh }
}
