import type {
  QuizQuestionDetail,
  QuizQuestionListItem,
  QuizRecordItem,
  QuizStats,
} from '@/api/student/quiz'
import type {
  DifficultyLevel,
  KnowledgePoint,
  Question,
  QuestionOption,
  QuestionType,
  QuizChapter,
  QuizOverview,
  QuizSection,
  SectionStatus,
} from '@/utils/types/quiz'
import type { PathNode } from '@/utils/types/learning-path'
import { resolveKpDisplay } from '@/utils/kp/kpDisplay'

export function mapQuestionType(raw?: string | null): QuestionType {
  const v = (raw || '').toLowerCase()
  if (['multiple', 'multiple_choice', 'multi'].includes(v)) return 'multiple_choice'
  if (['judge', 'true_false', 'boolean'].includes(v)) return 'true_false'
  if (['fill', 'fill_blank'].includes(v)) return 'fill_blank'
  if (['code', 'programming', 'code_reading', 'coding'].includes(v)) return 'coding'
  return 'single_choice'
}

export function mapDifficultyLevel(value?: number): DifficultyLevel {
  if (!value || value <= 1) return 'easy'
  if (value === 2) return 'medium'
  return 'hard'
}

export function difficultyLabel(level?: DifficultyLevel) {
  if (level === 'easy') return '基础'
  if (level === 'medium') return '进阶'
  if (level === 'hard') return '挑战'
  return '混合'
}

export function questionTypeLabel(type: QuestionType) {
  const map: Record<QuestionType, string> = {
    single_choice: '单选题',
    multiple_choice: '多选题',
    true_false: '判断题',
    fill_blank: '填空题',
    coding: '编程题',
  }
  return map[type]
}

function buildOptions(detail: QuizQuestionDetail): QuestionOption[] {
  const keys = ['A', 'B', 'C', 'D'] as const
  return keys
    .map((key) => {
      const raw = detail[`option${key}` as 'optionA']
      if (!raw) return null
      const content = String(raw).replace(new RegExp(`^${key}[.、\\s]+`, 'i'), '').trim()
      return { id: key, label: key, content }
    })
    .filter(Boolean) as QuestionOption[]
}

export function detailToQuestion(
  detail: QuizQuestionDetail,
  extras?: Partial<Question>,
): Question {
  const type = mapQuestionType(detail.questionType)
  const options =
    type === 'true_false'
      ? [
          { id: 'TRUE', label: 'A', content: '正确' },
          { id: 'FALSE', label: 'B', content: '错误' },
        ]
      : buildOptions(detail)

  return {
    id: String(detail.id),
    type,
    title: detail.title,
    content: detail.title,
    difficulty: mapDifficultyLevel(detail.difficulty),
    options: options.length > 0 ? options : undefined,
    analysis: detail.analysis || undefined,
    codeContent: detail.codeContent,
    knowledgePoints: detail.knowledgePoint
      ? [{ id: detail.knowledgePoint, name: detail.knowledgePoint }]
      : [],
    status: 'unanswered',
    ...extras,
  }
}

export function listItemToQuestionPreview(item: QuizQuestionListItem): Question {
  return {
    id: String(item.id),
    type: mapQuestionType(item.questionType),
    title: item.title,
    content: item.title,
    difficulty: mapDifficultyLevel(item.difficulty),
    knowledgePoints: item.knowledgePoint
      ? [{ id: item.knowledgePoint, name: item.knowledgePoint }]
      : [],
    status: 'unanswered',
  }
}

export function mapOverview(stats: QuizStats, records: QuizRecordItem[]): QuizOverview {
  const today = new Date().toISOString().slice(0, 10)
  const todayRecords = records.filter((r) => r.createdAt?.startsWith(today))
  return {
    totalQuestions: stats.total,
    completedQuestions: stats.correct + stats.wrong,
    accuracyRate: Math.round(stats.accuracy * 100) / 100,
    totalPracticeDurationMinutes: Math.max(1, Math.round(records.length * 2)),
    todayTargetCount: 10,
    todayCompletedCount: todayRecords.length,
  }
}

function sectionStatus(
  completed: number,
  total: number,
  isRecommended: boolean,
): SectionStatus {
  if (total === 0) return 'not_started'
  if (completed >= total) return 'completed'
  if (completed > 0) return 'in_progress'
  if (isRecommended) return 'recommended'
  return 'not_started'
}

