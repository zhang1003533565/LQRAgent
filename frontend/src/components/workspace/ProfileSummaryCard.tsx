import { useEffect, useState } from 'react'
import { getMe, type UserProfileDto } from '@/api/user'
import { useAuthStore } from '@/store/authStore'
import styles from './ProfileSummaryCard.module.css'

/**
 * 左侧顶部用户信息卡片，显示头像、用户名、角色。
 */
export default function ProfileSummaryCard() {
  const authUser = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const [profile, setProfile] = useState<UserProfileDto | null>(null)

  useEffect(() => {
    getMe()
      .then(setProfile)
      .catch(() => {/* 静默失败，用 authStore 数据兜底 */})
  }, [])

  const displayName = profile?.displayName ?? authUser?.username ?? '—'
  const role = profile?.role ?? authUser?.role ?? '—'

  const roleLabel: Record<string, string> = {
    student: '学生',
    teacher: '教师',
    admin: '管理员',
  }

  return (
    <div className={styles.card}>
      <div className={styles.avatar}>
        {displayName.charAt(0).toUpperCase()}
      </div>
      <div className={styles.info}>
        <p className={styles.name}>{displayName}</p>
        <p className={styles.role}>{roleLabel[role] ?? role}</p>
      </div>
      <button
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
