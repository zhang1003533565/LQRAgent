import { useEffect, useRef, useState } from 'react'
import type { NodeLabelDescriptor } from '@/utils/knowledgeGraph/graphLabels'
import { GRAPH_VISUAL } from '@/utils/knowledgeGraph/graphVisualConfig'
import { easeOutCubic, lerp } from '@/utils/knowledgeGraph/graphEasing'

const LABEL_MS = GRAPH_VISUAL.label.transitionMs

export function useAnimatedLabelOpacity(
  labelByNodeId: Map<string, NodeLabelDescriptor>,
): Map<string, number> {
  const currentRef = useRef(new Map<string, number>())
  const rafRef = useRef(0)
  const [animated, setAnimated] = useState(() => new Map<string, number>())

  useEffect(() => {
    const from = new Map(currentRef.current)
    const targets = new Map<string, number>()
    for (const [id, desc] of labelByNodeId) {
      targets.set(id, desc.targetOpacity)
    }
    for (const id of from.keys()) {
      if (!targets.has(id)) targets.set(id, 0)
    }

    const start = performance.now()

    const tick = (now: number) => {
      const raw = Math.min(1, (now - start) / LABEL_MS)
      const t = easeOutCubic(raw)
      const next = new Map<string, number>()

      for (const [id, target] of targets) {
        const prev = from.get(id) ?? 0
        next.set(id, lerp(prev, target, t))
      }

      currentRef.current = next
      setAnimated(new Map(next))

      if (raw < 1) {
        rafRef.current = requestAnimationFrame(tick)
      }
    }

    cancelAnimationFrame(rafRef.current)
    rafRef.current = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(rafRef.current)
  }, [labelByNodeId])

  return animated
}
