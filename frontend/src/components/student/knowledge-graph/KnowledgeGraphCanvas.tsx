import { useCallback, useLayoutEffect, useRef } from 'react'
import { useAnimatedLabelOpacity } from '@/hooks/useAnimatedLabelOpacity'
import { Circle, GitBranch, Info, Route } from 'lucide-react'
import type { GraphEdge, GraphHighlightState, GraphViewport, LayoutNode } from '@/types/knowledgeGraph'
import type { FocusGraphView } from '@/utils/knowledgeGraph/graphFocus'
import type { NodeLabelDescriptor } from '@/utils/knowledgeGraph/graphLabels'
import {
  drawKnowledgeGraph,
  drawMiniMap,
  pickEdgeAt,
  pickNodeAt,
} from '@/utils/knowledgeGraph/graphCanvasDraw'
import { computeFitViewport, type LayoutBoundsOptions } from '@/utils/knowledgeGraph/graphLayout'
import { screenToWorld } from '@/utils/knowledgeGraph/graphLayout'
import styles from '@/pages/student/KnowledgeGraphPage.module.css'

interface KnowledgeGraphCanvasProps {
  layoutNodes: LayoutNode[]
  edges: GraphEdge[]
  viewport: GraphViewport
  highlight: GraphHighlightState
  focusView: FocusGraphView
  visibleIds: Set<string>
  loading: boolean
  fitRequestId?: number
  fitNodes?: LayoutNode[]
  zoneRects?: import('@/utils/knowledgeGraph/graphRelationshipLayout').ZoneRect[]
  hiddenByZone?: Map<string, number>
  layoutBoundsOptions?: LayoutBoundsOptions
  onViewportChange?: (viewport: GraphViewport) => void
  onSelectNode: (node: LayoutNode | null) => void
  onHoverNode: (node: LayoutNode | null) => void
  onHoverEdge: (edge: GraphEdge | null) => void
  onPanBy: (dx: number, dy: number) => void
  onZoomAtPoint: (x: number, y: number, delta: number) => void
  onDoubleClickNode: (node: LayoutNode) => void
  labelByNodeId: Map<string, NodeLabelDescriptor>
  tooltip: {
    x: number
    y: number
    title: string
    rows: { icon: 'status' | 'link' | 'route' | 'info'; text: string }[]
  } | null
}

function resolveVisibleIds(nodes: LayoutNode[], visibleIds: Set<string>) {
  if (visibleIds.size > 0) return visibleIds
  return new Set(nodes.map((node) => node.id))
}

