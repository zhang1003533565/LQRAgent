import type { GraphEdge, GraphNode, GraphViewMode, LayoutNode } from '@/types/knowledgeGraph'
import { getNeighborIds } from '@/utils/knowledgeGraph/graphRelations'
import {
  getRelationshipZoneLayoutByMode,
  type ZoneRect,
} from '@/utils/knowledgeGraph/graphRelationshipLayout'

export const DETAIL_PANEL_WIDTH = 350
export const DETAIL_PANEL_GAP = 24

export const GRAPH_SAFE_AREA = {
  top: 140,
  left: 200,
  right: 40,
  bottom: 120,
} as const

export interface LayoutBoundsOptions {
  /** @deprecated Panel no longer squeezes layout; use focusPanelOffset instead */
  detailPanelOpen?: boolean
  /** Horizontal offset (px) to shift camera focus left when a floating panel occludes the right side */
  focusPanelOffset?: number
}

export const FLOATING_PANEL_FOCUS_OFFSET = Math.round(DETAIL_PANEL_WIDTH / 2 + 20)

export function getLayoutRightReserve(options?: LayoutBoundsOptions): number {
  if (options?.detailPanelOpen === false) return GRAPH_SAFE_AREA.right
  return GRAPH_SAFE_AREA.right
}

export function getGraphViewCenter(
  width: number,
  height: number,
  options?: LayoutBoundsOptions,
): { x: number; y: number } {
  const leftPad = GRAPH_SAFE_AREA.left
  const topPad = GRAPH_SAFE_AREA.top
  const rightPad = getLayoutRightReserve(options)
  const bottomPad = GRAPH_SAFE_AREA.bottom
  return {
    x: leftPad + (width - leftPad - rightPad) / 2,
    y: topPad + (height - topPad - bottomPad) / 2,
  }
}

export function getUsableBounds(
  width: number,
  height: number,
  options?: LayoutBoundsOptions,
) {
  const minX = GRAPH_SAFE_AREA.left
  const minY = GRAPH_SAFE_AREA.top
  const maxX = width - getLayoutRightReserve(options)
  const maxY = height - GRAPH_SAFE_AREA.bottom
  const center = getGraphViewCenter(width, height, options)
  const usableW = Math.max(maxX - minX, 200)
  const usableH = Math.max(maxY - minY, 200)
  const maxRadius = Math.min(usableW, usableH) * 0.48
  return { minX, minY, maxX, maxY, center, usableW, usableH, maxRadius }
}

export function pickLayoutCenterNode(
  nodes: GraphNode[],
  options: {
    selectedNodeId?: string | null
    currentLearningId?: string | null
  },
): GraphNode | null {
  const { selectedNodeId, currentLearningId } = options
  if (selectedNodeId) {
    const selected = nodes.find((n) => n.id === selectedNodeId)
    if (selected) return selected
  }
  if (currentLearningId) {
    const current = nodes.find((n) => n.id === currentLearningId)
    if (current) return current
  }
  const byScore = [...nodes].sort((a, b) => b.importanceScore - a.importanceScore)[0]
  if (byScore) return byScore
  const byDegree = [...nodes].sort((a, b) => b.degree - a.degree)[0]
  return byDegree ?? nodes[0] ?? null
}

export function computeHopDistances(centerId: string, nodes: GraphNode[], edges: GraphEdge[]): Map<string, number> {
  const hops = new Map<string, number>()
  const queue: string[] = [centerId]
  hops.set(centerId, 0)

  while (queue.length > 0) {
    const id = queue.shift()!
    const depth = hops.get(id)!
    for (const neighbor of getNeighborIds(id, edges)) {
      if (!hops.has(neighbor)) {
        hops.set(neighbor, depth + 1)
        queue.push(neighbor)
      }
    }
  }

  for (const node of nodes) {
    if (!hops.has(node.id)) hops.set(node.id, 99)
  }
  return hops
}

/** 将 BFS 跳数归并到 3–4 个可视圈层，避免大量节点挤在同一环 */
function normalizeRingLayers(
  nodes: GraphNode[],
  hops: Map<string, number>,
  centerId: string,
): Map<string, number> {
  const others = nodes
    .filter((n) => n.id !== centerId)
    .sort((a, b) => {
      const hopDiff = (hops.get(a.id) ?? 99) - (hops.get(b.id) ?? 99)
      if (hopDiff !== 0) return hopDiff
      if (b.importanceScore !== a.importanceScore) return b.importanceScore - a.importanceScore
      return a.name.localeCompare(b.name)
    })

  const layerCount = others.length <= 8 ? 2 : others.length <= 18 ? 3 : 4
  const layers = new Map<string, number>()
  layers.set(centerId, 0)

  const perLayer = Math.ceil(others.length / layerCount)
  others.forEach((node, index) => {
    layers.set(node.id, Math.min(layerCount, Math.floor(index / Math.max(perLayer, 1)) + 1))
  })
  return layers
}

