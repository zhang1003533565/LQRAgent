import type { GraphEdge, GraphNode, GraphViewMode, LayoutNode } from '@/types/knowledgeGraph'
import {
  type RelationshipZone,
  classifyNodeZone,
} from '@/utils/knowledgeGraph/graphRelationshipZones'
import {
  type LayoutBoundsOptions,
  computeHopDistances,
  getGraphViewCenter,
  getUsableBounds,
  minRadiusForCount,
  pickLayoutCenterNode,
  placeNodesOnRing,
  ringBaseRadius,
} from '@/utils/knowledgeGraph/graphLayout'

export const LAYOUT_CONSTANTS = {
  centerNodeSize: 92,
  /** Expanded ~1.4× for label breathing room */
  coreNodeDistance: 252,
  firstHopDistance: 308,
  secondHopDistance: 476,
  minNodeGap: 45,
  clusterGap: 126,
  selectedClearRadius: 168,
  /** forceCollide padding — each node repels at radius + this value */
  collidePadding: 20,
  forceIterations: 180,
  /** Soft center gravity (equal X/Y — no directional forceY) */
  forceCenterStrength: 0.007,
  /** Weak spring back toward zone seed positions */
  forceLinkStrength: 0.012,
} as const

export interface ZoneLayoutResult {
  nodes: LayoutNode[]
  zoneRects: ZoneRect[]
}

export interface ZoneRect {
  zone: RelationshipZone
  label: string
  x: number
  y: number
  width: number
  height: number
}

const ZONE_TITLES: Record<RelationshipZone, string> = {
  center: '当前知识',
  prerequisite: '前置知识',
  successor: '后续知识',
  sameModule: '同模块相关',
  resourcePractice: '练习与资源',
  mainPath: '主学习路径',
  related: '扩展关联',
}

function placeInRow(
  items: GraphNode[],
  cx: number,
  cy: number,
  direction: 'horizontal' | 'vertical',
  gap: number,
): Map<string, { x: number; y: number }> {
  const positions = new Map<string, { x: number; y: number }>()
  if (items.length === 0) return positions

  const totalSpan = items.reduce((sum, n) => sum + n.size + gap, -gap)
  let cursor = direction === 'horizontal' ? cx - totalSpan / 2 : cy - totalSpan / 2

  for (const node of items) {
    if (direction === 'horizontal') {
      positions.set(node.id, { x: cursor + node.size / 2, y: cy })
      cursor += node.size + gap
    } else {
      positions.set(node.id, { x: cx, y: cursor + node.size / 2 })
      cursor += node.size + gap
    }
  }
  return positions
}

function placeOnArc(
  items: GraphNode[],
  cx: number,
  cy: number,
  radius: number,
  startAngle: number,
  endAngle: number,
): Map<string, { x: number; y: number }> {
  const positions = new Map<string, { x: number; y: number }>()
  if (items.length === 0) return positions

  items.forEach((node, index) => {
    const t = items.length <= 1 ? 0.5 : index / (items.length - 1)
    const angle = startAngle + (endAngle - startAngle) * t
    positions.set(node.id, {
      x: cx + Math.cos(angle) * radius,
      y: cy + Math.sin(angle) * radius,
    })
  })
  return positions
}

function buildZoneRects(
  bounds: ReturnType<typeof getUsableBounds>,
  center: { x: number; y: number },
  groups: Map<RelationshipZone, GraphNode[]>,
): ZoneRect[] {
  const { minX, maxX, minY, maxY } = bounds
  const midW = (maxX - minX) * 0.42
  const midH = (maxY - minY) * 0.28
  const rects: ZoneRect[] = []

  const add = (zone: RelationshipZone, x: number, y: number, w: number, h: number) => {
    if ((groups.get(zone)?.length ?? 0) === 0) return
    rects.push({ zone, label: ZONE_TITLES[zone], x, y, width: w, height: h })
  }

  add('sameModule', center.x - midW / 2, minY + 8, midW, midH * 0.85)
  add('prerequisite', minX + 12, center.y - midH, midW * 0.95, midH * 1.2)
  add('successor', center.x + LAYOUT_CONSTANTS.firstHopDistance * 0.55, center.y - midH, midW * 0.95, midH * 1.2)
  add('resourcePractice', center.x - midW / 2, center.y + LAYOUT_CONSTANTS.firstHopDistance * 0.65, midW, midH * 0.9)
  add('related', minX + 16, maxY - midH * 0.75, maxX - minX - 32, midH * 0.65)

  return rects
}