export default function KnowledgeGraphCanvas({
  layoutNodes,
  edges,
  viewport,
  highlight,
  focusView,
  visibleIds,
  loading,
  fitRequestId = 0,
  fitNodes,
  layoutBoundsOptions,
  zoneRects = [],
  hiddenByZone = new Map(),
  onViewportChange,
  onSelectNode,
  onHoverNode,
  onHoverEdge,
  onPanBy,
  onZoomAtPoint,
  onDoubleClickNode,
  labelByNodeId,
  tooltip,
}: KnowledgeGraphCanvasProps) {
  const labelOpacity = useAnimatedLabelOpacity(labelByNodeId)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const miniMapRef = useRef<HTMLCanvasElement>(null)
  const panDragRef = useRef<{ startX: number; startY: number; moved: boolean } | null>(null)
  const nodeDragRef = useRef<{ node: LayoutNode; ox: number; oy: number } | null>(null)
  const viewportRef = useRef(viewport)
  const fitRequestRef = useRef(fitRequestId)
  const appliedFitRequestRef = useRef(-1)
  viewportRef.current = viewport
  fitRequestRef.current = fitRequestId

  const redraw = useCallback(() => {
    const canvas = canvasRef.current
    const mini = miniMapRef.current
    if (!canvas) return

    const rect = canvas.getBoundingClientRect()
    const width = rect.width
    const height = rect.height
    if (width <= 0 || height <= 0) return

    let activeViewport = viewportRef.current
    const nodesForFit = fitNodes && fitNodes.length > 0 ? fitNodes : layoutNodes
    if (layoutNodes.length > 0 && fitRequestRef.current !== appliedFitRequestRef.current) {
      activeViewport = computeFitViewport(nodesForFit, width, height, 48, layoutBoundsOptions)
      appliedFitRequestRef.current = fitRequestRef.current
      viewportRef.current = activeViewport
      onViewportChange?.(activeViewport)
    }

    const effectiveVisibleIds = resolveVisibleIds(layoutNodes, visibleIds)
    activeViewport = drawKnowledgeGraph({
      canvas,
      nodes: layoutNodes,
      edges,
      viewport: activeViewport,
      highlight,
      focusView,
      visibleIds: effectiveVisibleIds,
      labelByNodeId,
      labelOpacity,
      zoneRects,
      hiddenByZone,
      layoutBoundsOptions,
      onViewportCorrected: onViewportChange,
    })

    if (mini && layoutNodes.length > 0) {
      drawMiniMap(mini, layoutNodes, edges, activeViewport, { width, height }, focusView)
    }
  }, [layoutNodes, edges, highlight, focusView, visibleIds, fitNodes, labelByNodeId, labelOpacity, zoneRects, hiddenByZone, layoutBoundsOptions, onViewportChange])

  useLayoutEffect(() => {
    redraw()
    const canvas = canvasRef.current
    if (!canvas) return
    const observer = new ResizeObserver(() => redraw())
    observer.observe(canvas)
    return () => observer.disconnect()
  }, [redraw, fitRequestId, viewport, labelOpacity])

  const getWorldPoint = (clientX: number, clientY: number) => {
    const canvas = canvasRef.current
    if (!canvas) return null
    const rect = canvas.getBoundingClientRect()
    const sx = clientX - rect.left
    const sy = clientY - rect.top
    const active = viewportRef.current
    return screenToWorld(sx, sy, rect.width, rect.height, active.zoom, active.panX, active.panY)
  }

  const handleMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const world = getWorldPoint(e.clientX, e.clientY)
    if (!world) return
    const effectiveVisibleIds = resolveVisibleIds(layoutNodes, visibleIds)
    const node = pickNodeAt(layoutNodes, world.x, world.y, effectiveVisibleIds)
    if (node) {
      nodeDragRef.current = { node, ox: world.x - node.x, oy: world.y - node.y }
    } else {
      panDragRef.current = { startX: e.clientX, startY: e.clientY, moved: false }
    }
  }

  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (panDragRef.current) {
      const dx = e.clientX - panDragRef.current.startX
      const dy = e.clientY - panDragRef.current.startY
      if (Math.abs(dx) > 3 || Math.abs(dy) > 3) panDragRef.current.moved = true
      onPanBy(dx, dy)
      panDragRef.current = { startX: e.clientX, startY: e.clientY, moved: panDragRef.current.moved }
      return
    }
    if (nodeDragRef.current) {
      const world = getWorldPoint(e.clientX, e.clientY)
      if (world) {
        nodeDragRef.current.node.x = world.x - nodeDragRef.current.ox
        nodeDragRef.current.node.y = world.y - nodeDragRef.current.oy
        redraw()
      }
      return
    }
    const world = getWorldPoint(e.clientX, e.clientY)
    if (!world) return
    const effectiveVisibleIds = resolveVisibleIds(layoutNodes, visibleIds)
    const node = pickNodeAt(layoutNodes, world.x, world.y, effectiveVisibleIds)
    onHoverNode(node)
    if (!node) {
      const edge = pickEdgeAt(layoutNodes, edges, world.x, world.y)
      onHoverEdge(edge)
    } else {
      onHoverEdge(null)
    }
  }

  const handleMouseUp = () => {
    panDragRef.current = null
    nodeDragRef.current = null
  }

  const handleClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (panDragRef.current?.moved || nodeDragRef.current) return
    const world = getWorldPoint(e.clientX, e.clientY)
    if (!world) return
    const effectiveVisibleIds = resolveVisibleIds(layoutNodes, visibleIds)
    const node = pickNodeAt(layoutNodes, world.x, world.y, effectiveVisibleIds)
    onSelectNode(node)
  }

  const handleDoubleClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const world = getWorldPoint(e.clientX, e.clientY)
    if (!world) return
    const effectiveVisibleIds = resolveVisibleIds(layoutNodes, visibleIds)
    const node = pickNodeAt(layoutNodes, world.x, world.y, effectiveVisibleIds)
    if (node) onDoubleClickNode(node)
  }

  const handleWheel = (e: React.WheelEvent<HTMLCanvasElement>) => {
    e.preventDefault()
    const canvas = canvasRef.current
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()
    onZoomAtPoint(e.clientX - rect.left, e.clientY - rect.top, e.deltaY < 0 ? 1 : -1)
  }

  return (
    <div className={styles.canvasWrap}>
      <canvas
        ref={canvasRef}
        className={styles.canvas}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onClick={handleClick}
        onDoubleClick={handleDoubleClick}
        onWheel={handleWheel}
      />
      {loading ? <div className={styles.loadingState}>加载知识图谱...</div> : null}
      {tooltip ? (
        <div className={styles.nodeTooltip} style={{ left: tooltip.x, top: tooltip.y }}>
          <div className={styles.tooltipTitle}>{tooltip.title}</div>
          {tooltip.rows.map((row) => (
            <div key={`${row.icon}-${row.text}`} className={styles.tooltipRow}>
              {row.icon === 'status' ? <Circle size={10} strokeWidth={1.5} /> : null}
              {row.icon === 'link' ? <GitBranch size={10} strokeWidth={1.5} /> : null}
              {row.icon === 'route' ? <Route size={10} strokeWidth={1.5} /> : null}
              {row.icon === 'info' ? <Info size={10} strokeWidth={1.5} /> : null}
              <span>{row.text}</span>
            </div>
          ))}
        </div>
      ) : null}
      <div className={styles.miniMap}>
        <canvas ref={miniMapRef} />
      </div>
    </div>
  )
}
