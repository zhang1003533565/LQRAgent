export type ResourceType =
  | 'LESSON'
  | 'QUIZ'
  | 'CODE_CASE'
  | 'ILLUSTRATION'
  | 'VIDEO_CLIP'

export interface LearningResource {
  id: number
  kpId: string
  resourceType: ResourceType
  title: string
  content?: string
  mediaUrl?: string
  mediaMime?: string
  generationPrompt?: string
  relatedKpIds?: string[]
}

export interface MediaPayload {
  url: string
  mime: string
  title: string
  kpId: string
  thumbnailUrl?: string
  posterUrl?: string
  durationSec?: number
}
