export type QuestionType =
  | 'single_choice'
  | 'multiple_choice'
  | 'true_false'
  | 'fill_blank'
  | 'coding'

export type DifficultyLevel = 'easy' | 'medium' | 'hard' | 'mixed'

export type SectionStatus =
  | 'not_started'
  | 'in_progress'
  | 'completed'
  | 'recommended'
  | 'locked'

export type PracticeMode =
  | 'section'
  | 'wrong_questions'
  | 'favorites'
  | 'ai_generated'
  | 'review'

export type QuestionStatus = 'unanswered' | 'answered' | 'correct' | 'wrong' | 'skipped'

export type SessionStatus = 'not_started' | 'in_progress' | 'submitted' | 'expired'

export type RecommendReasonType =
  | 'learning_path'
  | 'weak_point'
  | 'wrong_questions'
  | 'review'
  | 'new_topic'

export interface KnowledgePoint {
  id: string
  name: string
  masteryLevel?: number
}

export interface QuestionOption {
  id: string
  label: string
  content: string
}

export interface Question {
  id: string
  type: QuestionType
  title: string
  content: string
  difficulty?: DifficultyLevel
  options?: QuestionOption[]
  answer?: string | string[]
  userAnswer?: string | string[]
  analysis?: string
  knowledgePoints?: KnowledgePoint[]
  isFavorite?: boolean
  isMarked?: boolean
  status?: QuestionStatus
  score?: number
  codeContent?: string | null
}

export interface QuizOverview {
  totalQuestions: number
  completedQuestions: number
  accuracyRate: number
  totalPracticeDurationMinutes: number
  todayTargetCount?: number
  todayCompletedCount?: number
}

export interface QuizSection {
  id: string
  chapterId: string
  title: string
  description?: string
  order: number
  questionCount: number
  completedCount: number
  accuracyRate?: number
  difficulty?: DifficultyLevel
  status: SectionStatus
  knowledgePointIds?: string[]
  learningPathNodeId?: string
  estimatedMinutes?: number
  lastSessionId?: string
  questionIds: number[]
}

export interface QuizChapter {
  id: string
  title: string
  description?: string
  order: number
  totalQuestions: number
  completedQuestions: number
  accuracyRate?: number
  isLocked?: boolean
  sections: QuizSection[]
}

export interface RecommendedPractice {
  id: string
  title: string
  reason: string
  reasonType: RecommendReasonType
  description?: string
  questionCount: number
  difficulty: DifficultyLevel
  estimatedMinutes?: number
  priority?: number
  knowledgePointIds?: string[]
  learningPathNodeId?: string
  startPayload: {
    mode: PracticeMode
    sectionId?: string
    questionIds?: number[]
    sessionId?: string
  }
}

export interface PracticeRecord {
  id: string
  sessionId: string
  title: string
  practicedAt: string
  completedCount: number
  totalCount: number
  accuracyRate?: number
  durationMinutes?: number
  canContinue: boolean
}

export interface PracticeSession {
  id: string
  title: string
  mode: PracticeMode
  totalQuestions: number
  currentIndex: number
  completedCount: number
  correctCount: number
  wrongCount: number
  durationLimitSeconds?: number
  remainingSeconds?: number
  questions: Question[]
  startedAt: string
  status: SessionStatus
  sectionId?: string
  kpId?: string
}

export interface SubmitAnswerResult {
  questionId: string
  isCorrect: boolean
  correctAnswer?: string | string[]
  analysis?: string
  knowledgePoints?: KnowledgePoint[]
  updatedSession?: PracticeSession
}

export interface PracticeResult {
  sessionId: string
  score?: number
  totalQuestions: number
  correctCount: number
  wrongCount: number
  accuracyRate: number
  durationMinutes: number
  weakKnowledgePoints: KnowledgePoint[]
  aiSuggestion?: string
}

export interface QuizCatalogFilters {
  keyword?: string
  type?: string
  difficulty?: string
  status?: string
  learningPathId?: string
  sort?: string
  page?: number
  pageSize?: number
}

export interface QuizCatalogPageResult {
  chapters: QuizChapter[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}

export interface QuestionTypeDistribution {
  type: QuestionType
  label: string
  count: number
  percent: number
}

export interface CreateSessionPayload {
  mode: PracticeMode
  sectionId?: string
  questionIds?: number[]
  learningPathNodeId?: string
  title?: string
}
