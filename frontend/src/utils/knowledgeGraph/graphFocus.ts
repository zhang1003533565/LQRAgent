import type { GraphEdge, GraphNode, GraphViewMode } from '@/types/knowledgeGraph'
import { getNeighborIds } from '@/utils/knowledgeGraph/graphRelations'
import type { VisibleGraphResult } from '@/utils/knowledgeGraph/graphRelationshipZones'
import { GRAPH_VISUAL } from '@/utils/knowledgeGraph/graphVisualConfig'

export { GRAPH_VISUAL }

export interface FocusGraphView {
  viewMode: GraphViewMode
  prominentIds: Set<string>
  nodeAlpha: Map<string, number>
  edgeAlpha?: Map<string, number>
  anchorId: string | null
  currentLearningId: string | null
  hiddenByZone: Map<string, number>
}

export function resolveCurrentLearningKpId(
  pathNodes: { kpId: string; status?: string; completed?: boolean }[],
  graphNodes: GraphNode[],
): string | null {
  const active = pathNodes.find((n) => n.status === 'ACTIVE' && !n.completed)
  if (active) return active.kpId
  const next = pathNodes.find((n) => !n.completed)
  if (next) return next.kpId
  const learning = graphNodes.find((n) => n.status === 'learning' && n.isLearningPathNode)
  if (learning) return learning.id
  const anyLearning = graphNodes.find((n) => n.status === 'learning')
  return anyLearning?.id ?? null
}

export function computeFocusGraphView(
  nodes: GraphNode[],
  graphVisibility: VisibleGraphResult,
  options: {
    viewMode: GraphViewMode
    anchorId: string | null
    currentLearningId: string | null
  },
): FocusGraphView {
  const { viewMode, anchorId, currentLearningId } = options
  const prominentIds = new Set(graphVisibility.visibleIds)
  const nodeAlpha = new Map<string, number>()

  for (const node of nodes) {
    if (viewMode === 'full') {
      nodeAlpha.set(node.id, prominentIds.has(node.id) ? 1 : 0.28)
    } else if (prominentIds.has(node.id)) {
      nodeAlpha.set(node.id, 1)
    } else {
      nodeAlpha.set(node.id, 0.05)
    }
  }

  return {
    viewMode,
    prominentIds,
    nodeAlpha,
    anchorId,
    currentLearningId,
    hiddenByZone: graphVisibility.hiddenByZone,
  }
}

export interface EdgeDrawStyle {
  visible: boolean
  opacity: number
  width: number
  showArrow: boolean
  color: string
}

function isFocusLinked(
  edge: GraphEdge,
  focusId: string | null,
): boolean {
  if (!focusId) return false
  return edge.source === focusId || edge.target === focusId
}

export function resolveEdgeDrawStyle(
  edge: GraphEdge,
  view: FocusGraphView,
  hoverId: string | null,
  sourceVisible: boolean,
  targetVisible: boolean,
  selectedId?: string | null,
  allEdges?: GraphEdge[],
): EdgeDrawStyle {
  const bothVisible = sourceVisible && targetVisible
  if (!bothVisible) {
    return { visible: false, opacity: 0, width: 0, showArrow: false, color: GRAPH_VISUAL.edge.idle.stroke }
  }

  const { idle, activeHover, activeSelected, dimmed } = GRAPH_VISUAL.edge
  const focusId = hoverId ?? selectedId ?? null
  const hasFocus = Boolean(focusId)
  const hoverLinked = isFocusLinked(edge, hoverId)
  const selectedLinked = isFocusLinked(edge, selectedId ?? null)

  if (hoverLinked) {
    return {
      visible: true,
      opacity: activeHover.opacity,
      width: activeHover.width,
      showArrow: true,
      color: activeHover.stroke,
    }
  }

  if (selectedLinked) {
    return {
      visible: true,
      opacity: activeSelected.opacity,
      width: activeSelected.width,
      showArrow: true,
      color: activeSelected.stroke,
    }
  }

  if (hasFocus) {
    return {
      visible: true,
      opacity: dimmed.opacity,
      width: dimmed.width,
      showArrow: false,
      color: dimmed.stroke,
    }
  }

  const isPathMain = edge.displayCategory === 'path_main'
  return {
    visible: true,
    opacity: idle.opacity,
    width: idle.width,
    showArrow: isPathMain,
    color: idle.stroke,
  }
}

export function shouldDimOnHover(
  nodeId: string,
  hoverId: string | null,
  edges: GraphEdge[],
): boolean {
  if (!hoverId || nodeId === hoverId) return false
  return !getNeighborIds(hoverId, edges).has(nodeId)
}

export function getCurvedControlPoint(
  from: { x: number; y: number },
  to: { x: number; y: number },
  bend = 0.18,
): { x: number; y: number } {
  const midX = (from.x + to.x) / 2
  const midY = (from.y + to.y) / 2
  const dx = to.x - from.x
  const dy = to.y - from.y
  const distance = Math.max(Math.hypot(dx, dy), 1)
  const curve = Math.min(72, Math.max(18, distance * bend))
  return { x: midX - (dy / distance) * curve, y: midY + (dx / distance) * curve }
}
