import { getCurrentPath } from '@/api/student/learningPath'
import { listKnowledgePointsByIds } from '@/api/student/knowledge'
import { getQuizRecords, getQuizStats, generateQuiz } from '@/api/student/quiz'
import { listUploadTasks } from '@/api/student/upload'
import { getMe } from '@/api/student/user'
import { fetchProfileDetailRaw, fetchProfileAchievements, fetchProfileTrends, mapApiAchievements, mapApiTrendPoints, exportProfile, refreshProfileRaw } from '@/api/student/profile'
import { createPracticeSession, getPracticeSession } from '@/services/quizService'
import {
  buildAbilityDimensions,
  buildAchievements,
  buildInsights,
  buildKnowledgeMastery,
  buildOverview,
  buildRecentActivities,
  buildTrends,
  buildWeakPoints,
  quizStatsByKp,
  type BackendProfileDetail,
} from '@/utils/learningProfile/profileMappers'
import type {
  AbilityDimension,
  LearningAchievement,
  LearningInsight,
  LearningProfile,
  LearningProfileFilters,
  LearningProfileOverview,
  LearningTrendPoint,
  KnowledgeMasteryItem,
  ProfileRange,
  RecentLearningActivity,
  TrendMetric,
  WeakKnowledgePoint,
} from '@/utils/types/learningProfile'
import { useAuthStore } from '@/utils/store/authStore'

function formatPathGoal(goal: string): { title: string; summary: string } {
  const main = goal.split(/补充信息|调整建议/)[0]?.trim() || goal.trim()
  const title = main.length > 20 ? `${main.slice(0, 20)}…` : main
  const summary = main.length > 72 ? `${main.slice(0, 72)}…` : main
  return { title, summary }
}

async function resolveKpTitles(kpIds: string[]): Promise<Record<string, string>> {
  if (kpIds.length === 0) return {}
  try {
    const items = await listKnowledgePointsByIds(kpIds)
    return items.reduce<Record<string, string>>((acc, item) => {
      acc[item.kpId] = item.title
      return acc
    }, {})
  } catch {
    return {}
  }
}

async function loadContext(filters?: LearningProfileFilters) {
  const [detail, stats, records, path, uploads, user] = await Promise.all([
    fetchProfileDetailRaw(),
    getQuizStats().catch(() => ({ total: 0, correct: 0, wrong: 0, accuracy: 0 })),
    getQuizRecords().catch(() => []),
    getCurrentPath().catch(() => null),
    listUploadTasks().catch(() => []),
    getMe().catch(() => null),
  ])

  const userId = String(user?.id ?? useAuthStore.getState().user?.userId ?? '')
  const km = detail.knowledgeMap ?? []
  const kpIds = km.map((k) => k.kpId).filter(Boolean)
  const kpTitles = await resolveKpTitles(kpIds)
  const quizByKp = quizStatsByKp(records)

  const pathFiltered =
    filters?.learningPathId && filters.learningPathId !== 'all' && path
      ? path.goal === filters.learningPathId
        ? path
        : path
      : path

  return {
    detail,
    stats,
    records,
    path: pathFiltered,
    uploads,
    userId,
    kpTitles,
    quizByKp,
  }
}

export async function getLearningProfileOverview(
  filters?: LearningProfileFilters,
): Promise<LearningProfileOverview> {
  const ctx = await loadContext(filters)
  const dimensions = buildAbilityDimensions(ctx.detail, ctx.stats.accuracy, ctx.path)
  return buildOverview(ctx.userId, ctx.detail, dimensions, ctx.stats.accuracy, ctx.path, ctx.stats.total)
}

export async function getAbilityDimensions(
  filters?: LearningProfileFilters,
): Promise<AbilityDimension[]> {
  const ctx = await loadContext(filters)
  return buildAbilityDimensions(ctx.detail, ctx.stats.accuracy, ctx.path)
}

