import type { GraphEdge, GraphNode, ImportanceLevel } from '@/types/knowledgeGraph'

const MIN_SIZE = 32
const MAX_SIZE = 100

const SIZE_BY_LEVEL: Record<ImportanceLevel, [number, number]> = {
  minor: [32, 40],
  normal: [44, 56],
  important: [60, 72],
  core: [76, 88],
}

function scoreToLevel(score: number, maxScore: number): ImportanceLevel {
  if (maxScore <= 0) return 'normal'
  const ratio = score / maxScore
  if (ratio >= 0.82) return 'core'
  if (ratio >= 0.58) return 'important'
  if (ratio >= 0.32) return 'normal'
  return 'minor'
}

function levelToSize(level: ImportanceLevel, normalized: number): number {
  const [min, max] = SIZE_BY_LEVEL[level]
  return min + normalized * (max - min)
}

export function computeDegreeMetrics(nodeId: string, edges: GraphEdge[]) {
  let degree = 0
  let inDegree = 0
  let outDegree = 0
  for (const edge of edges) {
    if (edge.source === nodeId) {
      degree += 1
      outDegree += 1
    }
    if (edge.target === nodeId) {
      degree += 1
      inDegree += 1
    }
  }
  return { degree, inDegree, outDegree }
}

export function calculateImportanceScore(node: GraphNode, edges: GraphEdge[]): number {
  if (node.importance != null && node.importance > 0) return node.importance

  const { degree, inDegree, outDegree } = computeDegreeMetrics(node.id, edges)
  return (
    degree * 2
    + inDegree * 1.5
    + outDegree * 1.5
    + (node.isLearningPathNode ? 6 : 0)
    + Math.min(node.resourceCount ?? 0, 10) * 0.5
    + Math.min(node.questionCount ?? 0, 20) * 0.25
    + Math.min(node.wrongQuestionCount ?? 0, 10) * 0.6
  )
}

export function getNodeSize(
  node: GraphNode,
  options?: { isSelected?: boolean; isCurrentLearning?: boolean },
): number {
  let size = node.size
  if (options?.isSelected) size = Math.min(MAX_SIZE, size * 1.1)
  if (options?.isCurrentLearning) size = Math.min(MAX_SIZE, Math.max(size, 88))
  return size
}

export function applyNodeImportance(
  nodes: GraphNode[],
  edges: GraphEdge[],
  currentLearningId?: string | null,
  selectedNodeId?: string | null,
): GraphNode[] {
  const scores = nodes.map((node) => calculateImportanceScore(node, edges))
  const minScore = Math.min(...scores)
  const maxScore = Math.max(...scores)

  return nodes.map((node, index) => {
    const importanceScore = scores[index]
    const importanceLevel = scoreToLevel(importanceScore, maxScore)
    const normalized = maxScore === minScore ? 0.5 : (importanceScore - minScore) / (maxScore - minScore)
    let size = levelToSize(importanceLevel, normalized)

    if (currentLearningId && node.id === currentLearningId) {
      size = Math.min(MAX_SIZE, Math.max(88, size * 1.08))
    }
    if (selectedNodeId && node.id === selectedNodeId) {
      size = Math.min(MAX_SIZE, size * 1.1)
    }

    const { degree, inDegree, outDegree } = computeDegreeMetrics(node.id, edges)
    return {
      ...node,
      importanceScore,
      importanceLevel,
      size,
      degree,
      inDegree,
      outDegree,
    }
  })
}

export { MIN_SIZE, MAX_SIZE, SIZE_BY_LEVEL }

/** Spec alias: compute importance scores and sizes for all nodes */
export const calculateNodeImportance = applyNodeImportance
