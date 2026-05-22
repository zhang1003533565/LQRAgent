import { NavLink } from 'react-router-dom'
import ProfileCard from '../profile/ProfileCard'
import styles from './WorkspaceSidebar.module.css'

const NAV = [
  { to: '/workspace', end: true, label: '聊天学习', icon: '💬' },
  { to: '/workspace/upload', end: false, label: '上传资料', icon: '📤' },
  { to: '/workspace/profile', end: false, label: '我的画像', icon: '📊' },
] as const

export default function WorkspaceSidebar() {
  return (
    <aside className={styles.sidebar}>
      <div className={styles.brand}>
        <span className={styles.logo}>LQR</span>
        <span className={styles.sub}>学习工作台</span>
      </div>

      <ProfileCard />

      <nav className={styles.nav} aria-label="工作台导航">
        {NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              isActive ? `${styles.link} ${styles.active}` : styles.link
            }
          >
            <span className={styles.icon} aria-hidden>
              {item.icon}
            </span>
            {item.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
