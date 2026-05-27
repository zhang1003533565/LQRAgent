import type { JSX } from 'react'
import { NavLink } from 'react-router-dom'
import styles from './WorkspaceSidebar.module.css'

type NavItem = {
  to: string
  label: string
  end?: boolean
  icon: JSX.Element
}

const NAV_ITEMS: NavItem[] = [
  {
    to: '/workspace',
    end: true,
    label: '聊天学习',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M7 18.5c-1.933 0-3.5-1.567-3.5-3.5v-6c0-1.933 1.567-3.5 3.5-3.5h10c1.933 0 3.5 1.567 3.5 3.5v6c0 1.933-1.567 3.5-3.5 3.5H12l-4.5 3v-3H7Z"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <circle cx="9" cy="12" r="1" fill="currentColor" />
        <circle cx="12" cy="12" r="1" fill="currentColor" />
        <circle cx="15" cy="12" r="1" fill="currentColor" />
      </svg>
    ),
  },
  {
    to: '/workspace/learning-path',
    label: '学习路径',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M4.75 6.75A1.75 1.75 0 0 1 6.5 5h4.25c.93 0 1.79.465 2.31 1.24.52-.775 1.38-1.24 2.31-1.24h2.13a1.75 1.75 0 0 1 1.75 1.75v10.5A1.75 1.75 0 0 1 17.5 19.5h-2.13c-.93 0-1.79.465-2.31 1.24-.52-.775-1.38-1.24-2.31-1.24H6.5a1.75 1.75 0 0 1-1.75-1.75V6.75Z"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path d="M12.5 7v13" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    to: '/workspace/resources',
    label: '学习资源展示',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <rect
          x="4"
          y="5"
          width="16"
          height="14"
          rx="2.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
        />
        <path d="M8 10.5h8M8 14h5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    to: '/workspace/quiz',
    label: '答题',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M12 20.25c4.556 0 8.25-3.694 8.25-8.25S16.556 3.75 12 3.75 3.75 7.444 3.75 12 7.444 20.25 12 20.25Z"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
        />
        <path
          d="M9.7 9.15a2.65 2.65 0 1 1 4.65 1.73c-.52.59-1.32 1.1-1.85 1.7-.3.33-.5.7-.5 1.17"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <circle cx="12" cy="16.4" r="1" fill="currentColor" />
      </svg>
    ),
  },
  {
    to: '/workspace/upload',
    label: '上传',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path
          d="M12 15V6.75M8.75 10l3.25-3.25L15.25 10M5.75 15.5v1A1.75 1.75 0 0 0 7.5 18.25h9A1.75 1.75 0 0 0 18.25 16.5v-1"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    ),
  },
  {
    to: '/workspace/profile',
    label: '学习画像',
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="8" r="3.25" fill="none" stroke="currentColor" strokeWidth="1.8" />
        <path
          d="M5.5 18.25c.72-2.76 3.35-4.75 6.5-4.75s5.78 1.99 6.5 4.75"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
        />
      </svg>
    ),
  },
]

export default function WorkspaceSidebar() {
  return (
    <aside className={styles.sidebar}>
      <div className={styles.brand}>
        <div className={styles.brandMark}>
          <div className={styles.brandIcon}>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <rect x="3" y="3" width="18" height="18" rx="5" fill="currentColor" />
              <path
                d="M7.5 13.5a4.5 4.5 0 0 1 8.76-1.5A3.25 3.25 0 1 1 16 18.5H9.25A3.25 3.25 0 0 1 7.5 13.5Z"
                fill="none"
                stroke="#fff"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </div>
        </div>
        <span className={styles.brandText}>Edu.AI</span>
      </div>

      <nav className={styles.nav} aria-label="工作台导航">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              isActive ? `${styles.navItem} ${styles.navItemActive}` : styles.navItem
            }
          >
            <span className={styles.navIcon}>{item.icon}</span>
            <span className={styles.navLabel}>{item.label}</span>
          </NavLink>
        ))}
      </nav>

      <div className={styles.courseCard}>
        <p className={styles.courseHint}>当前课程</p>
        <h3 className={styles.courseTitle}>高等数学（上）</h3>
        <p className={styles.courseMeta}>
          学习进度 <span>68%</span>
        </p>
        <div className={styles.progressTrack} aria-hidden="true">
          <div className={styles.progressFill} style={{ width: '68%' }} />
        </div>
      </div>

      <button type="button" className={styles.userCard}>
        <div className={styles.avatar}>
          <svg viewBox="0 0 48 48" aria-hidden="true">
            <defs>
              <linearGradient id="avatar-bg" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stopColor="#9ec5ff" />
                <stop offset="100%" stopColor="#3f87ff" />
              </linearGradient>
            </defs>
            <circle cx="24" cy="24" r="24" fill="url(#avatar-bg)" />
            <circle cx="24" cy="18" r="8" fill="#f7d2b2" />
            <path d="M10 39c2.8-7.4 8.1-11 14-11s11.2 3.6 14 11" fill="#16325c" opacity="0.2" />
            <path d="M14 40c1.6-5.8 5.8-10 10-10s8.4 4.2 10 10" fill="#1d62f0" />
            <path d="M17.5 18.5c0-3.5 2.9-6.5 6.5-6.5s6.5 3 6.5 6.5c0 1.9-.8 3.6-2 4.8-.7.7-1 1.7-1 2.7h-7c0-1-.3-2-1-2.7-1.2-1.2-2-2.9-2-4.8Z" fill="#1d1d1d" />
          </svg>
        </div>
        <div className={styles.userInfo}>
          <span className={styles.userName}>小明</span>
          <span className={styles.userRole}>学生</span>
        </div>
        <span className={styles.userArrow} aria-hidden="true">
          <svg viewBox="0 0 20 20">
            <path d="M5.5 7.5 10 12l4.5-4.5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </span>
      </button>
    </aside>
  )
}
