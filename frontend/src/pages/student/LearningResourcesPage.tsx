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
import {
  MOCK_KNOWLEDGE_TOPICS,
  MOCK_LATEST_RESOURCES,
  MOCK_RECOMMENDED,
  MOCK_RESOURCE_STATS,
} from '@/mock/learningResources'
import { trackBehavior } from '@/utils/tracker'
import { usePathStore } from '@/utils/store/pathStore'
import { useArtifactStore } from '@/utils/store/artifactStore'
import { syncKpFromSearchParams } from '@/utils/navigation/workspaceNav'
import type { LatestResource, ResourceCategory } from '@/mock/learningResources'
import type { LearningResource, ResourceType } from '@/utils/types/media-resource'

type SortKey = 'latest' | 'favorite' | 'rating'

function upsertResource(list: LearningResource[], next: LearningResource) {
  const sameIndex = list.findIndex(
    (item) =>
      (next.id > 0 && item.id === next.id) ||
      (item.kpId === next.kpId && item.resourceType === next.resourceType && item.title === next.title),
  )
  if (sameIndex < 0) return [next, ...list]
  return list.map((item, index) => (index === sameIndex ? next : item))
}

function apiResourceToLatest(item: LearningResource): LatestResource | null {
  if (!item.resourceType) return null

  const typeMap: Record<ResourceType, string> = {
    LESSON: '文档资料',
    QUIZ: '练习题库',
    CODE_CASE: '项目案例',
    ILLUSTRATION: '思维导图',
    VIDEO_CLIP: '视频课程',
  }
  const categoryMap: Record<ResourceType, ResourceCategory> = {
    LESSON: 'document',
    QUIZ: 'quiz',
    CODE_CASE: 'project',
    ILLUSTRATION: 'mindmap',
    VIDEO_CLIP: 'video',
  }

  const typeLabel = typeMap[item.resourceType]
  if (!typeLabel) return null

  return {
    id: `api-${item.id ?? Date.now()}`,
    title: item.title?.trim() || '未命名资源',
    description: item.content?.slice(0, 60) || 'AI 生成的学习资料',
    typeLabel,
    meta: item.mediaMime || 'AI 生成',
    date: new Date().toISOString().slice(0, 10),
    rating: 4.8,
    knowledgeId: item.kpId || '',
    category: categoryMap[item.resourceType],
  }
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
  const { selectedKpId, nodes } = usePathStore()
  const chatResources = useArtifactStore((s) => s.resources)
  const resourceRefreshToken = useArtifactStore((s) => s.resourceRefreshToken)

  const [apiResources, setApiResources] = useState<LearningResource[]>([])
  const [search, setSearch] = useState('')
  const [activeCategory, setActiveCategory] = useState<ResourceCategory>('all')
  const [activeKnowledgeId, setActiveKnowledgeId] = useState<string | null>(null)
  const [sortKey, setSortKey] = useState<SortKey>('latest')
  const [favorites, setFavorites] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [genKnowledge, setGenKnowledge] = useState('')
  const [genTypes, setGenTypes] = useState<ResourceType[]>(['LESSON'])
  const [genDifficulty, setGenDifficulty] = useState('基础')
  const [genPrompt, setGenPrompt] = useState('')

  const fallbackNode = nodes[1] || nodes[0]
  const kpId = selectedKpId || fallbackNode?.kpId || 'python_basic_syntax'
  const currentNode = nodes.find((node) => node.kpId === kpId) || fallbackNode
  const currentTitle = currentNode?.title || 'Python 简介与环境搭建'

  const [searchParams] = useSearchParams()
  useEffect(() => {
    syncKpFromSearchParams(searchParams)
  }, [searchParams])

  useEffect(() => {
    setGenKnowledge(currentTitle)
    setGenPrompt(`帮我生成适合初学者的 ${currentTitle} 讲义，包含步骤和常见问题`)
  }, [currentTitle])

  const refreshResources = useCallback(async () => {
    if (!kpId) return
    setLoading(true)
    try {
      const next = await getResources(kpId)
      setApiResources(next)
      const first = next[0]
      if (first) trackBehavior({ kpId, action: 'view_resource', extra: first.title })
    } finally {
      setLoading(false)
    }
  }, [kpId])

  useEffect(() => {
    void refreshResources()
  }, [refreshResources])

  useEffect(() => {
    if (resourceRefreshToken > 0) {
      void refreshResources()
    }
  }, [resourceRefreshToken, refreshResources])

  const mergedApiLatest = useMemo(() => {
    const chatForKp = chatResources.filter((item) => item.kpId === kpId)
    const merged = chatForKp.reduce(upsertResource, apiResources)
    return merged
      .map(apiResourceToLatest)
      .filter((item): item is LatestResource => item != null)
  }, [chatResources, kpId, apiResources])

  const hasChatResources = chatResources.some((item) => item.kpId === kpId)

  const recommendedResources = useMemo(() => {
    let list = MOCK_RECOMMENDED
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
  }, [search, activeCategory, activeKnowledgeId])

  const latestResources = useMemo(() => {
    const combined = [...mergedApiLatest, ...MOCK_LATEST_RESOURCES]
    const seen = new Set<string>()
    const deduped = combined.filter((item) => {
      if (seen.has(item.title)) return false
      seen.add(item.title)
      return true
    })
    const filtered = filterResources(deduped, {
      search,
      category: activeCategory,
      knowledgeId: activeKnowledgeId,
    })
    return sortResources(filtered, sortKey, favorites).slice(0, 5)
  }, [mergedApiLatest, search, activeCategory, activeKnowledgeId, sortKey, favorites])

  const toggleFavorite = useCallback((id: string) => {
    setFavorites((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  const toggleGenType = useCallback((type: ResourceType) => {
    setGenTypes((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type],
    )
  }, [])

  const handleGenerateSubmit = useCallback(async () => {
    if (!kpId || generating || genTypes.length === 0) return
    setGenerating(true)
    try {
      const prompt = `${genPrompt}\n知识点：${genKnowledge}\n难度：${genDifficulty}`
      const results = await Promise.all(
        genTypes.map((resourceType) => generateResource({ kpId, resourceType, prompt })),
      )
      setApiResources((prev) => results.reduce(upsertResource, prev))
      setModalOpen(false)
    } finally {
      setGenerating(false)
    }
  }, [kpId, generating, genTypes, genPrompt, genKnowledge, genDifficulty])

  return (
    <div className="flex h-full min-h-0 overflow-hidden bg-[#F6F9FE] font-sans">
      <div className="flex min-w-0 flex-1 flex-col gap-4 overflow-y-auto px-5 pb-8 pt-6">
        <PageHeader
          search={search}
          loading={loading}
          onSearchChange={setSearch}
          onRefresh={() => void refreshResources()}
          onGenerate={() => setModalOpen(true)}
        />

        {currentNode ? (
          <div className="flex items-center gap-2 rounded-[14px] border border-[#D8E8FF] bg-[#F8FBFF] px-4 py-2.5 text-sm text-[#64748B]">
            <Sparkles className="h-4 w-4 shrink-0 text-[#2563EB]" />
            <span>
              当前学习阶段：<strong className="text-[#0F2A5F]">{currentTitle}</strong>
              {hasChatResources ? ' · 聊天已生成资源已合并展示' : ''}
            </span>
          </div>
        ) : null}

        <ResourceStatsBar
          stats={MOCK_RESOURCE_STATS}
          activeCategory={activeCategory}
          onSelect={setActiveCategory}
        />

        <RecommendedResourceSection
          resources={recommendedResources}
          onResourceClick={(id) => trackBehavior({ kpId, action: 'view_resource', extra: id })}
        />

        <KnowledgeResourceTabs
          topics={MOCK_KNOWLEDGE_TOPICS}
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
        onViewCoverage={() => navigate('/workspace/learning-path')}
        onViewPlan={() => navigate('/workspace/learning-path')}
        onLibraryItemClick={() => undefined}
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
