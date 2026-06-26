import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Sparkles } from 'lucide-react'
import { generateResource, getResources } from '@/api/student/resources'
import {
  GenerateResourceModal,
  KnowledgeResourceTabs,
  LatestResourceList,
  PageHeader,
  RecommendedResourceSection,
  ResourceStatsBar,
  ResourcesAuxPanel,
} from '@/components/student/workspace/learning-resources'
import NoPathGuide from '@/components/student/workspace/shared/NoPathGuide'
import {
  buildKnowledgeTopics,
  buildMyLibrarySummary,
  buildPathCoverage,
  buildRecommendedResources,
  buildResourceStats,
  buildWeeklyPlan,
  countResourcesByKp,
  learningResourceToLatest,
} from '@/utils/learningResources/resourceMappers'
import {
  getResourceFavoriteIds,
  setResourceFavorite,
} from '@/utils/learningResources/resourceFavoritesStorage'
import { trackBehavior } from '@/utils/tracker'
import { usePathStore } from '@/utils/store/pathStore'
import { useArtifactStore } from '@/utils/store/artifactStore'
import { syncWorkspaceFromSearchParams } from '@/utils/navigation/workspaceNav'
import type { LatestResource, ResourceCategory } from '@/utils/types/learningResources'
import type { LearningResource, ResourceType } from '@/utils/types/media-resource'

type SortKey = 'latest' | 'favorite' | 'rating'

function upsertResource(list: LearningResource[], next: LearningResource) {
  const sameIndex = list.findIndex(
    (item) =>
      (next.id != null && next.id > 0 && item.id === next.id) ||
      (item.kpId === next.kpId && item.resourceType === next.resourceType && item.title === next.title),
  )
  if (sameIndex < 0) return [next, ...list]
  return list.map((item, index) => (index === sameIndex ? next : item))
}

function filterResources(
  items: LatestResource[],
  {
    search,
    category,
    knowledgeId,
  }: { search: string; category: ResourceCategory; knowledgeId: string | null },
) {
  let result = items
  if (category !== 'all') {
    result = result.filter((item) => item.category === category)
  }
  if (knowledgeId) {
    result = result.filter((item) => item.knowledgeId === knowledgeId)
  }
  if (search.trim()) {
    const q = search.trim().toLowerCase()
    result = result.filter(
      (item) =>
        item.title.toLowerCase().includes(q) ||
        item.description.toLowerCase().includes(q) ||
        item.typeLabel.toLowerCase().includes(q),
    )
  }
  return result
}

function sortResources(items: LatestResource[], sortKey: SortKey, favorites: Set<string>) {
  const list = [...items]
  if (sortKey === 'rating') {
    return list.sort((a, b) => b.rating - a.rating)
  }
  if (sortKey === 'favorite') {
    return list.sort((a, b) => Number(favorites.has(b.id)) - Number(favorites.has(a.id)))
  }
  return list.sort((a, b) => b.date.localeCompare(a.date))
}

