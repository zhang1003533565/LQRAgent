import type { GraphEdge, GraphHighlightState, GraphViewport, LayoutNode } from '@/types/knowledgeGraph'
import type { GraphStatus } from '@/utils/knowledgeGraph/graphStatus'
import {
  type FocusGraphView,
  getCurvedControlPoint,
  resolveEdgeDrawStyle,
  shouldDimOnHover,
} from '@/utils/knowledgeGraph/graphFocus'
import type { AnimatedFocusView } from '@/hooks/useGraphFocusTransition'
import type { ZoneRect } from '@/utils/knowledgeGraph/graphRelationshipLayout'
import { computeFitViewport, type LayoutBoundsOptions } from '@/utils/knowledgeGraph/graphLayout'
import {
  type NodeLabelDescriptor,
  getLeafLabelAnchor,
} from '@/utils/knowledgeGraph/graphLabels'
import { getCoreNodeFill, graphLightConfig, GRAPH_VISUAL } from '@/utils/knowledgeGraph/graphVisualConfig'

export function scaleCanvasForDpr(canvas: HTMLCanvasElement) {
  const rect = canvas.getBoundingClientRect()
  const width = rect.width || canvas.clientWidth || canvas.width
  const height = rect.height || canvas.clientHeight || canvas.height
  const dpr = Math.max(2, window.devicePixelRatio || 1)
  canvas.width = Math.max(1, Math.round(width * dpr))
  canvas.height = Math.max(1, Math.round(height * dpr))
  const ctx = canvas.getContext('2d')
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0)
  return { width, height, dpr }
}

function getCurveControlPoint(from: LayoutNode, to: LayoutNode) {
  return getCurvedControlPoint(from, to, 0.24)
}

function drawBackground(ctx: CanvasRenderingContext2D, w: number, h: number, zoom: number, panX: number, panY: number) {
  ctx.fillStyle = GRAPH_VISUAL.canvas.bg
  ctx.fillRect(0, 0, w, h)

  const gridStep = GRAPH_VISUAL.canvas.dotStep
  ctx.save()
  ctx.translate(panX, panY)
  ctx.scale(zoom, zoom)
  const worldLeft = (-panX) / zoom - 40
  const worldTop = (-panY) / zoom - 40
  const worldRight = (w - panX) / zoom + 40
  const worldBottom = (h - panY) / zoom + 40

  ctx.fillStyle = GRAPH_VISUAL.canvas.dot
  const dotR = GRAPH_VISUAL.canvas.dotRadius / zoom
  for (let x = Math.floor(worldLeft / gridStep) * gridStep; x < worldRight; x += gridStep) {
    for (let y = Math.floor(worldTop / gridStep) * gridStep; y < worldBottom; y += gridStep) {
      ctx.beginPath()
      ctx.arc(x, y, dotR, 0, Math.PI * 2)
      ctx.fill()
    }
  }
  ctx.restore()
}

function drawArrow(ctx: CanvasRenderingContext2D, from: LayoutNode, to: LayoutNode, color: string, zoom: number) {
  const control = getCurveControlPoint(from, to)
  const t = 0.88
  const endX = (1 - t) ** 2 * from.x + 2 * (1 - t) * t * control.x + t ** 2 * to.x
  const endY = (1 - t) ** 2 * from.y + 2 * (1 - t) * t * control.y + t ** 2 * to.y
  const tangentX = 2 * (1 - t) * (control.x - from.x) + 2 * t * (to.x - control.x)
  const tangentY = 2 * (1 - t) * (control.y - from.y) + 2 * t * (to.y - control.y)
  const angle = Math.atan2(tangentY, tangentX)
  const arrowSize = Math.max(3, 4 / zoom)
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.moveTo(endX, endY)
  ctx.lineTo(endX - arrowSize * Math.cos(angle - 0.42), endY - arrowSize * Math.sin(angle - 0.42))
  ctx.lineTo(endX - arrowSize * Math.cos(angle + 0.42), endY - arrowSize * Math.sin(angle + 0.42))
  ctx.closePath()
  ctx.fill()
}

function isCoreNode(
  node: LayoutNode,
  isSelected: boolean,
  isCurrentLearning: boolean,
): boolean {
  return isSelected || isCurrentLearning || node.importanceLevel === 'core' || node.size >= 72
}

function estimateTextWidth(text: string, fontSize: number): number {
  let width = 0
  for (const ch of text) {
    width += /[\u4e00-\u9fff]/u.test(ch) ? fontSize : fontSize * 0.58
  }
  return width
}

