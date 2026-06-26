import { useCallback, useEffect, useMemo, useState } from 'react'
import { getQuizCatalog } from '@/services/quizService'
import type { QuizCatalogFilters } from '@/utils/types/quiz'

const DEFAULT_PAGE_SIZE = 5

export function useQuizCatalog(filters: QuizCatalogFilters) {
  const [chapters, setChapters] = useState<Awaited<ReturnType<typeof getQuizCatalog>>['chapters']>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const filterKey = useMemo(
    () =>
      JSON.stringify({
        keyword: filters.keyword,
        type: filters.type,
        difficulty: filters.difficulty,
        status: filters.status,
        learningPathId: filters.learningPathId,
        sort: filters.sort,
      }),
    [
      filters.keyword,
      filters.type,
      filters.difficulty,
      filters.status,
      filters.learningPathId,
      filters.sort,
    ],
  )

  useEffect(() => {
    setPage(1)
  }, [filterKey])

  const load = useCallback(async (targetPage: number) => {
    setLoading(true)
    setError(null)
    try {
      const res = await getQuizCatalog({
        ...filters,
        page: targetPage,
        pageSize: DEFAULT_PAGE_SIZE,
      })
      setChapters(res.chapters)
      setTotal(res.total)
      setPage(res.page)
      setTotalPages(res.totalPages)
      setPageSize(res.pageSize)
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载题库失败')
      setChapters([])
      setTotal(0)
      setTotalPages(1)
    } finally {
      setLoading(false)
    }
  }, [filterKey, filters])

  useEffect(() => {
    void load(page)
  }, [load, page])

  const goToPage = useCallback((next: number) => {
    setPage((prev) => Math.max(1, Math.min(next, totalPages || 1)))
  }, [totalPages])

  return {
    chapters,
    loading,
    error,
    refresh: () => load(page),
    page,
    total,
    totalPages,
    pageSize,
    goToPage,
  }
}
