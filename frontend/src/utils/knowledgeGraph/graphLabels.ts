import type { KnowledgeGraphNode } from '@/api/student/knowledge'
import type { GraphStatus } from '@/utils/knowledgeGraph/graphStatus'
import type { LayoutNode } from '@/types/knowledgeGraph'
import {
  GRAPH_VISUAL,
  graphLightConfig,
  type LabelTier,
  smoothstep,
} from '@/utils/knowledgeGraph/graphVisualConfig'

export type LabelPlacement = 'inside' | 'below' | 'float' | 'abbrev'
export type LabelVariant = 'default' | 'selected' | 'mainPath' | 'weak'

export interface NodeLabelStyle {
  placement: LabelPlacement
  fontSize: number
  fontWeight: number
  maxLines: number
  maxCharsPerLine: number
  showFull: boolean
  variant: LabelVariant
}

export interface NodeLabelDescriptor {
  nodeId: string
  text: string
  lines: string[]
  fullText: string
  priority: number
  tier: LabelTier
  placement: LabelPlacement
  fontSize: number
  fontWeight: number
  variant: LabelVariant
  targetOpacity: number
  bounds: { x: number; y: number; width: number; height: number }
  anchorX: number
  anchorY: number
}

export interface LabelCollisionInput {
  nodeId: string
  priority: number
  bounds: { x: number; y: number; width: number; height: number }
}

const SHORT_NAME_RULES: Array<{ pattern: RegExp; replace: string }> = [
  { pattern: /Python简介与环境搭建/u, replace: 'Python简介' },
  { pattern: /Python\s*简介与环境搭建/u, replace: 'Python简介' },
  { pattern: /变量与数据类型/u, replace: '变量类型' },
  { pattern: /函数定义与调用/u, replace: '函数调用' },
  { pattern: /条件判断\s*if\s*\/\s*elif\s*\/\s*else/ui, replace: '条件判断' },
  { pattern: /异常处理\s*try\s*\/\s*except/ui, replace: '异常处理' },
  { pattern: /标准库常用模块/u, replace: '标准库' },
  { pattern: /上下文管理器\s*with/ui, replace: 'with管理器' },
  { pattern: /推导式[（(][^）)]+[）)]/u, replace: '推导式' },
  { pattern: /函数参数[（(][^）)]+[）)]/u, replace: '函数参数' },
  { pattern: /定义与/u, replace: '' },
  { pattern: /与调用/u, replace: '调用' },
  { pattern: /常用/u, replace: '' },
]

type RawNameFields = KnowledgeGraphNode & {
  shortName?: string
  alias?: string
  displayName?: string
  label?: string
}

function readRawNameFields(node: LayoutNode): RawNameFields {
  return node.raw as RawNameFields
}

export function getNodeDisplayName(node: LayoutNode): string {
  const raw = readRawNameFields(node)
  return raw.displayName?.trim() || node.name?.trim() || raw.title?.trim() || node.id
}

export function getNodeShortName(node: LayoutNode): string {
  const raw = readRawNameFields(node)
  if (raw.shortName?.trim()) return raw.shortName.trim()
  if (raw.alias?.trim()) return raw.alias.trim()
  if (raw.label?.trim()) return raw.label.trim()

  const display = getNodeDisplayName(node)
  for (const rule of SHORT_NAME_RULES) {
    if (rule.pattern.test(display)) {
      const next = display.replace(rule.pattern, rule.replace).replace(/\s+/g, ' ').trim()
      if (next.length >= 2) return next
    }
  }

  const withoutParen = display.replace(/[（(][^）)]+[）)]/g, '').trim()
  if (withoutParen.length >= 2 && withoutParen.length < display.length) {
    return withoutParen
  }

  const slashPart = display.split(/[/／]/)[0]?.trim()
  if (slashPart && slashPart.length >= 2 && slashPart.length <= 8) return slashPart

  if (display.length <= 8) return display
  return compressChineseTitle(display, 8)
}

function compressChineseTitle(text: string, maxLen: number): string {
  const cleaned = text.replace(/[、，,：:\s]+/g, '').trim()
  if (cleaned.length <= maxLen) return cleaned
  if (maxLen <= 1) return cleaned.slice(0, maxLen)
  return `${cleaned.slice(0, maxLen - 1)}…`
}

export function getNodeLabelTier(
  node: LayoutNode,
  selectedNodeId: string | null,
  currentLearningId: string | null,
): LabelTier {
  if (node.id === selectedNodeId || node.id === currentLearningId) return 1
  if (node.importanceLevel === 'core' || node.size >= 72) return 1
  if (node.isLearningPathNode || node.importanceLevel === 'important' || node.size >= 50) return 2
  return 3
}

