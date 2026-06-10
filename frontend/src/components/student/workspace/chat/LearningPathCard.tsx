import { useNavigate } from 'react-router-dom'
import { usePathStore } from '@/utils/store/pathStore'
import styles from './LearningPathCard.module.css'

export default function LearningPathCard() {
  const navigate = useNavigate()
  const { goal, nodes } = usePathStore()

  const completedCount = nodes.filter((n) => n.completed || n.status === 'COMPLETED').length
  const activeCount = nodes.filter((n) => n.status === 'ACTIVE').length
  const pendingCount = nodes.length - completedCount - activeCount

  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M4.75 6.75A1.75 1.75 0 0 1 6.5 5h4.25c.93 0 1.79.465 2.31 1.24.52-.775 1.38-1.24 2.31-1.24h2.13a1.75 1.75 0 0 1 1.75 1.75v10.5A1.75 1.75 0 0 1 17.5 19.5h-2.13c-.93 0-1.79.465-2.31 1.24-.52-.775-1.38-1.24-2.31-1.24H6.5a1.75 1.75 0 0 1-1.75-1.75V6.75Z" />
          <path d="M12.5 7v13" />
        </svg>
        <span className={styles.title}>学习路径已生成</span>
      </div>

      {goal && <p className={styles.goal}>{goal}</p>}

      <div className={styles.stats}>
        <div className={styles.stat}>
          <span className={styles.statValue}>{nodes.length}</span>
          <span className={styles.statLabel}>总节点</span>
        </div>
        <div className={`${styles.stat} ${styles.statActive}`}>
          <span className={styles.statValue}>{activeCount}</span>
          <span className={styles.statLabel}>进行中</span>
        </div>
        <div className={`${styles.stat} ${styles.statCompleted}`}>
          <span className={styles.statValue}>{completedCount}</span>
          <span className={styles.statLabel}>已完成</span>
        </div>
        <div className={styles.stat}>
          <span className={styles.statValue}>{pendingCount}</span>
          <span className={styles.statLabel}>待学习</span>
        </div>
      </div>

      <div className={styles.nodes}>
        {nodes.slice(0, 3).map((node, i) => (
          <div key={node.kpId} className={styles.node}>
            <span className={`${styles.nodeIndex} ${node.completed || node.status === 'COMPLETED' ? styles.nodeDone : ''}`}>
              {node.completed || node.status === 'COMPLETED' ? '✓' : i + 1}
            </span>
            <span className={styles.nodeTitle}>{node.title}</span>
          </div>
        ))}
        {nodes.length > 3 && (
          <div className={styles.nodeMore}>+{nodes.length - 3} 个节点</div>
        )}
      </div>

      <button
        type="button"
        className={styles.viewBtn}
        onClick={() => navigate('/workspace/learning-path')}
      >
        查看完整学习路径
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M5 12h14M12 5l7 7-7 7" />
        </svg>
      </button>
    </div>
  )
}
