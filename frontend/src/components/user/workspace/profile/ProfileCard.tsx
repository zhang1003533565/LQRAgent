import { useEffect, useState } from 'react'
import { getMe, type UserProfileDto } from '@/api/user/user'
import { getProfileSummary } from '@/api/user/profile'
import { useAuthStore } from '@/components/user/store/authStore'
import { useProfileStore } from '@/components/user/store/profileStore'
import { Badge } from '@/components/user/ui'
import styles from './ProfileCard.module.css'

const ROLE_LABEL: Record<string, string> = {
  student: '学生',
  teacher: '教师',
  admin: '管理员',
}

export default function ProfileCard() {
  const authUser = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const summary = useProfileStore((s) => s.summary)
  const setSummary = useProfileStore((s) => s.setSummary)
  const [profile, setProfile] = useState<UserProfileDto | null>(null)

  useEffect(() => {
    getMe().then(setProfile).catch(() => {})
    getProfileSummary().then(setSummary).catch(() => {})
  }, [setSummary])

  const displayName =
    summary?.displayName ?? profile?.displayName ?? authUser?.username ?? '—'
  const role = profile?.role ?? authUser?.role ?? '—'

  return (
    <div className={styles.card}>
      <div className={styles.avatar}>
        {displayName.charAt(0).toUpperCase()}
      </div>
      <div className={styles.info}>
        <p className={styles.name}>{displayName}</p>
        <p className={styles.role}>{ROLE_LABEL[role] ?? role}</p>
        {summary && (
          <Badge variant="muted">掌握 {summary.masteryLevel ?? 0}%</Badge>
        )}
      </div>
      <button
        type="button"
        className={styles.logoutBtn}
        onClick={logout}
        title="退出登录"
        aria-label="退出登录"
      >
        ⏻
      </button>
    </div>
  )
}
