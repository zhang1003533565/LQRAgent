import type { LucideIcon } from 'lucide-react'

export type NodeDifficulty = '简单' | '中等' | '困难'
export type LearningNodeStatus = 'current' | 'completed' | 'locked' | 'pending'

export interface LearningPathNodeItem {
  id: string
  order: number
  title: string
  description: string
  status: LearningNodeStatus
  difficulty: NodeDifficulty
  durationMinutes: number
  objectives: string[]
}

export interface LearningChapter {
  id: string
  index: number
  title: string
  description?: string
  nodes: LearningPathNodeItem[]
}

export interface PathOverviewStat {
  id: string
  label: string
  value: string
  icon: LucideIcon
  iconBg: string
  iconColor: string
}

/** 路径生成周期选项（传给编排器 cycle 参数） */
export const PATH_CYCLE_OPTIONS = ['1 周', '2 周', '4 周', '8 周'] as const
