import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import ReactECharts from 'echarts-for-react'
import { getProfileDetail, patchProfile } from '@/api/student/profile'
import { getCurrentPath } from '@/api/student/learningPath'
import type { ProfileDetail } from '@/utils/types/profile'
import type { LearningPathDto } from '@/utils/types/learning-path'
import styles from './ProfilePage.module.css'

const LEVEL_LABEL: Record<string, string> = {
  BEGINNER: '初学者',
  INTERMEDIATE: '进阶者',
  ADVANCED: '高级',
}

const PACE_LABEL: Record<string, string> = {
  SLOW: '慢节奏',
  NORMAL: '正常',
  FAST: '快节奏',
}

const STYLE_LABEL: Record<string, string> = {
  visual: '视觉型',
  reading: '阅读型',
  practice: '实践型',
}

function computeDimensionScores(profile: ProfileDetail) {
  const km = profile.knowledgeMap ?? []
  const mastered = km.filter((k) => k.status === 'MASTERED').length
  const total = km.length || 1
  const masteryPct = Math.round((mastered / total) * 100)

  const avgMastery = km.length > 0
    ? Math.round(km.reduce((s, k) => s + (k.mastery ?? 0), 0) / km.length)
    : 0

  const knowledge = Math.max(10, avgMastery)
  const application = Math.max(10, Math.min(100, avgMastery + 5))
  const efficiency = profile.learningPace === 'FAST' ? 85 : profile.learningPace === 'SLOW' ? 55 : 70
  const thinking = Math.max(10, Math.min(100, masteryPct + 10))
  const habit = profile.cognitiveStyle ? 70 : 45
  const attitude = profile.streakDays ? Math.min(100, 50 + (profile.streakDays ?? 0) * 3) : 50

  return { knowledge, application, efficiency, thinking, habit, attitude }
}


function extractWeakTopicNames(weakTopics: any): string[] {
  if (!weakTopics) return []
  // 如果整体就是 JSON 字符串 → 解析
  let arr: any[] = []
  if (typeof weakTopics === 'string') {
    try { arr = JSON.parse(weakTopics) } catch { return [weakTopics] }
  } else if (Array.isArray(weakTopics)) {
    arr = weakTopics
  } else {
    return []
  }
  if (!Array.isArray(arr)) return []
  const result: string[] = []
  for (const t of arr) {
    if (typeof t === 'string') {
      // 字符串元素可能是 JSON（如整串被兜底包进数组），尝试解析
      try {
        const parsed = JSON.parse(t)
        if (Array.isArray(parsed)) {
          for (const p of parsed) {
            result.push(typeof p === 'string' ? p : p?.kpId ?? p?.kpld ?? p?.name ?? p?.title ?? String(p))
          }
        } else if (typeof parsed === 'object' && parsed !== null) {
          result.push(parsed?.kpId ?? parsed?.kpld ?? parsed?.name ?? parsed?.title ?? String(parsed))
        } else {
          result.push(t)
        }
      } catch {
        result.push(t)
      }
    } else if (typeof t === 'object' && t !== null) {
      result.push(t?.kpId ?? t?.kpld ?? t?.name ?? t?.title ?? String(t))
    }
  }
  return result.filter(Boolean)
}

function generateInsights(profile: ProfileDetail, scores: ReturnType<typeof computeDimensionScores>) {
  const strengths: string[] = []
  const weaknesses: string[] = []
  const suggestions: string[] = []
  const nextSteps: string[] = []

  if (scores.knowledge >= 70) strengths.push('知识掌握扎实，基础概念理解较好')
  if (scores.attitude >= 70) strengths.push('学习态度积极，持续性强')
  if (scores.application >= 70) strengths.push('应用能力良好，能将知识用于实践')

  if (scores.knowledge < 50) weaknesses.push('知识掌握不足，需要加强基础学习')
  if (scores.efficiency < 60) weaknesses.push('学习效率有提升空间')
  if (scores.habit < 55) weaknesses.push('学习习惯需要优化')
  const weakNames = extractWeakTopicNames(profile.weakTopics ?? [])
  if (weakNames.length > 0) {
    weaknesses.push(`薄弱知识点：${weakNames.slice(0, 3).join('、')}`)
  }

  if (scores.efficiency < 70) suggestions.push('建议制定固定学习计划，提升学习效率')
  if (scores.habit < 60) suggestions.push('建议采用番茄学习法，培养良好学习习惯')
  if (weakNames.length > 0) suggestions.push('建议优先复习薄弱知识点')

  if (profile.learningGoal) nextSteps.push(`继续推进学习目标：${profile.learningGoal}`)
  if (weakNames.length > 0) nextSteps.push(`针对薄弱点 "${weakNames[0]}" 进行专项练习`)
  nextSteps.push('完成当前学习路径的下一个节点')

  return { strengths, weaknesses, suggestions, nextSteps }
}

