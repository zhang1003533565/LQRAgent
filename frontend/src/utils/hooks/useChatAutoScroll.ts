import { useEffect, useRef, useCallback } from 'react'

/**
 * 消息列表变化时滚动到底部。
 * 流式输出时使用 instant 行为避免抖动，完成后使用 smooth。
 * 使用 throttle 避免过于频繁的滚动操作。
 * 
 * @param isHistoryLoading - 是否正在加载历史消息（加载时不自动滚动）
 */
export function useChatAutoScroll<T>(deps: T[], isHistoryLoading = false) {
  const bottomRef = useRef<HTMLDivElement>(null)
  const lastScrollTime = useRef(0)
  const rafId = useRef<number | null>(null)
  const hasScrolledForHistory = useRef(false)

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
    // 加载历史消息时不自动滚动（首次加载后标记）
    if (isHistoryLoading) {
      hasScrolledForHistory.current = false
      return
    }

    // 首次加载历史消息后，滚动一次到顶部附近（显示最新消息）
    if (!hasScrolledForHistory.current) {
      hasScrolledForHistory.current = true
      return
    }

    // 后续消息变化时自动滚动
    scroll()
  }, [...deps, scroll, isHistoryLoading])

  return bottomRef
}
