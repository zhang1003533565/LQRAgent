import type { ArtifactKind } from '@/utils/types/artifact'
import type { LearningResource, ResourceType } from '@/utils/types/media-resource'

function mapKindToType(kind: ArtifactKind): ResourceType | null {
  if (kind === 'lesson' || kind === 'text') return 'LESSON'
  if (kind === 'code_case') return 'CODE_CASE'
  if (kind === 'quiz') return 'QUIZ'
  if (kind === 'media_image') return 'ILLUSTRATION'
  if (kind === 'media_video' || kind === 'video') return 'VIDEO_CLIP'
  return null
}

/** 将 WS artifact 转为资源页可展示的 LearningResource */
export function artifactPayloadToResource(
  kind: ArtifactKind,
  payload: unknown,
  kpId: string,
): LearningResource | null {
  const resourceType = mapKindToType(kind)
  if (!resourceType || !kpId) return null

  const p = (payload && typeof payload === 'object' ? payload : {}) as Record<string, unknown>
  const content = String(p.content ?? p.body ?? p.text ?? '').trim()
  const mediaUrl = String(p.url ?? p.mediaUrl ?? '').trim()

  if (resourceType === 'ILLUSTRATION' || resourceType === 'VIDEO_CLIP') {
    if (!mediaUrl) return null
    return {
      id: -Date.now(),
      kpId,
      resourceType,
      title: String(p.title ?? p.caption ?? 'AI 生成媒体'),
      mediaUrl,
      generationPrompt: String(p.prompt ?? ''),
    }
  }

  if (!content) return null
  return {
    id: -Date.now(),
    kpId,
    resourceType,
    title: String(p.title ?? p.topic ?? 'AI 生成资源'),
    content,
    generationPrompt: String(p.prompt ?? ''),
  }
}
