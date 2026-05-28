export type PathNodeStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'SKIPPED'

export interface PathNode {
  kpId: string
  title: string
  description: string
  order: number
  completed: boolean
  status?: PathNodeStatus
}

export interface LearningPathDto {
  goal: string
  nodes: PathNode[]
  planDescription: string
}
