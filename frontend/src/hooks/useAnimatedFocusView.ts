import { useEffect, useRef, useState } from 'react'
import type { FocusGraphView } from '@/utils/knowledgeGraph/graphFocus'

function easeOutCubic(t: number): number {
  return 1 - (1 - t) ** 3
}

const DEFAULT_DIM = 0.05

export function useAnimatedFocusView(
  target: FocusGraphView,
  duration = 250,
): FocusGraphView {
  const [animated, setAnimated] = useState(target)
  const fromRef = useRef<Map<string, number>>(new Map())
  const rafRef = useRef<number | null>(null)

  useEffect(() => {
    if (rafRef.current != null) {
      cancelAnimationFrame(rafRef.current)
      rafRef.current = null
    }

    const fromAlphas = new Map(fromRef.current)
    const toAlphas = target.nodeAlpha
    const allIds = new Set([...fromAlphas.keys(), ...toAlphas.keys()])

    for (const id of allIds) {
      if (!fromAlphas.has(id)) {
        fromAlphas.set(id, toAlphas.get(id) ?? 1)
      }
    }

    const startTime = performance.now()

    const tick = (now: number) => {
      const t = Math.min(1, (now - startTime) / duration)
      const eased = easeOutCubic(t)
      const nextAlpha = new Map<string, number>()

      for (const id of allIds) {
        const from = fromAlphas.get(id) ?? DEFAULT_DIM
        const to = toAlphas.get(id) ?? DEFAULT_DIM
        nextAlpha.set(id, from + (to - from) * eased)
      }

      setAnimated({
        ...target,
        nodeAlpha: nextAlpha,
      })

      if (t < 1) {
        rafRef.current = requestAnimationFrame(tick)
      } else {
        fromRef.current = toAlphas
        rafRef.current = null
      }
    }

    rafRef.current = requestAnimationFrame(tick)

    return () => {
      if (rafRef.current != null) {
        cancelAnimationFrame(rafRef.current)
        rafRef.current = null
      }
    }
  }, [
    target,
    duration,
    target.anchorId,
    target.viewMode,
    target.prominentIds.size,
  ])

  return animated
}
