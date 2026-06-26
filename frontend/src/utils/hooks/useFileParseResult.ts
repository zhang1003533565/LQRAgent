import { useCallback, useEffect, useState } from 'react'
import { getFileParseResult } from '@/services/uploadService'
import type { FileParseResult } from '@/utils/types/upload'

export function useFileParseResult(fileId: string | null, pollWhenProcessing = false) {
  const [data, setData] = useState<FileParseResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    if (!fileId) {
      setData(null)
      return
    }
    setLoading(true)
    setError(null)
    try {
      setData(await getFileParseResult(fileId))
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载解析结果失败')
      setData(null)
    } finally {
      setLoading(false)
    }
  }, [fileId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (!pollWhenProcessing || !fileId) return
    if (data?.status !== 'processing' && data?.status !== 'pending') return

    const timer = setInterval(() => {
      void refresh()
    }, 5000)
    return () => clearInterval(timer)
  }, [data?.status, fileId, pollWhenProcessing, refresh])

  return { data, loading, error, refresh }
}
