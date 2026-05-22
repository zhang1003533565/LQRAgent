import { useState } from 'react'
import { getLearningPath, type PathNode } from '@/api/learningPath'
import styles from './LearningPathPanel.module.css'

/**
 * 右侧学习路径面板。
 * 输入学习目标 → 请求后端 → 展示路径节点列表。
 */
export default function LearningPathPanel() {
  const [goal, setGoal] = useState('')
  const [nodes, setNodes] = useState<PathNode[]>([])
  const [plan, setPlan] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleGenerate() {
    const g = goal.trim()
    if (!g) return
    setLoading(true)
    setError('')
    try {
      const data = await getLearningPath(g)
      setNodes(data.nodes)
      setPlan(data.planDescription)
    } catch {
      setError('路径生成失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.container}>
      <h3 className={styles.title}>学习路径</h3>

      <div className={styles.inputRow}>
        <input
          value={goal}
          onChange={(e) => setGoal(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleGenerate()}
          placeholder="输入学习目标"
          className={styles.input}
        />
        <button onClick={handleGenerate} disabled={loading} className={styles.btn}>
          {loading ? '...' : '生成'}
        </button>
      </div>

      {error && <p className={styles.error}>{error}</p>}

      {plan && <p className={styles.plan}>{plan}</p>}

      {nodes.length > 0 && (
        <ol className={styles.nodeList}>
          {nodes.map((node) => (
            <li
              key={node.kpId}
              className={`${styles.node} ${node.completed ? styles.completed : ''}`}
            >
              <span className={styles.dot} />
              <div>
                <p className={styles.nodeTitle}>{node.title}</p>
                {node.description && (
                  <p className={styles.nodeDesc}>{node.description}</p>
                )}
              </div>
            </li>
          ))}
        </ol>
      )}

      {nodes.length === 0 && !loading && (
        <p className={styles.empty}>输入学习目标后生成路径</p>
      )}
    </div>
  )
}
