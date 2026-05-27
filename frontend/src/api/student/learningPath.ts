import http from '@/api/http'
import type { LearningPathDto } from '@/utils/types/learning-path'

export type { PathNode, LearningPathDto } from '@/utils/types/learning-path'

export async function getLearningPath(
  goal: string,
  currentKpId?: string,
): Promise<LearningPathDto> {
  const res = await http.get<{ data: LearningPathDto }>('/learning-path', {
    params: { goal, currentKpId },
  })
  return res.data.data
}
