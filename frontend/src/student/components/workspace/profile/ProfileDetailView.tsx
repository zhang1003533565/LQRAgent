import { useEffect, useState } from 'react'
import { getProfileDetail } from '@/shared/api/profile'
import type { ProfileDetail } from '@/shared/types/profile'
import { Badge, PlaceholderBanner } from '@/shared/components/ui'
import styles from './ProfileDetailView.module.css'

export default function ProfileDetailView() {
  const [detail, setDetail] = useState<ProfileDetail | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getProfileDetail()
      .then(setDetail)
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return <p className={styles.loading}>加载中...</p>
  }

  if (!detail) {
    return <p className={styles.loading}>暂无画像数据</p>
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h2>学习画像</h2>
        <PlaceholderBanner
          label="画像 API 占位"
          hint="GET /api/profile/detail 就绪后替换"
        />
      </header>

      <section className={styles.grid}>
        <div className={styles.card}>
          <span className={styles.label}>掌握度</span>
          <strong>{detail.masteryLevel ?? 0}%</strong>
        </div>
        <div className={styles.card}>
          <span className={styles.label}>已完成知识点</span>
          <strong>{detail.completedKpCount ?? 0}</strong>
        </div>
        <div className={styles.card}>
          <span className={styles.label}>连续学习</span>
          <strong>{detail.streakDays ?? 0} 天</strong>
        </div>
      </section>

      {detail.weakTopics && detail.weakTopics.length > 0 && (
        <section className={styles.block}>
          <h3>薄弱项</h3>
          <div className={styles.tags}>
            {detail.weakTopics.map((t) => (
              <Badge key={t} variant="warn">
                {t}
              </Badge>
            ))}
          </div>
        </section>
      )}

      {detail.recentGoals && (
        <section className={styles.block}>
          <h3>近期目标</h3>
          <ul>
            {detail.recentGoals.map((g) => (
              <li key={g}>{g}</li>
            ))}
          </ul>
        </section>
      )}

      {detail.knowledgeMap && (
        <section className={styles.block}>
          <h3>知识掌握地图</h3>
          <ul className={styles.map}>
            {detail.knowledgeMap.map((k) => (
              <li key={k.kpId}>
                <span>{k.title}</span>
                <div className={styles.bar}>
                  <div
                    className={styles.fill}
                    style={{ width: `${k.mastery}%` }}
                  />
                </div>
                <span className={styles.pct}>{k.mastery}%</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
