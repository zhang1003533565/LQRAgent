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
