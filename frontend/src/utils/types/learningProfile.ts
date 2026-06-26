export type ProfileRange = '7d' | '30d' | '90d' | 'all'
export type TrendMetric = 'mastery' | 'accuracy' | 'duration' | 'nodes' | 'questions'
export type MasteryLevel = 'beginner' | 'developing' | 'good' | 'advanced' | 'excellent'
export type MasteryStatus = 'weak' | 'normal' | 'good' | 'mastered'
export type InsightType = 'strength' | 'weakness' | 'suggestion' | 'risk' | 'achievement'
export type InsightActionType =
  | 'practice'
  | 'review_resource'
  | 'continue_path'
  | 'generate_plan'
  | 'view_graph'

export interface LearningProfileFilters {
  learningPathId?: string
  range?: ProfileRange
  trendMetric?: TrendMetric
}

export interface LearningProfileOverview {
  userId: string
  overallMasteryRate: number
  masteryLevel?: MasteryLevel
  continuousLearningDays: number
  longestContinuousLearningDays?: number
  totalLearningDurationMinutes?: number
  completedLearningPathNodes?: number
  totalLearningPathNodes?: number
  completedQuestions?: number
  accuracyRate?: number
  strongestDimension?: string
  weakestDimension?: string
  currentSuggestion?: string
  updatedAt: string
}

export interface AbilityDimension {
  id: string
  name: string
  score: number
  averageScore?: number
  maxScore?: number
  description?: string
  trend?: 'up' | 'down' | 'stable'
}

export interface KnowledgeMasteryItem {
  knowledgePointId: string
  name: string
  parentName?: string
  masteryRate: number
  completedQuestionCount?: number
  totalQuestionCount?: number
  correctRate?: number
  learningResourceCount?: number
  status: MasteryStatus
  trend?: 'up' | 'down' | 'stable'
  relatedLearningPathNodeId?: string
}

export interface LearningInsight {
  id: string
  type: InsightType
  title: string
  content: string
  priority?: number
  relatedKnowledgePointIds?: string[]
  relatedLearningPathNodeIds?: string[]
  action?: {
    label: string
    type: InsightActionType
    targetId?: string
    href?: string
  }
}

export interface LearningTrendPoint {
  date: string
  overallMasteryRate?: number
  accuracyRate?: number
  learningDurationMinutes?: number
  completedNodeCount?: number
  completedQuestionCount?: number
}

export interface LearningAchievement {
  id: string
  title: string
  description?: string
  icon?: string
  achievedAt?: string
  level?: 'bronze' | 'silver' | 'gold' | 'platinum'
  progress?: number
  target?: number
  achieved: boolean
}

export interface WeakKnowledgePoint {
  knowledgePointId: string
  name: string
  weaknessReason?: string
  masteryRate: number
  wrongQuestionCount?: number
  recommendedAction?: {
    label: string
    type: 'practice' | 'review' | 'resource' | 'chat'
    targetId?: string
  }
}

export interface RecentLearningActivity {
  id: string
  type: 'path' | 'quiz' | 'resource' | 'upload' | 'chat'
  title: string
  description?: string
  occurredAt: string
  targetId?: string
}

export interface LearningProfile {
  overview: LearningProfileOverview
  abilityDimensions: AbilityDimension[]
  knowledgeMastery: KnowledgeMasteryItem[]
  insights: LearningInsight[]
  trends: LearningTrendPoint[]
  achievements: LearningAchievement[]
  weakKnowledgePoints: WeakKnowledgePoint[]
  recentActivities: RecentLearningActivity[]
  pathOptions: Array<{ id: string; title: string }>
  pathSummary?: string
  pathNodeTitles?: string[]
}