export async function getKnowledgeMastery(
  filters?: LearningProfileFilters & { limit?: number },
): Promise<KnowledgeMasteryItem[]> {
  const ctx = await loadContext(filters)
  const items = buildKnowledgeMastery(ctx.detail.knowledgeMap ?? [], ctx.kpTitles, ctx.quizByKp)
  const limit = filters?.limit ?? 8
  return items.sort((a, b) => a.masteryRate - b.masteryRate).slice(0, limit)
}

export async function getLearningInsights(
  filters?: LearningProfileFilters,
): Promise<LearningInsight[]> {
  const ctx = await loadContext(filters)
  const dimensions = buildAbilityDimensions(ctx.detail, ctx.stats.accuracy, ctx.path)
  const overview = buildOverview(ctx.userId, ctx.detail, dimensions, ctx.stats.accuracy, ctx.path, ctx.stats.total)
  const mastery = buildKnowledgeMastery(ctx.detail.knowledgeMap ?? [], ctx.kpTitles, ctx.quizByKp)
  const weak = buildWeakPoints(mastery)
  return buildInsights(overview, dimensions, weak, ctx.detail)
}

export async function getLearningTrends(
  filters?: LearningProfileFilters & { metric?: TrendMetric },
): Promise<LearningTrendPoint[]> {
  const range = filters?.range ?? '30d'
  const metric = filters?.metric ?? filters?.trendMetric ?? 'mastery'
  try {
    const apiTrends = await fetchProfileTrends(range, metric)
    if (apiTrends.length > 0) return mapApiTrendPoints(apiTrends)
  } catch {
    // fallback below
  }
  const ctx = await loadContext(filters)
  return buildTrends(
    ctx.records,
    ctx.detail.knowledgeMap ?? [],
    range,
    metric,
  )
}

export async function getLearningAchievements(
  filters?: LearningProfileFilters,
): Promise<LearningAchievement[]> {
  try {
    const apiAchievements = await fetchProfileAchievements()
    if (apiAchievements.length > 0) return mapApiAchievements(apiAchievements)
  } catch {
    // fallback below
  }
  const ctx = await loadContext(filters)
  const dimensions = buildAbilityDimensions(ctx.detail, ctx.stats.accuracy, ctx.path)
  const overview = buildOverview(ctx.userId, ctx.detail, dimensions, ctx.stats.accuracy, ctx.path, ctx.stats.total)
  return buildAchievements(overview, ctx.uploads.length, ctx.stats.total)
}

export async function getWeakKnowledgePoints(
  filters?: LearningProfileFilters & { limit?: number },
): Promise<WeakKnowledgePoint[]> {
  const ctx = await loadContext(filters)
  const mastery = buildKnowledgeMastery(ctx.detail.knowledgeMap ?? [], ctx.kpTitles, ctx.quizByKp)
  return buildWeakPoints(mastery).slice(0, filters?.limit ?? 5)
}

export async function getRecentLearningActivities(params?: {
  limit?: number
}): Promise<RecentLearningActivity[]> {
  const ctx = await loadContext()
  return buildRecentActivities(ctx.records, ctx.uploads, ctx.path).slice(0, params?.limit ?? 4)
}

export async function getLearningPathOptions(): Promise<Array<{ id: string; title: string }>> {
  const path = await getCurrentPath().catch(() => null)
  if (!path) return []
  return [{ id: path.goal, title: path.goal }]
}

