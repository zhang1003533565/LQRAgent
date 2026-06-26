import { useCallback, useEffect, useState } from 'react'
import { getUploadStats } from '@/services/uploadService'
import type { UploadStats } from '@/utils/types/upload'

export function useUploadStats() {
  const [data, setData] = useState<UploadStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await getUploadStats())
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载统计失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  return { data, loading, error, refresh }
}
