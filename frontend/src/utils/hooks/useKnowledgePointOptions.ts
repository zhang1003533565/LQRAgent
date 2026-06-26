import { useCallback, useEffect, useState } from 'react'
import { getKnowledgePointOptions } from '@/services/uploadService'
import type { KnowledgePointOption } from '@/utils/types/upload'

export function useKnowledgePointOptions(keyword?: string, learningPathId?: string) {
  const [options, setOptions] = useState<KnowledgePointOption[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setOptions(await getKnowledgePointOptions({ keyword, learningPathId }))
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载知识点失败')
      setOptions([])
    } finally {
      setLoading(false)
    }
  }, [keyword, learningPathId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  return { options, loading, error, refresh }
}
