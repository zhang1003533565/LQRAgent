import http from '@/api/http'

export interface KnowledgePointOption {
  kpId: string
  title: string
  subject?: string
  description?: string
}

export interface KnowledgeGraphNode {
  kpId: string
  title: string
  subject?: string
  chapter?: string
  description?: string
  difficulty?: number
  shortName?: string
  alias?: string
  displayName?: string
  label?: string
}

export interface KnowledgeGraphEdge {
  fromKpId: string
  toKpId: string
  relationType: string
}

export interface KnowledgeGraphData {
  nodes: KnowledgeGraphNode[]
  edges: KnowledgeGraphEdge[]
  nodeCount: number
  edgeCount: number
  subjects: string[]
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

/** 获取完整知识图谱（节点+边） */
export async function getKnowledgeGraph(subject?: string): Promise<KnowledgeGraphData> {
  const params = subject ? { subject } : {}
  const res = await http.get<{ code: number; message: string; data: KnowledgeGraphData }>(
    '/knowledge-points/graph',
    { params },
  )
  const payload = res.data?.data
  if (!payload || !Array.isArray(payload.nodes)) {
    throw new Error(res.data?.message || '知识图谱数据格式异常')
  }
  return {
    nodes: payload.nodes,
    edges: payload.edges ?? [],
    nodeCount: payload.nodeCount ?? payload.nodes.length,
    edgeCount: payload.edgeCount ?? (payload.edges?.length ?? 0),
    subjects: payload.subjects ?? [],
  }
}
