import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { getCurrentPath } from '@/api/student/learningPath'
import {
  ChapterAccordion,
  LearningGoalInput,
  LearningPathEmpty,
  LearningPathSkeleton,
  PageHeader,
  PathAuxPanel,
  PathOverviewCards,
} from '@/components/student/workspace/learning-path'
import { PATH_CYCLE_OPTIONS, type PathOverviewStat } from '@/utils/types/learning-path-ui'
import { useOrchestrator } from '@/utils/hooks/useOrchestrator'
import { navigateToWorkspace, syncWorkspaceFromSearchParams } from '@/utils/navigation/workspaceNav'
import {
  buildChaptersFromPathNodes,
  computeOverviewFromChapters,
  findNodeInChapters,
} from '@/utils/learningPath/chapterUtils'
import { usePathStore } from '@/utils/store/pathStore'
import { trackBehavior } from '@/utils/tracker'
import {
  BookOpen,
  CalendarDays,
  CheckCircle,
  Layers,
  PlayCircle,
} from 'lucide-react'

function buildOverviewStats(
  total: number,
  completed: number,
  current: number,
  pending: number,
  eta: string,
): PathOverviewStat[] {
  return [
    {
      id: 'total',
      label: '总节点数',
      value: String(total),
      icon: Layers,
      iconBg: 'bg-[#EAF3FF]',
      iconColor: 'text-[#2563EB]',
    },
    {
      id: 'done',
      label: '已完成',
      value: String(completed),
      icon: CheckCircle,
      iconBg: 'bg-[#DCFCE7]',
      iconColor: 'text-[#16A34A]',
    },
    {
      id: 'active',
      label: '进行中',
      value: String(current),
      icon: PlayCircle,
      iconBg: 'bg-[#EAF3FF]',
      iconColor: 'text-[#2563EB]',
    },
    {
      id: 'pending',
      label: '待学习',
      value: String(pending),
      icon: BookOpen,
      iconBg: 'bg-[#FFF7ED]',
      iconColor: 'text-[#D97706]',
    },
    {
      id: 'eta',
      label: '预计完成时间',
      value: eta,
      icon: CalendarDays,
      iconBg: 'bg-[#F5F3FF]',
      iconColor: 'text-[#8B5CF6]',
    },
  ]
}