export default function LearningResourcesPage() {
  const navigate = useNavigate()
  const { selectedKpId, nodes, loading: pathLoading } = usePathStore()
  const chatResources = useArtifactStore((s) => s.resources)
  const resourceRefreshToken = useArtifactStore((s) => s.resourceRefreshToken)

  const [resourcesByKp, setResourcesByKp] = useState<Record<string, LearningResource[]>>({})
  const [search, setSearch] = useState('')
  const [activeCategory, setActiveCategory] = useState<ResourceCategory>('all')
  const [activeKnowledgeId, setActiveKnowledgeId] = useState<string | null>(null)
  const [sortKey, setSortKey] = useState<SortKey>('latest')
  const [favorites, setFavorites] = useState<Set<string>>(() => getResourceFavoriteIds())
  const [loading, setLoading] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [genKnowledge, setGenKnowledge] = useState('')
  const [genKpId, setGenKpId] = useState('')
  const [genTypes, setGenTypes] = useState<ResourceType[]>(['LESSON'])
  const [genDifficulty, setGenDifficulty] = useState('基础')
  const [genPrompt, setGenPrompt] = useState('')

  const fallbackNode = nodes[0]
  const kpId = selectedKpId || fallbackNode?.kpId
  const currentNode = nodes.find((node) => node.kpId === kpId) || fallbackNode
  const currentTitle = currentNode?.title || '未选择知识点'

  const [searchParams] = useSearchParams()
  useEffect(() => {
    syncWorkspaceFromSearchParams(searchParams)
  }, [searchParams])

  useEffect(() => {
    setGenKnowledge(currentTitle)
    setGenKpId(kpId || '')
    setGenPrompt(`帮我生成适合初学者的 ${currentTitle} 讲义，包含步骤和常见问题`)
  }, [currentTitle, kpId])

  const refreshAllResources = useCallback(async () => {
    if (nodes.length === 0) return
    setLoading(true)
    try {
      const entries = await Promise.all(
        nodes.map(async (node) => {
          try {
            const list = await getResources(node.kpId)
            return [node.kpId, list] as const
          } catch {
            return [node.kpId, []] as const
          }
        }),
      )
      setResourcesByKp(Object.fromEntries(entries))
      if (kpId) {
        const first = entries.find(([id]) => id === kpId)?.[1]?.[0]
        if (first) trackBehavior({ kpId, action: 'view_resource', extra: first.title })
      }
    } finally {
      setLoading(false)
    }
  }, [nodes, kpId])

  useEffect(() => {
    void refreshAllResources()
  }, [refreshAllResources])

  useEffect(() => {
    if (resourceRefreshToken > 0) {
      void refreshAllResources()
    }
  }, [resourceRefreshToken, refreshAllResources])

  const allLatestResources = useMemo(() => {
    const seen = new Set<string>()
    const items: LatestResource[] = []

    for (const node of nodes) {
      const apiList = resourcesByKp[node.kpId] || []
      const chatForKp = chatResources.filter((item) => item.kpId === node.kpId)
      const merged = chatForKp.reduce(upsertResource, apiList)

      for (const resource of merged) {
        const latest = learningResourceToLatest(resource)
        if (!latest) continue
        const dedupeKey = `${latest.knowledgeId}:${latest.title}`
        if (seen.has(dedupeKey)) continue
        seen.add(dedupeKey)
        items.push(latest)
      }
    }

    return items.sort((a, b) => b.date.localeCompare(a.date))
  }, [nodes, resourcesByKp, chatResources])

  const countByKp = useMemo(() => countResourcesByKp(allLatestResources), [allLatestResources])

  const hasChatResources = chatResources.some((item) => item.kpId === kpId)

  const resourceStats = useMemo(() => buildResourceStats(allLatestResources), [allLatestResources])

  const knowledgeTopics = useMemo(
    () => buildKnowledgeTopics(nodes, countByKp),
    [nodes, countByKp],
  )

  const recommendedResources = useMemo(() => {
    let list = buildRecommendedResources(allLatestResources, nodes, countByKp, kpId)
    if (search.trim()) {
      const q = search.trim().toLowerCase()
      list = list.filter(
        (item) =>
          item.title.toLowerCase().includes(q) ||
          item.description.toLowerCase().includes(q),
      )
    }
    if (activeCategory !== 'all') {
      list = list.filter((item) => item.category === activeCategory)
    }
    if (activeKnowledgeId) {
      list = list.filter((item) => item.knowledgeId === activeKnowledgeId)
    }
    return list
  }, [allLatestResources, nodes, countByKp, kpId, search, activeCategory, activeKnowledgeId])

  const latestResources = useMemo(() => {
    const filtered = filterResources(allLatestResources, {
      search,
      category: activeCategory,
      knowledgeId: activeKnowledgeId,
    })
    return sortResources(filtered, sortKey, favorites).slice(0, 20)
  }, [allLatestResources, search, activeCategory, activeKnowledgeId, sortKey, favorites])

  const pathCoverage = useMemo(() => buildPathCoverage(nodes), [nodes])
  const weeklyPlan = useMemo(() => buildWeeklyPlan(nodes), [nodes])
  const libraryItems = useMemo(
    () => buildMyLibrarySummary(favorites.size, allLatestResources),
    [favorites.size, allLatestResources],
  )

  const toggleFavorite = useCallback((id: string) => {
    setFavorites((prev) => {
      const next = new Set(prev)
      const willFavorite = !next.has(id)
      if (willFavorite) next.add(id)
      else next.delete(id)
      setResourceFavorite(id, willFavorite)
      return next
    })
  }, [])

  const toggleGenType = useCallback((type: ResourceType) => {
    setGenTypes((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type],
    )
  }, [])

  const openGenerateForKp = useCallback(
    (targetKpId: string) => {
      const node = nodes.find((n) => n.kpId === targetKpId)
      setGenKpId(targetKpId)
      setGenKnowledge(node?.title || targetKpId)
      setGenPrompt(`帮我生成适合初学者的 ${node?.title || '该知识点'} 讲义，包含步骤和常见问题`)
      setModalOpen(true)
    },
    [nodes],
  )

  const handleGenerateSubmit = useCallback(async () => {
    const targetKpId = genKpId || kpId
    if (!targetKpId || generating || genTypes.length === 0) return
    setGenerating(true)
    try {
      const prompt = `${genPrompt}\n知识点：${genKnowledge}\n难度：${genDifficulty}`
      const results = await Promise.all(
        genTypes.map((resourceType) => generateResource({ kpId: targetKpId, resourceType, prompt })),
      )
      setResourcesByKp((prev) => ({
        ...prev,
        [targetKpId]: results.reduce(upsertResource, prev[targetKpId] || []),
      }))
      setModalOpen(false)
    } finally {
      setGenerating(false)
    }
  }, [genKpId, kpId, generating, genTypes, genPrompt, genKnowledge, genDifficulty])

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
          loading={loading}
          onSearchChange={setSearch}
          onRefresh={() => void refreshAllResources()}
          onGenerate={() => setModalOpen(true)}
        />

        {currentNode ? (
          <div className="flex items-center gap-2 rounded-[14px] border border-[#D8E8FF] bg-[#F8FBFF] px-4 py-2.5 text-sm text-[#64748B]">
            <Sparkles className="h-4 w-4 shrink-0 text-[#2563EB]" />
            <span>
              当前学习阶段：<strong className="text-[#0F2A5F]">{currentTitle}</strong>
              {hasChatResources ? ' · 聊天已生成资源已合并展示' : ''}
              {allLatestResources.length > 0 ? ` · 路径共 ${allLatestResources.length} 份资源` : ' · 暂无资源，可点击生成'}
            </span>
          </div>
        ) : null}

        <ResourceStatsBar
          stats={resourceStats}
          activeCategory={activeCategory}
          onSelect={setActiveCategory}
        />

        <RecommendedResourceSection
          resources={recommendedResources}
          onResourceClick={(id, resource) => {
            if (resource?.isGapSuggestion) {
              openGenerateForKp(resource.knowledgeId)
              return
            }
            trackBehavior({ kpId, action: 'view_resource', extra: id })
          }}
        />

        <KnowledgeResourceTabs
          topics={knowledgeTopics}
          activeId={activeKnowledgeId}
          onSelect={setActiveKnowledgeId}
        />

        <LatestResourceList
          resources={latestResources}
          sortKey={sortKey}
          favorites={favorites}
          searchQuery={search}
          onSortChange={setSortKey}
          onToggleFavorite={toggleFavorite}
        />
      </div>

      <ResourcesAuxPanel
        libraryItems={libraryItems}
        coverage={pathCoverage}
        weeklyPlan={weeklyPlan}
        onViewCoverage={() => navigate('/workspace/learning-path')}
        onViewPlan={() => navigate('/workspace/learning-path')}
        onLibraryItemClick={(id) => {
          if (id === 'fav') setSortKey('favorite')
        }}
      />

      <GenerateResourceModal
        open={modalOpen}
        knowledgePoint={genKnowledge}
        selectedTypes={genTypes}
        difficulty={genDifficulty}
        prompt={genPrompt}
        generating={generating}
        onClose={() => setModalOpen(false)}
        onKnowledgeChange={setGenKnowledge}
        onToggleType={toggleGenType}
        onDifficultyChange={setGenDifficulty}
        onPromptChange={setGenPrompt}
        onSubmit={() => void handleGenerateSubmit()}
      />
    </div>
  )
}
