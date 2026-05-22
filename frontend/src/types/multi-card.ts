/** 聊天区多模态卡片块（artifact kind: multi_card） */

export type MultiCardBlockType = 'text' | 'image' | 'video' | 'artifact_ref'

export interface MultiCardBlock {
  type: MultiCardBlockType
  content?: string
  url?: string
  title?: string
  mime?: string
  artifactKind?: string
}
