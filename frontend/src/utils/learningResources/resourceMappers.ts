import {
  ClipboardCheck,
  Code2,
  FileText,
  Folder,
  Network,
  Pencil,
  PlaySquare,
} from 'lucide-react'
import type { PathNode } from '@/utils/types/learning-path'
import type { LearningResource, ResourceType } from '@/utils/types/media-resource'
import type {
  CoverageLegend,
  KnowledgeTopic,
  LatestResource,
  MyLibraryItem,
  RecommendedResource,
  ResourceCategory,
  ResourceStatItem,
  WeeklyPlanItem,
} from '@/utils/types/learningResources'

const STAT_TEMPLATE: Omit<ResourceStatItem, 'count'>[] = [
  { id: 'all', label: '全部资源', icon: Folder, iconBg: 'bg-[#EAF3FF]', iconColor: 'text-[#2563EB]' },
  { id: 'video', label: '视频课程', icon: PlaySquare, iconBg: 'bg-[#F5F3FF]', iconColor: 'text-[#8B5CF6]' },
  { id: 'document', label: '文档资料', icon: FileText, iconBg: 'bg-[#ECFDF5]', iconColor: 'text-[#22C55E]' },
  { id: 'quiz', label: '练习题库', icon: Pencil, iconBg: 'bg-[#FFF7ED]', iconColor: 'text-[#F59E0B]' },
  { id: 'project', label: '项目案例', icon: Code2, iconBg: 'bg-[#EFF6FF]', iconColor: 'text-[#3B82F6]' },
  { id: 'mindmap', label: '思维导图', icon: Network, iconBg: 'bg-[#FDF2F8]', iconColor: 'text-[#EC4899]' },
]

const TYPE_LABEL: Record<ResourceType, string> = {
  LESSON: '文档资料',
  QUIZ: '练习题库',
  CODE_CASE: '项目案例',
  ILLUSTRATION: '思维导图',
  VIDEO_CLIP: '视频课程',
}

const CATEGORY_MAP: Record<ResourceType, ResourceCategory> = {
  LESSON: 'document',
  QUIZ: 'quiz',
  CODE_CASE: 'project',
  ILLUSTRATION: 'mindmap',
  VIDEO_CLIP: 'video',
}

const CATEGORY_ICON: Record<ResourceCategory, typeof FileText> = {
  all: Folder,
  video: PlaySquare,
  document: FileText,
  quiz: Pencil,
  project: Code2,
  mindmap: Network,
}

const COVER_GRADIENTS = [
  'from-[#3B82F6] to-[#93C5FD]',
  'from-[#34D399] to-[#A7F3D0]',
  'from-[#FDBA74] to-[#FED7AA]',
  'from-[#A78BFA] to-[#DDD6FE]',
  'from-[#F472B6] to-[#FBCFE8]',
]

const WEEKDAY_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

function formatResourceDate(value?: string): string {
  if (!value) return new Date().toISOString().slice(0, 10)
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return new Date().toISOString().slice(0, 10)
  return parsed.toISOString().slice(0, 10)
}

export function learningResourceToLatest(item: LearningResource): LatestResource | null {
  if (!item.resourceType) return null

  const typeLabel = TYPE_LABEL[item.resourceType]
  if (!typeLabel) return null

  return {
    id: `api-${item.id ?? `${item.kpId}-${item.resourceType}-${item.title}`}`,
    title: item.title?.trim() || '未命名资源',
    description: item.content?.slice(0, 80) || 'AI 生成的学习资料',
    typeLabel,
    meta: item.mediaMime || 'AI 生成',
    date: formatResourceDate(),
    rating: 0,
    knowledgeId: item.kpId || '',
    category: CATEGORY_MAP[item.resourceType],
  }
}

export function buildResourceStats(items: LatestResource[]): ResourceStatItem[] {
  const counts: Record<ResourceCategory, number> = {
    all: items.length,
    video: 0,
    document: 0,
    quiz: 0,
    project: 0,
    mindmap: 0,
  }
  for (const item of items) {
    if (item.category !== 'all') counts[item.category] += 1
  }

  return STAT_TEMPLATE.map((template) => ({
    ...template,
    count: counts[template.id],
  }))
}

export function buildKnowledgeTopics(
  nodes: PathNode[],
  countByKp: Record<string, number>,
): KnowledgeTopic[] {
  return nodes.map((node) => ({
    id: node.kpId,
    title: node.title,
    count: countByKp[node.kpId] || 0,
    icon: node.completed ? ClipboardCheck : node.status === 'ACTIVE' ? PlaySquare : FileText,
  }))
}