/* ==================== 知识点掌握列表 ==================== */
const STATUS_CONFIG: Record<string, { label: string; color: string; barColor: string }> = {
  MASTERED:  { label: '已掌握', color: '#26b56e',  barColor: '#26b56e' },
  PENDING:   { label: '学习中', color: '#2f78ff',  barColor: '#2f78ff' },
}

function KnowledgeMasteryList({ knowledgeMap }: { knowledgeMap: { kpId: string; title: string; mastery: number; status?: string }[] }) {
  const [animated, setAnimated] = useState(false)

  useEffect(() => {
    // 首帧后触发动画
    const raf = requestAnimationFrame(() => requestAnimationFrame(() => setAnimated(true)))
    return () => cancelAnimationFrame(raf)
  }, [])

  if (knowledgeMap.length === 0) return <p className={styles.kpEmpty}>暂无知识点数据</p>

  const mastered = knowledgeMap.filter((k) => k.status === 'MASTERED').length
  const learning = knowledgeMap.filter((k) => k.status !== 'MASTERED').length

  return (
    <div className={styles.kpSection}>
      {/* 统计头部 */}
      <div className={styles.kpStats}>
        <span className={styles.kpStatItem}>
          <span className={styles.kpStatLabel}>已掌握</span>
          <strong className={`${styles.kpStatNum} ${styles.toneGreen}`}>{mastered}</strong>
        </span>
        <span className={styles.kpStatItem}>
          <span className={styles.kpStatLabel}>学习中</span>
          <strong className={`${styles.kpStatNum} ${styles.toneBlue}`}>{learning}</strong>
        </span>
      </div>

      {/* 表头 */}
      <div className={styles.kpTableHead}>
        <span className={styles.kpColName}>知识点</span>
        <span className={styles.kpColMastery}>掌握度</span>
        <span className={styles.kpColStatus}>状态</span>
      </div>

      {/* 列表 */}
      {knowledgeMap.map((kp) => {
        const mastery = kp.mastery ?? 0
        const isMastered = kp.status === 'MASTERED'
        const cfg = STATUS_CONFIG[kp.status ?? ''] ?? STATUS_CONFIG.PENDING
        return (
          <div key={kp.kpId} className={styles.kpRow}>
            <span className={styles.kpColName}>
              <span className={`${styles.kpDot} ${isMastered ? styles.kpDotSolid : styles.kpDotHollow}`} style={{ background: isMastered ? cfg.color : 'transparent', borderColor: cfg.color }} />
              {kp.title}
            </span>
            <span className={styles.kpColMastery}>
              <span className={styles.kpPct}>{mastery}%</span>
              <div className={styles.kpTrack}>
                <span
                  className={`${styles.kpFill} ${animated ? styles.kpAnimated : ''}`}
                  style={{ width: animated ? `${mastery}%` : '0%', background: cfg.barColor, transitionDelay: `${knowledgeMap.indexOf(kp) * 80}ms` }}
                />
              </div>
            </span>
            <span className={styles.kpColStatus} style={{ color: cfg.color }}>{cfg.label}</span>
          </div>
        )
      })}
    </div>
  )
}