export function minRadiusForCount(count: number, maxRadius: number, avgNodeSize: number): number {
  if (count <= 1) return maxRadius * 0.28
  const circumference = count * (avgNodeSize + 36)
  const needed = circumference / (2 * Math.PI)
  return Math.min(maxRadius * 0.92, Math.max(maxRadius * 0.24, needed))
}

export function ringBaseRadius(layer: number, layerCount: number, maxRadius: number): number {
  if (layer <= 0) return 0
  const t = layer / layerCount
  return maxRadius * (0.28 + t * 0.62)
}

export function placeNodesOnRing(
  ringNodes: GraphNode[],
  radius: number,
  cx: number,
  cy: number,
  layerIndex: number,
): Map<string, { x: number; y: number }> {
  const positions = new Map<string, { x: number; y: number }>()
  if (ringNodes.length === 0) return positions

  const sorted = [...ringNodes].sort((a, b) => {
    const chapterDiff = (a.chapterId || '').localeCompare(b.chapterId || '')
    if (chapterDiff !== 0) return chapterDiff
    return a.name.localeCompare(b.name)
  })

  const step = (Math.PI * 2) / sorted.length
  const startAngle = -Math.PI / 2 + layerIndex * 0.18

  sorted.forEach((node, index) => {
    const angle = startAngle + index * step
    const stagger = (index % 2) * Math.min(22, radius * 0.06)
    const r = radius + stagger
    positions.set(node.id, {
      x: cx + Math.cos(angle) * r,
      y: cy + Math.sin(angle) * r,
    })
  })

  return positions
}

export function getRadialGraphLayout(
  nodes: GraphNode[],
  edges: GraphEdge[],
  width: number,
  height: number,
  options: {
    centerNodeId?: string | null
    selectedNodeId?: string | null
    currentLearningId?: string | null
    detailPanelOpen?: boolean
  } = {},
): LayoutNode[] {
  if (nodes.length === 0) return []

  const bounds = getUsableBounds(width, height, options)
  const centerNode = pickLayoutCenterNode(nodes, options)
  const centerId = options.centerNodeId ?? centerNode?.id ?? nodes[0].id
  const hops = computeHopDistances(centerId, nodes, edges)
  const layers = normalizeRingLayers(nodes, hops, centerId)
  const avgSize = nodes.reduce((sum, n) => sum + n.size, 0) / Math.max(nodes.length, 1)

  const layerGroups = new Map<number, GraphNode[]>()
  for (const node of nodes) {
    const layer = layers.get(node.id) ?? 1
    const group = layerGroups.get(layer) ?? []
    group.push(node)
    layerGroups.set(layer, group)
  }

  const layerCount = Math.max(...layerGroups.keys(), 1)
  const targetPositions = new Map<string, { x: number; y: number }>()
  targetPositions.set(centerId, { x: bounds.center.x, y: bounds.center.y })

  for (const [layer, ringNodes] of layerGroups) {
    if (layer === 0) continue
    const baseR = ringBaseRadius(layer, layerCount, bounds.maxRadius)
    const minR = minRadiusForCount(ringNodes.length, bounds.maxRadius, avgSize)
    const radius = Math.max(baseR, minR)
    const ringPositions = placeNodesOnRing(
      ringNodes,
      radius,
      bounds.center.x,
      bounds.center.y,
      layer,
    )
    for (const [id, pos] of ringPositions) targetPositions.set(id, pos)
  }

  return nodes.map((node) => {
    const target = targetPositions.get(node.id) ?? bounds.center
    return {
      ...node,
      x: target.x,
      y: target.y,
      vx: 0,
      vy: 0,
      radius: node.size / 2,
    }
  })
}