function wrapTextForCore(
  text: string,
  maxWidth: number,
  fontSize: number,
  maxLines: number,
): string[] {
  const normalized = text.trim()
  if (!normalized) return []

  const lines: string[] = []
  let cursor = 0
  while (cursor < normalized.length && lines.length < maxLines) {
    let end = cursor
    let lineWidth = 0
    while (end < normalized.length) {
      const ch = normalized[end]
      const chW = /[\u4e00-\u9fff]/u.test(ch) ? fontSize : fontSize * 0.58
      if (lineWidth + chW > maxWidth && end > cursor) break
      lineWidth += chW
      end += 1
    }
    if (end === cursor) end += 1
    lines.push(normalized.slice(cursor, end))
    cursor = end
  }
  return lines
}

function drawCoreInsideLabel(
  ctx: CanvasRenderingContext2D,
  node: LayoutNode,
  r: number,
  label: NodeLabelDescriptor | undefined,
  zoom: number,
  dimmed: boolean,
) {
  const { core } = graphLightConfig.node
  const text = label?.text || node.name
  if (!text) return

  const fontSize = Math.min(core.fontSize, Math.max(10, r * 0.28))
  const maxWidth = r * 1.55
  const lines = (label?.lines.length && label.placement === 'inside')
    ? label.lines
    : wrapTextForCore(text, maxWidth, fontSize, 2)

  if (lines.length === 0) return

  ctx.save()
  ctx.font = `bold ${fontSize}px "Segoe UI", system-ui, sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillStyle = dimmed ? GRAPH_VISUAL.label.color.dimmed : core.textColor

  const lineHeight = fontSize * 1.2
  const totalH = lines.length * lineHeight
  const startY = node.y - totalH / 2 + lineHeight / 2

  lines.forEach((line, index) => {
    ctx.fillText(line, node.x, startY + index * lineHeight)
  })
  ctx.restore()
}

function drawMinimalNode(
  ctx: CanvasRenderingContext2D,
  node: LayoutNode,
  r: number,
  isSelected: boolean,
  isCurrentLearning: boolean,
  isSearchHit: boolean,
  zoom: number,
  hoverDimmed: boolean,
  coreLabel?: NodeLabelDescriptor,
) {
  const core = isCoreNode(node, isSelected, isCurrentLearning)
  const config = graphLightConfig

  ctx.save()

  if (core) {
    const { fill, border } = getCoreNodeFill(isSelected, isCurrentLearning)

    ctx.beginPath()
    ctx.arc(node.x, node.y, r, 0, Math.PI * 2)
    ctx.fillStyle = hoverDimmed ? 'rgba(226, 232, 240, 0.85)' : fill
    ctx.fill()

    ctx.beginPath()
    ctx.arc(node.x, node.y, r, 0, Math.PI * 2)
    ctx.strokeStyle = isSearchHit ? config.node.core.searchBorder : border
    ctx.lineWidth = Math.max(1.2 / zoom, 1)
    ctx.stroke()

    drawCoreInsideLabel(ctx, node, r, coreLabel, zoom, hoverDimmed)
    ctx.restore()
    return
  }

  const dotR = Math.max(4, Math.min(5.5, r * 0.22))
  const dotColor = node.isLearningPathNode
    ? config.node.leaf.learningFill
    : config.node.leaf.fill

  ctx.beginPath()
  ctx.arc(node.x, node.y, dotR, 0, Math.PI * 2)
  ctx.fillStyle = hoverDimmed ? config.node.leaf.dimmedFill : dotColor
  ctx.fill()
  ctx.restore()
}

function shouldDrawLeafLabel(
  label: NodeLabelDescriptor,
  isCore: boolean,
): boolean {
  return !isCore
    && label.lines.length > 0
    && label.placement === 'float'
}

function drawLeafLabelBadge(
  ctx: CanvasRenderingContext2D,
  label: NodeLabelDescriptor,
  node: LayoutNode,
  nodeScale: number,
  opacity: number,
) {
  const { leaf } = graphLightConfig.node
  const fontSize = label.fontSize
  const lineHeight = fontSize * 1.35
  const { textX, textY } = getLeafLabelAnchor(node, nodeScale)

  ctx.save()
  ctx.font = `${label.fontWeight} ${fontSize}px "Segoe UI", system-ui, sans-serif`
  ctx.globalAlpha = Math.min(1, Math.max(0, opacity))

  let maxLineWidth = 0
  for (const line of label.lines) {
    maxLineWidth = Math.max(maxLineWidth, ctx.measureText(line).width || estimateTextWidth(line, fontSize))
  }

  const badgeX = textX - leaf.badgePaddingX
  const badgeY = textY - fontSize / 2 - leaf.badgePaddingY
  const badgeW = maxLineWidth + leaf.badgePaddingX * 2
  const badgeH = label.lines.length * lineHeight + leaf.badgePaddingY * 2

  ctx.fillStyle = graphLightConfig.backgroundColor
  ctx.fillRect(badgeX, badgeY, badgeW, badgeH)

  ctx.globalAlpha = 1
  ctx.restore()
}

function drawLeafLabelText(
  ctx: CanvasRenderingContext2D,
  label: NodeLabelDescriptor,
  node: LayoutNode,
  nodeScale: number,
  dimmed: boolean,
  opacity: number,
) {
  const { leaf } = graphLightConfig.node
  const labelCfg = graphLightConfig.label
  const fontSize = label.fontSize
  const lineHeight = fontSize * 1.35
  const { textX, textY } = getLeafLabelAnchor(node, nodeScale)

  ctx.save()
  ctx.font = `${label.fontWeight} ${fontSize}px "Segoe UI", system-ui, sans-serif`
  ctx.textAlign = 'left'
  ctx.textBaseline = 'middle'
  ctx.globalAlpha = Math.min(1, Math.max(0, opacity))
  ctx.fillStyle = dimmed ? labelCfg.dimmedColor : leaf.textColor

  const startY = textY - ((label.lines.length - 1) * lineHeight) / 2
  label.lines.forEach((line, index) => {
    ctx.fillText(line, textX, startY + index * lineHeight)
  })

  ctx.globalAlpha = 1
  ctx.restore()
}

function isViewportShowingNodes(
  nodes: LayoutNode[],
  width: number,
  height: number,
  viewport: GraphViewport,
): boolean {
  if (nodes.length === 0 || width <= 0 || height <= 0) return true
  const margin = 48
  for (const node of nodes) {
    const sx = node.x * viewport.zoom + viewport.panX
    const sy = node.y * viewport.zoom + viewport.panY
    const r = node.radius + margin
    if (sx + r >= 0 && sx - r <= width && sy + r >= 0 && sy - r <= height) return true
  }
  return false
}

export interface DrawGraphOptions {
  canvas: HTMLCanvasElement
  nodes: LayoutNode[]
  edges: GraphEdge[]
  viewport: GraphViewport
  highlight: GraphHighlightState
  focusView: FocusGraphView
  visibleIds: Set<string>
  labelByNodeId?: Map<string, NodeLabelDescriptor>
  labelOpacity?: Map<string, number>
  zoneRects?: ZoneRect[]
  hiddenByZone?: Map<string, number>
  layoutBoundsOptions?: LayoutBoundsOptions
  onViewportCorrected?: (viewport: GraphViewport) => void
}

export function drawKnowledgeGraph(options: DrawGraphOptions): GraphViewport {
  const {
    canvas,
    nodes,
    edges,
    viewport,
    highlight,
    focusView,
    visibleIds,
    labelByNodeId,
    labelOpacity,
    zoneRects = [],
    hiddenByZone = new Map(),
    layoutBoundsOptions,
    onViewportCorrected,
  } = options
  const ctx = canvas.getContext('2d')
  if (!ctx) return viewport
  const { width: w, height: h } = scaleCanvasForDpr(canvas)

  let activeViewport = viewport
  if (nodes.length > 0 && w > 0 && h > 0 && !isViewportShowingNodes(nodes, w, h, viewport)) {
    activeViewport = computeFitViewport(nodes, w, h, 48, layoutBoundsOptions)
    onViewportCorrected?.(activeViewport)
  }

  const { zoom, panX, panY } = activeViewport
  const hoverId = highlight.hoveredId
  const selectedId = highlight.selectedId
  const animatedFocus = focusView as AnimatedFocusView

  ctx.clearRect(0, 0, w, h)

  // Layer 1 — background grid
  drawBackground(ctx, w, h, zoom, panX, panY)

  ctx.save()
  ctx.translate(panX, panY)
  ctx.scale(zoom, zoom)

  const hasHoverFocus = Boolean(hoverId)
  const sortedNodes = [...nodes].sort((a, b) => a.importanceScore - b.importanceScore)
  const labelEntries = [...(labelByNodeId?.values() ?? [])].sort((a, b) => a.priority - b.priority)

  const getNodeDrawState = (node: LayoutNode) => {
    const isSelected = highlight.selectedId === node.id
    const isHovered = highlight.hoveredId === node.id
    const isCurrentLearning = focusView.currentLearningId === node.id
    const isSearchHit = highlight.searchHighlightIds.has(node.id)
    const baseAlpha = focusView.nodeAlpha.get(node.id) ?? 1
    const hoverDimmed = hasHoverFocus && shouldDimOnHover(node.id, hoverId, edges)
    const alpha = hoverDimmed ? Math.min(baseAlpha, 0.14) : baseAlpha
    const scale = isSelected ? 1.06 : isCurrentLearning ? 1.04 : isHovered ? 1.02 : 1
    const isCore = isCoreNode(node, isSelected, isCurrentLearning)
    return { isSelected, isCurrentLearning, isSearchHit, alpha, scale, isCore, hoverDimmed }
  }

  // Layer 2 — edges
  for (const edge of edges) {
    const from = nodes.find((n) => n.id === edge.source)
    const to = nodes.find((n) => n.id === edge.target)
    if (!from || !to) continue
    if (!visibleIds.has(from.id) || !visibleIds.has(to.id)) continue

    const sourceVisible = visibleIds.has(from.id)
    const targetVisible = visibleIds.has(to.id)
    const drawStyle = resolveEdgeDrawStyle(
      edge,
      focusView,
      hoverId,
      sourceVisible,
      targetVisible,
      selectedId,
      edges,
    )
    if (!drawStyle.visible) continue

    const control = getCurveControlPoint(from, to)
    const strokeAlpha = animatedFocus.edgeAlpha?.get(edge.id) ?? drawStyle.opacity
    const strokeWidth = animatedFocus.edgeWidth?.get(edge.id) ?? drawStyle.width

    ctx.beginPath()
    ctx.moveTo(from.x, from.y)
    ctx.quadraticCurveTo(control.x, control.y, to.x, to.y)
    ctx.setLineDash([])
    ctx.strokeStyle = drawStyle.color
    ctx.lineWidth = Math.max(strokeWidth / zoom, 0.5 / zoom)
    ctx.globalAlpha = strokeAlpha
    ctx.stroke()
    ctx.globalAlpha = 1
    if (drawStyle.showArrow) drawArrow(ctx, from, to, drawStyle.color, zoom)
  }

  // Layer 3 — opaque label badges (cover edges under text)
  for (const label of labelEntries) {
    const node = nodes.find((n) => n.id === label.nodeId)
    if (!node || !visibleIds.has(node.id)) continue
    const state = getNodeDrawState(node)
    if (!shouldDrawLeafLabel(label, state.isCore)) continue
    const labelAlpha = labelOpacity?.get(label.nodeId) ?? label.targetOpacity
    if (labelAlpha <= 0.01) continue
    drawLeafLabelBadge(ctx, label, node, state.scale, labelAlpha)
  }

  // Layer 4 — node bodies
  for (const node of sortedNodes) {
    if (!visibleIds.has(node.id)) continue
    const state = getNodeDrawState(node)
    const r = node.radius * state.scale
    const coreLabel = labelByNodeId?.get(node.id)

    ctx.globalAlpha = state.alpha
    drawMinimalNode(
      ctx,
      node,
      r,
      state.isSelected,
      state.isCurrentLearning,
      state.isSearchHit,
      zoom,
      state.hoverDimmed,
      state.isCore ? coreLabel : undefined,
    )
    ctx.globalAlpha = 1
  }

  // Layer 4 — leaf label text (on top of nodes)
  for (const label of labelEntries) {
    const node = nodes.find((n) => n.id === label.nodeId)
    if (!node || !visibleIds.has(node.id)) continue
    const state = getNodeDrawState(node)
    if (!shouldDrawLeafLabel(label, state.isCore)) continue
    const labelAlpha = labelOpacity?.get(label.nodeId) ?? label.targetOpacity
    if (labelAlpha <= 0.01) continue
    drawLeafLabelText(ctx, label, node, state.scale, state.hoverDimmed, labelAlpha)
  }

  ctx.restore()
  return activeViewport
}

export function drawMiniMap(
  canvas: HTMLCanvasElement,
  nodes: LayoutNode[],
  edges: GraphEdge[],
  viewport: GraphViewport,
  mainSize: { width: number; height: number },
  focusView?: FocusGraphView,
) {
  const ctx = canvas.getContext('2d')
  if (!ctx || nodes.length === 0) return
  const { width: w, height: h } = scaleCanvasForDpr(canvas)

  ctx.clearRect(0, 0, w, h)

  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (const node of nodes) {
    minX = Math.min(minX, node.x)
    minY = Math.min(minY, node.y)
    maxX = Math.max(maxX, node.x)
    maxY = Math.max(maxY, node.y)
  }
  const pad = 16
  const gw = Math.max(maxX - minX, 1)
  const gh = Math.max(maxY - minY, 1)
  const sx = (w - pad * 2) / gw
  const sy = (h - pad * 2) / gh
  const scale = Math.min(sx, sy)
  const ox = pad - minX * scale
  const oy = pad - minY * scale

  for (const edge of edges) {
    const from = nodes.find((n) => n.id === edge.source)
    const to = nodes.find((n) => n.id === edge.target)
    if (!from || !to) continue
    if (edge.displayCategory !== 'path_main' && edge.displayCategory !== 'path_segment') continue
    ctx.beginPath()
    ctx.moveTo(from.x * scale + ox, from.y * scale + oy)
    ctx.lineTo(to.x * scale + ox, to.y * scale + oy)
    ctx.strokeStyle = GRAPH_VISUAL.edge.idle.stroke
    ctx.globalAlpha = GRAPH_VISUAL.edge.idle.opacity
    ctx.lineWidth = 0.75
    ctx.stroke()
  }

  for (const node of nodes) {
    const alpha = focusView?.nodeAlpha.get(node.id) ?? 1
    ctx.globalAlpha = alpha * 0.9
    ctx.beginPath()
    ctx.arc(node.x * scale + ox, node.y * scale + oy, 2, 0, Math.PI * 2)
    const dotColor = node.isLearningPathNode
      ? GRAPH_VISUAL.node.dotFill.learning
      : (GRAPH_VISUAL.node.dotFill[node.status as GraphStatus]
        ?? GRAPH_VISUAL.node.dotFill.unlearned)
    ctx.fillStyle = dotColor
    ctx.fill()
    ctx.globalAlpha = 1
  }

  const viewTopLeft = { x: (-viewport.panX) / viewport.zoom, y: (-viewport.panY) / viewport.zoom }
  const viewBottomRight = {
    x: (mainSize.width - viewport.panX) / viewport.zoom,
    y: (mainSize.height - viewport.panY) / viewport.zoom,
  }
  ctx.strokeStyle = 'rgba(37, 99, 235, 0.45)'
  ctx.lineWidth = 0.75
  ctx.strokeRect(
    viewTopLeft.x * scale + ox,
    viewTopLeft.y * scale + oy,
    (viewBottomRight.x - viewTopLeft.x) * scale,
    (viewBottomRight.y - viewTopLeft.y) * scale,
  )
}

export function pickNodeAt(
  nodes: LayoutNode[],
  wx: number,
  wy: number,
  visibleIds: Set<string>,
): LayoutNode | null {
  for (const node of [...nodes].reverse().sort((a, b) => b.radius - a.radius)) {
    if (!visibleIds.has(node.id)) continue
    const dx = wx - node.x
    const dy = wy - node.y
    if (dx * dx + dy * dy <= (node.radius + 8) ** 2) return node
  }
  return null
}

export function pickEdgeAt(
  nodes: LayoutNode[],
  edges: GraphEdge[],
  wx: number,
  wy: number,
  threshold = 8,
): GraphEdge | null {
  let best: { edge: GraphEdge; dist: number } | null = null
  for (const edge of edges) {
    const from = nodes.find((n) => n.id === edge.source)
    const to = nodes.find((n) => n.id === edge.target)
    if (!from || !to) continue
    const control = getCurveControlPoint(from, to)
    for (let t = 0; t <= 1; t += 0.05) {
      const px = (1 - t) ** 2 * from.x + 2 * (1 - t) * t * control.x + t ** 2 * to.x
      const py = (1 - t) ** 2 * from.y + 2 * (1 - t) * t * control.y + t ** 2 * to.y
      const dist = Math.hypot(wx - px, wy - py)
      if (dist <= threshold && (!best || dist < best.dist)) {
        best = { edge, dist }
      }
    }
  }
  return best?.edge ?? null
}
