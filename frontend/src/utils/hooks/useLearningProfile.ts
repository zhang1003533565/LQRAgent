import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  exportLearningProfileReport,
  loadLearningProfile,
  refreshLearningProfile,
} from '@/services/learningProfileService'
import type {
  LearningProfile,
  LearningProfileFilters,
  ProfileRange,
  TrendMetric,
} from '@/utils/types/learningProfile'
import { useProfileStore } from '@/utils/store/profileStore'
import { mergeProfileFromWsPatch } from '@/utils/learningProfile/wsProfileMerge'

export function useLearningProfile(initialFilters?: LearningProfileFilters) {
  const profileRevision = useProfileStore((s) => s.revision)
  const [filters, setFilters] = useState<LearningProfileFilters>(initialFilters ?? { range: '30d' })
  const [data, setData] = useState<LearningProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const filterKey = useMemo(() => JSON.stringify(filters), [filters])

  const load = useCallback(async (options?: { silent?: boolean }) => {
    if (!options?.silent) setLoading(true)
    setError(null)
    try {
      setData(await loadLearningProfile(filters))
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载学习画像失败')
      if (!options?.silent) setData(null)
    } finally {
      if (!options?.silent) setLoading(false)
    }
  }, [filterKey, filters])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (profileRevision <= 0) return
    const wsPayload = useProfileStore.getState().wsPayload
    if (wsPayload?.topicMastery) {
      setData((prev) => {
        if (!prev) return prev
        const merged = mergeProfileFromWsPatch(prev, wsPayload)
        return merged ?? prev
      })
      return
    }
    void load({ silent: true })
  }, [profileRevision, load])

  const refresh = useCallback(async () => {
    setRefreshing(true)
    setError(null)
    try {
      setData(await refreshLearningProfile(filters))
    } catch (e) {
      setError(e instanceof Error ? e.message : '刷新画像失败')
    } finally {
      setRefreshing(false)
    }
  }, [filters])

  const exportReport = useCallback(
    async (format: 'markdown' | 'pdf' = 'markdown') => {
      if (!data && format === 'markdown') return null
      setExporting(true)
      try {
        return await exportLearningProfileReport(data ?? undefined, format)
      } finally {
        setExporting(false)
      }
    },
    [data],
  )

  const setRange = useCallback((range: ProfileRange) => {
    setFilters((f) => ({ ...f, range }))
  }, [])

  const setLearningPathId = useCallback((learningPathId: string) => {
    setFilters((f) => ({ ...f, learningPathId }))
  }, [])

  const setTrendMetric = useCallback((trendMetric: TrendMetric) => {
    setFilters((f) => ({ ...f, trendMetric }))
  }, [])

  return {
    data,
    loading,
    refreshing,
    exporting,
    error,
    filters,
    setFilters,
    setRange,
    setLearningPathId,
    setTrendMetric,
    refresh,
    exportReport,
    reload: load,
  }
}
