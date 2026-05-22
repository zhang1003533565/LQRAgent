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
  recentGoals?: string[]
  knowledgeMap?: { kpId: string; title: string; mastery: number }[]
}