export function buildCatalogFromQuestions(
  items: QuizQuestionListItem[],
  records: QuizRecordItem[],
  nodes: PathNode[],
  selectedKpId: string | null,
): QuizChapter[] {
  const recordByQuestion = new Map<number, QuizRecordItem[]>()
  records.forEach((r) => {
    const list = recordByQuestion.get(r.questionId) || []
    list.push(r)
    recordByQuestion.set(r.questionId, list)
  })

  const chapterMap = new Map<string, QuizQuestionListItem[]>()
  items.forEach((item) => {
    const kp = item.knowledgePoint?.trim() || '综合练习'
    const list = chapterMap.get(kp) || []
    list.push(item)
    chapterMap.set(kp, list)
  })

  let chapterOrder = 0
  return Array.from(chapterMap.entries()).map(([kp, questions]) => {
    chapterOrder += 1
    const typeGroups = new Map<string, QuizQuestionListItem[]>()
    questions.forEach((q) => {
      const typeKey = mapQuestionType(q.questionType)
      const list = typeGroups.get(typeKey) || []
      list.push(q)
      typeGroups.set(typeKey, list)
    })

    let sectionOrder = 0
    const sections: QuizSection[] = Array.from(typeGroups.entries()).map(([typeKey, qs]) => {
      sectionOrder += 1
      const questionIds = qs.map((q) => q.id)
      const completedCount = questionIds.filter((id) => recordByQuestion.has(id)).length
      const correctCount = questionIds.filter((id) =>
        recordByQuestion.get(id)?.some((r) => r.correct),
      ).length
      const accuracyRate =
        completedCount > 0 ? Math.round((correctCount / completedCount) * 100) : undefined
      const displayKp = resolveKpDisplay(kp, nodes)
      const isRecommended =
        Boolean(selectedKpId) &&
        (kp === selectedKpId || displayKp === resolveKpDisplay(selectedKpId, nodes))

      return {
        id: `${kp}::${typeKey}`,
        chapterId: kp,
        title: `${displayKp} · ${questionTypeLabel(typeKey as QuestionType)}`,
        description: `掌握 ${displayKp} 的相关题型`,
        order: sectionOrder,
        questionCount: qs.length,
        completedCount,
        accuracyRate,
        difficulty: mapDifficultyLevel(
          Math.round(qs.reduce((s, q) => s + (q.difficulty || 1), 0) / qs.length),
        ),
        status: sectionStatus(completedCount, qs.length, isRecommended),
        knowledgePointIds: [kp],
        learningPathNodeId: selectedKpId || undefined,
        estimatedMinutes: Math.max(5, Math.ceil(qs.length * 1.5)),
        questionIds,
      }
    })

    const totalQuestions = questions.length
    const completedQuestions = sections.reduce((s, sec) => s + sec.completedCount, 0)
    const correctTotal = questions.filter((q) =>
      recordByQuestion.get(q.id)?.some((r) => r.correct),
    ).length

    return {
      id: kp,
      title: `第 ${chapterOrder} 章 ${resolveKpDisplay(kp, nodes)}`,
      description: `${resolveKpDisplay(kp, nodes)} 相关练习`,
      order: chapterOrder,
      totalQuestions,
      completedQuestions,
      accuracyRate:
        completedQuestions > 0
          ? Math.round((correctTotal / completedQuestions) * 100)
          : undefined,
      isLocked: false,
      sections,
    }
  })
}

export function computeTypeDistribution(items: QuizQuestionListItem[]) {
  const counts = new Map<QuestionType, number>()
  items.forEach((item) => {
    const t = mapQuestionType(item.questionType)
    counts.set(t, (counts.get(t) || 0) + 1)
  })
  const total = items.length || 1
  return Array.from(counts.entries()).map(([type, count]) => ({
    type,
    label: questionTypeLabel(type),
    count,
    percent: Math.round((count / total) * 100),
  }))
}

export function weakPointsFromRecords(records: QuizRecordItem[]): KnowledgePoint[] {
  const wrong = records.filter((r) => !r.correct)
  const map = new Map<string, number>()
  wrong.forEach((r) => {
    const kp = r.kpId || 'unknown'
    map.set(kp, (map.get(kp) || 0) + 1)
  })
  return Array.from(map.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([id, count]) => ({ id, name: id, masteryLevel: Math.max(0, 100 - count * 10) }))
}