function placeOnRing(
  items: GraphNode[],
  cx: number,
  cy: number,
  radius: number,
  startAngle = -Math.PI / 2,
): Map<string, { x: number; y: number }> {
  const positions = new Map<string, { x: number; y: number }>()
  if (items.length === 0) return positions

  items.forEach((node, index) => {
    const angle = startAngle + (index / items.length) * Math.PI * 2
    positions.set(node.id, {
      x: cx + Math.cos(angle) * radius,
      y: cy + Math.sin(angle) * radius,
    })
  })
  return positions
}

function runForceSimulation(
  layoutNodes: LayoutNode[],
  options: {
    centerId?: string
    layoutCenter: { x: number; y: number }
    forceCenter: { x: number; y: number }
    seedPositions: Map<string, { x: number; y: number }>
  },
): LayoutNode[] {
  const {
    centerId,
    layoutCenter,
    forceCenter,
    seedPositions,
  } = options
  const {
    collidePadding,
    forceIterations,
    forceCenterStrength,
    forceLinkStrength,
    selectedClearRadius,
  } = LAYOUT_CONSTANTS

  for (let iter = 0; iter < forceIterations; iter += 1) {
    const cooling = 1 - iter / forceIterations

    if (centerId) {
      const c = layoutNodes.find((n) => n.id === centerId)
      if (c) {
        c.x = layoutCenter.x
        c.y = layoutCenter.y
      }
    }

    // forceCollide — isotropic 2D repulsion
    for (let i = 0; i < layoutNodes.length; i += 1) {
      for (let j = i + 1; j < layoutNodes.length; j += 1) {
        const a = layoutNodes[i]
        const b = layoutNodes[j]
        const dx = b.x - a.x
        const dy = b.y - a.y
        const dist = Math.max(Math.hypot(dx, dy), 0.01)
        const minDist = a.radius + b.radius + collidePadding
        if (dist >= minDist) continue
        const push = ((minDist - dist) / dist) * 0.9 * cooling
        if (a.id !== centerId) {
          a.x -= dx * push
          a.y -= dy * push
        }
        if (b.id !== centerId) {
          b.x += dx * push
          b.y += dy * push
        }
      }
    }

    for (const node of layoutNodes) {
      if (node.id === centerId) continue

      // Keep anchor zone clear — radial push (not Y-only)
      const cdx = node.x - layoutCenter.x
      const cdy = node.y - layoutCenter.y
      const cdist = Math.hypot(cdx, cdy)
      if (cdist < selectedClearRadius && cdist > 0.01) {
        const push = ((selectedClearRadius - cdist) / cdist) * 0.42 * cooling
        node.x += cdx * push
        node.y += cdy * push
      }

      // Soft forceCenter — equal X/Y pull toward geometric canvas center
      node.x += (forceCenter.x - node.x) * forceCenterStrength * cooling
      node.y += (forceCenter.y - node.y) * forceCenterStrength * cooling

      // Weak link to zone seed — preserves semantics while allowing 360° spread
      const seed = seedPositions.get(node.id)
      if (seed) {
        node.x += (seed.x - node.x) * forceLinkStrength * cooling
        node.y += (seed.y - node.y) * forceLinkStrength * cooling
      }
    }
  }

  return layoutNodes
}

function collectHubNodeIds(
  nodes: GraphNode[],
  anchorId: string | undefined,
  options: {
    selectedNodeId?: string | null
    currentLearningId?: string | null
  },
): Set<string> {
  const ids = new Set<string>()
  if (anchorId) ids.add(anchorId)
  if (options.selectedNodeId) ids.add(options.selectedNodeId)
  if (options.currentLearningId) ids.add(options.currentLearningId)

  const extras = nodes
    .filter((n) => !ids.has(n.id) && (
      n.importanceLevel === 'core'
      || n.importanceLevel === 'important'
      || n.isLearningPathNode
      || n.size >= 60
    ))
    .sort((a, b) => b.importanceScore - a.importanceScore)

  for (const node of extras) {
    if (ids.size >= 3) break
    ids.add(node.id)
  }

  return ids
}

