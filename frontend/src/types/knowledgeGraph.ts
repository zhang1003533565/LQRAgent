import type { KnowledgeGraphEdge, KnowledgeGraphNode } from '@/api/student/knowledge'
import type { GraphStatus } from '@/utils/knowledgeGraph/graphStatus'

export type GraphViewMode = 'path' | 'module' | 'full'

/** @deprecated use GraphViewMode */
export type GraphDisplayMode = 'path_focus' | 'full'

export type ImportanceLevel = 'core' | 'important' | 'normal' | 'minor'

export type EdgeDisplayCategory =
  | 'path_main'
  | 'path_segment'
  | 'prerequisite'
  | 'related'

export type EdgeFilter =
  | 'all'
  | 'path_main'
  | 'path_segment'
  | 'prerequisite'
  | 'prerequisite_in'
  | 'prerequisite_out'

export interface GraphNode {
  id: string
  name: string
  type?: string
  status: GraphStatus
  masteryRate: number
  importance?: number
  importanceScore: number
  importanceLevel: ImportanceLevel
  size: number
  chapterId?: string
  chapterName?: string
  moduleName?: string
  description?: string
  difficulty?: number
  isLearningPathNode: boolean
  pathOrder?: number
  resourceCount?: number
  questionCount?: number
  wrongQuestionCount?: number
  degree: number
  inDegree: number
  outDegree: number
  raw: KnowledgeGraphNode
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  type: string
  displayCategory: EdgeDisplayCategory
  label?: string
  direction: 'forward'
  raw: KnowledgeGraphEdge
}

export interface GraphStats {
  nodeCount: number
  edgeCount: number
  statusCounts: Record<GraphStatus, number>
  edgeCategoryCounts: Record<EdgeDisplayCategory, number>
  subjects: string[]
  chapters: { id: string; name: string; count: number }[]
}

export interface LayoutNode extends GraphNode {
  x: number
  y: number
  vx: number
  vy: number
  radius: number
}

export interface GraphViewport {
  zoom: number
  panX: number
  panY: number
}

export interface GraphHighlightState {
  selectedId: string | null
  hoveredId: string | null
  hoveredEdgeId: string | null
  searchHighlightIds: Set<string>
  currentLearningId: string | null
  displayMode: GraphViewMode
}
