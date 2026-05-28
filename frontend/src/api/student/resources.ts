import http from '@/api/http'
import type { LearningResource, ResourceType } from '@/utils/types/media-resource'

export interface GenerateResourceRequest {
  kpId: string
  resourceType: ResourceType
  prompt?: string
}

export async function getResources(kpId: string): Promise<LearningResource[]> {
  try {
    const res = await http.get<{ data: LearningResource[] }>(`/resources/${kpId}`)
    return res.data.data || []
  } catch {
    return []
  }
}

export async function generateResource(
  req: GenerateResourceRequest,
): Promise<LearningResource> {
  try {
    const res = await http.post<{ data: LearningResource }>('/resources/generate', req)
    return res.data.data
  } catch {
    return buildPlaceholderResource(req)
  }
}

function buildPlaceholderResource(req: GenerateResourceRequest): LearningResource {
  const titles: Record<ResourceType, string> = {
    LESSON: '讲义（占位）',
    QUIZ: '练习题（占位）',
    CODE_CASE: '代码案例（占位）',
    ILLUSTRATION: '示意图（占位）',
    VIDEO_CLIP: '讲解视频（占位）',
  }
  return {
    id: 0,
    kpId: req.kpId,
    resourceType: req.resourceType,
    title: titles[req.resourceType],
    content: `【待对接】${req.kpId} 的 ${req.resourceType} 资源将在后端 \`POST /api/resources/generate\` 就绪后自动加载。`,
  }
}
