import { useCallback, useEffect, useMemo, useState } from 'react'
import { getUploadedFiles } from '@/services/uploadService'
import type { UploadedFilesFilters } from '@/utils/types/upload'

const DEFAULT_PAGE_SIZE = 8

export function useUploadedFiles(filters: UploadedFilesFilters) {
  const [list, setList] = useState<Awaited<ReturnType<typeof getUploadedFiles>>['list']>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [totalPages, setTotalPages] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const filterKey = useMemo(
    () =>
      JSON.stringify({
        keyword: filters.keyword,
        sourceType: filters.sourceType,
        parseStatus: filters.parseStatus,
        learningPathId: filters.learningPathId,
        knowledgePointId: filters.knowledgePointId,
        sort: filters.sort,
        pageSize: filters.pageSize,
      }),
    [
      filters.keyword,
      filters.sourceType,
      filters.parseStatus,
      filters.learningPathId,
      filters.knowledgePointId,
      filters.sort,
      filters.pageSize,
    ],
  )

  useEffect(() => {
    setPage(1)
  }, [filterKey])

  const load = useCallback(
    async (targetPage: number) => {
      setLoading(true)
      setError(null)
      try {
        const res = await getUploadedFiles({
          ...filters,
          page: targetPage,
          pageSize: filters.pageSize ?? DEFAULT_PAGE_SIZE,
        })
        setList(res.list)
        setTotal(res.total)
        setPage(res.page)
        setTotalPages(res.totalPages)
        setPageSize(res.pageSize)
      } catch (e) {
        setError(e instanceof Error ? e.message : '加载文件列表失败')
        setList([])
        setTotal(0)
        setTotalPages(1)
      } finally {
        setLoading(false)
      }
    },
    [filterKey, filters],
  )

  useEffect(() => {
    void load(page)
  }, [load, page])

  const goToPage = useCallback(
    (next: number) => {
      setPage((prev) => Math.max(1, Math.min(next, totalPages || 1)))
    },
    [totalPages],
  )

  const refresh = useCallback(() => load(page), [load, page])

  return {
    list,
    total,
    page,
    totalPages,
    pageSize,
    loading,
    error,
    refresh,
    goToPage,
  }
}
