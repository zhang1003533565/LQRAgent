import http from './http'
import type { LearningPathDto } from '@/types/learning-path'

export type { PathNode, LearningPathDto } from '@/types/learning-path'

export async function getLearningPath(
  goal: string,
  currentKpId?: string,
): Promise<LearningPathDto> {
  const res = await http.get<{ data: LearningPathDto }>('/learning-path', {
    params: { goal, currentKpId },
  })
  return res.data.data
}