export default function ProfilePage() {
  const navigate = useNavigate()
  const [profile, setProfile] = useState<ProfileDetail | null>(null)
  const [currentPath, setCurrentPath] = useState<LearningPathDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

  const fetchData = useCallback(async () => {
    try {
      const [detail, path] = await Promise.all([
        getProfileDetail(),
        getCurrentPath(),
      ])
      setProfile(detail)
      setCurrentPath(path)
    } catch (e) {
      console.error('Failed to fetch profile', e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchData() }, [fetchData])

  const handleRefresh = async () => {
    setRefreshing(true)
    try {
      await patchProfile({})
      await fetchData()
    } finally {
      setRefreshing(false)
    }
  }

  const handleExport = () => {
    if (!profile) return
    const scores = computeDimensionScores(profile)
    const lines = [
      '# 学习画像报告',
      '',
      `## 基本信息`,
      `- 知识水平：${LEVEL_LABEL[profile.knowledgeLevel ?? ''] ?? profile.knowledgeLevel ?? '未知'}`,
      `- 学习目标：${profile.learningGoal || '未设置'}`,
      `- 认知风格：${STYLE_LABEL[profile.cognitiveStyle ?? ''] ?? profile.cognitiveStyle ?? '未知'}`,
      `- 学习节奏：${PACE_LABEL[profile.learningPace ?? ''] ?? profile.learningPace ?? '未知'}`,
      '',
      '## 六维度评分',
      `- 知识掌握：${scores.knowledge}`,
      `- 应用能力：${scores.application}`,
      `- 学习效率：${scores.efficiency}`,
      `- 思维能力：${scores.thinking}`,
      `- 学习习惯：${scores.habit}`,
      `- 学习态度：${scores.attitude}`,
      '',
      '## 薄弱知识点',
      ...extractWeakTopicNames(profile.weakTopics ?? []).map((t) => `- ${t}`),
      '',
      '## 知识点掌握',
      ...(profile.knowledgeMap ?? []).map((k) => `- ${k.title}：${k.status ?? '学习中'} (${k.mastery ?? 0}%)`),
    ]
    const blob = new Blob([lines.join('\n')], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '学习画像报告.md'
    a.click()
    URL.revokeObjectURL(url)
  }

  if (loading) return <div className={styles.page}><p>加载中...</p></div>
  if (!profile) return <div className={styles.page}><p>暂无数据</p></div>

  const scores = computeDimensionScores(profile)
  const insights = generateInsights(profile, scores)
  const overallScore = Math.round(
    scores.knowledge * 0.25 + scores.application * 0.2 + scores.efficiency * 0.15 +
    scores.thinking * 0.15 + scores.habit * 0.1 + scores.attitude * 0.15
  )

  const masteredCount = (profile.knowledgeMap ?? []).filter((k) => k.status === 'MASTERED').length
  const totalKp = (profile.knowledgeMap ?? []).length

  const dimensionDefs = [
    { label: '知识掌握', value: scores.knowledge, tone: 'blue', icon: '🎓' },
    { label: '应用能力', value: scores.application, tone: 'violet', icon: '◔' },
    { label: '学习效率', value: scores.efficiency, tone: 'teal', icon: '◷' },
    { label: '思维能力', value: scores.thinking, tone: 'green', icon: '⌘' },
    { label: '学习习惯', value: scores.habit, tone: 'orange', icon: '🗓' },
    { label: '学习态度', value: scores.attitude, tone: 'amber', icon: '☆' },
  ]

  const scoreCards = dimensionDefs.map((d) => ({
    ...d,
    status: d.value >= 70 ? '上升' : d.value >= 50 ? '稳定' : '待提升',
  }))

  function toneClass(tone: string) {
    const map: Record<string, string> = {
      blue: styles.toneBlue, violet: styles.toneViolet, teal: styles.toneTeal,
      green: styles.toneGreen, orange: styles.toneOrange, amber: styles.toneAmber,
    }
    return map[tone] ?? ''
  }

  const pathProgress = currentPath?.nodes
    ? Math.round(
        currentPath.nodes.filter((n: any) => n.completed).length /
        Math.max(1, currentPath.nodes.length) * 100
      )
    : 0

  return (
    <section className={styles.page}>
      <div className={styles.glow} />

      <header className={styles.topbar}>
        <div>
          <h1 className={styles.title}>学习画像</h1>
          <p className={styles.subtitle}>基于你的学习数据，生成多维度能力画像与动态分析</p>
        </div>
        <div className={styles.topMeta}>
          {LEVEL_LABEL[profile.knowledgeLevel ?? ''] ?? '学习者'}
        </div>
        <div className={styles.topActions}>
          <button type="button" className={styles.secondaryBtn} onClick={handleRefresh} disabled={refreshing}>
            <span className={styles.btnIcon}>↻</span>
            {refreshing ? '刷新中...' : '刷新画像'}
          </button>
          <button type="button" className={styles.secondaryBtn} onClick={handleExport}>
            <span className={styles.btnIcon}>⇩</span>
            导出画像
          </button>
        </div>
      </header>

      <section className={styles.metricsRow}>
        <article className={`${styles.metricCard} ${styles.scoreCard}`}>
          <h2 className={styles.metricTitle}>综合评分</h2>
          <div className={styles.scoreWrap}>
            <strong className={styles.scoreValue}>{overallScore}</strong>
            <span className={styles.scoreUnit}>/100</span>
          </div>
          <p className={styles.metricTrend}>
            已掌握 {masteredCount}/{totalKp} 个知识点
          </p>
        </article>

        {scoreCards.map((card) => (
          <article key={card.label} className={styles.metricCard}>
            <div className={styles.metricHead}>
              <span className={`${styles.metricIcon} ${toneClass(card.tone)}`}>{card.icon}</span>
              <h2 className={styles.metricTitle}>{card.label}</h2>
            </div>
            <strong className={styles.metricValue}>{card.value}</strong>
            <p
              className={
                card.status === '待提升' ? styles.metricWarn
                  : card.status === '稳定' ? styles.metricStable
                    : styles.metricUp
              }
            >
              {card.status === '稳定' ? '→ ' : card.status === '待提升' ? '↓ ' : '↑ '}
              {card.status}
            </p>
          </article>
        ))}
      </section>

      <div className={styles.midGrid}>
        {/* 左栏：雷达图 */}
        <section className={styles.panel}>
          <div className={styles.radarHead}>
            <h2 className={styles.panelTitle}>能力雷达图</h2>
            <span className={styles.radarInfoIcon}>ⓘ</span>
          </div>
          <div className={styles.radarArea}>
            <div className={styles.radarChartBlock}>
              <ReactECharts
                option={{
                  radar: {
                    center: ['50%', '52%'],
                    radius: '68%',
                    indicator: [
                      { name: '知识掌握', max: 100 },
                      { name: '应用能力', max: 100 },
                      { name: '学习效率', max: 100 },
                      { name: '思维能力', max: 100 },
                      { name: '学习习惯', max: 100 },
                      { name: '学习态度', max: 100 },
                    ],
                    axisName: { color: '#637594', fontSize: 13, fontWeight: 700, padding: [4, 6] },
                    splitArea: { areaStyle: { color: ['#f8fbff', '#eef5ff'] } },
                    splitLine: { lineStyle: { color: '#d7e4fb' } },
                    axisLine: { lineStyle: { color: '#d7e4fb' } },
                  },
                  series: [
                    {
                      type: 'radar',
                      data: [
                        {
                          value: [scores.knowledge, scores.application, scores.efficiency, scores.thinking, scores.habit, scores.attitude],
                          name: '能力画像',
                          areaStyle: { color: 'rgba(47,120,255,0.18)' },
                          lineStyle: { color: '#2f78ff', width: 3 },
                          itemStyle: { color: '#2f78ff' },
                          symbol: 'circle',
                          symbolSize: 7,
                        },
                      ],
                    },
                  ],
                }}
                style={{ height: 360 }}
                opts={{ renderer: 'svg' }}
              />
            </div>

            {/* 图例 */}
            <div className={styles.radarLegend}>
              <span className={styles.radarLegendLine} />
              <span className={styles.radarLegendText}>能力画像</span>
            </div>

            {/* 总结描述 */}
            <p className={styles.radarDesc}>
              {(() => {
                const low = dimensionDefs.filter((d) => d.value < 60).map((d) => d.label)
                if (low.length > 0)
                  return `综合来看，该学生在${low.join('、')}方面有提升空间`
                if (dimensionDefs.some((d) => d.value < 80))
                  return `整体表现良好，各维度发展较为均衡，继续保持即可`
                return `各项能力表现优异，建议挑战更高难度的学习内容`
              })()}
            </p>
          </div>
        </section>

        {/* 中栏：画像解读 / 核心洞察 */}
        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>画像解读 / 核心洞察</h2>

          {insights.weaknesses.length > 0 && (
            <div className={`${styles.insightCard} ${styles.toneOrange}`}>
              <div className={styles.insightCardHead}>
                <h3>薄弱环节</h3>
              </div>
              <p className={styles.insightCardBody}>
                {insights.weaknesses.join('，')}
              </p>
            </div>
          )}

          {insights.suggestions.length > 0 && (
            <div className={`${styles.insightCard} ${styles.toneGreen}`}>
              <div className={styles.insightCardHead}>
                <h3>学习建议</h3>
              </div>
              <p className={styles.insightCardBody}>
                {insights.suggestions.join('，')}
              </p>
            </div>
          )}

          {insights.nextSteps.length > 0 && (
            <div className={`${styles.insightCard} ${styles.toneBlue}`}>
              <div className={styles.insightCardHead}>
                <h3>推荐下一步</h3>
              </div>
              <p className={styles.insightCardBody}>
                {insights.nextSteps.join('，')}
              </p>
            </div>
          )}

          {/* 画像完整度 */}
          {(() => {
            const completionPct = Math.round(([profile.knowledgeLevel, profile.learningGoal, profile.cognitiveStyle, profile.learningPace, (profile.knowledgeMap ?? []).length > 0].filter(Boolean).length / 5) * 100)
            return (
          <div className={styles.completionSection}>
            <div className={styles.completionHead}>
              <span className={styles.completionLabel}>画像完整度</span>
              <strong className={styles.completionValue}>{completionPct}%</strong>
            </div>
            <div className={styles.completionTrack}>
              <span className={styles.completionFill} style={{ width: completionPct + '%' }} />
            </div>
            <p className={styles.completionHint}>数据基于你的学习行为自动生成 ⓘ</p>
          </div>
            )
          })()}
        </section>

        {/* 右栏：知识点掌握 */}
        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>知识点掌握</h2>
          <KnowledgeMasteryList knowledgeMap={profile.knowledgeMap ?? []} />
        </section>
      </div>

      <div className={styles.bottomGrid}>
        {/* 左栏：六维度概览 */}
        <section className={styles.dimensionPanel}>
          <h2 className={styles.panelTitle}>六维度概览</h2>
          <div className={styles.dimensionBody}>
            {/* 进度条列表 */}
            <div className={styles.dimList}>
              {dimensionDefs.map((item) => (
                <div key={item.label} className={styles.dimRow}>
                  <span className={styles.dimLabel}>{item.label}</span>
                  <div className={styles.dimTrack}>
                    <span className={`${styles.dimFill} ${toneClass(item.tone)}`} style={{ width: `${item.value}%` }} />
                  </div>
                  <strong className={styles.dimScore}>{item.value}/100</strong>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* 右栏：历史学习路径 */}
        <section className={styles.panel}>
          <div className={styles.historyHead}>
            <h2 className={styles.panelTitle}>历史学习路径</h2>
            <span className={styles.historyMeta}>{(profile.knowledgeMap ?? []).length > 0 ? '近30天' : ''}学习轨迹回顾</span>
          </div>

          {currentPath?.nodes && currentPath.nodes.length > 0 ? (
            <>
              <ul className={styles.historyList}>
                {currentPath.nodes.slice(0, 4).map((node) => (
                  <li key={node.kpId} className={styles.historyItem}>
                    <span className={`${styles.historyDot} ${node.completed || node.status === 'COMPLETED' ? styles.historyDotDone : ''}`} />
                    <span className={styles.historyTitle}>{node.title}</span>
                    <span className={styles.historyDate}>{new Date().toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }).replace(/\//g, '.')}</span>
                    <span className={styles.historyBadge}>{node.completed || node.status === 'COMPLETED' ? '已完成' : '学习中'}</span>
                  </li>
                ))}
              </ul>
              <a className={styles.historyMore} onClick={() => navigate('/workspace/learning-path')}>
                查看完整学习历史 &gt;
              </a>
            </>
          ) : (
            <p className={styles.emptyText}>暂无学习路径，可在聊天中生成</p>
          )}
        </section>
      </div>
    </section>
  )
}
