import type { KnowledgeGraphEdge } from '@/api/student/knowledge'
import type { EdgeDisplayCategory, GraphEdge } from '@/types/knowledgeGraph'

export function classifyEdge(
  edge: KnowledgeGraphEdge,
  pathSet: Set<string>,
  pathIndexMap: Map<string, number>,
): EdgeDisplayCategory {
  const fromOnPath = pathSet.has(edge.fromKpId)
  const toOnPath = pathSet.has(edge.toKpId)
  if (fromOnPath && toOnPath) {
    const fromIdx = pathIndexMap.get(edge.fromKpId)
    const toIdx = pathIndexMap.get(edge.toKpId)
    if (fromIdx !== undefined && toIdx !== undefined && Math.abs(fromIdx - toIdx) === 1) {
      return 'path_main'
    }
    return 'path_segment'
  }
  const rel = (edge.relationType || 'PREREQUISITE').toUpperCase()
  if (rel === 'PREREQUISITE' || rel === 'DEPENDS_ON') return 'prerequisite'
  return 'related'
}

export function buildGraphEdges(
  edges: KnowledgeGraphEdge[],
  pathSet: Set<string>,
  pathIndexMap: Map<string, number>,
): GraphEdge[] {
  return edges.map((edge, index) => {
    const displayCategory = classifyEdge(edge, pathSet, pathIndexMap)
    return {
      id: `${edge.fromKpId}->${edge.toKpId}:${index}`,
      source: edge.fromKpId,
      target: edge.toKpId,
      type: edge.relationType || 'PREREQUISITE',
      displayCategory,
      label: displayCategory === 'prerequisite' ? '前置依赖' : displayCategory === 'path_main' ? '路径' : undefined,
      direction: 'forward',
      raw: edge,
    }
  })
}

export function getNeighborIds(nodeId: string, edges: GraphEdge[]): Set<string> {
  const set = new Set<string>()
  for (const edge of edges) {
    if (edge.source === nodeId) set.add(edge.target)
    if (edge.target === nodeId) set.add(edge.source)
  }
  return set
}

export function getNeighborMap(edges: GraphEdge[]): Map<string, Set<string>> {
  const map = new Map<string, Set<string>>()
  for (const edge of edges) {
    if (!map.has(edge.source)) map.set(edge.source, new Set())
    if (!map.has(edge.target)) map.set(edge.target, new Set())
    map.get(edge.source)!.add(edge.target)
    map.get(edge.target)!.add(edge.source)
  }
  return map
}

export function getPrerequisites(nodeId: string, edges: GraphEdge[]): string[] {
  return edges.filter((e) => e.target === nodeId).map((e) => e.source)
}

export function getDependents(nodeId: string, edges: GraphEdge[]): string[] {
  return edges.filter((e) => e.source === nodeId).map((e) => e.target)
}

export function edgeMatchesFilter(
  edge: GraphEdge,
  filter: import('@/types/knowledgeGraph').EdgeFilter,
  selectedId: string | null,
): boolean {
  if (filter === 'all') return true
  if (filter === 'path_main') return edge.displayCategory === 'path_main'
  if (filter === 'path_segment') return edge.displayCategory === 'path_segment' || edge.displayCategory === 'path_main'
  if (filter === 'prerequisite') return edge.displayCategory === 'prerequisite'
  if (filter === 'prerequisite_in' && selectedId) return edge.target === selectedId
  if (filter === 'prerequisite_out' && selectedId) return edge.source === selectedId
  return true
}
