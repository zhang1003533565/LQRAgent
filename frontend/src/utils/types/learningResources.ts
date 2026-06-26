import type { LucideIcon } from 'lucide-react'

export type ResourceCategory =
  | 'all'
  | 'video'
  | 'document'
  | 'quiz'
  | 'project'
  | 'mindmap'

export type ResourceDifficulty = '基础' | '初级' | '中级' | '高级'

export interface ResourceStatItem {
  id: ResourceCategory
  label: string
  count: number
  icon: LucideIcon
  iconBg: string
  iconColor: string
}

export interface RecommendedResource {
  id: string
  type: string
  typeLabel: string
  title: string
  description: string
  difficulty: ResourceDifficulty
  meta: string
  rating: number
  coverGradient: string
  icon: LucideIcon
  knowledgeId: string
  category: ResourceCategory
  /** 路径缺口推荐：点击打开生成弹窗 */
  isGapSuggestion?: boolean
}

export interface KnowledgeTopic {
  id: string
  title: string
  count: number
  icon: LucideIcon
}

export interface LatestResource {
  id: string
  title: string
  description: string
  typeLabel: string
  difficulty?: string
  meta: string
  date: string
  rating: number
  knowledgeId: string
  category: ResourceCategory
  favorited?: boolean
}

export interface MyLibraryItem {
  id: string
  label: string
  count: number
  icon: LucideIcon
  tone: 'orange' | 'blue' | 'cyan' | 'purple'
}

export interface CoverageLegend {
  label: string
  percent: number
  color: string
}

export interface WeeklyPlanItem {
  day: string
  title: string
  typeLabel: string
}

export const GENERATE_MATERIAL_TYPES = [
  { id: 'LESSON', label: '讲义' },
  { id: 'QUIZ', label: '练习题' },
  { id: 'ILLUSTRATION', label: '思维导图' },
  { id: 'CODE_CASE', label: '代码示例' },
  { id: 'VIDEO_CLIP', label: '学习视频脚本' },
] as const

export const GENERATE_DIFFICULTIES = ['基础', '进阶', '挑战'] as const
