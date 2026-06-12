import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
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

function computeRadarPoints(scores: ReturnType<typeof computeDimensionScores>) {
  const cx = 180, cy = 170, maxR = 130, minR = 20
  const values = [scores.knowledge, scores.application, scores.efficiency, scores.thinking, scores.habit, scores.attitude]
  const angles = [270, 330, 30, 90, 150, 210]
  return values.map((v, i) => {
    const r = minR + (v / 100) * (maxR - minR)
    const rad = (angles[i] * Math.PI) / 180
    return {
      x: cx + r * Math.cos(rad),
      y: cy + r * Math.sin(rad),
    }
  })
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
  if ((profile.weakTopics ?? []).length > 0) {
    weaknesses.push(`薄弱知识点：${profile.weakTopics!.slice(0, 3).join('、')}`)
  }

  if (scores.efficiency < 70) suggestions.push('建议制定固定学习计划，提升学习效率')
  if (scores.habit < 60) suggestions.push('建议采用番茄学习法，培养良好学习习惯')
  if ((profile.weakTopics ?? []).length > 0) suggestions.push('建议优先复习薄弱知识点')

  if (profile.learningGoal) nextSteps.push(`继续推进学习目标：${profile.learningGoal}`)
  if ((profile.weakTopics ?? []).length > 0) nextSteps.push(`针对薄弱点 "${profile.weakTopics![0]}" 进行专项练习`)
  nextSteps.push('完成当前学习路径的下一个节点')

  return { strengths, weaknesses, suggestions, nextSteps }
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
      ...(profile.weakTopics ?? []).map((t) => `- ${t}`),
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
  const radarPts = computeRadarPoints(scores)
  const insights = generateInsights(profile, scores)
  const overallScore = Math.round(
    scores.knowledge * 0.25 + scores.application * 0.2 + scores.efficiency * 0.15 +
    scores.thinking * 0.15 + scores.habit * 0.1 + scores.attitude * 0.15
  )

  const masteredCount = (profile.knowledgeMap ?? []).filter((k) => k.status === 'MASTERED').length
  const totalKp = (profile.knowledgeMap ?? []).length
  const filledFields = [
    profile.knowledgeLevel, profile.learningGoal, profile.cognitiveStyle,
    profile.learningPace, (profile.knowledgeMap ?? []).length > 0,
  ].filter(Boolean).length
  const completionPct = Math.round((filledFields / 5) * 100)

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

  const radarLabels = ['知识掌握', '应用能力', '学习效率', '思维能力', '学习习惯', '学习态度']

  const polygonPoints = radarPts.map((p) => `${p.x},${p.y}`).join(' ')

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
        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>能力雷达图</h2>
          <div className={styles.radarArea}>
            <div className={styles.radarChartBlock}>
              <svg viewBox="0 0 360 340" className={styles.radarSvg} aria-hidden="true">
                <g stroke="#d7e4fb" strokeWidth="1.5" fill="none">
                  {[130, 100, 70, 40].map((r) => {
                    const pts = [0, 60, 120, 180, 240, 300].map((deg) => {
                      const rad = ((deg - 90) * Math.PI) / 180
                      return `${180 + r * Math.cos(rad)},${170 + r * Math.sin(rad)}`
                    }).join(' ')
                    return <polygon key={r} points={pts} />
                  })}
                  {[0, 60, 120, 180, 240, 300].map((deg) => {
                    const rad = ((deg - 90) * Math.PI) / 180
                    return <line key={deg} x1={180} y1={170} x2={180 + 130 * Math.cos(rad)} y2={170 + 130 * Math.sin(rad)} />
                  })}
                </g>
                <polygon
                  points={polygonPoints}
                  fill="rgba(47,120,255,0.18)"
                  stroke="#2f78ff"
                  strokeWidth="3"
                />
                {radarPts.map((pt, i) => (
                  <g key={i}>
                    <circle cx={pt.x} cy={pt.y} r="4" fill="#2f78ff" />
                  </g>
                ))}
              </svg>
              <div className={styles.radarLegend}>
                {radarLabels.map((label, i) => (
                  <span key={label} className={styles.radarLegendItem}>
                    <span className={styles.radarLegendDot} />
                    {label}
                  </span>
                ))}
              </div>
            </div>

            <div className={styles.radarSummary}>
              <h3 className={styles.summaryTitle}>画像摘要</h3>
              <span className={styles.summaryPill}>
                {LEVEL_LABEL[profile.knowledgeLevel ?? ''] ?? '学习者'}
                {profile.cognitiveStyle ? ` · ${STYLE_LABEL[profile.cognitiveStyle] ?? profile.cognitiveStyle}` : ''}
              </span>
              <p className={styles.summaryText}>
                {profile.learningGoal
                  ? `学习目标：${profile.learningGoal}。`
                  : '尚未设置学习目标。'}
                {masteredCount > 0
                  ? `已掌握 ${masteredCount} 个知识点。`
                  : '尚未掌握任何知识点。'}
                {(profile.weakTopics ?? []).length > 0
                  ? `薄弱点：${profile.weakTopics!.slice(0, 2).join('、')}。`
                  : ''}
              </p>

              <div className={styles.completionHead}>
                <span className={styles.completionLabel}>画像完整度</span>
                <strong className={styles.completionValue}>{completionPct}%</strong>
              </div>
              <div className={styles.completionTrack}>
                <span className={styles.completionFill} style={{ width: `${completionPct}%` }} />
              </div>
              <p className={styles.completionHint}>
                数据基于你的学习行为自动生成
                <span className={styles.hintInfo}>i</span>
              </p>
            </div>
          </div>
        </section>

        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>画像解读</h2>
          {insights.strengths.length > 0 && (
            <div className={styles.insightBlock}>
              <div className={styles.insightHead}>
                <span className={`${styles.insightIcon} ${styles.toneGreen}`}>👍</span>
                <h3>当前优势</h3>
              </div>
              <ul className={styles.insightList}>
                {insights.strengths.map((s, i) => <li key={i}>{s}</li>)}
              </ul>
            </div>
          )}
          {insights.weaknesses.length > 0 && (
            <div className={styles.insightBlock}>
              <div className={styles.insightHead}>
                <span className={`${styles.insightIcon} ${styles.toneOrange}`}>⚠</span>
                <h3>薄弱环节</h3>
              </div>
              <ul className={styles.insightList}>
                {insights.weaknesses.map((s, i) => <li key={i}>{s}</li>)}
              </ul>
            </div>
          )}
          {insights.suggestions.length > 0 && (
            <div className={styles.insightBlock}>
              <div className={styles.insightHead}>
                <span className={`${styles.insightIcon} ${styles.toneBlue}`}>💡</span>
                <h3>学习建议</h3>
              </div>
              <ul className={styles.insightList}>
                {insights.suggestions.map((s, i) => <li key={i}>{s}</li>)}
              </ul>
            </div>
          )}
          {insights.nextSteps.length > 0 && (
            <div className={styles.insightBlock}>
              <div className={styles.insightHead}>
                <span className={`${styles.insightIcon} ${styles.toneViolet}`}>⚑</span>
                <h3>推荐下一步</h3>
              </div>
              <ul className={styles.insightList}>
                {insights.nextSteps.map((s, i) => <li key={i}>{s}</li>)}
              </ul>
            </div>
          )}
        </section>

        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>知识点掌握</h2>
          {(profile.knowledgeMap ?? []).length > 0 ? (
            <div className={styles.timeline}>
              {profile.knowledgeMap!.slice(0, 8).map((kp) => (
                <article key={kp.kpId} className={styles.timelineItem}>
                  <span className={styles.timelineDot} style={{
                    background: kp.status === 'MASTERED' ? '#22c55e' : kp.mastery > 50 ? '#f59e0b' : '#94a3b8'
                  }} />
                  <div>
                    <h3 className={styles.timelineTitle}>{kp.title}</h3>
                    <p className={styles.timelineDesc}>
                      掌握度 {kp.mastery ?? 0}% · {kp.status === 'MASTERED' ? '已掌握' : '学习中'}
                    </p>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <p className={styles.emptyText}>暂无知识点数据，开始学习后自动生成</p>
          )}
        </section>
      </div>

      <div className={styles.bottomGrid}>
        <section className={styles.panel}>
          <div className={styles.bottomHeadCompact}>
            <h2 className={styles.panelTitle}>
              当前学习路径
              {currentPath?.goal && <span className={styles.bottomMetaInline}>（{currentPath.goal}）</span>}
            </h2>
          </div>
          {currentPath?.nodes && currentPath.nodes.length > 0 ? (
            <div className={styles.pathGrid}>
              <article className={styles.pathCard}>
                <div className={styles.pathTop}>
                  <span className={styles.pathIcon}>📚</span>
                  <div>
                    <h3>{currentPath.goal || '学习路径'}</h3>
                    <p>共 {currentPath.nodes.length} 个知识点节点</p>
                  </div>
                </div>
                <div className={styles.pathBottom}>
                  <div className={styles.pathProgress}>
                    <div className={styles.progressHead}>
                      <span>进度 {pathProgress}%</span>
                    </div>
                    <div className={styles.progressTrack}>
                      <span className={styles.progressFill} style={{ width: `${pathProgress}%` }} />
                    </div>
                  </div>
                  <button type="button" className={styles.pathBtn} onClick={() => navigate('/workspace/learning-path')}>
                    查看路径
                  </button>
                </div>
              </article>
            </div>
          ) : (
            <p className={styles.emptyText}>暂无学习路径，可在聊天中生成</p>
          )}
        </section>

        <section className={styles.panel}>
          <div className={styles.bottomHeadCompact}>
            <h2 className={styles.panelTitle}>
              六维度概览
              <span className={styles.bottomMetaInline}>（实时计算）</span>
            </h2>
          </div>
          <div className={styles.dimensionGrid}>
            {[dimensionDefs.slice(0, 3), dimensionDefs.slice(3, 6)].map((column, ci) => (
              <div key={ci} className={styles.dimensionColumn}>
                {column.map((item) => (
                  <div key={item.label} className={styles.dimensionRow}>
                    <div className={styles.dimensionLabel}>
                      <span className={`${styles.dimensionIcon} ${toneClass(item.tone)}`}>{item.icon}</span>
                      <span>{item.label}</span>
                    </div>
                    <div className={styles.dimensionTrack}>
                      <span className={`${styles.dimensionFill} ${toneClass(item.tone)}`} style={{ width: `${item.value}%` }} />
                    </div>
                    <strong className={styles.dimensionValue}>{item.value}</strong>
                  </div>
                ))}
              </div>
            ))}
          </div>
        </section>
      </div>
    </section>
  )
}
