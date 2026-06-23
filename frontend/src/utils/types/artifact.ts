/** artifact 事件与右栏资源结构 */

export type ArtifactKind =
  | 'learning_path'
  | 'lesson'
  | 'quiz'
  | 'code_case'
  | 'media_image'
  | 'media_video'
  | 'multi_card'
  | 'rag_sources'
  | 'diagram'
  | 'video'

export interface MediaVideoPayload {
  url: string
  caption?: string
  mediaType?: string
}

export interface QuizArtifactPayload {
  title?: string
  topic?: string
  difficulty?: string
  questions: {
    id: number
    type: string
    stem: string
    options?: string[]
    answer?: string
    explanation?: string
    difficulty?: string
  }[]
}

export interface ArtifactEvent {
  type: 'artifact'
  kind: ArtifactKind
  payload: unknown
  session_id?: string
}

export interface MediaImagePayload {
  url: string
  prompt?: string
  mediaType?: string
}

export interface LearningPathArtifactPayload {
  goal: string
  nodes: import('./learning-path').PathNode[]
  planDescription?: string
}

/** RAG 引用来源（知识库检索结果片段） */
export interface RagSource {
  title: string
  content: string
  source: string
  page?: string
  chunk_id?: string
  score?: number | string
  kbName?: string
}
