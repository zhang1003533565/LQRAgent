import { useEffect, useRef, useCallback } from 'react'

/**
 * 消息列表变化时滚动到底部。
 * 流式输出时使用 instant 行为避免抖动，完成后使用 smooth。
 * 使用 throttle 避免过于频繁的滚动操作。
 */
export function useChatAutoScroll<T>(deps: T[]) {
  const bottomRef = useRef<HTMLDivElement>(null)
  const lastScrollTime = useRef(0)
  const rafId = useRef<number | null>(null)

  const scroll = useCallback(() => {
    const el = bottomRef.current
    if (!el) return

    const now = Date.now()
    // 节流：至少间隔 50ms 才滚动一次
    if (now - lastScrollTime.current < 50) {
      if (rafId.current === null) {
        rafId.current = requestAnimationFrame(() => {
          rafId.current = null
          scroll()
        })
      }
      return
    }
    lastScrollTime.current = now

    // 检查是否有正在流式输出的消息
    const parent = el.parentElement
    let isStreaming = false
    if (parent) {
      const streamingEls = parent.querySelectorAll('[data-streaming="true"]')
      isStreaming = streamingEls.length > 0
    }

    // 流式输出时使用 instant 避免抖动
    el.scrollIntoView({ behavior: isStreaming ? 'instant' : 'smooth' })
  }, [])

  useEffect(() => {
    scroll()
  }, [...deps, scroll])

  return bottomRef
}
