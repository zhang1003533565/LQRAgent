import { useCallback, useEffect, useRef, useState } from 'react'
import type { GraphEdge, GraphNode, GraphViewMode, LayoutNode } from '@/types/knowledgeGraph'
import { runForceLayout } from '@/utils/knowledgeGraph/graphLayout'
import type { ZoneRect } from '@/utils/knowledgeGraph/graphRelationshipLayout'

export type LayoutCompleteHandler = (
  nodes: LayoutNode[],
  size: { width: number; height: number },
) => void

export function useGraphLayout(
  nodes: GraphNode[],
  edges: GraphEdge[],
  viewMode: GraphViewMode,
  containerRef: React.RefObject<HTMLDivElement | null>,
  onLayoutComplete?: LayoutCompleteHandler,
  options: {
    currentLearningId?: string | null
    selectedNodeId?: string | null
    detailPanelOpen?: boolean
    pathSet?: Set<string>
    visibleIds?: Set<string>
  } = {},
) {
  const [layoutNodes, setLayoutNodes] = useState<LayoutNode[]>([])
  const [zoneRects, setZoneRects] = useState<ZoneRect[]>([])
  const onLayoutCompleteRef = useRef(onLayoutComplete)
  const selectedNodeIdRef = useRef(options.selectedNodeId)
  onLayoutCompleteRef.current = onLayoutComplete
  selectedNodeIdRef.current = options.selectedNodeId

  const {
    currentLearningId,
    pathSet = new Set<string>(),
  } = options

  const runLayout = useCallback(() => {
    const container = containerRef.current
    if (!container || nodes.length === 0) return [] as LayoutNode[]
    const rect = container.getBoundingClientRect()
    const w = rect.width
    const h = rect.height
    if (w <= 0 || h <= 0) return [] as LayoutNode[]
    const allIds = new Set(nodes.map((n) => n.id))
    const result = runForceLayout(nodes, edges, w, h, viewMode, {
      currentLearningId,
      selectedNodeId: selectedNodeIdRef.current,
      detailPanelOpen: false,
      pathSet,
      visibleIds: allIds,
    })
    setLayoutNodes(result.nodes)
    setZoneRects(result.zoneRects)
    onLayoutCompleteRef.current?.(result.nodes, { width: w, height: h })
    return result.nodes
  }, [nodes, edges, viewMode, containerRef, currentLearningId, pathSet])

  useEffect(() => {
    const timer = window.setTimeout(() => runLayout(), 0)
    return () => window.clearTimeout(timer)
  }, [runLayout])

  useEffect(() => {
    const container = containerRef.current
    if (!container) return
    let timer: number | undefined
    const observer = new ResizeObserver(() => {
      if (timer) window.clearTimeout(timer)
      timer = window.setTimeout(() => runLayout(), 80)
    })
    observer.observe(container)
    return () => {
      observer.disconnect()
      if (timer) window.clearTimeout(timer)
    }
  }, [containerRef, runLayout])

  return { layoutNodes, zoneRects, setLayoutNodes, runLayout }
}
