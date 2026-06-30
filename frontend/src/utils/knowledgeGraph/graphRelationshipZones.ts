import type { GraphEdge, GraphNode, GraphViewMode } from '@/types/knowledgeGraph'
import { getDependents, getPrerequisites } from '@/utils/knowledgeGraph/graphRelations'

export type RelationshipZone =
  | 'center'
  | 'prerequisite'
  | 'successor'
  | 'sameModule'
  | 'resourcePractice'
  | 'mainPath'
  | 'related'

export interface ZoneGroup {
  zone: RelationshipZone
  label: string
  nodeIds: string[]
  hiddenCount: number
}

export interface VisibleGraphResult {
  visibleIds: Set<string>
  zoneByNodeId: Map<string, RelationshipZone>
  zones: ZoneGroup[]
  hiddenByZone: Map<RelationshipZone, number>
}

export const MAX_FOCUS_VISIBLE_NODES = 24

const ZONE_LABELS: Record<RelationshipZone, string> = {
  center: '当前知识',
  prerequisite: '前置知识',
  successor: '后续知识',
  sameModule: '同模块相关',
  resourcePractice: '练习与资源',
  mainPath: '主学习路径',
  related: '扩展关联',
}

function isResourcePracticeNode(node: GraphNode): boolean {
  return (node.resourceCount ?? 0) > 0
    || (node.questionCount ?? 0) > 0
    || (node.wrongQuestionCount ?? 0) > 0
}

export function classifyNodeZone(
  node: GraphNode,
  selectedId: string,
  edges: GraphEdge[],
  pathSet: Set<string>,
  selectedModule?: string,
): RelationshipZone {
  if (node.id === selectedId) return 'center'

  const prereqs = new Set(getPrerequisites(selectedId, edges))
  const deps = new Set(getDependents(selectedId, edges))

  if (prereqs.has(node.id)) return 'prerequisite'
  if (deps.has(node.id)) return 'successor'
  if (pathSet.has(node.id) && node.isLearningPathNode) return 'mainPath'

  if (selectedModule && (node.chapterId === selectedModule || node.moduleName === selectedModule)) {
    return 'sameModule'
  }

  if (isResourcePracticeNode(node)) return 'resourcePractice'
  return 'related'
}

function nodePriority(
  node: GraphNode,
  zone: RelationshipZone,
  selectedId: string,
  pathSet: Set<string>,
): number {
  if (node.id === selectedId) return 1000
  if (zone === 'mainPath') return 900
  if (zone === 'prerequisite') return 800 + node.importanceScore
  if (zone === 'successor') return 780 + node.importanceScore
  if (zone === 'sameModule') return 650 + node.importanceScore
  if (node.status === 'weak') return 520 + node.importanceScore
  if (pathSet.has(node.id)) return 500 + node.importanceScore
  if (zone === 'resourcePractice') return 420 + node.importanceScore
  if (node.importanceLevel === 'core') return 400 + node.importanceScore
  if (node.importanceLevel === 'important') return 300 + node.importanceScore
  if (zone === 'related') return 100 + node.importanceScore
  return node.importanceScore
}

