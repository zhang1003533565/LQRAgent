import http from '@/api/http'
import type { LearningResource } from '@/utils/types/media-resource'
import type { PracticeSession } from '@/utils/types/quiz'

export interface QuizQuestionListItem {
  id: number
  title: string
  questionType: string
  difficulty: number
  knowledgePoint?: string | null
  status: number
}

export interface QuizQuestionPage {
  items: QuizQuestionListItem[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface QuizQuestionDetail {
  id: number
  title: string
  codeContent?: string | null
  questionType: string
  optionA?: string | null
  optionB?: string | null
  optionC?: string | null
  optionD?: string | null
  difficulty: number
  knowledgePoint?: string | null
  status: number
  analysis?: string | null
}

export interface QuizSubmitRequest {
  questionId: number
  kpId?: string
  resourceId?: number
  answer: string
}

export interface QuizResult {
  id: number
  questionId: number
  correct: boolean
  score: number
  kpId: string
  answer: string
  correctAnswer?: string | null
  analysis?: string | null
  weaknessReport?: string | null
}

export interface NextQuizQuestion {
  hasNext: boolean
  nextQuestionId?: number | null
}

export async function listQuizQuestions(params?: {
  page?: number
  size?: number
  questionType?: string
  knowledgePoint?: string
}): Promise<QuizQuestionPage> {
  const res = await http.get<{ data: QuizQuestionPage }>('/quiz/questions', { params })
  return res.data.data
}

export async function getQuizQuestionDetail(id: number): Promise<QuizQuestionDetail> {
  const res = await http.get<{ data: QuizQuestionDetail }>(`/quiz/questions/${id}`)
  return res.data.data
}

export async function getNextQuizQuestion(id: number): Promise<NextQuizQuestion> {
  const res = await http.get<{ data: NextQuizQuestion }>(`/quiz/questions/${id}/next`)
  return res.data.data
}

export async function getQuizQuestions(kpId: string): Promise<LearningResource[]> {
  try {
    const res = await http.get<{ data: LearningResource[] }>(`/resources/${kpId}`)
    const all = res.data.data || []
    const quizzes = all.filter((r) => r.resourceType === 'QUIZ')
    if (quizzes.length > 0) return quizzes
  } catch {
    // fall through to generation
  }

  try {
    const res = await http.post<{ data: LearningResource }>('/resources/generate', {
      kpId,
      resourceType: 'QUIZ',
    })
    return res.data.data ? [res.data.data] : []
  } catch {
    return []
  }
}

export async function submitQuiz(req: QuizSubmitRequest): Promise<QuizResult> {
  const res = await http.post<{ data: QuizResult }>('/quiz/submit', req)
  return res.data.data
}

export interface QuizStats {
  total: number
  correct: number
  wrong: number
  accuracy: number
}

export async function getQuizStats(): Promise<QuizStats> {
  const res = await http.get<{ data: QuizStats }>('/quiz/stats')
  return res.data.data
}

export interface QuizRecordItem {
  id: number
  questionId: number
  correct: boolean
  score: number
  kpId: string
  answer: string
  correctAnswer: string | null
  createdAt: string
}

export async function getQuizRecords(): Promise<QuizRecordItem[]> {
  const res = await http.get<{ data: QuizRecordItem[] }>('/quiz/records')
  return res.data.data
}

export interface QuizPreferences {
  favoriteQuestionIds: number[]
  markedQuestionIds: number[]
}

export async function getQuizPreferences(): Promise<QuizPreferences> {
  const res = await http.get<{ data: QuizPreferences }>('/quiz/preferences')
  return res.data.data
}

export async function saveQuizSession(session: PracticeSession): Promise<PracticeSession> {
  const res = await http.post<{ data: PracticeSession }>('/quiz/sessions', session)
  return res.data.data
}

export async function getQuizSession(sessionId: string): Promise<PracticeSession> {
  const res = await http.get<{ data: PracticeSession }>(`/quiz/sessions/${sessionId}`)
  return res.data.data
}

export async function listQuizSessions(): Promise<PracticeSession[]> {
  const res = await http.get<{ data: PracticeSession[] }>('/quiz/sessions')
  return res.data.data
}

export async function deleteQuizSession(sessionId: string): Promise<void> {
  await http.delete(`/quiz/sessions/${sessionId}`)
}

export async function favoriteQuizQuestion(questionId: number, favorite: boolean): Promise<void> {
  await http.post(`/quiz/questions/${questionId}/favorite`, { favorite })
}

export async function markQuizQuestion(questionId: number, marked: boolean): Promise<void> {
  await http.post(`/quiz/questions/${questionId}/mark`, { marked })
}
