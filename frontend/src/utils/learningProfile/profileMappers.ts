import type { QuizRecordItem } from '@/api/student/quiz'
import type { UploadTask } from '@/api/student/upload'
import type { LearningPathDto } from '@/utils/types/learning-path'
import type {
  AbilityDimension,
  InsightActionType,
  KnowledgeMasteryItem,
  LearningAchievement,
  LearningInsight,
  LearningProfileOverview,
  LearningTrendPoint,
  MasteryLevel,
  MasteryStatus,
  ProfileRange,
  RecentLearningActivity,
  TrendMetric,
  WeakKnowledgePoint,
} from '@/utils/types/learningProfile'

export type BackendKnowledgeMapItem = {
  kpId: string
  title: string
  mastery: number
  status?: string
}

export type BackendProfileDetail = {
  knowledgeLevel?: string
  learningGoal?: string
  cognitiveStyle?: string
  learningPace?: string
  completedKpCount?: number
  streakDays?: number
  weakTopics?: string[]
  recentGoals?: string[]
  knowledgeMap?: BackendKnowledgeMapItem[]
}

const KNOWLEDGE_LEVEL_LABEL: Record<string, string> = {
  BEGINNER: '入门',
  INTERMEDIATE: '进阶',
  ADVANCED: '高级',
}

const PACE_SCORE: Record<string, number> = {
  FAST: 85,
  NORMAL: 70,
  SLOW: 55,
}

export function mapMasteryLevel(rate: number, knowledgeLevel?: string): MasteryLevel {
  if (knowledgeLevel === 'ADVANCED' || rate >= 85) return 'excellent'
  if (rate >= 70) return 'advanced'
  if (rate >= 50) return 'good'
  if (rate >= 30) return 'developing'
  return 'beginner'
}

export function mapKpStatus(mastery: number, status?: string): MasteryStatus {
  if (status === 'MASTERED' || mastery >= 80) return 'mastered'
  if (mastery >= 60) return 'good'
  if (mastery >= 35) return 'normal'
  return 'weak'
}

function avgMastery(items: BackendKnowledgeMapItem[]): number {
  if (!items.length) return 0
  return Math.round(items.reduce((s, k) => s + (k.mastery ?? 0), 0) / items.length)
}

function inRange(dateStr: string, range: ProfileRange): boolean {
  if (range === 'all') return true
  const days = range === '7d' ? 7 : range === '30d' ? 30 : 90
  const cutoff = Date.now() - days * 24 * 60 * 60 * 1000
  return new Date(dateStr).getTime() >= cutoff
}

export function buildAbilityDimensions(
  detail: BackendProfileDetail,
  accuracyRate: number,
  path?: LearningPathDto | null,
): AbilityDimension[] {
  const km = detail.knowledgeMap ?? []
  const avg = avgMastery(km)
  const mastered = km.filter((k) => k.status === 'MASTERED' || (k.mastery ?? 0) >= 80).length
  const pathTotal = path?.nodes.length ?? 0
  const pathDone = path?.nodes.filter((n) => n.completed).length ?? 0
  const pathRate = pathTotal > 0 ? Math.round((pathDone / pathTotal) * 100) : 0

  return [
    {
      id: 'knowledge',
      name: '知识掌握',
      score: Math.max(10, avg),
      averageScore: 65,
      maxScore: 100,
      description: '基于知识点掌握度评估',
    },
    {
      id: 'attitude',
      name: '学习态度',
      score: Math.min(100, Math.max(10, 40 + (detail.streakDays ?? 0) * 8)),
      averageScore: 60,
      maxScore: 100,
    },
    {
      id: 'habit',
      name: '学习习惯',
      score: Math.max(10, pathRate || (detail.cognitiveStyle ? 65 : 40)),
      averageScore: 62,
      maxScore: 100,
    },
    {
      id: 'thinking',
      name: '思维能力',
      score: Math.max(10, Math.min(100, Math.round((mastered / Math.max(km.length, 1)) * 100) + 10)),
      averageScore: 58,
      maxScore: 100,
    },
    {
      id: 'efficiency',
      name: '学习效率',
      score: PACE_SCORE[detail.learningPace || 'NORMAL'] ?? 70,
      averageScore: 68,
      maxScore: 100,
    },
    {
      id: 'application',
      name: '应用能力',
      score: Math.max(10, Math.round(accuracyRate || avg)),
      averageScore: 64,
      maxScore: 100,
    },
  ]
}

