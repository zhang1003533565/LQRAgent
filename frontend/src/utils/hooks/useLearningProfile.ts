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

export function useLearningProfile(initialFilters?: LearningProfileFilters) {
  const [filters, setFilters] = useState<LearningProfileFilters>(initialFilters ?? { range: '30d' })
  const [data, setData] = useState<LearningProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const filterKey = useMemo(() => JSON.stringify(filters), [filters])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await loadLearningProfile(filters))
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载学习画像失败')
      setData(null)
    } finally {
      setLoading(false)
    }
  }, [filterKey, filters])

  useEffect(() => {
    void load()
  }, [load])

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

  const exportReport = useCallback(async () => {
    if (!data) return null
    setExporting(true)
    try {
      const res = await exportLearningProfileReport(data)
      const a = document.createElement('a')
      a.href = res.downloadUrl
      a.download = '学习画像报告.md'
      a.click()
      URL.revokeObjectURL(res.downloadUrl)
      return res
    } finally {
      setExporting(false)
    }
  }, [data])

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
