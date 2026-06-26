import type { NavigateFunction } from 'react-router-dom'
import { usePathStore } from '@/utils/store/pathStore'

type WorkspaceTarget =
  | '/workspace/resources'
  | '/workspace/quiz'
  | '/workspace/learning-path'
  | '/workspace/upload'
  | '/workspace/profile'

export function navigateToWorkspace(
  navigate: NavigateFunction,
  path: WorkspaceTarget,
  kpId?: string | null,
) {
  const id = kpId ?? usePathStore.getState().selectedKpId
  if (id) usePathStore.getState().selectNode(id)
  const search = id ? `?kpId=${encodeURIComponent(id)}` : ''
  navigate(`${path}${search}`)
}

export function syncKpFromSearchParams(searchParams: URLSearchParams) {
  const kpId = searchParams.get('kpId')
  if (kpId) usePathStore.getState().selectNode(kpId)
}
