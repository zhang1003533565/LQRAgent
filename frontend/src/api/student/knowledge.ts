import http from '@/api/http'

export interface KnowledgePointOption {
  kpId: string
  title: string
  subject?: string
  description?: string
}

export async function listKnowledgePointsByIds(ids: string[]): Promise<KnowledgePointOption[]> {
  if (ids.length === 0) return []
  const res = await http.get<{ data: KnowledgePointOption[] }>('/knowledge-points', {
    params: { ids },
    paramsSerializer: {
      indexes: null,
    },
  })
  return res.data.data
}
