import type { LucideIcon } from 'lucide-react'

export type TodayGoalItem = {
  label: string
  current: number
  total: number
  unit: string
  color: string
}

export type TodayGoalData = {
  progress: number
  items: TodayGoalItem[]
  currentStageTitle?: string
}

export type QuickToolItem = {
  id: string
  label: string
  icon: LucideIcon
  iconBg: string
  prompt: string
}

export type RecentLearningItem = {
  id: string
  title: string
  time: string
  icon: LucideIcon
  href: string
}

export type ChatSidebarData = {
  todayGoal: TodayGoalData
  quickTools: QuickToolItem[]
  recentLearning: RecentLearningItem[]
}