function hopToRingLayer(hop: number): number {
  if (hop <= 1) return 1
  if (hop <= 2) return 2
  if (hop <= 3) return 3
  return 4
}

/** Center hubs + 360° concentric rings — main nodes in middle, satellites radiating outward */
export function getRadialSpreadLayout(
  nodes: GraphNode[],
  edges: GraphEdge[],
  width: number,
  height: number,
  options: {
    selectedNodeId?: string | null
    currentLearningId?: string | null
    visibleIds: Set<string>
    detailPanelOpen?: boolean
  },
): ZoneLayoutResult {
  const bounds = getUsableBounds(width, height, { detailPanelOpen: options.detailPanelOpen })
  const center = getGraphViewCenter(width, height, { detailPanelOpen: options.detailPanelOpen })
  const visibleNodes = nodes.filter((n) => options.visibleIds.has(n.id))
  if (visibleNodes.length === 0) {
    return { nodes: [], zoneRects: [] }
  }

  const anchor = pickLayoutCenterNode(visibleNodes, {
    selectedNodeId: options.selectedNodeId,
    currentLearningId: options.currentLearningId,
  })
  const anchorId = anchor?.id ?? visibleNodes[0].id
  const hubIds = collectHubNodeIds(visibleNodes, anchorId, options)
  const hubs = visibleNodes.filter((n) => hubIds.has(n.id))
  const satellites = visibleNodes.filter((n) => !hubIds.has(n.id))

  const positions = new Map<string, { x: number; y: number }>()
  const hubRingR = Math.min(88, bounds.maxRadius * 0.16)

  if (hubs.length === 1) {
    positions.set(hubs[0].id, { x: center.x, y: center.y })
  } else {
    for (const [id, pos] of placeNodesOnRing(hubs, hubRingR, center.x, center.y, 0)) {
      positions.set(id, pos)
    }
  }

  const hops = computeHopDistances(anchorId, visibleNodes, edges)
  const layerGroups = new Map<number, GraphNode[]>()
  for (const node of satellites) {
    const layer = hopToRingLayer(hops.get(node.id) ?? 99)
    const group = layerGroups.get(layer) ?? []
    group.push(node)
    layerGroups.set(layer, group)
  }

  const layerCount = Math.max(...layerGroups.keys(), 1)
  const avgSize = visibleNodes.reduce((sum, n) => sum + n.size, 0) / visibleNodes.length
  const hubBoost = hubs.length > 1 ? hubRingR + 40 : hubRingR * 0.5

  for (const [layer, ringNodes] of layerGroups) {
    const baseR = ringBaseRadius(layer, layerCount, bounds.maxRadius)
    const minR = minRadiusForCount(ringNodes.length, bounds.maxRadius, avgSize)
    const radius = Math.max(baseR, minR) + hubBoost
    for (const [id, pos] of placeNodesOnRing(ringNodes, radius, center.x, center.y, layer)) {
      positions.set(id, pos)
    }
  }

  let layoutNodes: LayoutNode[] = visibleNodes.map((node) => {
    const isHub = hubIds.has(node.id)
    const isAnchor = node.id === anchorId
    const size = isAnchor
      ? LAYOUT_CONSTANTS.centerNodeSize
      : isHub
        ? Math.max(node.size, 72)
        : node.size
    const pos = positions.get(node.id) ?? center
    return {
      ...node,
      size,
      x: pos.x,
      y: pos.y,
      vx: 0,
      vy: 0,
      radius: size / 2,
    }
  })

  layoutNodes = runForceSimulation(layoutNodes, {
    centerId: hubs.length === 1 ? anchorId : undefined,
    layoutCenter: center,
    forceCenter: { x: width / 2, y: height / 2 },
    seedPositions: positions,
  })

  return { nodes: layoutNodes, zoneRects: [] }
}