/** Zoom-based target opacity before hover-reveal override */
export function getLabelZoomOpacity(
  tier: LabelTier,
  zoom: number,
): number {
  const { zoomHideThreshold, zoomMeso, zoomMicroFade } = graphLightConfig.label

  if (tier === 1) return 1

  if (zoom < zoomHideThreshold) return 0

  if (tier === 2) {
    if (zoom < zoomMeso) {
      return 0.88 + 0.12 * smoothstep(zoomHideThreshold, zoomMeso, zoom)
    }
    return 1
  }

  if (tier === 3) {
    if (zoom < zoomHideThreshold) return 0
    return 1
  }

  return 1
}

export function isCoreLabelNode(
  node: LayoutNode,
  selectedNodeId: string | null,
  currentLearningId: string | null,
): boolean {
  return getNodeLabelTier(node, selectedNodeId, currentLearningId) === 1
    || node.size >= 72
    || node.id === selectedNodeId
    || node.id === currentLearningId
}

export function getNodeLabelPriority(
  node: LayoutNode,
  selectedNodeId: string | null,
  currentLearningId: string | null,
): number {
  if (selectedNodeId === node.id) return 700
  if (currentLearningId === node.id) return 600
  if (node.isLearningPathNode) return 500
  if (node.importanceLevel === 'core') return 400
  if (node.importanceLevel === 'important') return 300
  if (node.importanceLevel === 'normal') return 200
  return 100
}

export function shouldShowNodeLabel(
  node: LayoutNode,
  zoom: number,
  selectedNodeId: string | null,
  hoveredNodeId: string | null,
  currentLearningId: string | null,
): boolean {
  if (hoveredNodeId === node.id) return true
  if (selectedNodeId === node.id) return true
  if (currentLearningId === node.id) return true

  const tier = getNodeLabelTier(node, selectedNodeId, currentLearningId)
  return getLabelZoomOpacity(tier, zoom) > 0.02
}

function resolveLabelVariant(
  node: LayoutNode,
  selectedNodeId: string | null,
): LabelVariant {
  if (selectedNodeId === node.id) return 'selected'
  if (node.status === 'weak') return 'weak'
  if (node.isLearningPathNode) return 'mainPath'
  return 'default'
}

export function getNodeLabelStyle(
  node: LayoutNode,
  zoom: number,
  selectedNodeId: string | null,
  currentLearningId: string | null,
): NodeLabelStyle {
  const showFull = node.id === selectedNodeId || node.id === currentLearningId
  const variant = resolveLabelVariant(node, selectedNodeId)
  const tier = getNodeLabelTier(node, selectedNodeId, currentLearningId)
  const isCore = isCoreLabelNode(node, selectedNodeId, currentLearningId)
  const { font } = GRAPH_VISUAL.label

  if (isCore) {
    return {
      placement: 'inside',
      fontSize: font.core.size,
      fontWeight: font.core.weight,
      maxLines: showFull ? 2 : 2,
      maxCharsPerLine: showFull ? 8 : 6,
      showFull,
      variant,
    }
  }

  if (tier === 2) {
    return {
      placement: 'float',
      fontSize: font.secondary.size,
      fontWeight: font.secondary.weight,
      maxLines: 1,
      maxCharsPerLine: 10,
      showFull: false,
      variant,
    }
  }

  return {
    placement: 'float',
    fontSize: font.tertiary.size,
    fontWeight: font.tertiary.weight,
    maxLines: 1,
    maxCharsPerLine: 8,
    showFull: false,
    variant,
  }
}

export function wrapLabelLines(
  text: string,
  maxCharsPerLine: number,
  maxLines: number,
): string[] {
  const normalized = text.trim()
  if (!normalized) return []
  if (normalized.length <= maxCharsPerLine) return [normalized]

  const lines: string[] = []
  let cursor = 0
  while (cursor < normalized.length && lines.length < maxLines) {
    const slice = normalized.slice(cursor, cursor + maxCharsPerLine)
    lines.push(slice)
    cursor += maxCharsPerLine
  }

  if (cursor < normalized.length && lines.length > 0 && maxLines <= 1) {
    const short = getNodeShortNameFromText(normalized)
    if (short.length <= maxCharsPerLine) return [short]
    return [`${short.slice(0, Math.max(1, maxCharsPerLine - 1))}…`]
  }

  return lines
}

function getNodeShortNameFromText(text: string): string {
  if (text.length <= 8) return text
  return compressChineseTitle(text, 8)
}

export function buildNodeLabelText(node: LayoutNode, style: NodeLabelStyle): string {
  if (style.placement === 'abbrev') {
    const short = getNodeShortName(node)
    return short.slice(0, style.maxCharsPerLine)
  }
  const source = style.showFull ? getNodeDisplayName(node) : getNodeShortName(node)
  return source
}

