import { useEffect, useRef, useState } from 'react'
import type { GraphEdge } from '@/types/knowledgeGraph'
import type { FocusGraphView } from '@/utils/knowledgeGraph/graphFocus'
import { resolveEdgeDrawStyle } from '@/utils/knowledgeGraph/graphFocus'
import { GRAPH_VISUAL } from '@/utils/knowledgeGraph/graphVisualConfig'
import { easeOutCubic, lerpAlphaMap } from '@/utils/knowledgeGraph/graphEasing'

export const FOCUS_TRANSITION_MS = 250
export const EDGE_TRANSITION_MS = GRAPH_VISUAL.edge.transitionMs

export interface AnimatedFocusView extends FocusGraphView {
  edgeAlpha: Map<string, number>
  edgeWidth: Map<string, number>
}

function computeEdgeStyleMaps(
  edges: GraphEdge[],
  focusView: FocusGraphView,
  selectedId: string | null,
  hoverId: string | null,
): { alpha: Map<string, number>; width: Map<string, number> } {
  const alpha = new Map<string, number>()
  const width = new Map<string, number>()
  for (const edge of edges) {
    const sourceVisible = focusView.prominentIds.has(edge.source)
    const targetVisible = focusView.prominentIds.has(edge.target)
    const style = resolveEdgeDrawStyle(
      edge,
      focusView,
      hoverId,
      sourceVisible,
      targetVisible,
      selectedId,
      edges,
    )
    alpha.set(edge.id, style.visible ? style.opacity : 0)
    width.set(edge.id, style.visible ? style.width : 0)
  }
  return { alpha, width }
}

function lerpNumberMap(
  from: Map<string, number>,
  to: Map<string, number>,
  t: number,
): Map<string, number> {
  const result = new Map<string, number>()
  const keys = new Set([...from.keys(), ...to.keys()])
  for (const key of keys) {
    const start = from.get(key) ?? to.get(key) ?? 0
    const end = to.get(key) ?? from.get(key) ?? 0
    result.set(key, start + (end - start) * t)
  }
  return result
}

export function useGraphFocusTransition(
  target: FocusGraphView,
  edges: GraphEdge[],
  selectedId: string | null,
  hoverId: string | null,
): AnimatedFocusView {
  const nodeAlphaRef = useRef(new Map(target.nodeAlpha))
  const edgeAlphaRef = useRef(new Map<string, number>())
  const edgeWidthRef = useRef(new Map<string, number>())
  const rafRef = useRef(0)

  const initialStyles = computeEdgeStyleMaps(edges, target, selectedId, hoverId)

  const [animated, setAnimated] = useState<AnimatedFocusView>(() => ({
    ...target,
    nodeAlpha: new Map(target.nodeAlpha),
    edgeAlpha: initialStyles.alpha,
    edgeWidth: initialStyles.width,
  }))

  useEffect(() => {
    const fromNode = nodeAlphaRef.current
    const toNode = target.nodeAlpha
    const fromEdgeAlpha = edgeAlphaRef.current
    const fromEdgeWidth = edgeWidthRef.current
    const { alpha: toEdgeAlpha, width: toEdgeWidth } = computeEdgeStyleMaps(
      edges,
      target,
      selectedId,
      hoverId,
    )

    const duration = hoverId ? EDGE_TRANSITION_MS : FOCUS_TRANSITION_MS
    const start = performance.now()

    const tick = (now: number) => {
      const raw = Math.min(1, (now - start) / duration)
      const t = easeOutCubic(raw)
      const nodeAlpha = lerpAlphaMap(fromNode, toNode, t)
      const edgeAlpha = lerpNumberMap(fromEdgeAlpha, toEdgeAlpha, t)
      const edgeWidth = lerpNumberMap(fromEdgeWidth, toEdgeWidth, t)
      nodeAlphaRef.current = nodeAlpha
      edgeAlphaRef.current = edgeAlpha
      edgeWidthRef.current = edgeWidth
      setAnimated({ ...target, nodeAlpha, edgeAlpha, edgeWidth })
      if (raw < 1) {
        rafRef.current = requestAnimationFrame(tick)
      }
    }

    cancelAnimationFrame(rafRef.current)
    rafRef.current = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(rafRef.current)
  }, [target, edges, selectedId, hoverId])

  return animated
}