export function buildOverview(
  userId: string,
  detail: BackendProfileDetail,
  dimensions: AbilityDimension[],
  accuracyRate: number,
  path?: LearningPathDto | null,
  completedQuestions?: number,
): LearningProfileOverview {
  const km = detail.knowledgeMap ?? []
  const overall = km.length > 0 ? avgMastery(km) : Math.round(accuracyRate)
  const sorted = [...dimensions].sort((a, b) => b.score - a.score)
  const strongest = sorted[0]
  const weakest = sorted[sorted.length - 1]
  const pathTotal = path?.nodes.length ?? 0
  const pathDone = path?.nodes.filter((n) => n.completed).length ?? 0

  let suggestion = detail.learningGoal || detail.recentGoals?.[0] || ''
  if (!suggestion && weakest && weakest.score < 60) {
    suggestion = `建议优先加强「${weakest.name}」相关练习`
  }
  if (!suggestion && detail.weakTopics?.[0]) {
    suggestion = `建议复习：${detail.weakTopics[0]}`
  }

  return {
    userId,
    overallMasteryRate: overall,
    masteryLevel: mapMasteryLevel(overall, detail.knowledgeLevel),
    continuousLearningDays: detail.streakDays ?? 0,
    longestContinuousLearningDays: detail.streakDays ?? 0,
    completedLearningPathNodes: pathDone,
    totalLearningPathNodes: pathTotal,
    completedQuestions,
    accuracyRate: accuracyRate || undefined,
    strongestDimension: strongest?.name,
    weakestDimension: weakest?.name,
    currentSuggestion: suggestion || undefined,
    updatedAt: new Date().toISOString(),
  }
}

export function buildKnowledgeMastery(
  items: BackendKnowledgeMapItem[],
  kpTitles: Record<string, string>,
  quizByKp: Record<string, { total: number; correct: number }>,
): KnowledgeMasteryItem[] {
  return items.map((item) => {
    const stats = quizByKp[item.kpId]
    const correctRate =
      stats && stats.total > 0 ? Math.round((stats.correct / stats.total) * 100) : undefined
    return {
      knowledgePointId: item.kpId,
      name: kpTitles[item.kpId] || item.title || item.kpId,
      masteryRate: item.mastery ?? 0,
      completedQuestionCount: stats?.total,
      totalQuestionCount: stats?.total,
      correctRate,
      status: mapKpStatus(item.mastery ?? 0, item.status),
      relatedLearningPathNodeId: item.kpId,
    }
  })
}

