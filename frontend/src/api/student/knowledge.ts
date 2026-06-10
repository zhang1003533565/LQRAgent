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

/** 按 kpId 查询单个知识点详情 */
export async function getKnowledgePointDetail(kpId: string): Promise<KnowledgePointOption | null> {
  try {
    const res = await http.get<{ data: KnowledgePointOption }>(`/knowledge-points/${encodeURIComponent(kpId)}`)
    return res.data.data
  } catch {
    return null
  }
}

/** 按关键词搜索知识点 */
export async function searchKnowledgePoints(keyword: string): Promise<KnowledgePointOption[]> {
  if (!keyword.trim()) return []
  const res = await http.get<{ data: KnowledgePointOption[] }>('/knowledge-points/search', {
    params: { keyword },
  })
  return res.data.data
}
