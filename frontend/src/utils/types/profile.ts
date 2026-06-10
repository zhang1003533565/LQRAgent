export interface ProfileSummary {
  displayName: string
  masteryLevel?: number
  completedKpCount?: number
  weakTopics?: string[]
  streakDays?: number
}

export interface ProfilePatchEvent {
  type: 'profile_patch'
  payload: Partial<ProfileSummary>
  session_id?: string
}

export interface ProfileDetail extends ProfileSummary {
  username: string
  role: string
  // 展平的 summary 字段（后端已扁平化）
  knowledgeLevel?: string
  learningGoal?: string
  cognitiveStyle?: string
  learningPace?: string
  // 兼容旧嵌套结构
  summary?: {
    knowledgeLevel?: string
    learningGoal?: string
    cognitiveStyle?: string
    learningPace?: string
  }
  recentGoals?: string[]
  knowledgeMap?: { kpId: string; title: string; mastery: number; status?: string }[]
}
