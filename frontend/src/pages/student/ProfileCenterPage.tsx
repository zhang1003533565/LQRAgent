import { useEffect, useState } from 'react'
import { getProfileDetail } from '@/api/student/profile'
import { getQuizStats, getQuizRecords, type QuizStats, type QuizRecordItem } from '@/api/student/quiz'
import { useAuthStore } from '@/utils/store/authStore'
import type { ProfileDetail } from '@/utils/types/profile'
import styles from './ProfileCenterPage.module.css'

export default function ProfileCenterPage() {
  const [profile, setProfile] = useState<ProfileDetail | null>(null)
  const [quizStats, setQuizStats] = useState<QuizStats | null>(null)
  const [quizRecords, setQuizRecords] = useState<QuizRecordItem[]>([])
  const [loading, setLoading] = useState(true)
  const user = useAuthStore((s) => s.user)

  useEffect(() => {
    async function load() {
      setLoading(true)
      try {
        const [p, s, r] = await Promise.all([
          getProfileDetail(),
          getQuizStats().catch(() => null),
          getQuizRecords().catch(() => []),
        ])
        setProfile(p)
        setQuizStats(s)
        setQuizRecords(r)
      } catch (err) {
        console.error('Failed to load profile:', err)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  if (loading) {
    return (
      <div className={styles.page}>
        <div className={styles.loading}>加载中...</div>
      </div>
    )
  }

  if (!profile) {
    return (
      <div className={styles.page}>
        <div className={styles.empty}>暂无数据</div>
      </div>
    )
  }

  const masteryPercent = Math.round((profile.masteryLevel ?? 0) * 100)
  const accuracy = quizStats?.accuracy ?? 0
  const recentRecords = quizRecords.slice(0, 8)

  return (
    <div className={styles.page}>
      <h2 className={styles.title}>个人中心</h2>

      {/* 顶部概览卡片 */}
      <div className={styles.topRow}>
        <div className={styles.userCard}>
          <div className={styles.avatar}>
            {(profile.displayName || user?.username || '?')[0].toUpperCase()}
          </div>
          <div className={styles.userInfo}>
            <div className={styles.userName}>{profile.displayName || user?.username || '学习者'}</div>
            <div className={styles.userRole}>{profile.role === 'admin' ? '管理员' : '学员'}</div>
          </div>
        </div>

        <div className={styles.statCard}>
          <div className={styles.statValue}>{profile.streakDays ?? 0}</div>
          <div className={styles.statLabel}>连续学习天数</div>
        </div>

        <div className={styles.statCard}>
          <div className={styles.statValue}>{profile.completedKpCount ?? 0}</div>
          <div className={styles.statLabel}>已完成知识点</div>
        </div>

        <div className={styles.statCard}>
          <div className={styles.statValue}>{quizStats?.total ?? 0}</div>
          <div className={styles.statLabel}>总答题数</div>
        </div>
      </div>

      {/* 中间区域 */}
      <div className={styles.midRow}>
        {/* 掌握度环形图 */}
        <div className={styles.masteryCard}>
          <h3 className={styles.sectionTitle}>掌握度</h3>
          <div className={styles.ringContainer}>
            <svg viewBox="0 0 120 120" className={styles.ringSvg}>
              <circle cx="60" cy="60" r="52" fill="none" stroke="#e5e7eb" strokeWidth="10" />
              <circle
                cx="60" cy="60" r="52"
                fill="none"
                stroke={masteryPercent >= 60 ? '#22c55e' : masteryPercent >= 30 ? '#f59e0b' : '#ef4444'}
                strokeWidth="10"
                strokeLinecap="round"
                strokeDasharray={`${(masteryPercent / 100) * 327} 327`}
                strokeDashoffset="0"
                transform="rotate(-90 60 60)"
                className={styles.ringProgress}
              />
            </svg>
            <div className={styles.ringText}>
              <span className={styles.ringValue}>{masteryPercent}%</span>
            </div>
          </div>
        </div>

        {/* 知识点掌握地图 */}
        <div className={styles.knowledgeCard}>
          <h3 className={styles.sectionTitle}>知识点掌握</h3>
          <div className={styles.knowledgeList}>
            {(profile.knowledgeMap && profile.knowledgeMap.length > 0
              ? profile.knowledgeMap
              : [{ kpId: 'none', title: '暂无知识点数据', mastery: 0 }]
            ).map((kp) => (
              <div key={kp.kpId} className={styles.knowledgeItem}>
                <div className={styles.knowledgeName}>{kp.title}</div>
                <div className={styles.progressBar}>
                  <div
                    className={styles.progressFill}
                    style={{
                      width: `${Math.round(kp.mastery * 100)}%`,
                      backgroundColor:
                        kp.mastery >= 0.6 ? '#22c55e' : kp.mastery >= 0.3 ? '#f59e0b' : '#ef4444',
                    }}
                  />
                </div>
                <span className={styles.knowledgePercent}>{Math.round(kp.mastery * 100)}%</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 答题统计与弱项 */}
      <div className={styles.bottomRow}>
        <div className={styles.quizCard}>
          <h3 className={styles.sectionTitle}>答题统计</h3>
          <div className={styles.quizStats}>
            <div className={styles.quizStatItem}>
              <div className={styles.quizStatNum}>{quizStats?.total ?? 0}</div>
              <div className={styles.quizStatLabel}>总答题</div>
            </div>
            <div className={styles.quizStatItem}>
              <div className={`${styles.quizStatNum} ${styles.correct}`}>{quizStats?.correct ?? 0}</div>
              <div className={styles.quizStatLabel}>正确</div>
            </div>
            <div className={styles.quizStatItem}>
              <div className={`${styles.quizStatNum} ${styles.wrong}`}>{quizStats?.wrong ?? 0}</div>
              <div className={styles.quizStatLabel}>错误</div>
            </div>
            <div className={styles.quizStatItem}>
              <div className={styles.quizStatNum}>{accuracy}%</div>
              <div className={styles.quizStatLabel}>正确率</div>
            </div>
          </div>

          {/* 正确率条形图 */}
          <div className={styles.accuracyBarContainer}>
            <div className={styles.accuracyBarTrack}>
              <div
                className={styles.accuracyBarFill}
                style={{ width: `${accuracy}%` }}
              />
            </div>
            <span className={styles.accuracyLabel}>{accuracy}% 正确率</span>
          </div>
        </div>

        {/* 薄弱知识点 */}
        <div className={styles.weakCard}>
          <h3 className={styles.sectionTitle}>薄弱知识点</h3>
          {profile.weakTopics && profile.weakTopics.length > 0 ? (
            <ul className={styles.weakList}>
              {profile.weakTopics.map((topic, i) => (
                <li key={i} className={styles.weakItem}>{topic}</li>
              ))}
            </ul>
          ) : (
            <p className={styles.emptyHint}>暂无数据，继续学习吧！</p>
          )}
        </div>

        {/* 学习目标 */}
        <div className={styles.goalsCard}>
          <h3 className={styles.sectionTitle}>学习目标</h3>
          {profile.recentGoals && profile.recentGoals.length > 0 ? (
            <ul className={styles.goalsList}>
              {profile.recentGoals.map((goal, i) => (
                <li key={i} className={styles.goalsItem}>{goal}</li>
              ))}
            </ul>
          ) : (
            <p className={styles.emptyHint}>通过聊天设置学习目标</p>
          )}
        </div>
      </div>

      {/* 最近答题记录 */}
      {recentRecords.length > 0 && (
        <div className={styles.recordsCard}>
          <h3 className={styles.sectionTitle}>最近答题</h3>
          <div className={styles.recordsTable}>
            {recentRecords.map((r) => (
              <div key={r.id} className={styles.recordRow}>
                <span className={styles.recordQId}>#{r.questionId}</span>
                <span className={`${styles.recordResult} ${r.correct ? styles.recordCorrect : styles.recordWrong}`}>
                  {r.correct ? '正确' : '错误'}
                </span>
                <span className={styles.recordAnswer}>{r.answer}</span>
                <span className={styles.recordTime}>{formatTime(r.createdAt)}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function formatTime(iso: string): string {
  try {
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return iso
  }
}