export function latestToRecommended(item: LatestResource, index: number): RecommendedResource {
  const Icon = CATEGORY_ICON[item.category] || FileText
  return {
    id: item.id,
    type: item.category,
    typeLabel: item.typeLabel,
    title: item.title,
    description: item.description,
    difficulty: '基础',
    meta: item.meta,
    rating: item.rating,
    coverGradient: COVER_GRADIENTS[index % COVER_GRADIENTS.length],
    icon: Icon,
    knowledgeId: item.knowledgeId,
    category: item.category,
  }
}

export function buildGapRecommendations(
  nodes: PathNode[],
  countByKp: Record<string, number>,
  currentKpId?: string,
): RecommendedResource[] {
  return nodes
    .filter((node) => node.kpId !== currentKpId && (countByKp[node.kpId] || 0) === 0 && !node.completed)
    .slice(0, 4)
    .map((node, index) => ({
      id: `gap-${node.kpId}`,
      type: 'document',
      typeLabel: '待生成',
      title: `为「${node.title}」生成资料`,
      description: '该知识点暂无学习资源，可一键 AI 生成',
      difficulty: '基础',
      meta: '点击生成',
      rating: 0,
      coverGradient: COVER_GRADIENTS[index % COVER_GRADIENTS.length],
      icon: FileText,
      knowledgeId: node.kpId,
      category: 'document',
      isGapSuggestion: true,
    }))
}

export function buildRecommendedResources(
  latestItems: LatestResource[],
  nodes: PathNode[],
  countByKp: Record<string, number>,
  currentKpId?: string,
): RecommendedResource[] {
  const siblingItems = latestItems.filter((item) => item.knowledgeId && item.knowledgeId !== currentKpId)
  const fromExisting = siblingItems.slice(0, 4).map((item, index) => latestToRecommended(item, index))

  if (fromExisting.length >= 4) return fromExisting

  const gaps = buildGapRecommendations(nodes, countByKp, currentKpId)
  return [...fromExisting, ...gaps].slice(0, 4)
}

export function buildPathCoverage(nodes: PathNode[]): { percent: number; legend: CoverageLegend[] } {
  if (nodes.length === 0) {
    return {
      percent: 0,
      legend: [
        { label: '已掌握', percent: 0, color: '#22C55E' },
        { label: '学习中', percent: 0, color: '#2563EB' },
        { label: '未学习', percent: 100, color: '#CBD5E1' },
      ],
    }
  }

  const completed = nodes.filter((n) => n.completed || n.status === 'COMPLETED').length
  const active = nodes.filter(
    (n) => !n.completed && n.status !== 'COMPLETED' && (n.status === 'ACTIVE' || n.status === 'PENDING'),
  ).length
  const rest = Math.max(0, nodes.length - completed - active)

  const total = nodes.length
  const masteredPct = Math.round((completed / total) * 100)
  const learningPct = Math.round((active / total) * 100)
  const pendingPct = Math.max(0, 100 - masteredPct - learningPct)

  return {
    percent: masteredPct,
    legend: [
      { label: '已掌握', percent: masteredPct, color: '#22C55E' },
      { label: '学习中', percent: learningPct, color: '#2563EB' },
      { label: '未学习', percent: pendingPct || (rest > 0 ? Math.round((rest / total) * 100) : 0), color: '#CBD5E1' },
    ],
  }
}

export function buildWeeklyPlan(nodes: PathNode[]): WeeklyPlanItem[] {
  const pending = nodes.filter((n) => !n.completed && n.status !== 'COMPLETED' && n.status !== 'SKIPPED')
  return pending.slice(0, 5).map((node, index) => ({
    day: WEEKDAY_LABELS[index % WEEKDAY_LABELS.length],
    title: node.title,
    typeLabel: node.status === 'ACTIVE' ? '进行中' : '待学习',
  }))
}

export function buildMyLibrarySummary(
  favoriteCount: number,
  latestItems: LatestResource[],
): MyLibraryItem[] {
  const byCategory: Record<string, number> = {}
  for (const item of latestItems) {
    byCategory[item.category] = (byCategory[item.category] || 0) + 1
  }

  return [
    { id: 'fav', label: '收藏的资源', count: favoriteCount, icon: Folder, tone: 'orange' },
    { id: 'recent', label: '全部资源', count: latestItems.length, icon: PlaySquare, tone: 'blue' },
    { id: 'document', label: '文档资料', count: byCategory.document || 0, icon: FileText, tone: 'cyan' },
    { id: 'quiz', label: '练习题库', count: byCategory.quiz || 0, icon: Pencil, tone: 'purple' },
  ]
}

export function countResourcesByKp(items: LatestResource[]): Record<string, number> {
  const map: Record<string, number> = {}
  for (const item of items) {
    if (!item.knowledgeId) continue
    map[item.knowledgeId] = (map[item.knowledgeId] || 0) + 1
  }
  return map
}
