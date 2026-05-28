import http from '@/api/http'
import type { LearningResource } from '@/utils/types/media-resource'

/** 答题提交请求 */
export interface QuizSubmitRequest {
  kpId: string
  resourceId?: number
  answer: string
  expectedAnswer?: string
}

/** 答题结果 */
export interface QuizResult {
  id: number
  correct: boolean
  score: number
  kpId: string
  answer: string
}

/**
 * 获取某知识点的 QUIZ 类题目资源。
 * 先查已有资源，若无则生成一份。
 */
export async function getQuizQuestions(kpId: string): Promise<LearningResource[]> {
  try {
    const res = await http.get<{ data: LearningResource[] }>(`/resources/${kpId}`)
    const all = res.data.data || []
    const quizzes = all.filter((r) => r.resourceType === 'QUIZ')
    if (quizzes.length > 0) return quizzes
  } catch {
    // 不存在则生成
  }

  // 没有现有题目 → 生成一份
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

/**
 * 提交答案。
 * POST /api/quiz/submit → 判分 + 落记录 + 触发画像更新
 */
export async function submitQuiz(req: QuizSubmitRequest): Promise<QuizResult> {
  const res = await http.post<{ data: QuizResult }>('/quiz/submit', req)
  return res.data.data
}
