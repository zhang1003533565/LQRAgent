import http from '@/api/http'
import type { BackendProfileDetail } from '@/utils/learningProfile/profileMappers'
import type {
  LearningAchievement,
  LearningTrendPoint,
  ProfileRange,
  TrendMetric,
} from '@/utils/types/learningProfile'

export type BackendProfileTrendPoint = {
  date: string
  overallMasteryRate?: number
  accuracyRate?: number
  learningDurationMinutes?: number
  completedNodeCount?: number
  completedQuestionCount?: number
}

export type BackendLearningAchievement = {
  id: string
  title: string
  description?: string
  achieved: boolean
  progress?: number
  target?: number
  level?: 'bronze' | 'silver' | 'gold' | 'platinum'
  achievedAt?: string
}

export async function fetchProfileDetailRaw(): Promise<BackendProfileDetail> {
  const res = await http.get<{ data: BackendProfileDetail }>('/profile/detail')
  return res.data.data
}

export async function refreshProfileRaw(): Promise<BackendProfileDetail> {
  await http.patch('/profile/summary', {})
  return fetchProfileDetailRaw()
}

export async function fetchProfileTrends(
  range: ProfileRange = '30d',
  metric: TrendMetric = 'mastery',
): Promise<BackendProfileTrendPoint[]> {
  const res = await http.get<{ data: BackendProfileTrendPoint[] }>('/profile/trends', {
    params: { range, metric },
  })
  return res.data.data
}

export async function fetchProfileAchievements(): Promise<BackendLearningAchievement[]> {
  const res = await http.get<{ data: BackendLearningAchievement[] }>('/profile/achievements')
  return res.data.data
}

export type ProfileExportResult = {
  format: string
  content?: string
  fileName: string
}

export async function exportProfile(format: 'markdown' | 'pdf' = 'markdown'): Promise<ProfileExportResult> {
  const res = await http.get<{ data: ProfileExportResult }>('/profile/export', {
    params: { format },
  })
  return res.data.data
}

export function mapApiTrendPoints(items: BackendProfileTrendPoint[]): LearningTrendPoint[] {
  return items.map((item) => ({
    date: item.date,
    overallMasteryRate: item.overallMasteryRate,
    accuracyRate: item.accuracyRate,
    learningDurationMinutes: item.learningDurationMinutes,
    completedNodeCount: item.completedNodeCount,
    completedQuestionCount: item.completedQuestionCount,
  }))
}

export function mapApiAchievements(items: BackendLearningAchievement[]): LearningAchievement[] {
  return items.map((item) => ({
    id: item.id,
    title: item.title,
    description: item.description,
    achieved: item.achieved,
    progress: item.progress,
    target: item.target,
    level: item.level,
    achievedAt: item.achievedAt,
  }))
}
