import { useCallback, useEffect, useState } from 'react'
import { loadChatSidebarData } from '@/services/chatSidebarService'
import { usePathStore } from '@/utils/store/pathStore'
import { useProfileStore } from '@/utils/store/profileStore'
import type { ChatSidebarData } from '@/utils/types/chatSidebar'

const EMPTY: ChatSidebarData = {
  todayGoal: {
    progress: 0,
    items: [
      { label: '今日练习', current: 0, total: 5, unit: '题', color: '#22C55E' },
      { label: '路径节点', current: 0, total: 1, unit: '个', color: '#2563EB' },
      { label: '当前阶段', current: 0, total: 1, unit: '待开始', color: '#F59E0B' },
    ],
  },
  quickTools: [],
  recentLearning: [],
}

export function useChatSidebar() {
  const profileRevision = useProfileStore((s) => s.revision)
  const pathNodes = usePathStore((s) => s.nodes)
  const [data, setData] = useState<ChatSidebarData>(EMPTY)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      setData(await loadChatSidebarData())
    } catch {
      setData(EMPTY)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh, profileRevision, pathNodes.length])

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === 'visible') void refresh()
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [refresh])

  return { data, loading, refresh }
}