export function buildInsights(
  overview: LearningProfileOverview,
  dimensions: AbilityDimension[],
  weakPoints: WeakKnowledgePoint[],
  detail: BackendProfileDetail,
): LearningInsight[] {
  const insights: LearningInsight[] = []
  const top = [...dimensions].sort((a, b) => b.score - a.score)[0]
  const low = [...dimensions].sort((a, b) => a.score - b.score)[0]

  if (top && top.score >= 55) {
    insights.push({
      id: `strength-${top.id}`,
      type: 'strength',
      title: top.name,
      content: `当前「${top.name}」得分 ${top.score} 分，是你的相对优势方向。`,
      priority: 1,
    })
  }

  if (detail.cognitiveStyle) {
    insights.push({
      id: 'strength-style',
      type: 'strength',
      title: '认知风格',
      content: `偏好 ${detail.cognitiveStyle} 学习方式，可继续沿用适合的学习资源形式。`,
      priority: 2,
    })
  }

  if (low && low.score < 60) {
    insights.push({
      id: `weakness-${low.id}`,
      type: 'weakness',
      title: low.name,
      content: `「${low.name}」得分 ${low.score} 分，建议针对性补强。`,
      priority: 1,
      action: weakPoints[0]
        ? {
            label: '去练习',
            type: 'practice' as InsightActionType,
            targetId: weakPoints[0].knowledgePointId,
          }
        : undefined,
    })
  }

  for (const wp of weakPoints.slice(0, 2)) {
    insights.push({
      id: `risk-${wp.knowledgePointId}`,
      type: 'risk',
      title: wp.name,
      content: wp.weaknessReason || `掌握度 ${wp.masteryRate}%，需要重点复习。`,
      priority: 2,
      relatedKnowledgePointIds: [wp.knowledgePointId],
      action: wp.recommendedAction
        ? {
            label: wp.recommendedAction.label,
            type: (wp.recommendedAction.type === 'practice'
              ? 'practice'
              : wp.recommendedAction.type === 'resource'
                ? 'review_resource'
                : 'view_graph') as InsightActionType,
            targetId: wp.recommendedAction.targetId,
          }
        : undefined,
    })
  }

  if (overview.currentSuggestion) {
    insights.push({
      id: 'suggestion-main',
      type: 'suggestion',
      title: '当前学习建议',
      content: overview.currentSuggestion,
      priority: 1,
      action: {
        label: '继续学习路径',
        type: 'continue_path',
      },
    })
  }

  if (detail.recentGoals?.[0]) {
    insights.push({
      id: 'suggestion-goal',
      type: 'suggestion',
      title: '学习目标',
      content: detail.recentGoals[0],
      priority: 2,
      action: { label: '查看路径', type: 'continue_path' },
    })
  }

  return insights
}

export function buildTrends(
  records: QuizRecordItem[],
  km: BackendKnowledgeMapItem[],
  range: ProfileRange,
  metric: TrendMetric,
): LearningTrendPoint[] {
  const filtered = records.filter((r) => inRange(r.createdAt, range))
  const byDate = new Map<string, QuizRecordItem[]>()
  for (const r of filtered) {
    const date = r.createdAt.slice(0, 10)
    const list = byDate.get(date) ?? []
    list.push(r)
    byDate.set(date, list)
  }

  const dates = Array.from(byDate.keys()).sort()
  if (dates.length === 0 && km.length > 0) {
    return [
      {
        date: new Date().toISOString().slice(0, 10),
        overallMasteryRate: avgMastery(km),
        accuracyRate: undefined,
        completedQuestionCount: 0,
      },
    ]
  }

  return dates.map((date) => {
    const dayRecords = byDate.get(date) ?? []
    const correct = dayRecords.filter((r) => r.correct).length
    const accuracy = dayRecords.length
      ? Math.round((correct / dayRecords.length) * 100)
      : undefined
    return {
      date,
      overallMasteryRate: metric === 'mastery' ? avgMastery(km) : undefined,
      accuracyRate: metric === 'accuracy' ? accuracy : accuracy,
      completedQuestionCount:
        metric === 'questions' ? dayRecords.length : dayRecords.length,
      learningDurationMinutes: metric === 'duration' ? dayRecords.length * 5 : undefined,
    }
  })
}

