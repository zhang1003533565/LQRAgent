import { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { getProfileDetail } from '@/api/student/profile'
import { getQuizStats, getQuizRecords, type QuizStats, type QuizRecordItem } from '@/api/student/quiz'
import { getCurrentPath } from '@/api/student/learningPath'
import type { ProfileDetail } from '@/utils/types/profile'
import type { LearningPathDto } from '@/utils/types/learning-path'
import styles from './DashboardPage.module.css'

export default function DashboardPage() {
  const navigate = useNavigate()
  const [profile, setProfile] = useState<ProfileDetail | null>(null)
  const [quizStats, setQuizStats] = useState<QuizStats | null>(null)
  const [records, setRecords] = useState<QuizRecordItem[]>([])
  const [currentPath, setCurrentPath] = useState<LearningPathDto | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      getProfileDetail().catch(() => null),
      getQuizStats().catch(() => null),
      getQuizRecords().catch(() => []),
      getCurrentPath().catch(() => null),
    ]).then(([p, qs, rec, path]) => {
      setProfile(p)
      setQuizStats(qs)
      setRecords(rec ?? [])
      setCurrentPath(path)
    }).finally(() => setLoading(false))
  }, [])

  const masteryStats = useMemo(() => {
    const km = profile?.knowledgeMap ?? []
    const mastered = km.filter((k) => k.status === 'MASTERED').length
    const learning = km.filter((k) => k.status !== 'MASTERED' && (k.mastery ?? 0) > 0).length
    const notStarted = km.filter((k) => (k.mastery ?? 0) === 0).length
    return { mastered, learning, notStarted, total: km.length }
  }, [profile])

  const trendData = useMemo(() => {
    const byDate: Record<string, { total: number; correct: number }> = {}
    for (const r of records) {
      const day = (r.createdAt ?? '').slice(0, 10)
      if (!day) continue
      if (!byDate[day]) byDate[day] = { total: 0, correct: 0 }
      byDate[day].total++
      if (r.correct) byDate[day].correct++
    }
    return Object.entries(byDate)
      .sort(([a], [b]) => a.localeCompare(b))
      .slice(-14)
      .map(([date, d]) => ({
        date: date.slice(5),
        accuracy: d.total > 0 ? Math.round((d.correct / d.total) * 100) : 0,
        total: d.total,
      }))
  }, [records])

  const maxTrendAccuracy = useMemo(() => {
    if (trendData.length === 0) return 100
    return Math.max(...trendData.map((d) => d.accuracy), 10)
  }, [trendData])

  const pathProgress = useMemo(() => {
    if (!currentPath?.nodes) return { completed: 0, total: 0, pct: 0 }
    const completed = currentPath.nodes.filter((n: any) => n.completed).length
    const total = currentPath.nodes.length
    return { completed, total, pct: total > 0 ? Math.round((completed / total) * 100) : 0 }
  }, [currentPath])

  if (loading) return <div className={styles.page}><p className={styles.loading}>加载中...</p></div>

  return (
    <section className={styles.page}>
      <div className={styles.glow} />

      <header className={styles.topbar}>
        <div>
          <h1 className={styles.title}>学习概览</h1>
          <p className={styles.subtitle}>
            {profile?.learningGoal ? `目标：${profile.learningGoal}` : '你的学习数据一览'}
          </p>
        </div>
        <div className={styles.topMeta}>
          {profile?.knowledgeLevel === 'BEGINNER' ? '初学者' : profile?.knowledgeLevel === 'INTERMEDIATE' ? '进阶者' : profile?.knowledgeLevel === 'ADVANCED' ? '高级' : '学习者'}
        </div>
      </header>

      <div className={styles.statsRow}>
        <article className={styles.statCard}>
          <span className={styles.statIcon}>📚</span>
          <div>
            <p className={styles.statValue}>{masteryStats.mastered}</p>
            <p className={styles.statLabel}>已掌握知识点</p>
          </div>
        </article>
        <article className={styles.statCard}>
          <span className={styles.statIcon}>📝</span>
          <div>
            <p className={styles.statValue}>{quizStats?.total ?? 0}</p>
            <p className={styles.statLabel}>总答题数</p>
          </div>
        </article>
        <article className={styles.statCard}>
          <span className={styles.statIcon}>🎯</span>
          <div>
            <p className={styles.statValue}>{quizStats ? Math.round(quizStats.accuracy * 100) : 0}%</p>
            <p className={styles.statLabel}>正确率</p>
          </div>
        </article>
        <article className={styles.statCard}>
          <span className={styles.statIcon}>🔥</span>
          <div>
            <p className={styles.statValue}>{profile?.streakDays ?? 0}</p>
            <p className={styles.statLabel}>连续学习天数</p>
          </div>
        </article>
      </div>

      <div className={styles.mainGrid}>
        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>知识点掌握分布</h2>
          {masteryStats.total > 0 ? (
            <>
              <div className={styles.masteryBar}>
                <div className={styles.masterySegment} style={{ width: `${(masteryStats.mastered / masteryStats.total) * 100}%`, background: '#22c55e' }} />
                <div className={styles.masterySegment} style={{ width: `${(masteryStats.learning / masteryStats.total) * 100}%`, background: '#3b82f6' }} />
                <div className={styles.masterySegment} style={{ width: `${(masteryStats.notStarted / masteryStats.total) * 100}%`, background: '#e2e8f0' }} />
              </div>
              <div className={styles.masteryLegend}>
                <span><span className={styles.legendDot} style={{ background: '#22c55e' }} /> 已掌握 {masteryStats.mastered}</span>
                <span><span className={styles.legendDot} style={{ background: '#3b82f6' }} /> 学习中 {masteryStats.learning}</span>
                <span><span className={styles.legendDot} style={{ background: '#e2e8f0' }} /> 未开始 {masteryStats.notStarted}</span>
              </div>
              <div className={styles.heatmap}>
                {(profile?.knowledgeMap ?? []).map((kp) => (
                  <div
                    key={kp.kpId}
                    className={styles.heatCell}
                    title={`${kp.title}: ${kp.mastery ?? 0}%`}
                    style={{
                      background: (kp.mastery ?? 0) >= 80 ? '#22c55e'
                        : (kp.mastery ?? 0) >= 50 ? '#3b82f6'
                        : (kp.mastery ?? 0) > 0 ? '#f59e0b'
                        : '#e2e8f0',
                    }}
                  >
                    <span className={styles.heatLabel}>{kp.title.slice(0, 3)}</span>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <p className={styles.emptyText}>暂无知识点数据，开始学习后自动生成</p>
          )}
        </section>

        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>答题正确率趋势</h2>
          {trendData.length > 0 ? (
            <div className={styles.chartArea}>
              <div className={styles.chartYAxis}>
                <span>{maxTrendAccuracy}%</span>
                <span>{Math.round(maxTrendAccuracy / 2)}%</span>
                <span>0%</span>
              </div>
              <div className={styles.chartBars}>
                {trendData.map((d, i) => (
                  <div key={i} className={styles.chartCol}>
                    <div
                      className={styles.chartBar}
                      style={{ height: `${(d.accuracy / maxTrendAccuracy) * 100}%` }}
                      title={`${d.date}: ${d.accuracy}% (${d.total}题)`}
                    />
                    <span className={styles.chartLabel}>{d.date}</span>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <p className={styles.emptyText}>暂无答题记录</p>
          )}
        </section>

        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>薄弱知识点</h2>
          {(profile?.weakTopics ?? []).length > 0 ? (
            <div className={styles.weakList}>
              {profile!.weakTopics!.map((topic, i) => (
                <div key={i} className={styles.weakItem}>
                  <span className={styles.weakDot} />
                  <span className={styles.weakText}>{topic}</span>
                </div>
              ))}
            </div>
          ) : (
            <p className={styles.emptyText}>暂无薄弱知识点，继续保持！</p>
          )}
          {(profile?.weakTopics ?? []).length > 0 && (
            <button type="button" className={styles.actionBtn} onClick={() => navigate('/workspace/quiz')}>
              去练习
            </button>
          )}
        </section>

        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>当前学习路径</h2>
          {currentPath?.nodes && currentPath.nodes.length > 0 ? (
            <>
              <p className={styles.pathGoal}>{currentPath.goal || '学习路径'}</p>
              <div className={styles.pathProgressBar}>
                <div className={styles.pathProgressFill} style={{ width: `${pathProgress.pct}%` }} />
              </div>
              <p className={styles.pathProgressText}>
                已完成 {pathProgress.completed}/{pathProgress.total} 节点 ({pathProgress.pct}%)
              </p>
              <div className={styles.pathSteps}>
                {currentPath.nodes.slice(0, 5).map((node: any, i: number) => (
                  <div key={node.kpId} className={styles.pathStep}>
                    <span className={`${styles.pathDot} ${node.completed ? styles.pathDotDone : ''}`} />
                    <span className={styles.pathStepTitle}>{node.title}</span>
                  </div>
                ))}
                {currentPath.nodes.length > 5 && (
                  <span className={styles.pathMore}>+{currentPath.nodes.length - 5} 更多</span>
                )}
              </div>
              <button type="button" className={styles.actionBtn} onClick={() => navigate('/workspace/learning-path')}>
                查看完整路径
              </button>
            </>
          ) : (
            <>
              <p className={styles.emptyText}>暂无学习路径</p>
              <button type="button" className={styles.actionBtn} onClick={() => navigate('/workspace')}>
                去生成
              </button>
            </>
          )}
        </section>

        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>推荐下一步</h2>
          <div className={styles.recommendList}>
            {(profile?.weakTopics ?? []).length > 0 && (
              <div className={styles.recommendItem}>
                <span className={styles.recommendIcon}>🎯</span>
                <div>
                  <p className={styles.recommendTitle}>复习薄弱知识点</p>
                  <p className={styles.recommendDesc}>{profile!.weakTopics![0]}</p>
                </div>
              </div>
            )}
            {profile?.learningGoal && (
              <div className={styles.recommendItem}>
                <span className={styles.recommendIcon}>📚</span>
                <div>
                  <p className={styles.recommendTitle}>继续学习目标</p>
                  <p className={styles.recommendDesc}>{profile.learningGoal}</p>
                </div>
              </div>
            )}
            {pathProgress.total > 0 && pathProgress.pct < 100 && (
              <div className={styles.recommendItem}>
                <span className={styles.recommendIcon}>✅</span>
                <div>
                  <p className={styles.recommendTitle}>完成下一个节点</p>
                  <p className={styles.recommendDesc}>
                    {currentPath?.nodes?.find((n: any) => !n.completed)?.title ?? '继续推进'}
                  </p>
                </div>
              </div>
            )}
            {masteryStats.mastered === 0 && (
              <div className={styles.recommendItem}>
                <span className={styles.recommendIcon}>🚀</span>
                <div>
                  <p className={styles.recommendTitle}>开始你的学习之旅</p>
                  <p className={styles.recommendDesc}>在聊天中告诉助手你想学什么</p>
                </div>
              </div>
            )}
          </div>
        </section>
      </div>
    </section>
  )
}