export function getRelationshipZoneLayout(
  nodes: GraphNode[],
  edges: GraphEdge[],
  width: number,
  height: number,
  options: {
    selectedNodeId?: string | null
    currentLearningId?: string | null
    pathSet: Set<string>
    visibleIds: Set<string>
    detailPanelOpen?: boolean
  },
): ZoneLayoutResult {
  const bounds = getUsableBounds(width, height, { detailPanelOpen: options.detailPanelOpen })
  const centerNode = pickLayoutCenterNode(nodes, {
    selectedNodeId: options.selectedNodeId,
    currentLearningId: options.currentLearningId,
  })
  const anchorId = options.selectedNodeId ?? options.currentLearningId ?? centerNode?.id
  const center = getGraphViewCenter(width, height, { detailPanelOpen: options.detailPanelOpen })
  const selected = anchorId ? nodes.find((n) => n.id === anchorId) : null
  const selectedModule = selected?.chapterId || selected?.moduleName

  const visibleNodes = nodes.filter((n) => options.visibleIds.has(n.id))
  const groups = new Map<RelationshipZone, GraphNode[]>()

  for (const node of visibleNodes) {
    const zone = anchorId
      ? classifyNodeZone(node, anchorId, edges, options.pathSet, selectedModule)
      : 'related'
    const list = groups.get(zone) ?? []
    list.push(node)
    groups.set(zone, list)
  }

  for (const list of groups.values()) {
    list.sort((a, b) => b.importanceScore - a.importanceScore)
  }

  const positions = new Map<string, { x: number; y: number }>()
  const dist = LAYOUT_CONSTANTS.firstHopDistance
  const secondDist = LAYOUT_CONSTANTS.secondHopDistance

  if (anchorId && selected) {
    positions.set(anchorId, { x: center.x, y: center.y })
  }

  const prereq = groups.get('prerequisite') ?? []
  const prereqPos = placeInRow(
    prereq,
    center.x - dist - 40,
    center.y,
    'vertical',
    LAYOUT_CONSTANTS.clusterGap + 12,
  )
  for (const [id, pos] of prereqPos) positions.set(id, pos)

  const succ = groups.get('successor') ?? []
  const succPos = placeInRow(
    succ,
    center.x + dist + 40,
    center.y,
    'vertical',
    LAYOUT_CONSTANTS.clusterGap + 12,
  )
  for (const [id, pos] of succPos) positions.set(id, pos)

  const sameModule = groups.get('sameModule') ?? []
  const modulePos = placeOnArc(
    sameModule,
    center.x,
    center.y - dist * 0.85,
    Math.max(140, sameModule.length * 28),
    -Math.PI * 0.85,
    -Math.PI * 0.15,
  )
  for (const [id, pos] of modulePos) positions.set(id, pos)

  const mainPath = (groups.get('mainPath') ?? []).filter((n) => n.id !== anchorId)
  const pathPos = placeOnArc(
    mainPath,
    center.x,
    center.y,
    dist * 1.15,
    Math.PI * 0.15,
    Math.PI * 0.85,
  )
  for (const [id, pos] of pathPos) positions.set(id, pos)

  const resource = groups.get('resourcePractice') ?? []
  const resPos = placeOnArc(
    resource,
    center.x,
    center.y,
    dist * 1.05,
    Math.PI * 0.52,
    Math.PI * 0.98,
  )
  for (const [id, pos] of resPos) positions.set(id, pos)

  const related = (groups.get('related') ?? []).filter((n) => !positions.has(n.id))
  const relPos = placeOnRing(
    related,
    center.x,
    center.y,
    secondDist,
  )
  for (const [id, pos] of relPos) positions.set(id, pos)

  let layoutNodes: LayoutNode[] = visibleNodes.map((node) => {
    const pos = positions.get(node.id) ?? center
    const isSelected = node.id === anchorId
    const size = isSelected ? LAYOUT_CONSTANTS.centerNodeSize : node.size
    return {
      ...node,
      size,
      x: pos.x,
      y: pos.y,
      vx: 0,
      vy: 0,
      radius: size / 2,
    }
  })

  layoutNodes = runForceSimulation(layoutNodes, {
    centerId: anchorId,
    layoutCenter: center,
    forceCenter: { x: width / 2, y: height / 2 },
    seedPositions: positions,
  })

  return {
    nodes: layoutNodes,
    zoneRects: buildZoneRects(bounds, center, groups),
  }
}