function estimateTextWidth(text: string, fontSize: number): number {
  let width = 0
  for (const ch of text) {
    width += /[\u4e00-\u9fff]/u.test(ch) ? fontSize : fontSize * 0.58
  }
  return width
}

export function getLeafLabelAnchor(
  node: LayoutNode,
  scale = 1,
): { textX: number; textY: number; dotR: number } {
  const r = node.radius * scale
  const dotR = Math.max(4, Math.min(5.5, r * 0.22))
  const floatOffsetX = graphLightConfig.label.floatOffsetX
  return {
    textX: node.x + dotR + floatOffsetX,
    textY: node.y,
    dotR,
  }
}

export function calculateLabelBounds(
  node: LayoutNode,
  lines: string[],
  style: NodeLabelStyle,
  scale = 1,
): { bounds: NodeLabelDescriptor['bounds']; anchorX: number; anchorY: number } {
  const r = node.radius * scale
  const fontSize = style.fontSize
  const lineHeight = fontSize * 1.35
  const paddingX = graphLightConfig.node.leaf.badgePaddingX
  const paddingY = graphLightConfig.node.leaf.badgePaddingY

  let maxWidth = 0
  for (const line of lines) {
    maxWidth = Math.max(maxWidth, estimateTextWidth(line, fontSize))
  }

  const maxLabelWidth = Math.max(r * 2.2, node.size * 0.95)
  let boxW = Math.min(maxLabelWidth, maxWidth + paddingX * 2)
  let boxH = lines.length * lineHeight + paddingY * 2

  let anchorX = node.x
  let anchorY = node.y
  let boxX = anchorX - boxW / 2
  let boxY = anchorY - boxH / 2

  if (style.placement === 'float') {
    const { textX, textY } = getLeafLabelAnchor(node, scale)
    anchorX = textX
    anchorY = textY
    boxX = anchorX - paddingX
    boxY = anchorY - lineHeight / 2 - paddingY
    boxW = maxWidth + paddingX * 2
    boxH = lines.length * lineHeight + paddingY * 2
  } else if (style.placement === 'inside' || style.placement === 'abbrev') {
    boxX = anchorX - boxW / 2
    boxY = anchorY - boxH / 2
  }

  return {
    bounds: { x: boxX, y: boxY, width: boxW, height: boxH },
    anchorX,
    anchorY,
  }
}

export function buildNodeLabelDescriptor(
  node: LayoutNode,
  style: NodeLabelStyle,
  scale = 1,
): NodeLabelDescriptor {
  const text = buildNodeLabelText(node, style)
  const lines = style.placement === 'abbrev' ? [text] : wrapLabelLines(text, style.maxCharsPerLine, style.maxLines)
  const { bounds, anchorX, anchorY } = calculateLabelBounds(node, lines, style, scale)

  return {
    nodeId: node.id,
    text,
    lines,
    fullText: getNodeDisplayName(node),
    priority: getNodeLabelPriority(node, null, null),
    tier: getNodeLabelTier(node, null, null),
    placement: style.placement,
    fontSize: style.fontSize,
    fontWeight: style.fontWeight,
    variant: style.variant,
    targetOpacity: 1,
    bounds,
    anchorX,
    anchorY,
  }
}

function boundsOverlap(
  a: LabelCollisionInput['bounds'],
  b: LabelCollisionInput['bounds'],
  padding = 4,
): boolean {
  return !(
    a.x + a.width + padding <= b.x
    || b.x + b.width + padding <= a.x
    || a.y + a.height + padding <= b.y
    || b.y + b.height + padding <= a.y
  )
}

function nudgeFloatLabelYCollisions(labels: NodeLabelDescriptor[], nodes: LayoutNode[]): void {
  const nodeById = new Map(nodes.map((n) => [n.id, n]))
  const floatLabels = labels.filter((l) => l.placement === 'float')

  for (let i = 0; i < floatLabels.length; i += 1) {
    for (let j = i + 1; j < floatLabels.length; j += 1) {
      const a = floatLabels[i]
      const b = floatLabels[j]
      const nodeA = nodeById.get(a.nodeId)
      const nodeB = nodeById.get(b.nodeId)
      if (!nodeA || !nodeB) continue
      if (Math.abs(nodeA.x - nodeB.x) > 140) continue

      const lineHeight = Math.max(a.fontSize, b.fontSize) * 1.35
      const minGap = lineHeight * 0.25
      const aBottom = a.bounds.y + a.bounds.height
      if (b.bounds.y < aBottom + minGap) {
        const delta = aBottom + minGap - b.bounds.y
        b.bounds.y += delta
        b.anchorY += delta
      }
    }
  }
}

