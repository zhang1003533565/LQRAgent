import http from '@/api/http'

type BehaviorAction =
  | 'chat_send'
  | 'view_resource'
  | 'quiz_answer'
  | 'view_path'
  | 'upload_file'
  | 'view_diagram'
  | 'view_summary'

interface TrackPayload {
  kpId: string
  action: BehaviorAction
  durationSec?: number
  extra?: string
}

/**
 * 学习行为埋点上报（Fire-and-forget，不阻塞主流程）
 */
export function trackBehavior(payload: TrackPayload): void {
  http
    .post('/behavior', payload)
    .catch(() => {
      // 埋点失败不应影响用户体验
    })
}

/**
 * 页面停留计时工具
 * 用法：在 useEffect 中调用 startPageTimer，返回的 stop 在 cleanup 中调用
 */
export function usePageTimer(action: BehaviorAction, kpId?: string): () => void {
  const startTime = Date.now()
  return () => {
    const durationSec = Math.round((Date.now() - startTime) / 1000)
    if (durationSec > 0 && kpId) {
      trackBehavior({ kpId, action, durationSec })
    }
  }
}
