import { useEffect, useState } from 'react'
import type { PracticeRecord, Question, QuizChapter } from '@/utils/types/quiz'
import { getRecentPracticeRecords, getWrongQuestions, getFavoriteQuestions } from '@/services/quizService'
import QuizChapterAccordion, { QuestionListTab } from './QuizChapterAccordion'
import CatalogPagination from './CatalogPagination'
import { LoadingSkeleton } from './shared'

type TabKey = 'catalog' | 'wrong' | 'favorites' | 'records'

const TABS: { key: TabKey; label: string }[] = [
  { key: 'catalog', label: '题库目录' },
  { key: 'wrong', label: '我的错题' },
  { key: 'favorites', label: '我的收藏' },
  { key: 'records', label: '练习记录' },
]

type Props = {
  chapters: QuizChapter[]
  catalogLoading?: boolean
  defaultExpandedChapterId?: string | null
  startingSectionId?: string | null
  onStartSection: (section: import('@/utils/types/quiz').QuizSection) => void
  keyword?: string
  catalogPage?: number
  catalogTotal?: number
  catalogTotalPages?: number
  catalogPageSize?: number
  onCatalogPageChange?: (page: number) => void
}

export default function QuizCatalogTabs({
  chapters,
  catalogLoading,
  defaultExpandedChapterId,
  startingSectionId,
  onStartSection,
  keyword,
  catalogPage = 1,
  catalogTotal = 0,
  catalogTotalPages = 1,
  catalogPageSize = 5,
  onCatalogPageChange,
}: Props) {
  const [tab, setTab] = useState<TabKey>('catalog')
  const [wrong, setWrong] = useState<Question[]>([])
  const [favorites, setFavorites] = useState<Question[]>([])
  const [records, setRecords] = useState<PracticeRecord[]>([])
  const [tabLoading, setTabLoading] = useState(false)

  useEffect(() => {
    if (tab === 'catalog') return
    setTabLoading(true)
    const load = async () => {
      try {
        if (tab === 'wrong') setWrong(await getWrongQuestions({ keyword }))
        if (tab === 'favorites') setFavorites(await getFavoriteQuestions({ keyword }))
        if (tab === 'records') setRecords(await getRecentPracticeRecords({ limit: 10 }))
      } finally {
        setTabLoading(false)
      }
    }
    void load()
  }, [tab, keyword])

  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-bold text-[#0F2A5F]">题库目录</h2>
        <div className="flex flex-wrap gap-2">
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              onClick={() => setTab(t.key)}
              className={`h-8 rounded-full px-3 text-xs font-medium ${
                tab === t.key ? 'bg-[#EAF3FF] text-[#2563EB]' : 'text-[#64748B] hover:bg-[#F8FBFF]'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {tab === 'catalog' ? (
        <>
          <QuizChapterAccordion
            chapters={chapters}
            loading={catalogLoading}
            defaultExpandedId={defaultExpandedChapterId}
            startingSectionId={startingSectionId}
            onStartSection={onStartSection}
          />
          {onCatalogPageChange ? (
            <CatalogPagination
              page={catalogPage}
              totalPages={catalogTotalPages}
              total={catalogTotal}
              pageSize={catalogPageSize}
              onPageChange={onCatalogPageChange}
            />
          ) : null}
        </>
      ) : null}

      {tab === 'wrong' ? (
        tabLoading ? (
          <LoadingSkeleton />
        ) : (
          <QuestionListTab questions={wrong} emptyTitle="目前没有错题" />
        )
      ) : null}

      {tab === 'favorites' ? (
        tabLoading ? (
          <LoadingSkeleton />
        ) : (
          <QuestionListTab questions={favorites} emptyTitle="还没有收藏题目" />
        )
      ) : null}

      {tab === 'records' ? (
        tabLoading ? (
          <LoadingSkeleton />
        ) : records.length === 0 ? (
          <QuestionListTab questions={[]} emptyTitle="还没有练习记录" />
        ) : (
          <div className="space-y-2">
            {records.map((r) => (
              <div key={r.id} className="flex items-center justify-between rounded-xl border border-[#E6EEFA] px-4 py-3">
                <div>
                  <p className="text-sm font-semibold text-[#0F2A5F]">{r.title}</p>
                  <p className="text-xs text-[#64748B]">
                    {r.practicedAt.slice(0, 10)} · {r.completedCount}/{r.totalCount}
                    {r.accuracyRate != null ? ` · ${r.accuracyRate}%` : ''}
                  </p>
                </div>
                {r.canContinue ? (
                  <span className="text-xs font-semibold text-[#2563EB]">可继续</span>
                ) : null}
              </div>
            ))}
          </div>
        )
      ) : null}
    </section>
  )
}
