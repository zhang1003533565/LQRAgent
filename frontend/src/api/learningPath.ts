import http from './http'

export interface PathNode {
  kpId: string
  title: string
  description: string
  order: number
  completed: boolean
}

export interface LearningPathDto {
  goal: string
  nodes: PathNode[]
  planDescription: string
}

export async function getLearningPath(
  goal: string,
  currentKpId?: string,
): Promise<LearningPathDto> {
  const res = await http.get<{ data: LearningPathDto }>('/learning-path', {
    params: { goal, currentKpId },
  })
  return res.data.data
}
