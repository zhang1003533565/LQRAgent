import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Sparkles } from 'lucide-react'
import {
  PageHeader,
  QuizAuxPanel,
  QuizCatalogTabs,
  QuizFilterBar,
  QuizOverviewCards,
  RecommendedPracticeSection,
} from '@/components/student/workspace/quiz'
import { createPracticeSession, generatePracticeFromPath, syncQuizPreferences, syncQuizSessionsFromCloud } from '@/services/quizService'
import { useQuizCatalog } from '@/utils/hooks/useQuizCatalog'
import { useQuizOverview } from '@/utils/hooks/useQuizOverview'
import { useRecommendedPractices } from '@/utils/hooks/useRecommendedPractices'
import { usePathStore } from '@/utils/store/pathStore'
import { syncWorkspaceFromSearchParams } from '@/utils/navigation/workspaceNav'
import NoPathGuide from '@/components/student/workspace/shared/NoPathGuide'
import type { QuizCatalogFilters, QuizSection, RecommendedPractice } from '@/utils/types/quiz'

const DEFAULT_FILTERS: QuizCatalogFilters = {
  keyword: '',
  type: 'all',
  difficulty: 'all',
  status: 'all',
  learningPathId: 'all',
}

export default function QuizHomePage() {
  const navigate = useNavigate()
  const { selectedKpId, nodes, loading: pathLoading } = usePathStore()
  const [searchParams] = useSearchParams()
  const [search, setSearch] = useState('')
  const [filters, setFilters] = useState<QuizCatalogFilters>(DEFAULT_FILTERS)
  const [startingSectionId, setStartingSectionId] = useState<string | null>(null)
  const [startingRecId, setStartingRecId] = useState<string | null>(null)
  const [generating, setGenerating] = useState(false)

  useEffect(() => {
    syncWorkspaceFromSearchParams(searchParams)
    if (searchParams.get('kpId')) {
      setFilters((f) => ({ ...f, learningPathId: 'current' }))
    }
  }, [searchParams])

  useEffect(() => {
    void syncQuizPreferences(true)
    void syncQuizSessionsFromCloud()
  }, [])

  const catalogFilters = useMemo(
    () => ({ ...filters, keyword: search }),
    [filters, search],
  )

  const { data: overview, loading: overviewLoading, error: overviewError, refresh: refreshOverview } = useQuizOverview()
  const {
    chapters,
    loading: catalogLoading,
    error: catalogError,
    refresh: refreshCatalog,
    page: catalogPage,
    total: catalogTotal,
    totalPages: catalogTotalPages,
    pageSize: catalogPageSize,
    goToPage: goCatalogPage,
  } = useQuizCatalog(catalogFilters)
  const { items: recommendations, loading: recLoading, error: recError, refresh: refreshRec } = useRecommendedPractices(3)

  const defaultExpandedChapterId = useMemo(() => {
    if (selectedKpId) {
      const match = chapters.find((ch) => ch.id === selectedKpId || ch.sections.some((s) => s.knowledgePointIds?.includes(selectedKpId)))
      if (match) return match.id
    }
    return chapters[0]?.id ?? null
  }, [chapters, selectedKpId])

  const startSession = useCallback(
    async (payload: Parameters<typeof createPracticeSession>[0]) => {
      const session = await createPracticeSession(payload)
      navigate(`/workspace/quiz/session/${session.id}`)
      return session
    },
    [navigate],
  )

  const handleStartSection = useCallback(
    async (section: QuizSection) => {
      setStartingSectionId(section.id)
      try {
        if (section.lastSessionId && section.status === 'in_progress') {
          navigate(`/workspace/quiz/session/${section.lastSessionId}`)
          return
        }
        await startSession({
          mode: 'section',
          sectionId: section.id,
          questionIds: section.questionIds,
          learningPathNodeId: section.learningPathNodeId,
          title: section.title,
        })
      } finally {
        setStartingSectionId(null)
      }
    },
    [navigate, startSession],
  )

  const handleStartRecommendation = useCallback(
    async (item: RecommendedPractice) => {
      setStartingRecId(item.id)
      try {
        if (item.startPayload.sessionId && item.reasonType === 'review') {
          navigate(`/workspace/quiz/session/${item.startPayload.sessionId}`)
          return
        }
        await startSession({
          mode: item.startPayload.mode,
          sectionId: item.startPayload.sectionId,
          questionIds: item.startPayload.questionIds,
          learningPathNodeId: item.learningPathNodeId,
          title: item.title,
        })
      } finally {
        setStartingRecId(null)
      }
    },
    [navigate, startSession],
  )

  const handleGenerate = useCallback(async () => {
    setGenerating(true)
    try {
      const session = await generatePracticeFromPath(selectedKpId || undefined)
      navigate(`/workspace/quiz/session/${session.id}`)
    } finally {
      setGenerating(false)
    }
  }, [navigate, selectedKpId])

  const suggestion = recommendations[0]?.description || recommendations[0]?.reason || null

  if (!pathLoading && nodes.length === 0) {
    return (
      <div className="h-full min-h-0 overflow-y-auto bg-[#F6F9FE] p-6">
        <NoPathGuide
          onGoPath={() => navigate('/workspace/learning-path')}
          onGoChat={() => navigate('/workspace')}
        />
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-0 overflow-hidden bg-[#F6F9FE] font-sans">
      <div className="flex min-w-0 flex-1 flex-col gap-4 overflow-y-auto px-5 pb-8 pt-6">
        <PageHeader
          search={search}
          onSearchChange={setSearch}
          onGenerate={handleGenerate}
          generating={generating}
        />

        {selectedKpId ? (
          <div className="flex items-center gap-2 rounded-[14px] border border-[#D8E8FF] bg-[#F8FBFF] px-4 py-2.5 text-sm text-[#64748B]">
            <Sparkles className="h-4 w-4 shrink-0 text-[#2563EB]" />
            <span>
              当前学习路径关联练习已优先展示
              {nodes.find((n) => n.kpId === selectedKpId)?.title
                ? ` · ${nodes.find((n) => n.kpId === selectedKpId)?.title}`
                : ''}
            </span>
          </div>
        ) : null}

        <QuizOverviewCards
          overview={overview}
          loading={overviewLoading}
          error={overviewError}
          onRetry={refreshOverview}
        />

        <QuizFilterBar
          filters={filters}
          onChange={setFilters}
          onReset={() => setFilters(DEFAULT_FILTERS)}
        />

        <RecommendedPracticeSection
          items={recommendations}
          loading={recLoading}
          error={recError}
          startingId={startingRecId}
          onRetry={refreshRec}
          onStart={handleStartRecommendation}
        />

        {catalogError ? (
          <div className="rounded-xl border border-[#FECACA] bg-[#FEF2F2] px-4 py-3 text-sm text-[#DC2626]">
            {catalogError}
            <button type="button" className="ml-3 underline" onClick={refreshCatalog}>重试</button>
          </div>
        ) : null}

        <QuizCatalogTabs
          chapters={chapters}
          catalogLoading={catalogLoading}
          defaultExpandedChapterId={defaultExpandedChapterId}
          startingSectionId={startingSectionId}
          onStartSection={handleStartSection}
          keyword={search}
          catalogPage={catalogPage}
          catalogTotal={catalogTotal}
          catalogTotalPages={catalogTotalPages}
          catalogPageSize={catalogPageSize}
          onCatalogPageChange={goCatalogPage}
        />
      </div>

      <QuizAuxPanel
        overview={overview}
        overviewLoading={overviewLoading}
        suggestion={suggestion}
        onContinueRecord={(sessionId) => navigate(`/workspace/quiz/session/${sessionId}`)}
        onViewPath={() => navigate('/workspace/learning-path')}
      />
    </div>
  )
}
