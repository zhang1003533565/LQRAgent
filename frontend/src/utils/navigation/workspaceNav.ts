import type { NavigateFunction } from 'react-router-dom'
import { usePathStore } from '@/utils/store/pathStore'

export type WorkspaceTarget =
  | '/workspace/resources'
  | '/workspace/quiz'
  | '/workspace/learning-path'
  | '/workspace/upload'
  | '/workspace/profile'
  | '/workspace/knowledge-graph'

export type WorkspaceSearch = {
  kpId?: string | null
  resourceId?: string | null
  subject?: string | null
  tab?: string | null
}

export function parseWorkspaceSearch(searchParams: URLSearchParams): WorkspaceSearch {
  return {
    kpId: searchParams.get('kpId'),
    resourceId: searchParams.get('resourceId'),
    subject: searchParams.get('subject'),
    tab: searchParams.get('tab'),
  }
}

export function buildWorkspaceSearch(params: WorkspaceSearch): string {
  const parts: string[] = []
  if (params.kpId) parts.push(`kpId=${encodeURIComponent(params.kpId)}`)
  if (params.resourceId) parts.push(`resourceId=${encodeURIComponent(params.resourceId)}`)
  if (params.subject) parts.push(`subject=${encodeURIComponent(params.subject)}`)
  if (params.tab) parts.push(`tab=${encodeURIComponent(params.tab)}`)
  return parts.length > 0 ? `?${parts.join('&')}` : ''
}

function resolveSearchParams(params?: WorkspaceSearch | string | null): WorkspaceSearch {
  if (typeof params === 'string') {
    return { kpId: params }
  }
  if (params && typeof params === 'object') {
    return params
  }
  const kpId = usePathStore.getState().selectedKpId
  return kpId ? { kpId } : {}
}

export function navigateToWorkspace(
  navigate: NavigateFunction,
  path: WorkspaceTarget,
  params?: WorkspaceSearch | string | null,
) {
  const searchParams = resolveSearchParams(params)
  if (searchParams.kpId) {
    usePathStore.getState().selectNode(searchParams.kpId)
  }
  navigate(`${path}${buildWorkspaceSearch(searchParams)}`)
}

export function syncWorkspaceFromSearchParams(searchParams: URLSearchParams) {
  const { kpId } = parseWorkspaceSearch(searchParams)
  if (kpId) usePathStore.getState().selectNode(kpId)
}

/** @deprecated use syncWorkspaceFromSearchParams */
export function syncKpFromSearchParams(searchParams: URLSearchParams) {
  syncWorkspaceFromSearchParams(searchParams)
}