export function resolveLabelCollisions(
  labels: LabelCollisionInput[],
  pinnedIds: Set<string>,
): Set<string> {
  const sorted = [...labels].sort((a, b) => b.priority - a.priority)
  const kept: LabelCollisionInput[] = []
  const visible = new Set<string>()

  for (const label of sorted) {
    if (pinnedIds.has(label.nodeId)) {
      kept.push(label)
      visible.add(label.nodeId)
      continue
    }

    const tier = (label as NodeLabelDescriptor).tier
    if (tier <= 2) {
      kept.push(label)
      visible.add(label.nodeId)
      continue
    }

    const collides = kept.some((other) => boundsOverlap(label.bounds, other.bounds, 4))
    if (collides) continue
    kept.push(label)
    visible.add(label.nodeId)
  }

  return visible
}

export function resolveGraphNodeLabels(
  nodes: LayoutNode[],
  zoom: number,
  selectedNodeId: string | null,
  hoveredNodeId: string | null,
  currentLearningId: string | null,
  visibleIds: Set<string>,
): { visibleLabelIds: Set<string>; labelByNodeId: Map<string, NodeLabelDescriptor> } {
  const candidates: Array<LabelCollisionInput & NodeLabelDescriptor> = []
  const pinnedIds = new Set<string>()

  for (const node of nodes) {
    if (visibleIds.size > 0 && !visibleIds.has(node.id)) continue

    const tier = getNodeLabelTier(node, selectedNodeId, currentLearningId)
    const isHovered = hoveredNodeId === node.id
    const isPinned = isHovered
      || node.id === selectedNodeId
      || node.id === currentLearningId
      || tier === 1

    if (isPinned) pinnedIds.add(node.id)

    if (!isHovered && !shouldShowNodeLabel(node, zoom, selectedNodeId, hoveredNodeId, currentLearningId)) {
      continue
    }

    const style = getNodeLabelStyle(node, zoom, selectedNodeId, currentLearningId)
    const effectiveStyle = isHovered
      ? {
          ...style,
          maxLines: Math.max(style.maxLines, 1),
          maxCharsPerLine: Math.max(style.maxCharsPerLine, 12),
          showFull: true,
        }
      : style
    const descriptor = buildNodeLabelDescriptor(node, effectiveStyle)
    descriptor.priority = getNodeLabelPriority(node, selectedNodeId, currentLearningId)
    descriptor.tier = tier
    descriptor.variant = resolveLabelVariant(node, selectedNodeId)
    descriptor.targetOpacity = isHovered
      ? 1
      : getLabelZoomOpacity(tier, zoom)

    if (descriptor.lines.length === 0 && descriptor.targetOpacity <= 0) continue
    candidates.push(descriptor)
  }

  nudgeFloatLabelYCollisions(candidates, nodes)

  const visibleLabelIds = resolveLabelCollisions(candidates, pinnedIds)
  const labelByNodeId = new Map<string, NodeLabelDescriptor>()
  for (const item of candidates) {
    if (visibleLabelIds.has(item.nodeId) || pinnedIds.has(item.nodeId)) {
      labelByNodeId.set(item.nodeId, item)
    }
  }

  return { visibleLabelIds, labelByNodeId }
}

export function getLabelBorderColor(variant: LabelVariant, status: GraphStatus): string {
  if (variant === 'selected') return 'rgba(249, 115, 22, 0.72)'
  if (variant === 'mainPath') return 'rgba(59, 130, 246, 0.58)'
  if (variant === 'weak' || status === 'weak') return 'rgba(249, 115, 22, 0.52)'
  return 'rgba(148, 163, 184, 0.18)'
}

/** @deprecated use shouldShowNodeLabel + buildNodeLabelDescriptor */
export type LabelMode = 'none' | 'truncated' | 'full'

/** @deprecated */
export function resolveNodeLabelMode(
  node: LayoutNode,
  zoom: number,
  options: {
    selectedId: string | null
    hoveredId: string | null
    currentLearningId: string | null
  },
): LabelMode {
  if (!shouldShowNodeLabel(node, zoom, options.selectedId, options.hoveredId, options.currentLearningId)) {
    return 'none'
  }
  if (node.id === options.selectedId || node.id === options.currentLearningId) return 'full'
  if (node.importanceLevel === 'minor') return 'truncated'
  return zoom >= 1.2 ? 'full' : 'truncated'
}

/** @deprecated */
export function formatNodeLabel(name: string, mode: LabelMode, radius: number): string {
  if (mode === 'none') return ''
  if (mode === 'full') return name
  const maxLen = radius >= 34 ? 6 : radius >= 24 ? 4 : 2
  if (name.length <= maxLen) return name
  return `${name.slice(0, Math.max(1, maxLen - 1))}…`
}
