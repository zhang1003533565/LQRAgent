import { useState, useRef, useEffect, useCallback } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { STUDENT_NAV_ITEMS } from '@/components/student/studentNavItems'
import { useAuthStore } from '@/utils/store/authStore'
import { useChatStore } from '@/utils/store/chatStore'
import { usePathStore } from '@/utils/store/pathStore'
import { chatApi } from '@/api/student/chat'
import styles from './WorkspaceSidebar.module.css'

export default function WorkspaceSidebar() {
  const [collapsed, setCollapsed] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [sessions, setSessions] = useState<Array<{ id: string; title: string }>>([])
  const menuRef = useRef<HTMLDivElement>(null)
  const logout = useAuthStore((s) => s.logout)
  const user = useAuthStore((s) => s.user)
  const navigate = useNavigate()
  const currentSessionId = useChatStore((s) => s.sessionId)
  const sessionListVersion = useChatStore((s) => s.sessionListVersion)
  const setSessionId = useChatStore((s) => s.setSessionId)
  const clearMessages = useChatStore((s) => s.clearMessages)
  const loadMessages = useChatStore((s) => s.loadMessages)
  const setAutoLoaded = useChatStore((s) => s.setAutoLoaded)
  const hasPathUpdates = usePathStore((s) => s.hasUpdates)

  const loadSessions = useCallback(async () => {
    if (!user?.userId) return
    try {
      const data = await chatApi.getSessions(String(user.userId))
      setSessions(data.map((s: any) => ({ id: String(s.id), title: s.title || '新对话' })))
    } catch {}
  }, [user?.userId])

  useEffect(() => { loadSessions() }, [loadSessions, sessionListVersion])

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false)
      }
    }
    if (menuOpen) document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [menuOpen])

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const handleNewChat = () => {
    setSessionId(null)
    clearMessages()
    setAutoLoaded(true)
    navigate('/workspace')
  }

  const handleSelectSession = (id: string) => {
    setSessionId(id)
    loadMessages(id)
    navigate('/workspace')
  }

  const handleDeleteSession = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation()
    if (!confirm('确定删除这个对话吗？')) return
    try {
      await chatApi.deleteSession(id)
      setSessions((prev) => prev.filter((s) => s.id !== id))
      if (String(currentSessionId) === String(id)) {
        setSessionId(null)
        clearMessages()
        setAutoLoaded(true)
      }
    } catch {}
  }

  return (
    <aside className={`${styles.sidebar} ${collapsed ? styles.collapsed : ''}`}>
      <div className={styles.topSection}>
        <div className={styles.brand}>
          <div className={styles.brandMark}>
            <div className={styles.brandIcon}>
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="3" width="18" height="18" rx="5" fill="currentColor" />
                <path d="M7.5 13.5a4.5 4.5 0 0 1 8.76-1.5A3.25 3.25 0 1 1 16 18.5H9.25A3.25 3.25 0 0 1 7.5 13.5Z" fill="none" stroke="#fff" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </div>
          </div>
          {!collapsed && <span className={styles.brandText}>Edu.AI</span>}
          <button className={styles.collapseBtn} onClick={() => setCollapsed((v) => !v)} title={collapsed ? '展开' : '收起'}>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              {collapsed ? <path d="M9 18l6-6-6-6" /> : <path d="M15 18l-6-6 6-6" />}
            </svg>
          </button>
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
              title={collapsed ? item.label : undefined}
            >
              <span className={styles.navIcon}>{item.icon}</span>
              {!collapsed && (
                <span className={styles.navLabelWrap}>
                  <span className={styles.navLabel}>{item.label}</span>
                  {item.to === '/workspace/learning-path' && hasPathUpdates ? (
                    <span className={styles.navBadge} title="路径已更新" />
                  ) : null}
                </span>
              )}
            </NavLink>
          ))}
        </nav>
      </div>

      <div className={styles.historySection}>
        {!collapsed && (
          <>
            <div className={styles.historyHeader}>
              <span className={styles.historyTitle}>历史对话</span>
              <button className={styles.newChatBtn} onClick={handleNewChat} title="新建对话">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                  <path d="M12 5v14M5 12h14" />
                </svg>
              </button>
            </div>
            <div className={styles.historyList}>
              {sessions.length === 0 && (
                <div className={styles.emptyHistory}>暂无对话</div>
              )}
              {sessions.slice(0, 5).map((s) => (
                <div
                  key={s.id}
                  className={`${styles.historyItem} ${currentSessionId === s.id ? styles.historyItemActive : ''}`}
                  onClick={() => handleSelectSession(s.id)}
                  role="button"
                  tabIndex={0}
                >
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.8" className={styles.historyIcon}>
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                  </svg>
                  <span className={styles.historyText}>{s.title}</span>
                  <button
                    type="button"
                    className={styles.deleteBtn}
                    onClick={(e) => handleDeleteSession(e, s.id)}
                    title="删除对话"
                  >
                    <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                      <path d="M18 6L6 18M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              ))}
            </div>
          </>
        )}
        {collapsed && (
          <button className={styles.newChatBtnCollapsed} onClick={handleNewChat} title="新建对话">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <path d="M12 5v14M5 12h14" />
            </svg>
          </button>
        )}
      </div>

      <div className={styles.userSection} ref={menuRef}>
        <button
          type="button"
          className={`${styles.userCard} ${menuOpen ? styles.userCardActive : ''}`}
          onClick={() => setMenuOpen((v) => !v)}
        >
          <div className={styles.avatar}>
            <span className={styles.avatarText}>{user?.username?.[0]?.toUpperCase() || 'U'}</span>
          </div>
          {!collapsed && (
            <>
              <div className={styles.userInfo}>
                <span className={styles.userName}>{user?.username || '用户'}</span>
                <span className={styles.userRole}>学生</span>
              </div>
              <span className={`${styles.userArrow} ${menuOpen ? styles.userArrowUp : ''}`}>
                <svg viewBox="0 0 20 20" width="16" height="16">
                  <path d="M5.5 7.5 10 12l4.5-4.5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </span>
            </>
          )}
        </button>

        {menuOpen && (
          <div className={styles.userDropdown}>
            <button type="button" className={styles.userDropdownItem} onClick={() => { setMenuOpen(false); navigate('/workspace/profile') }}>
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.8">
                <circle cx="12" cy="8" r="3.25" />
                <path d="M5.5 18.25c.72-2.76 3.35-4.75 6.5-4.75s5.78 1.99 6.5 4.75" strokeLinecap="round" />
              </svg>
              个人中心
            </button>
            <button type="button" className={styles.userDropdownItem} onClick={handleLogout}>
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <path d="M9.75 3.75h-2a2 2 0 0 0-2 2v12.5a2 2 0 0 0 2 2h2M14.25 8.75l3.25 3.25-3.25 3.25M17.5 12h-7.75" />
              </svg>
              退出登录
            </button>
          </div>
        )}
      </div>
    </aside>
  )
}
