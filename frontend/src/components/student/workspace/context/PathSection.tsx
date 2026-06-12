import { useState } from 'react'
import { getLearningPath } from '@/api/student/learningPath'
import { usePathStore } from '@/utils/store/pathStore'
import { useArtifactStore } from '@/utils/store/artifactStore'
import styles from './PathSection.module.css'

export default function PathSection() {
  const [goalInput, setGoalInput] = useState('')
  const {
    goal,
    planDescription,
    nodes,
    selectedKpId,
    loading,
    setLoading,
    setPath,
    selectNode,
  } = usePathStore()
  const setActiveKpId = useArtifactStore((s) => s.setActiveKpId)
  const [error, setError] = useState('')

  async function handleGenerate() {
    const g = goalInput.trim()
    if (!g) return
    setLoading(true)
    setError('')
    try {
      const data = await getLearningPath(g)
      setPath(data)
      setGoalInput(data.goal)
    } catch {
      setError('路径生成失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  async function handleNodeClick(kpId: string) {
    selectNode(kpId)
    setActiveKpId(kpId)
  }

  return (
    <div className={styles.page}>
      <div className={styles.inputRow}>
        <input
          value={goalInput}
          onChange={(e) => setGoalInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleGenerate()}
          placeholder="输入学习目标，如：学习 Python 装饰器"
          className={styles.input}
        />
        <button
          type="button"
          onClick={handleGenerate}
          disabled={loading}
          className={styles.btn}
        >
          {loading ? '生成中...' : '生成路径'}
        </button>
      </div>

      {error && <p className={styles.error}>{error}</p>}
      {goal && <p className={styles.goalTag}>当前目标：{goal}</p>}
      {planDescription && <p className={styles.plan}>{planDescription}</p>}

      {nodes.length > 0 ? (
        <ol className={styles.nodeList}>
          {nodes.map((node) => (
            <li key={node.kpId}>
              <button
                type="button"
                className={`${styles.nodeBtn} ${
                  selectedKpId === node.kpId ? styles.selected : ''
                } ${node.completed ? styles.completed : ''}`}
                onClick={() => handleNodeClick(node.kpId)}
              >
                <span className={styles.dot} />
                <div>
                  <p className={styles.nodeTitle}>{node.title}</p>
                  {node.description && (
                    <p className={styles.nodeDesc}>{node.description}</p>
                  )}
                </div>
              </button>
            </li>
          ))}
        </ol>
      ) : (
        !loading && <p className={styles.empty}>输入学习目标后生成路径</p>
      )}
    </div>
  )
}
