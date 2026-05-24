import { useEffect, useRef } from 'react'

/**
 * 消息列表变化时滚动到底部。
 */
export function useChatAutoScroll<T>(deps: T[]) {
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, deps)

  return bottomRef
}
