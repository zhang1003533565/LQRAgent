import http from '@/api/http'
import type { BackendProfileDetail } from '@/utils/learningProfile/profileMappers'

export async function fetchProfileDetailRaw(): Promise<BackendProfileDetail> {
  const res = await http.get<{ data: BackendProfileDetail }>('/profile/detail')
  return res.data.data
}

export async function refreshProfileRaw(): Promise<BackendProfileDetail> {
  await http.patch('/profile/summary', {})
  return fetchProfileDetailRaw()
}
