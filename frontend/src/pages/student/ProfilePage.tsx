import { useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AbilityRadarCard,
  AchievementCard,
  EmptyState,
  ErrorState,
  GrowthTrendCard,
  InsightCardsSection,
  KnowledgeMasteryCard,
  LoadingSkeleton,
  PageHeader,
  ProfileFilterBar,
  ProfileMetricCards,
} from '@/components/student/workspace/learning-profile'
import { startPracticeForKnowledgePoint } from '@/services/learningProfileService'
import { useLearningProfile } from '@/utils/hooks/useLearningProfile'
import { navigateToWorkspace } from '@/utils/navigation/workspaceNav'
import type { LearningInsight, TrendMetric } from '@/utils/types/learningProfile'

function computeTrendDelta(
  trends: Array<{ overallMasteryRate?: number; accuracyRate?: number }>,
  metric: TrendMetric,
): number | null {
  if (trends.length < 2) return null
  const pick = (t: (typeof trends)[0]) =>
    metric === 'accuracy' ? t.accuracyRate : t.overallMasteryRate
  const first = pick(trends[0])
  const last = pick(trends[trends.length - 1])
  if (first == null || last == null) return null
  return Math.round(last - first)
}

export default function ProfilePage() {
  const navigate = useNavigate()
  const {
    data,
    loading,
    refreshing,
    exporting,
    error,
    filters,
    setRange,
    setLearningPathId,
    setTrendMetric,
    refresh,
    exportReport,
    reload,
  } = useLearningProfile({ range: '30d', trendMetric: 'mastery' })

  const metric = filters.trendMetric ?? 'mastery'
  const range = filters.range ?? '30d'

  const masteryTrendDelta = useMemo(
    () => (data ? computeTrendDelta(data.trends, 'mastery') : null),
    [data],
  )

  const chartTrendDelta = useMemo(
    () => (data ? computeTrendDelta(data.trends, metric) : null),
    [data, metric],
  )

  const radarHint = useMemo(() => {
    if (!data?.abilityDimensions.length) return undefined
    const sorted = [...data.abilityDimensions].sort((a, b) => b.score - a.score)
    const top = sorted[0]
    const low = sorted[sorted.length - 1]
    if (top && low && top.score - low.score >= 8) {
      return `你的${top.name}表现突出，${low.name}仍有提升空间，建议针对性补强。`
    }
    return '能力分析基于当前学习数据动态生成'
  }, [data?.abilityDimensions])

  const handleInsightAction = useCallback(
    (insight: LearningInsight) => {
      const action = insight.action
      if (!action) return
      switch (action.type) {
        case 'practice':
          if (action.targetId) {
            void startPracticeForKnowledgePoint(action.targetId).then((session) => {
              navigate(`/workspace/quiz/session/${session.id}`)
            })
          } else {
            navigateToWorkspace(navigate, '/workspace/quiz')
          }
          break
        case 'review_resource':
          navigateToWorkspace(navigate, '/workspace/resources', action.targetId)
          break
        case 'continue_path':
          navigateToWorkspace(navigate, '/workspace/learning-path', action.targetId)
          break
        case 'view_graph':
          navigateToWorkspace(navigate, '/workspace/knowledge-graph', action.targetId)
          break
        default:
          navigateToWorkspace(navigate, '/workspace/learning-path')
      }
    },
    [navigate],
  )

  const hasData =
    data &&
    (data.knowledgeMastery.length > 0 ||
      data.abilityDimensions.some((d) => d.score > 0) ||
      data.overview.overallMasteryRate > 0)

  return (
    <div className="h-full min-h-0 overflow-x-hidden overflow-y-auto bg-[#F6F9FE]">
      <div className="min-w-0 space-y-4 p-5 pb-8 lg:p-6">
        <PageHeader
          refreshing={refreshing}
          exporting={exporting}
          onRefresh={() => void refresh()}
          onExportMarkdown={() => void exportReport('markdown')}
          onExportPdf={() => void exportReport('pdf')}
        />

        <ProfileFilterBar
          pathOptions={data?.pathOptions ?? []}
          pathSummary={data?.pathSummary}
          pathNodeTitles={data?.pathNodeTitles}
          learningPathId={filters.learningPathId}
          range={range}
          onPathChange={setLearningPathId}
          onRangeChange={setRange}
        />

        {loading ? <LoadingSkeleton rows={4} /> : null}
        {error && !loading ? <ErrorState message={error} onRetry={() => void reload()} /> : null}

        {!loading && !error && !hasData ? (
          <EmptyState
            title="暂无学习画像数据"
            description="完成学习路径、答题练习或上传学习资料后，AI 将生成你的学习画像"
            action={
              <div className="flex flex-wrap justify-center gap-2">
                <button
                  type="button"
                  onClick={() => navigateToWorkspace(navigate, '/workspace/learning-path')}
                  className="rounded-xl bg-[#2563EB] px-4 py-2 text-sm font-semibold text-white"
                >
                  去学习路径
                </button>
                <button
                  type="button"
                  onClick={() => navigateToWorkspace(navigate, '/workspace/quiz')}
                  className="rounded-xl border border-[#D8E4F5] px-4 py-2 text-sm font-semibold text-[#2563EB]"
                >
                  去答题练习
                </button>
              </div>
            }
          />
        ) : null}

        {!loading && !error && data ? (
          <>
            <ProfileMetricCards
              overview={data.overview}
              dimensions={data.abilityDimensions}
              masteryTrendDelta={masteryTrendDelta}
            />

            <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2 lg:items-start">
              <AbilityRadarCard
                dimensions={data.abilityDimensions}
                hint={radarHint}
                onGoPractice={() => navigateToWorkspace(navigate, '/workspace/quiz')}
              />
              <KnowledgeMasteryCard
                items={data.knowledgeMastery}
                onViewGraph={() => navigateToWorkspace(navigate, '/workspace/knowledge-graph')}
                onItemClick={(item) =>
                  navigateToWorkspace(navigate, '/workspace/knowledge-graph', item.knowledgePointId)
                }
              />
            </div>

            <InsightCardsSection insights={data.insights} onAction={handleInsightAction} />

            <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-[1.6fr_1fr] lg:items-start">
              <GrowthTrendCard
                trends={data.trends}
                metric={metric}
                range={range}
                trendDelta={chartTrendDelta}
                onMetricChange={setTrendMetric}
              />
              <AchievementCard achievements={data.achievements} />
            </div>
          </>
        ) : null}
      </div>
    </div>
  )
}