export function buildAchievements(
  overview: LearningProfileOverview,
  uploadCount: number,
  quizTotal: number,
): LearningAchievement[] {
  const items: LearningAchievement[] = []

  items.push({
    id: 'streak',
    title: '坚持学习',
    description: `连续学习 ${overview.continuousLearningDays} 天`,
    achieved: overview.continuousLearningDays >= 3,
    progress: Math.min(overview.continuousLearningDays, 7),
    target: 7,
    level: overview.continuousLearningDays >= 7 ? 'gold' : 'bronze',
  })

  items.push({
    id: 'quiz',
    title: '练习积累',
    description: `累计完成 ${quizTotal} 道练习`,
    achieved: quizTotal >= 5,
    progress: Math.min(quizTotal, 20),
    target: 20,
    level: quizTotal >= 20 ? 'gold' : quizTotal >= 10 ? 'silver' : 'bronze',
  })

  items.push({
    id: 'upload',
    title: '资料沉淀',
    description: `上传 ${uploadCount} 份学习资料`,
    achieved: uploadCount >= 1,
    progress: Math.min(uploadCount, 5),
    target: 5,
    level: uploadCount >= 5 ? 'silver' : 'bronze',
  })

  items.push({
    id: 'mastery',
    title: '知识进阶',
    description: `已掌握 ${overview.completedLearningPathNodes ?? 0} 个路径节点`,
    achieved: (overview.completedLearningPathNodes ?? 0) >= 3,
    progress: overview.completedLearningPathNodes ?? 0,
    target: overview.totalLearningPathNodes || 10,
    level: (overview.completedLearningPathNodes ?? 0) >= 5 ? 'gold' : 'bronze',
  })

  return items
}

export function buildWeakPoints(items: KnowledgeMasteryItem[]): WeakKnowledgePoint[] {
  return items
    .filter((k) => k.status === 'weak' || k.masteryRate < 40)
    .sort((a, b) => a.masteryRate - b.masteryRate)
    .map((k) => ({
      knowledgePointId: k.knowledgePointId,
      name: k.name,
      masteryRate: k.masteryRate,
      wrongQuestionCount:
        k.completedQuestionCount && k.correctRate != null
          ? Math.round(k.completedQuestionCount * (1 - k.correctRate / 100))
          : undefined,
      weaknessReason:
        k.correctRate != null && k.correctRate < 60
          ? `答题正确率 ${k.correctRate}%`
          : `掌握度 ${k.masteryRate}%`,
      recommendedAction: {
        label: '强化练习',
        type: 'practice' as const,
        targetId: k.knowledgePointId,
      },
    }))
}

export function buildRecentActivities(
  records: QuizRecordItem[],
  uploads: UploadTask[],
  path?: LearningPathDto | null,
): RecentLearningActivity[] {
  const activities: RecentLearningActivity[] = []

  for (const r of records.slice(0, 5)) {
    activities.push({
      id: `quiz-${r.id}`,
      type: 'quiz',
      title: r.correct ? '答对练习题' : '完成练习题',
      description: r.kpId,
      occurredAt: r.createdAt,
      targetId: String(r.questionId),
    })
  }

  for (const t of uploads.slice(0, 3)) {
    activities.push({
      id: `upload-${t.id}`,
      type: 'upload',
      title: t.fileName,
      description: t.status === 'COMPLETED' ? '资料解析完成' : '资料上传',
      occurredAt: t.createdAt,
      targetId: String(t.id),
    })
  }

  const activeNode = path?.nodes.find((n) => n.status === 'ACTIVE')
  if (activeNode) {
    activities.push({
      id: `path-${activeNode.kpId}`,
      type: 'path',
      title: activeNode.title,
      description: path?.goal,
      occurredAt: new Date().toISOString(),
      targetId: activeNode.kpId,
    })
  }

  return activities
    .sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime())
    .slice(0, 4)
}

export function quizStatsByKp(records: QuizRecordItem[]): Record<string, { total: number; correct: number }> {
  const map: Record<string, { total: number; correct: number }> = {}
  for (const r of records) {
    if (!r.kpId) continue
    const entry = map[r.kpId] ?? { total: 0, correct: 0 }
    entry.total += 1
    if (r.correct) entry.correct += 1
    map[r.kpId] = entry
  }
  return map
}

export function knowledgeLevelLabel(level?: string): string {
  return KNOWLEDGE_LEVEL_LABEL[level || ''] || level || '—'
}