function relaxLayout(
  layoutNodes: LayoutNode[],
  targetMap: Map<string, { x: number; y: number }>,
  centerId: string | undefined,
  bounds: ReturnType<typeof getUsableBounds>,
): LayoutNode[] {
  const { center } = bounds

  for (let iteration = 0; iteration < 140; iteration += 1) {
    const cooling = 1 - iteration / 140

    for (let i = 0; i < layoutNodes.length; i += 1) {
      for (let j = i + 1; j < layoutNodes.length; j += 1) {
        const a = layoutNodes[i]
        const b = layoutNodes[j]
        const dx = b.x - a.x
        const dy = b.y - a.y
        const dist = Math.max(Math.sqrt(dx * dx + dy * dy), 0.01)
        const minDist = a.radius + b.radius + 34
        if (dist >= minDist) continue
        const push = ((minDist - dist) / dist) * 0.55 * cooling
        a.x -= dx * push
        a.y -= dy * push
        b.x += dx * push
        b.y += dy * push
      }
    }

    for (const node of layoutNodes) {
      if (node.id === centerId) {
        node.x = center.x
        node.y = center.y
        continue
      }

      const target = targetMap.get(node.id)
      if (target) {
        const pull = 0.014 * cooling
        node.x += (target.x - node.x) * pull
        node.y += (target.y - node.y) * pull
      }
    }
  }

  if (centerId) {
    const centerNode = layoutNodes.find((n) => n.id === centerId)
    if (centerNode) {
      centerNode.x = center.x
      centerNode.y = center.y
    }
  }

  return layoutNodes
}

export function runForceLayout(
  nodes: GraphNode[],
  edges: GraphEdge[],
  width: number,
  height: number,
  viewMode: GraphViewMode,
  options: {
    currentLearningId?: string | null
    selectedNodeId?: string | null
    detailPanelOpen?: boolean
    pathSet: Set<string>
    visibleIds: Set<string>
  },
): { nodes: LayoutNode[]; zoneRects: ZoneRect[] } {
  const result = getRelationshipZoneLayoutByMode(nodes, edges, width, height, viewMode, {
    selectedNodeId: options.selectedNodeId,
    currentLearningId: options.currentLearningId,
    pathSet: options.pathSet,
    visibleIds: options.visibleIds,
    detailPanelOpen: options.detailPanelOpen,
  })
  return { nodes: result.nodes, zoneRects: result.zoneRects }
}

export function computeFitViewport(
  nodes: LayoutNode[],
  width: number,
  height: number,
  padding = 48,
  options?: LayoutBoundsOptions,
): { zoom: number; panX: number; panY: number } {
  if (nodes.length === 0) return { zoom: 1, panX: 0, panY: 0 }

  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (const node of nodes) {
    minX = Math.min(minX, node.x - node.radius - 48)
    minY = Math.min(minY, node.y - node.radius - 32)
    maxX = Math.max(maxX, node.x + node.radius + 96)
    maxY = Math.max(maxY, node.y + node.radius + 32)
  }

  const graphW = Math.max(maxX - minX, 1)
  const graphH = Math.max(maxY - minY, 1)
  const leftPad = GRAPH_SAFE_AREA.left + padding
  const topPad = GRAPH_SAFE_AREA.top + padding
  const rightPad = getLayoutRightReserve(options) + padding
  const bottomPad = GRAPH_SAFE_AREA.bottom + padding
  const usableW = Math.max(width - leftPad - rightPad, 120)
  const usableH = Math.max(height - topPad - bottomPad, 120)
  const zoom = Math.min(usableW / graphW, usableH / graphH, 1.45)
  const cx = (minX + maxX) / 2
  const cy = (minY + maxY) / 2
  const viewCenter = getGraphViewCenter(width, height, options)
  const panX = viewCenter.x - cx * zoom
  const panY = viewCenter.y - cy * zoom

  return { zoom, panX, panY }
}

export function panToNode(
  node: LayoutNode,
  width: number,
  height: number,
  zoom: number,
  options?: LayoutBoundsOptions,
): { panX: number; panY: number } {
  const center = getGraphViewCenter(width, height, { detailPanelOpen: false })
  const offsetX = options?.focusPanelOffset ?? 0
  return {
    panX: center.x - offsetX - node.x * zoom,
    panY: center.y - node.y * zoom,
  }
}

export function screenToWorld(
  sx: number,
  sy: number,
  width: number,
  height: number,
  zoom: number,
  panX: number,
  panY: number,
): { x: number; y: number } {
  return {
    x: (sx - panX) / zoom,
    y: (sy - panY) / zoom,
  }
}

export function worldToScreen(
  wx: number,
  wy: number,
  zoom: number,
  panX: number,
  panY: number,
): { x: number; y: number } {
  return { x: wx * zoom + panX, y: wy * zoom + panY }
}
