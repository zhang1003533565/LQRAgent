import { useState, useRef, useEffect } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { STUDENT_NAV_ITEMS } from '@/components/student/studentNavItems'
import { useAuthStore } from '@/utils/store/authStore'
import styles from './WorkspaceSidebar.module.css'

export default function WorkspaceSidebar() {
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const logout = useAuthStore((s) => s.logout)
  const navigate = useNavigate()

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false)
      }
    }
    if (menuOpen) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [menuOpen])

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const handleProfileCenter = () => {
    setMenuOpen(false)
    navigate('/workspace/profile-center')
  }

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
        {STUDENT_NAV_ITEMS.map((item) => (
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

      <div className={styles.userCardWrapper} ref={menuRef}>
        <button
          type="button"
          className={`${styles.userCard} ${menuOpen ? styles.userCardActive : ''}`}
          onClick={() => setMenuOpen((v) => !v)}
        >
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
          <span className={`${styles.userArrow} ${menuOpen ? styles.userArrowUp : ''}`} aria-hidden="true">
            <svg viewBox="0 0 20 20">
              <path d="M5.5 7.5 10 12l4.5-4.5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </span>
        </button>

        {menuOpen && (
          <div className={styles.userDropdown}>
            <button type="button" className={styles.userDropdownItem} onClick={handleProfileCenter}>
              <svg viewBox="0 0 24 24" aria-hidden="true" width="18" height="18">
                <circle cx="12" cy="10" r="2.75" fill="none" stroke="currentColor" strokeWidth="1.8" />
                <path d="M6.5 19.5c.55-2.12 2.55-3.5 5.5-3.5s4.95 1.38 5.5 3.5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                <path d="M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" fill="none" stroke="currentColor" strokeWidth="1.8" />
              </svg>
              个人中心
            </button>
            <button type="button" className={styles.userDropdownItem} onClick={handleLogout}>
              <svg viewBox="0 0 24 24" aria-hidden="true" width="18" height="18">
                <path d="M9.75 3.75h-2a2 2 0 0 0-2 2v12.5a2 2 0 0 0 2 2h2M14.25 8.75l3.25 3.25-3.25 3.25M17.5 12h-7.75" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              退出登录
            </button>
          </div>
        )}
      </div>
    </aside>
  )
}
