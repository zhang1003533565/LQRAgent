import { useCallback, useEffect, useRef, useState } from 'react'
import type { GraphViewport, LayoutNode } from '@/types/knowledgeGraph'
import {
  computeFitViewport,
  panToNode,
  type LayoutBoundsOptions,
} from '@/utils/knowledgeGraph/graphLayout'
import { easeOutCubic, lerp } from '@/utils/knowledgeGraph/graphEasing'

const CAMERA_DURATION_MS = 300

export function useGraphViewport(initial?: Partial<GraphViewport>) {
  const [viewport, setViewport] = useState<GraphViewport>({
    zoom: initial?.zoom ?? 1,
    panX: initial?.panX ?? 0,
    panY: initial?.panY ?? 0,
  })

  const viewportRef = useRef(viewport)
  const animFrameRef = useRef(0)
  viewportRef.current = viewport

  useEffect(() => () => cancelAnimationFrame(animFrameRef.current), [])

  const animateViewport = useCallback((target: GraphViewport, duration = CAMERA_DURATION_MS) => {
    const from = { ...viewportRef.current }
    const start = performance.now()

    const tick = (now: number) => {
      const raw = Math.min(1, (now - start) / duration)
      const t = easeOutCubic(raw)
      const next: GraphViewport = {
        zoom: lerp(from.zoom, target.zoom, t),
        panX: lerp(from.panX, target.panX, t),
        panY: lerp(from.panY, target.panY, t),
      }
      viewportRef.current = next
      setViewport(next)
      if (raw < 1) {
        animFrameRef.current = requestAnimationFrame(tick)
      }
    }

    cancelAnimationFrame(animFrameRef.current)
    animFrameRef.current = requestAnimationFrame(tick)
  }, [])

  const zoomIn = useCallback(() => {
    setViewport((v) => ({ ...v, zoom: Math.min(2.2, v.zoom + 0.12) }))
  }, [])

  const zoomOut = useCallback(() => {
    setViewport((v) => ({ ...v, zoom: Math.max(0.45, v.zoom - 0.12) }))
  }, [])

  const resetZoom = useCallback(() => {
    animateViewport({ zoom: 1, panX: 0, panY: 0 })
  }, [animateViewport])

  const fitToNodes = useCallback((
    nodes: LayoutNode[],
    width: number,
    height: number,
    options?: LayoutBoundsOptions,
    animate = true,
  ) => {
    const target = computeFitViewport(nodes, width, height, 48, options)
    if (animate) {
      animateViewport(target)
    } else {
      setViewport(target)
    }
  }, [animateViewport])

  const focusNode = useCallback((
    node: LayoutNode,
    width: number,
    height: number,
    zoom?: number,
    options?: LayoutBoundsOptions,
    animate = true,
  ) => {
    const nextZoom = zoom ?? 1.08
    const target: GraphViewport = {
      zoom: nextZoom,
      ...panToNode(node, width, height, nextZoom, options),
    }
    if (animate) {
      animateViewport(target)
    } else {
      setViewport(target)
    }
  }, [animateViewport])

  const panBy = useCallback((dx: number, dy: number) => {
    cancelAnimationFrame(animFrameRef.current)
    setViewport((v) => ({ ...v, panX: v.panX + dx, panY: v.panY + dy }))
  }, [])

  const zoomAtPoint = useCallback((mouseX: number, mouseY: number, delta: number) => {
    cancelAnimationFrame(animFrameRef.current)
    setViewport((v) => {
      const factor = delta > 0 ? 1.1 : 0.9
      const newZoom = Math.min(2.2, Math.max(0.45, v.zoom * factor))
      const wx = (mouseX - v.panX) / v.zoom
      const wy = (mouseY - v.panY) / v.zoom
      return {
        zoom: newZoom,
        panX: mouseX - wx * newZoom,
        panY: mouseY - wy * newZoom,
      }
    })
  }, [])

  return {
    viewport,
    setViewport,
    zoomIn,
    zoomOut,
    resetZoom,
    fitToNodes,
    focusNode,
    panBy,
    zoomAtPoint,
    animateViewport,
  }
}