export default function LearningPathPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { start: startOrch, running: orchRunning, error: orchError } = useOrchestrator()
  const {
    goal,
    planDescription,
    nodes,
    selectedKpId,
    loading,
    setPath,
    setLoading,
    selectNode,
    refresh,
    clearUpdates,
  } = usePathStore()

  const [inputGoal, setInputGoal] = useState('')
  const [cycle, setCycle] = useState('2 周')
  const [error, setError] = useState<string | null>(null)

  const isGenerating = loading || orchRunning
  const hasPath = nodes.length > 0

  useEffect(() => {
    clearUpdates()
    void refresh()
  }, [clearUpdates, refresh])

  useEffect(() => {
    syncWorkspaceFromSearchParams(searchParams)
  }, [searchParams])

  useEffect(() => {
    if (goal) setInputGoal(goal)
  }, [goal])

  const chapters = useMemo(() => {
    if (!hasPath) return []
    return buildChaptersFromPathNodes(nodes, selectedKpId)
  }, [hasPath, nodes, selectedKpId])

  const selectedNode = useMemo(
    () => findNodeInChapters(chapters, selectedKpId),
    [chapters, selectedKpId],
  )

  const overviewStats = useMemo(() => {
    if (!hasPath) return buildOverviewStats(0, 0, 0, 0, '—')
    const stats = computeOverviewFromChapters(chapters)
    return buildOverviewStats(stats.total, stats.completed, stats.current, stats.pending, stats.eta)
  }, [hasPath, chapters])

  const todayGoal = useMemo(() => {
    if (!hasPath) return { label: '完成 1 个学习任务', current: 0, total: 1 }
    const completed = chapters
      .flatMap((c) => c.nodes)
      .filter((n) => n.status === 'completed').length
    return { label: '完成 1 个学习任务', current: Math.min(1, completed), total: 1 }
  }, [hasPath, chapters])

  const handleSelectNode = useCallback(
    (nodeId: string) => {
      trackBehavior({ kpId: nodeId, action: 'view_path' })
      selectNode(nodeId)
    },
    [selectNode],
  )

  const handleGenerate = useCallback(() => {
    if (!inputGoal.trim()) return
    const cycleValue = cycle.replace(/\s/g, '')
    startOrch(inputGoal, cycleValue)
  }, [inputGoal, cycle, startOrch])

  const focusGoalInput = useCallback(() => {
    document.getElementById('learning-goal-input')?.focus()
  }, [])

  const handleRestore = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getCurrentPath()
      if (data) setPath(data)
      else setError('暂无活跃学习路径')
    } catch {
      setError('获取当前路径失败')
    } finally {
      setLoading(false)
    }
  }, [setLoading, setPath])

  const handleStartLearning = useCallback(() => {
    if (!selectedKpId) return
    navigate('/workspace')
  }, [navigate, selectedKpId])

  const handleGenerateNotes = useCallback(() => {
    if (!selectedKpId) return
    navigateToWorkspace(navigate, '/workspace/resources', selectedKpId)
  }, [navigate, selectedKpId])

  const handleGenerateQuiz = useCallback(() => {
    if (!selectedKpId) return
    navigateToWorkspace(navigate, '/workspace/quiz', selectedKpId)
  }, [navigate, selectedKpId])

  const showSkeleton = isGenerating && !hasPath

  return (
    <div className="flex h-full min-h-0 overflow-hidden bg-[#F6F9FE] font-sans">
      <div className="flex min-w-0 flex-1 flex-col gap-4 overflow-y-auto px-5 pb-8 pt-6">
        <PageHeader
          onRestore={handleRestore}
          onRegenerate={handleGenerate}
          restoreDisabled={isGenerating}
          regenerateDisabled={isGenerating}
          regenerateLabel={orchRunning ? 'Agent 协作中...' : loading ? '生成中...' : '重新生成路径'}
        />

        {error ? (
          <div className="rounded-xl border border-[#FECACA] bg-[#FEF2F2] px-4 py-2 text-sm text-[#DC2626]">
            {error}
          </div>
        ) : null}

        {orchError ? (
          <div className="rounded-xl border border-[#FECACA] bg-[#FEF2F2] px-4 py-2 text-sm text-[#DC2626]">
            {orchError}
          </div>
        ) : null}

        <LearningGoalInput
          goal={inputGoal}
          cycle={cycle}
          cycleOptions={[...PATH_CYCLE_OPTIONS]}
          loading={isGenerating}
          onGoalChange={setInputGoal}
          onCycleChange={setCycle}
          onGenerate={handleGenerate}
        />

        {hasPath || showSkeleton ? <PathOverviewCards stats={overviewStats} /> : null}

        {hasPath ? (
          <>
            <ChapterAccordion
              chapters={chapters}
              selectedNodeId={selectedKpId}
              onSelectNode={handleSelectNode}
            />
            {planDescription ? (
              <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
                <h2 className="mb-2 text-lg font-bold text-[#0F2A5F]">路径说明</h2>
                <p className="text-sm leading-relaxed text-[#64748B]">{planDescription}</p>
              </section>
            ) : null}
          </>
        ) : null}

        {!hasPath && !isGenerating ? (
          <LearningPathEmpty
            onGenerate={focusGoalInput}
            onGoChat={() => navigate('/workspace')}
            loading={false}
          />
        ) : null}

        {showSkeleton ? <LearningPathSkeleton /> : null}
      </div>

      <PathAuxPanel
        selectedNode={hasPath ? selectedNode : null}
        todayGoal={todayGoal}
        onStartLearning={handleStartLearning}
        onGenerateNotes={handleGenerateNotes}
        onGenerateQuiz={handleGenerateQuiz}
      />
    </div>
  )
}
