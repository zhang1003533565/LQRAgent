import http from '@/api/http'
import type { ProfileDetail, ProfileSummary } from '@/utils/types/profile'

/**
 * 画像摘要（后端未实现时使用占位数据）。
 */
export async function getProfileSummary(): Promise<ProfileSummary> {
  try {
    const res = await http.get<{ data: ProfileSummary }>('/profile/summary')
    return res.data.data
  } catch {
    return {
      displayName: '学习者',
      masteryLevel: 0,
      completedKpCount: 0,
      weakTopics: [],
      streakDays: 0,
    }
  }
}

/**
 * 画像详情页数据。
 */
export async function getProfileDetail(): Promise<ProfileDetail> {
  try {
    const res = await http.get<{ data: ProfileDetail }>('/profile/detail')
    return res.data.data
  } catch {
    return {
      displayName: '学习者',
      username: '—',
      role: 'student',
      masteryLevel: 0,
      completedKpCount: 0,
      weakTopics: ['待学习知识点'],
      streakDays: 0,
      recentGoals: ['学习 Python 装饰器'],
      knowledgeMap: [
        { kpId: 'kp_placeholder_1', title: '示例知识点', mastery: 0 },
      ],
    }
  }
}