export async function loadLearningProfile(filters?: LearningProfileFilters): Promise<LearningProfile> {
  const ctx = await loadContext(filters)
  const dimensions = buildAbilityDimensions(ctx.detail, ctx.stats.accuracy, ctx.path)
  const overview = buildOverview(ctx.userId, ctx.detail, dimensions, ctx.stats.accuracy, ctx.path, ctx.stats.total)
  const knowledgeMastery = buildKnowledgeMastery(
    ctx.detail.knowledgeMap ?? [],
    ctx.kpTitles,
    ctx.quizByKp,
  )
  const weakKnowledgePoints = buildWeakPoints(knowledgeMastery)
  const pathNodeTitles = ctx.path?.nodes.map((n) => n.title).filter(Boolean) ?? []
  const rawGoal = ctx.path?.goal?.trim() || ctx.detail.learningGoal?.trim() || ''
  const formattedGoal = rawGoal ? formatPathGoal(rawGoal) : null
  const pathSummary =
    formattedGoal?.summary ||
    (pathNodeTitles.length > 0
      ? `当前路径包含 ${pathNodeTitles.length} 个学习节点`
      : '汇总全部学习路径、答题练习与上传资料数据')

  const pathOptions = ctx.path
    ? [{ id: ctx.path.goal, title: formattedGoal?.title || '当前学习路径' }]
    : []

  const range = filters?.range ?? '30d'
  const trendMetric = filters?.trendMetric ?? 'mastery'

  let trends = buildTrends(
    ctx.records,
    ctx.detail.knowledgeMap ?? [],
    range,
    trendMetric,
  )
  let achievements = buildAchievements(overview, ctx.uploads.length, ctx.stats.total)

  try {
    const [apiTrends, apiAchievements] = await Promise.all([
      fetchProfileTrends(range, trendMetric),
      fetchProfileAchievements(),
    ])
    if (apiTrends.length > 0) trends = mapApiTrendPoints(apiTrends)
    if (apiAchievements.length > 0) achievements = mapApiAchievements(apiAchievements)
  } catch {
    // 保留本地推导结果
  }

  return {
    overview,
    abilityDimensions: dimensions,
    knowledgeMastery,
    insights: buildInsights(overview, dimensions, weakKnowledgePoints, ctx.detail),
    trends,
    achievements,
    weakKnowledgePoints,
    recentActivities: buildRecentActivities(ctx.records, ctx.uploads, ctx.path),
    pathOptions,
    pathSummary,
    pathNodeTitles,
  }
}

export async function refreshLearningProfile(
  filters?: LearningProfileFilters,
): Promise<LearningProfile> {
  await refreshProfileRaw()
  return loadLearningProfile(filters)
}

export type ProfileExportFormat = 'markdown' | 'pdf'

/** 导出学习画像报告（优先服务端 Markdown） */
export async function exportLearningProfileReport(
  profile?: LearningProfile,
  format: ProfileExportFormat = 'markdown',
): Promise<{ downloadUrl?: string; content?: string; fileName?: string }> {
  let markdown = ''
  let fileName = `learning-profile-${new Date().toISOString().slice(0, 10)}.md`

  try {
    const result = await exportProfile('markdown')
    if (result.content) {
      markdown = result.content
      fileName = result.fileName || fileName
    }
  } catch {
    // fallback below
  }

  if (!markdown) {
    if (!profile) {
      profile = await loadLearningProfile()
    }
    markdown = [
      '# 学习画像报告',
      '',
      `综合掌握度：${profile.overview.overallMasteryRate}%`,
      `连续学习：${profile.overview.continuousLearningDays} 天`,
      '',
      '## 能力维度',
      ...profile.abilityDimensions.map((d) => `- ${d.name}：${d.score}`),
      '',
      '## 学习洞察',
      ...profile.insights.map((i) => `- [${i.type}] ${i.title}：${i.content}`),
    ].join('\n')
  }

  if (format === 'pdf') {
    const { printProfileReportAsPdf } = await import('@/utils/learningProfile/exportReport')
    printProfileReportAsPdf(markdown)
    return { content: markdown, fileName: fileName.replace(/\.md$/i, '.pdf') }
  }

  const { downloadMarkdownFile } = await import('@/utils/learningProfile/exportReport')
  downloadMarkdownFile(markdown, fileName)
  return { content: markdown, fileName }
}

export async function startPracticeForKnowledgePoint(kpId: string) {
  try {
    const generated = await generateQuiz({
      kpId,
      count: 10,
      title: '薄弱点强化练习',
    })
    return getPracticeSession(generated.sessionId)
  } catch {
    return createPracticeSession({
      mode: 'review',
      questionIds: [],
      learningPathNodeId: kpId,
      title: '薄弱点强化练习',
    })
  }
}

export type { BackendProfileDetail }
