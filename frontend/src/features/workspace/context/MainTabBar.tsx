import styles from './MainTabBar.module.css'

export type LearnTab = 'chat' | 'path' | 'resources' | 'quiz'

const TABS: { id: LearnTab; label: string }[] = [
  { id: 'chat', label: '对话' },
  { id: 'path', label: '学习路径' },
  { id: 'resources', label: '资源' },
  { id: 'quiz', label: '练习' },
]

interface Props {
  active: LearnTab
  onChange: (tab: LearnTab) => void
}

/** 主区顶部 Tab（路径/资源/练习不再占第三栏） */
export default function MainTabBar({ active, onChange }: Props) {
  return (
    <div className={styles.bar} role="tablist" aria-label="学习模块">
      {TABS.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={active === tab.id}
          className={active === tab.id ? styles.active : styles.tab}
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
        </button>
      ))}
    </div>
  )
}
