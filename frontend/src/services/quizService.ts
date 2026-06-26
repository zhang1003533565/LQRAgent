import {
  getNextQuizQuestion,
  getQuizQuestionDetail,
  getQuizRecords,
  getQuizStats,
  listQuizQuestions,
  submitQuiz,
  type QuizQuestionListItem,
} from '@/api/student/quiz'
import {
  buildCatalogFromQuestions,
  computeTypeDistribution,
  detailToQuestion,
  mapOverview,
  weakPointsFromRecords,
} from '@/utils/quiz/quizMappers'
import {
  deleteSession,
  getFavoriteQuestionIds,
  getMarkedQuestionIds,
  getStoredSession,
  getStoredSessions,
  savePracticeResult,
  saveSession,
  setQuestionFavorite,
  setQuestionMarked,
} from '@/utils/quiz/quizSessionStorage'
import type {
  CreateSessionPayload,
  PracticeRecord,
  PracticeResult,
  PracticeSession,
  Question,
  QuizCatalogFilters,
  QuizCatalogPageResult,
  QuizChapter,
  QuizOverview,
  RecommendedPractice,
  SubmitAnswerResult,
} from '@/utils/types/quiz'
import { usePathStore } from '@/utils/store/pathStore'

function newSessionId() {
  return `session-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

async function loadQuestionsByIds(ids: number[]): Promise<Question[]> {
  const favorites = getFavoriteQuestionIds()
  const marked = getMarkedQuestionIds()
  const questions: Question[] = []
  for (const id of ids) {
    try {
      const detail = await getQuizQuestionDetail(id)
      questions.push(
        detailToQuestion(detail, {
          isFavorite: favorites.has(id),
          isMarked: marked.has(id),
        }),
      )
    } catch {
      // skip failed loads
    }
  }
  return questions
}

export async function getQuizOverview(): Promise<QuizOverview> {
  try {
    const [stats, records] = await Promise.all([getQuizStats(), getQuizRecords()])
    return mapOverview(stats, records)
  } catch {
    const res = await listQuizQuestions({ page: 1, size: 1 })
    return {
      totalQuestions: res.total || 0,
      completedQuestions: 0,
      accuracyRate: 0,
      totalPracticeDurationMinutes: 0,
      todayTargetCount: 10,
      todayCompletedCount: 0,
    }
  }
}

export async function getQuizCatalog(params?: QuizCatalogFilters): Promise<QuizCatalogPageResult> {
  const all = await buildQuizCatalog(params)
  const page = Math.max(1, params?.page ?? 1)
  const pageSize = Math.max(1, params?.pageSize ?? 5)
  const total = all.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, totalPages)
  const start = (safePage - 1) * pageSize

  return {
    chapters: all.slice(start, start + pageSize),
    total,
    page: safePage,
    pageSize,
    totalPages,
  }
}

async function buildQuizCatalog(params?: QuizCatalogFilters): Promise<QuizChapter[]> {
  const { nodes, selectedKpId, goal } = usePathStore.getState()
  const res = await listQuizQuestions({
    page: 1,
    size: 1000,
    questionType: params?.type && params.type !== 'all' ? params.type : undefined,
    knowledgePoint: params?.keyword || undefined,
  })

  let items = res.items || []
  const records = await getQuizRecords()

  if (params?.keyword?.trim()) {
    const q = params.keyword.trim().toLowerCase()
    items = items.filter(
      (item) =>
        item.title.toLowerCase().includes(q) ||
        (item.knowledgePoint || '').toLowerCase().includes(q),
    )
  }

  if (params?.learningPathId === 'current' && selectedKpId) {
    items = items.filter(
      (item) =>
        item.knowledgePoint === selectedKpId ||
        (item.knowledgePoint || '').includes(selectedKpId),
    )
  }

  let chapters = buildCatalogFromQuestions(items, records, nodes, selectedKpId)

  if (params?.status && params.status !== 'all') {
    chapters = chapters
      .map((ch) => ({
        ...ch,
        sections: ch.sections.filter((sec) => {
          if (params.status === 'completed') return sec.status === 'completed'
          if (params.status === 'in_progress') return sec.status === 'in_progress'
          if (params.status === 'not_started') return sec.status === 'not_started'
          if (params.status === 'recommended') return sec.status === 'recommended'
          return true
        }),
      }))
      .filter((ch) => ch.sections.length > 0)
  }

  if (params?.difficulty && params.difficulty !== 'all') {
    const map: Record<string, string> = { easy: 'easy', medium: 'medium', hard: 'hard', 基础: 'easy', 进阶: 'medium', 挑战: 'hard' }
    const target = map[params.difficulty] || params.difficulty
    chapters = chapters
      .map((ch) => ({
        ...ch,
        sections: ch.sections.filter((sec) => sec.difficulty === target),
      }))
      .filter((ch) => ch.sections.length > 0)
  }

  if (goal && chapters.length === 0 && items.length === 0) {
    return []
  }

  return chapters
}

export async function getRecommendedPractices(params?: {
  learningPathId?: string
  limit?: number
}): Promise<RecommendedPractice[]> {
  const limit = params?.limit ?? 3
  const { selectedKpId, nodes } = usePathStore.getState()
  const [chapters, records] = await Promise.all([
    buildQuizCatalog({ learningPathId: 'current' }),
    getQuizRecords(),
  ])

  const recommendations: RecommendedPractice[] = []

  const weak = weakPointsFromRecords(records)
  if (weak.length > 0) {
    recommendations.push({
      id: 'rec-wrong',
      title: '错题回顾练习',
      reason: '薄弱知识点',
      reasonType: 'wrong_questions',
      description: `针对 ${weak[0].name} 等薄弱点巩固`,
      questionCount: Math.min(10, records.filter((r) => !r.correct).length),
      difficulty: 'mixed',
      estimatedMinutes: 15,
      priority: 1,
      knowledgePointIds: weak.map((w) => w.id),
      startPayload: { mode: 'wrong_questions' },
    })
  }

  for (const ch of chapters) {
    for (const sec of ch.sections) {
      if (sec.status === 'recommended' || sec.status === 'in_progress') {
        recommendations.push({
          id: `rec-${sec.id}`,
          title: sec.title,
          reason: sec.status === 'recommended' ? '当前路径推荐' : '继续上次练习',
          reasonType: sec.status === 'recommended' ? 'learning_path' : 'review',
          description: sec.description,
          questionCount: sec.questionCount,
          difficulty: sec.difficulty || 'mixed',
          estimatedMinutes: sec.estimatedMinutes,
          priority: sec.status === 'recommended' ? 2 : 3,
          knowledgePointIds: sec.knowledgePointIds,
          learningPathNodeId: selectedKpId || undefined,
          startPayload: {
            mode: 'section',
            sectionId: sec.id,
            questionIds: sec.questionIds,
            sessionId: sec.lastSessionId,
          },
        })
      }
    }
  }

  // TODO: 接入后端 AI 推荐接口 GET /api/quiz/recommendations
  return recommendations.slice(0, limit)
}

export async function getRecentPracticeRecords(params?: {
  limit?: number
}): Promise<PracticeRecord[]> {
  const limit = params?.limit ?? 5
  const sessions = Object.values(getStoredSessions())
    .filter((s) => s.status !== 'submitted')
    .sort((a, b) => b.startedAt.localeCompare(a.startedAt))

  const submitted = Object.values(getStoredSessions())
    .filter((s) => s.status === 'submitted')
    .sort((a, b) => b.startedAt.localeCompare(a.startedAt))

  const all = [...sessions, ...submitted]
  return all.slice(0, limit).map((s) => ({
    id: s.id,
    sessionId: s.id,
    title: s.title,
    practicedAt: s.startedAt,
    completedCount: s.completedCount,
    totalCount: s.totalQuestions,
    accuracyRate:
      s.completedCount > 0 ? Math.round((s.correctCount / s.completedCount) * 100) : undefined,
    durationMinutes: Math.max(1, Math.round(s.completedCount * 2)),
    canContinue: s.status === 'in_progress',
  }))
}

export async function createPracticeSession(
  payload: CreateSessionPayload,
): Promise<PracticeSession> {
  let questionIds = payload.questionIds || []
  let title = payload.title || '练习'

  if (payload.mode === 'wrong_questions') {
    const records = await getQuizRecords()
    questionIds = [...new Set(records.filter((r) => !r.correct).map((r) => r.questionId))]
    title = '错题复习'
  } else if (payload.mode === 'favorites') {
    questionIds = Array.from(getFavoriteQuestionIds())
    title = '收藏题目练习'
  } else if (payload.sectionId) {
    const chapters = await buildQuizCatalog()
    const section = chapters.flatMap((c) => c.sections).find((s) => s.id === payload.sectionId)
    if (section) {
      questionIds = section.questionIds
      title = section.title
    }
  }

  if (questionIds.length === 0) {
    const res = await listQuizQuestions({ page: 1, size: 20 })
    questionIds = (res.items || []).map((i) => i.id)
    title = title === '练习' ? '综合练习' : title
  }

  const questions = await loadQuestionsByIds(questionIds)
  if (questions.length === 0) {
    throw new Error('暂无可练习的题目')
  }

  const session: PracticeSession = {
    id: newSessionId(),
    title,
    mode: payload.mode,
    totalQuestions: questions.length,
    currentIndex: 0,
    completedCount: 0,
    correctCount: 0,
    wrongCount: 0,
    questions,
    startedAt: new Date().toISOString(),
    status: 'in_progress',
    sectionId: payload.sectionId,
    kpId: payload.learningPathNodeId,
  }

  saveSession(session)
  return session
}

export async function getPracticeSession(sessionId: string): Promise<PracticeSession> {
  const session = getStoredSession(sessionId)
  if (!session) throw new Error('练习会话不存在或已过期')
  return session
}

export async function submitQuestionAnswer(payload: {
  sessionId: string
  questionId: string
  answer: string | string[]
}): Promise<SubmitAnswerResult> {
  const session = getStoredSession(payload.sessionId)
  if (!session) throw new Error('练习会话不存在')

  const qIndex = session.questions.findIndex((q) => q.id === payload.questionId)
  if (qIndex < 0) throw new Error('题目不存在')

  const answerStr = Array.isArray(payload.answer) ? payload.answer.join(',') : payload.answer
  const questionIdNum = Number(payload.questionId)

  let isCorrect = false
  let correctAnswer: string | undefined
  let analysis: string | undefined
  let knowledgePoints = session.questions[qIndex].knowledgePoints

  if (questionIdNum > 0) {
    const result = await submitQuiz({
      questionId: questionIdNum,
      kpId: session.kpId,
      answer: answerStr,
    })
    isCorrect = result.correct
    correctAnswer = result.correctAnswer || undefined
    analysis = result.analysis || undefined
  }

  const updatedQuestions = [...session.questions]
  const prevStatus = updatedQuestions[qIndex].status
  updatedQuestions[qIndex] = {
    ...updatedQuestions[qIndex],
    userAnswer: payload.answer,
    status: isCorrect ? 'correct' : 'wrong',
    analysis: analysis || updatedQuestions[qIndex].analysis,
    answer: correctAnswer,
  }

  let { correctCount, wrongCount, completedCount } = session
  if (prevStatus !== 'correct' && prevStatus !== 'wrong') {
    completedCount += 1
    if (isCorrect) correctCount += 1
    else wrongCount += 1
  } else if (prevStatus === 'wrong' && isCorrect) {
    correctCount += 1
    wrongCount = Math.max(0, wrongCount - 1)
  } else if (prevStatus === 'correct' && !isCorrect) {
    wrongCount += 1
    correctCount = Math.max(0, correctCount - 1)
  }

  const updatedSession: PracticeSession = {
    ...session,
    questions: updatedQuestions,
    correctCount,
    wrongCount,
    completedCount,
  }
  saveSession(updatedSession)

  return {
    questionId: payload.questionId,
    isCorrect,
    correctAnswer,
    analysis,
    knowledgePoints,
    updatedSession,
  }
}

export async function markQuestion(payload: {
  questionId: string
  marked: boolean
}): Promise<void> {
  setQuestionMarked(Number(payload.questionId), payload.marked)
  // TODO: POST /api/quiz/questions/{id}/mark
}

export async function favoriteQuestion(payload: {
  questionId: string
  favorite: boolean
}): Promise<void> {
  setQuestionFavorite(Number(payload.questionId), payload.favorite)
  // TODO: POST /api/quiz/questions/{id}/favorite
}

export async function submitPracticeSession(sessionId: string): Promise<PracticeResult> {
  const session = getStoredSession(sessionId)
  if (!session) throw new Error('练习会话不存在')

  const weakKnowledgePoints = session.questions
    .filter((q) => q.status === 'wrong')
    .flatMap((q) => q.knowledgePoints || [])
    .filter((kp, i, arr) => arr.findIndex((x) => x.id === kp.id) === i)

  const result: PracticeResult = {
    sessionId,
    score: session.totalQuestions > 0 ? Math.round((session.correctCount / session.totalQuestions) * 100) : 0,
    totalQuestions: session.totalQuestions,
    correctCount: session.correctCount,
    wrongCount: session.wrongCount,
    accuracyRate:
      session.completedCount > 0
        ? Math.round((session.correctCount / session.completedCount) * 100)
        : 0,
    durationMinutes: Math.max(1, Math.round(session.completedCount * 2)),
    weakKnowledgePoints,
    aiSuggestion:
      weakKnowledgePoints.length > 0
        ? `建议优先复习「${weakKnowledgePoints[0].name}」，可通过学习资源和错题本巩固。`
        : '表现不错，可以尝试进阶练习或学习路径中的下一节点。',
  }

  saveSession({ ...session, status: 'submitted' })
  savePracticeResult(result)
  return result
}

export async function getWrongQuestions(params?: {
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<Question[]> {
  const records = await getQuizRecords()
  const wrongIds = [...new Set(records.filter((r) => !r.correct).map((r) => r.questionId))]
  let questions = await loadQuestionsByIds(wrongIds)

  if (params?.keyword?.trim()) {
    const q = params.keyword.trim().toLowerCase()
    questions = questions.filter(
      (item) =>
        item.title.toLowerCase().includes(q) ||
        item.knowledgePoints?.some((kp) => kp.name.toLowerCase().includes(q)),
    )
  }

  return questions
}

export async function getFavoriteQuestions(params?: {
  keyword?: string
}): Promise<Question[]> {
  const ids = Array.from(getFavoriteQuestionIds())
  let questions = await loadQuestionsByIds(ids)

  if (params?.keyword?.trim()) {
    const q = params.keyword.trim().toLowerCase()
    questions = questions.filter((item) => item.title.toLowerCase().includes(q))
  }

  return questions
}

export async function getQuestionTypeDistribution() {
  const res = await listQuizQuestions({ page: 1, size: 1000 })
  return computeTypeDistribution(res.items || [])
}

export async function generatePracticeFromPath(kpId?: string): Promise<PracticeSession> {
  const id = kpId || usePathStore.getState().selectedKpId
  // TODO: POST /api/quiz/generate { kpId }
  const res = await listQuizQuestions({
    page: 1,
    size: 20,
    knowledgePoint: id || undefined,
  })
  const questionIds = (res.items || []).map((i: QuizQuestionListItem) => i.id)
  return createPracticeSession({
    mode: 'ai_generated',
    questionIds,
    learningPathNodeId: id || undefined,
    title: id ? `${id} 专项练习` : 'AI 生成练习',
  })
}

export { getNextQuizQuestion, deleteSession }