export function getVisibleGraphElements(
  nodes: GraphNode[],
  edges: GraphEdge[],
  options: {
    viewMode: GraphViewMode
    selectedNodeId: string | null
    currentLearningId: string | null
    pathSet: Set<string>
    statusFilter?: string
    maxVisible?: number
  },
): VisibleGraphResult {
  const {
    viewMode,
    selectedNodeId,
    currentLearningId,
    pathSet,
    maxVisible = MAX_FOCUS_VISIBLE_NODES,
  } = options

  const anchorId = selectedNodeId ?? currentLearningId ?? nodes[0]?.id ?? null
  const zoneByNodeId = new Map<string, RelationshipZone>()
  const selectedNode = anchorId ? nodes.find((n) => n.id === anchorId) : null
  const selectedModule = selectedNode?.chapterId || selectedNode?.moduleName

  if (viewMode === 'full') {
    for (const node of nodes) {
      zoneByNodeId.set(node.id, anchorId
        ? classifyNodeZone(node, anchorId, edges, pathSet, selectedModule)
        : 'related')
    }
    const zones = buildZoneGroups(zoneByNodeId, nodes, new Map())
    return {
      visibleIds: new Set(nodes.map((n) => n.id)),
      zoneByNodeId,
      zones,
      hiddenByZone: new Map(),
    }
  }

  if (!anchorId || !selectedNode) {
    const sorted = [...nodes].sort((a, b) => b.importanceScore - a.importanceScore)
    const visible = new Set(sorted.slice(0, maxVisible).map((n) => n.id))
    for (const node of nodes) zoneByNodeId.set(node.id, 'related')
    const hidden = countHidden(nodes, visible, zoneByNodeId)
    return {
      visibleIds: visible,
      zoneByNodeId,
      zones: buildZoneGroups(zoneByNodeId, nodes, hidden),
      hiddenByZone: hidden,
    }
  }

  for (const node of nodes) {
    zoneByNodeId.set(
      node.id,
      classifyNodeZone(node, anchorId, edges, pathSet, selectedModule),
    )
  }

  const ranked = nodes
    .map((node) => ({
      node,
      zone: zoneByNodeId.get(node.id)!,
      priority: nodePriority(node, zoneByNodeId.get(node.id)!, anchorId, pathSet),
    }))
    .sort((a, b) => b.priority - a.priority)

  const visibleIds = new Set<string>()
  visibleIds.add(anchorId)

  for (const pathId of pathSet) {
    if (visibleIds.size >= maxVisible) break
    visibleIds.add(pathId)
  }

  for (const item of ranked) {
    if (visibleIds.size >= maxVisible) break
    if (item.node.id === anchorId) continue
    visibleIds.add(item.node.id)
  }

  const hiddenByZone = countHidden(nodes, visibleIds, zoneByNodeId)
  const zones = buildZoneGroups(zoneByNodeId, nodes, hiddenByZone)

  return { visibleIds, zoneByNodeId, zones, hiddenByZone }
}

function countHidden(
  nodes: GraphNode[],
  visibleIds: Set<string>,
  zoneByNodeId: Map<string, RelationshipZone>,
): Map<RelationshipZone, number> {
  const counts = new Map<RelationshipZone, number>()
  for (const node of nodes) {
    if (visibleIds.has(node.id)) continue
    const zone = zoneByNodeId.get(node.id) ?? 'related'
    counts.set(zone, (counts.get(zone) ?? 0) + 1)
  }
  return counts
}

function buildZoneGroups(
  zoneByNodeId: Map<string, RelationshipZone>,
  nodes: GraphNode[],
  hiddenByZone: Map<RelationshipZone, number>,
): ZoneGroup[] {
  const buckets = new Map<RelationshipZone, string[]>()
  for (const node of nodes) {
    const zone = zoneByNodeId.get(node.id) ?? 'related'
    const list = buckets.get(zone) ?? []
    list.push(node.id)
    buckets.set(zone, list)
  }

  const order: RelationshipZone[] = [
    'prerequisite',
    'center',
    'successor',
    'sameModule',
    'mainPath',
    'resourcePractice',
    'related',
  ]

  return order
    .filter((zone) => (buckets.get(zone)?.length ?? 0) > 0)
    .map((zone) => ({
      zone,
      label: ZONE_LABELS[zone],
      nodeIds: buckets.get(zone) ?? [],
      hiddenCount: hiddenByZone.get(zone) ?? 0,
    }))
}

export function getZoneLabel(zone: RelationshipZone): string {
  return ZONE_LABELS[zone]
}

export interface ZoneRect {
  zone: RelationshipZone
  label: string
  x: number
  y: number
  width: number
  height: number
}

export function getEdgeRelationLabel(edge: GraphEdge): string {
  if (edge.displayCategory === 'path_main') return '主路径'
  if (edge.displayCategory === 'path_segment') return '路径关联'
  if (edge.displayCategory === 'prerequisite') return '前置依赖'
  return '普通关联'
}