export function getModuleClusterLayout(
  nodes: GraphNode[],
  width: number,
  height: number,
  visibleIds: Set<string>,
  detailPanelOpen?: boolean,
): ZoneLayoutResult {
  const bounds = getUsableBounds(width, height, { detailPanelOpen })
  const center = getGraphViewCenter(width, height, { detailPanelOpen })
  const visible = nodes.filter((n) => visibleIds.has(n.id))

  const chapters = new Map<string, GraphNode[]>()
  for (const node of visible) {
    const key = node.chapterId || node.moduleName || 'other'
    const list = chapters.get(key) ?? []
    list.push(node)
    chapters.set(key, list)
  }

  const positions = new Map<string, { x: number; y: number }>()
  const chapterKeys = [...chapters.keys()]
  const cols = Math.ceil(Math.sqrt(chapterKeys.length))
  const cellW = (bounds.maxX - bounds.minX) / Math.max(cols, 1)
  const cellH = (bounds.maxY - bounds.minY) / Math.max(Math.ceil(chapterKeys.length / cols), 1)

  chapterKeys.forEach((key, chapterIndex) => {
    const col = chapterIndex % cols
    const row = Math.floor(chapterIndex / cols)
    const cellCx = bounds.minX + cellW * (col + 0.5)
    const cellCy = bounds.minY + cellH * (row + 0.5)
    const group = chapters.get(key) ?? []
    group.sort((a, b) => b.importanceScore - a.importanceScore)
    const ringPos = placeOnArc(
      group,
      cellCx,
      cellCy,
      Math.min(cellW, cellH) * 0.28,
      0,
      Math.PI * 2,
    )
    for (const [id, pos] of ringPos) positions.set(id, pos)
  })

  let layoutNodes: LayoutNode[] = visible.map((node) => {
    const pos = positions.get(node.id) ?? center
    return { ...node, x: pos.x, y: pos.y, vx: 0, vy: 0, radius: node.size / 2 }
  })

  layoutNodes = runForceSimulation(layoutNodes, {
    layoutCenter: center,
    forceCenter: { x: width / 2, y: height / 2 },
    seedPositions: positions,
  })

  const zoneRects: ZoneRect[] = chapterKeys.map((key, index) => {
    const col = index % cols
    const row = Math.floor(index / cols)
    const sample = chapters.get(key)?.[0]
    return {
      zone: 'sameModule' as RelationshipZone,
      label: sample?.chapterName || sample?.moduleName || key,
      x: bounds.minX + cellW * col + 8,
      y: bounds.minY + cellH * row + 8,
      width: cellW - 16,
      height: cellH - 16,
    }
  })

  return { nodes: layoutNodes, zoneRects }
}

export function getFullGraphLayout(
  nodes: GraphNode[],
  edges: GraphEdge[],
  width: number,
  height: number,
  options: {
    selectedNodeId?: string | null
    currentLearningId?: string | null
    pathSet: Set<string>
    visibleIds: Set<string>
    detailPanelOpen?: boolean
  },
): ZoneLayoutResult {
  return getRelationshipZoneLayout(nodes, edges, width, height, {
    ...options,
    visibleIds: new Set(nodes.map((n) => n.id)),
  })
}

export function getRelationshipZoneLayoutByMode(
  nodes: GraphNode[],
  edges: GraphEdge[],
  width: number,
  height: number,
  viewMode: GraphViewMode,
  options: {
    selectedNodeId?: string | null
    currentLearningId?: string | null
    pathSet: Set<string>
    visibleIds: Set<string>
    detailPanelOpen?: boolean
  },
): ZoneLayoutResult {
  if (viewMode === 'module') {
    return getModuleClusterLayout(nodes, width, height, options.visibleIds, options.detailPanelOpen)
  }
  return getRadialSpreadLayout(nodes, edges, width, height, {
    selectedNodeId: options.selectedNodeId,
    currentLearningId: options.currentLearningId,
    visibleIds: viewMode === 'full'
      ? new Set(nodes.map((n) => n.id))
      : options.visibleIds,
    detailPanelOpen: options.detailPanelOpen,
  })
}
