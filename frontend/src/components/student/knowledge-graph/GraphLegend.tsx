import { useState } from 'react'
import { Info } from 'lucide-react'
import styles from '@/pages/student/KnowledgeGraphPage.module.css'

const LEGEND_ITEMS = [
  {
    key: 'core',
    label: '核心节点',
    mark: <span className={styles.legendCoreMark} aria-hidden />,
  },
  {
    key: 'leaf',
    label: '子节点',
    mark: <span className={styles.legendDot} style={{ background: '#475569' }} aria-hidden />,
  },
  {
    key: 'edge',
    label: '关联连线',
    mark: <span className={styles.legendLineSoft} aria-hidden />,
  },
  {
    key: 'active',
    label: '选中高亮',
    mark: <span className={styles.legendLineSolid} aria-hidden />,
  },
] as const

export default function GraphLegend() {
  const [open, setOpen] = useState(false)

  return (
    <div className={styles.legendWrap}>
      <button
        type="button"
        className={styles.legendToggle}
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-label="图例说明"
        title="图例说明"
      >
        <Info size={14} strokeWidth={1.5} />
      </button>
      {open ? (
        <div className={styles.legendPanel}>
          <p className={styles.legendHint}>学习状态与关系类型请使用顶部筛选</p>
          {LEGEND_ITEMS.map((item) => (
            <div key={item.key} className={styles.legendItem}>
              {item.mark}
              <span>{item.label}</span>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  )
}
