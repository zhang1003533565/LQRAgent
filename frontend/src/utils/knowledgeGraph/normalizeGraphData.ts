import type { KnowledgeGraphData, KnowledgeGraphNode } from '@/api/student/knowledge'
import type { KpMasteryEntry } from '@/utils/knowledgeGraph/graphStatus'
import { resolveGraphNodeStatus } from '@/utils/knowledgeGraph/graphStatus'
import type { GraphEdge, GraphNode, GraphStats } from '@/types/knowledgeGraph'
import { applyNodeImportance } from '@/utils/knowledgeGraph/graphImportance'
import { buildGraphEdges } from '@/utils/knowledgeGraph/graphRelations'

function extractChapterName(node: KnowledgeGraphNode): string | undefined {
  if (node.description?.includes(' — ')) {
    return node.description.split(' — ')[0]?.trim()
  }
  return node.chapter || undefined
}

export function normalizeGraphData(
  raw: KnowledgeGraphData,
  options: {
    masteryByKp: Map<string, KpMasteryEntry>
    pathSet: Set<string>
    pathCompletedSet: Set<string>
    pathIndexMap: Map<string, number>
    currentLearningId?: string | null
    resourceCountByKp?: Map<string, number>
    questionCountByKp?: Map<string, number>
    wrongCountByKp?: Map<string, number>
  },
): { nodes: GraphNode[]; edges: GraphEdge[]; stats: GraphStats } {
  const edges = buildGraphEdges(raw.edges, options.pathSet, options.pathIndexMap)

  const baseNodes: GraphNode[] = raw.nodes.map((node) => {
    const entry = options.masteryByKp.get(node.kpId)
    const status = resolveGraphNodeStatus(
      node.kpId,
      options.masteryByKp,
      options.pathSet,
      options.pathCompletedSet,
    )
    const masteryRate = entry?.mastery ?? (
      status === 'mastered' ? 100 : status === 'learning' ? 50 : status === 'weak' ? 25 : 0
    )
    const pathOrder = options.pathIndexMap.get(node.kpId)
    return {
      id: node.kpId,
      name: node.title,
      type: node.subject,
      status,
      masteryRate,
      importanceScore: 0,
      importanceLevel: 'normal',
      size: 44,
      chapterId: node.chapter,
      chapterName: extractChapterName(node),
      moduleName: node.subject,
      description: node.description,
      difficulty: node.difficulty,
      isLearningPathNode: options.pathSet.has(node.kpId),
      pathOrder: pathOrder != null ? pathOrder + 1 : undefined,
      resourceCount: options.resourceCountByKp?.get(node.kpId),
      questionCount: options.questionCountByKp?.get(node.kpId),
      wrongQuestionCount: options.wrongCountByKp?.get(node.kpId),
      degree: 0,
      inDegree: 0,
      outDegree: 0,
      raw: node,
    }
  })

  const nodes = applyNodeImportance(baseNodes, edges, options.currentLearningId)

  const statusCounts = nodes.reduce(
    (acc, node) => {
      acc[node.status] += 1
      return acc
    },
    { mastered: 0, learning: 0, weak: 0, unlearned: 0 },
  )

  const edgeCategoryCounts = edges.reduce(
    (acc, edge) => {
      acc[edge.displayCategory] += 1
      return acc
    },
    { path_main: 0, path_segment: 0, prerequisite: 0, related: 0 },
  )

  const chapterMap = new Map<string, { id: string; name: string; count: number }>()
  for (const node of nodes) {
    const id = node.chapterId || 'unknown'
    const existing = chapterMap.get(id)
    if (existing) {
      existing.count += 1
    } else {
      chapterMap.set(id, { id, name: node.chapterName || id, count: 1 })
    }
  }

  const stats: GraphStats = {
    nodeCount: raw.nodeCount ?? nodes.length,
    edgeCount: raw.edgeCount ?? edges.length,
    statusCounts,
    edgeCategoryCounts,
    subjects: raw.subjects ?? [],
    chapters: [...chapterMap.values()].sort((a, b) => a.id.localeCompare(b.id)),
  }

  return { nodes, edges, stats }
}
