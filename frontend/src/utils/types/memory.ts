/**
 * 记忆相关类型定义
 */

export type MemoryType = 
  | 'PREFERENCE'        // 用户偏好
  | 'LEARNING_PROGRESS' // 学习进度
  | 'TOPIC_INTEREST'    // 话题兴趣
  | 'INTERACTION_STYLE' // 交互风格
  | 'KNOWLEDGE_STATE'   // 知识掌握状态

export interface UserMemory {
  id: string
  userId: string
  memoryType: MemoryType
  content: string
  source?: string
  importance: number
  accessCount: number
  lastAccessedAt?: Date
  createdAt: Date
  updatedAt: Date
}

export interface MemoryState {
  memories: UserMemory[]
  loading: boolean
  error: string | null
  
  loadMemories: (userId: string, type?: MemoryType) => Promise<void>
  addMemory: (userId: string, memory: Partial<UserMemory>) => Promise<UserMemory>
  updateMemory: (memoryId: string, data: Partial<UserMemory>) => Promise<UserMemory>
  deleteMemory: (memoryId: string) => Promise<void>
  searchMemories: (userId: string, keyword: string) => Promise<UserMemory[]>
}
