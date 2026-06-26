import { useCallback, useEffect, useState } from 'react'
import { getStorageUsage } from '@/services/uploadService'
import type { StorageUsage } from '@/utils/types/upload'

export function useStorageUsage() {
  const [data, setData] = useState<StorageUsage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await getStorageUsage())
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载存储空间失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  return { data, loading, error, refresh }
}
