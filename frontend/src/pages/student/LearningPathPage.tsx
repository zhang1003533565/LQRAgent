import { useState, useMemo, useCallback, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { usePathStore } from '@/utils/store/pathStore'
import { trackBehavior } from '@/utils/tracker'
import { useOrchestrator } from '@/utils/hooks/useOrchestrator'
import { navigateToWorkspace } from '@/utils/navigation/workspaceNav'
import styles from './WorkspacePage.module.css'



export default function LearningPathPage() {
  const navigate = useNavigate()
  const { start: startOrch, progress: orchProgress, running: orchRunning, error: orchError } = useOrchestrator()
  const { goal, planDescription, nodes, selectedKpId, loading, setPath, setLoading, selectNode, refresh, clearUpdates } =
    usePathStore()
  const [inputGoal, setInputGoal] = useState(goal || '学习Python基础语法，包括变量、数据类型、控制流和函数')
  const [cycle, setCycle] = useState('2周')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    clearUpdates()
    void refresh()
  }, [clearUpdates, refresh])

  const handleSelectNode = useCallback((kpId: string) => {
    trackBehavior({ kpId, action: 'view_path' })
    selectNode(kpId)
  }, [selectNode])

  const totalNodes = nodes.length
  const completedCount = nodes.filter((n) => n.completed || n.status === 'COMPLETED').length
  const activeCount = nodes.filter((n) => n.status === 'ACTIVE').length
  const pendingCount = totalNodes - completedCount - activeCount

  const summaryCards = useMemo(() => {
    if (totalNodes === 0) {
      return [
        { label: '总节点数', value: '—', unit: '', tone: 'indigo' },
        { label: '已完成', value: '—', unit: '', tone: 'green' },
        { label: '进行中', value: '—', unit: '', tone: 'blue' },
        { label: '未开始', value: '—', unit: '', tone: 'slate' },
        { label: '预计完成时间', value: '—', unit: '', tone: 'violet' },
      ]
    }
    const estimatedDays = Math.max(7, Math.ceil(pendingCount * 1.5))
    const d = new Date()
    d.setDate(d.getDate() + estimatedDays)
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    return [
      { label: '总节点数', value: String(totalNodes), unit: '个', tone: 'indigo' },
      { label: '已完成', value: String(completedCount), unit: '个', tone: 'green' },
      { label: '进行中', value: String(activeCount), unit: '个', tone: 'blue' },
      { label: '未开始', value: String(pendingCount), unit: '个', tone: 'slate' },
      { label: '预计完成时间', value: dateStr, unit: '', tone: 'violet' },
    ]
  }, [totalNodes, completedCount, activeCount, pendingCount])

  const selectedNode = useMemo(
    () => nodes.find((n) => n.kpId === selectedKpId) ?? null,
    [nodes, selectedKpId],
  )

  const nodeState = (n: (typeof nodes)[0]): 'done' | 'active' | 'pending' => {
    if (n.completed || n.status === 'COMPLETED') return 'done'
    if (n.status === 'ACTIVE') return 'active'
    return 'pending'
  }

  const nodeStatusText = (n: (typeof nodes)[0]) => {
    if (n.completed || n.status === 'COMPLETED') return '已完成'
    if (n.status === 'ACTIVE') return '进行中'
    return '未开始'
  }

  async function handleGenerate() {
    if (!inputGoal.trim()) return
    // 使用 Orchestrator 多 Agent 协作
    startOrch(inputGoal, cycle)
  }

  async function handleRestore() {
    setLoading(true)
    setError(null)
    try {
      const data = await getCurrentPath()
      if (data) setPath(data)
      else setError('暂无活跃学习路径')
    } catch {
      setError('获取当前路径失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className={styles.page}>
      <div className={styles.heroGlow} />

      <header className={styles.topbar}>
        <div>
          <h1 className={styles.title}>学习路径</h1>
          <p className={styles.subtitle}>根据学习目标自动生成阶段化学习计划</p>
        </div>
        <div className={styles.topActions}>
          <button type="button" className={styles.secondaryBtn} onClick={handleRestore} disabled={loading || orchRunning}>
            <span className={styles.btnIcon}>◔</span>
            恢复当前路径
          </button>
          <button type="button" className={styles.primaryBtn} onClick={handleGenerate} disabled={loading || orchRunning}>
            <span className={styles.btnIcon}>✦</span>
            {orchRunning ? 'Agent协作中...' : loading ? '生成中...' : '重新生成路径'}
          </button>
        </div>
      </header>

      {error && (
        <div style={{ padding: '0 16px 8px', color: '#e53e3e', fontSize: 14 }}>{error}</div>
      )}

      <div className={styles.layout}>
        <div className={styles.mainColumn}>
          <section className={styles.panel}>
            <div className={styles.generator}>
              <div className={styles.generatorHead}>
                <div className={styles.sectionTag}>◎ 输入学习目标</div>
              </div>
              <div className={styles.generatorRow}>
                <div className={styles.inputWrap}>
                  <label className={styles.inputLabel}>想学：</label>
                  <input
                    className={styles.input}
                    value={inputGoal}
                    onChange={(e) => setInputGoal(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleGenerate()}
                    placeholder="输入学习目标，如：Python 装饰器"
                  />
                </div>
                <div className={styles.selectWrap}>
                  <label className={styles.inputLabel}>学习周期</label>
                  <select
                    className={styles.selectBtn}
                    value={cycle}
                    onChange={(e) => setCycle(e.target.value)}
                    style={{ cursor: 'pointer', background: 'transparent', border: 'none', font: 'inherit', color: 'inherit', padding: '6px 12px' }}
                  >
                    <option value="1周">🗓 1 周</option>
                    <option value="2周">🗓 2 周</option>
                    <option value="4周">🗓 4 周</option>
                  </select>
                </div>
                <button
                  type="button"
                  className={styles.generateBtn}
                  onClick={handleGenerate}
                  disabled={loading || orchRunning}
                >
                  <span className={styles.btnIcon}>⇪</span>
                  {orchRunning ? 'Agent协作中...' : loading ? '生成中...' : '生成学习路径'}
                </button>
              </div>
            </div>
          </section>

          <section className={styles.panel}>
            <h2 className={styles.panelTitle}>当前路径概览</h2>
            <div className={styles.summaryGrid}>
              {summaryCards.map((card) => (
                <article key={card.label} className={styles.summaryCard}>
                  <div className={`${styles.summaryBadge} ${styles[`tone${card.tone}`]}`} />
                  <div>
                    <p className={styles.summaryLabel}>{card.label}</p>
                    <p className={styles.summaryValue}>
                      {card.value}
                      {card.unit ? <span>{card.unit}</span> : null}
                    </p>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section className={`${styles.panel} ${styles.flowPanel}`}>
            <div className={styles.flowCanvas} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '16px' }}>
              {nodes.length > 0 ? (
                nodes.map((node, i) => {
                  const ns = nodeState(node)
                  return (
                    <article
                      key={node.kpId}
                      className={`${styles.nodeCard} ${styles[`node${ns}`]}`}
                      onClick={() => handleSelectNode(node.kpId)}
                    >
                      <div className={`${styles.nodeIndex} ${styles[`index${ns}`]}`}>{node.order || i + 1}</div>
                      <h3 className={styles.nodeTitle}>{node.title}</h3>
                      <p className={styles.nodeStatus}>
                        <span className={`${styles.statusDot} ${styles[`dot${ns}`]}`} />
                        {nodeStatusText(node)}
                      </p>
                    </article>
                  )
                })
              ) : (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#8b9ab6', fontSize: 14 }}>
                  {orchRunning ? (
                <div style={{ textAlign: 'left', fontSize: '13px', lineHeight: '1.8' }}>
                  {orchProgress.map((p, i) => (
                    <div key={i} style={{ color: '#6b7280' }}>
                      <span style={{ color: '#3b82f6', fontWeight: 500 }}>[{p.agent}]</span> {p.message}
                    </div>
                  ))}
                  {orchError && <div style={{ color: '#ef4444' }}>错误: {orchError}</div>}
                  <div style={{ color: '#9ca3af', marginTop: '4px' }}>⏳ 多Agent协作中...</div>
                </div>
              ) : nodes.length > 0 ? '点击节点查看详情' : '输入学习目标后点击「生成学习路径」'}
                </div>
              )}
            </div>

            <div className={styles.flowHint}>
              <span className={styles.infoDot}>i</span>
              支持从当前路径恢复薄弱学习
            </div>
          </section>

          {planDescription && (
            <section className={styles.panel}>
              <h2 className={styles.panelTitle}>路径说明</h2>
              <p style={{ margin: 0, fontSize: 14, lineHeight: 1.7, color: '#51698f' }}>{planDescription}</p>
            </section>
          )}
        </div>

        <aside className={styles.sideColumn}>
          <section className={styles.sidePanel}>
            <div className={styles.sectionTag}>✣ 节点详情</div>
            {selectedNode ? (
              <>
                <div className={styles.detailHeader}>
                  <div>
                    <div className={styles.detailMetaRow}>
                      <span className={styles.detailNumber}>{selectedNode.order || nodes.indexOf(selectedNode) + 1}</span>
                      <h2 className={styles.detailTitle}>{selectedNode.title}</h2>
                    </div>
                    <p className={styles.detailLead}>预计时长</p>
                  </div>
                  <span className={styles.progressPill}>{nodeStatusText(selectedNode)}</span>
                </div>

                <div className={styles.infoList}>
                  <div className={styles.infoItem}>
                    <span className={styles.infoKey}>推荐时长</span>
                    <span className={styles.infoValue}>约 {Math.max(1, Math.ceil((selectedNode.order || 1) * 0.5))}-{Math.ceil((selectedNode.order || 1) * 0.8)} 小时</span>
                  </div>
                  <div className={styles.infoBlock}>
                    <h3>学习目标</h3>
                    <p>{selectedNode.description || `掌握「${selectedNode.title}」的核心概念与应用`}</p>
                  </div>
                  <div className={styles.infoBlock}>
                    <h3>前置知识</h3>
                    <p>{selectedNode.order > 1 ? `完成第 ${selectedNode.order - 1} 步「${nodes[selectedNode.order - 2]?.title ?? ''}」` : '无前置要求'}</p>
                  </div>
                  <div className={styles.infoBlock}>
                    <h3>节点描述</h3>
                    <p>{selectedNode.description || `系统学习「${selectedNode.title}」相关内容，理解核心概念并完成配套练习。`}</p>
                  </div>
                </div>
              </>
            ) : (
              <p style={{ color: '#8b9ab6', fontSize: 14, padding: '20px 0' }}>
                {loading ? '路径生成中...' : nodes.length > 0 ? '点击节点查看详情' : '请先生成学习路径'}
              </p>
            )}
          </section>

          <section className={styles.sidePanel}>
            <div className={styles.sectionTag}>▣ 资源生成</div>
            <div className={styles.resourceGrid}>
              {[
                { label: '生成讲解资料', icon: 'doc', path: '/workspace/resources' },
                { label: '生成练习题', icon: 'edit', path: '/workspace/quiz' },
                { label: '查看关联资源', icon: 'folder', path: '/workspace/resources' },
              ].map((action) => (
                <button key={action.label} type="button" className={styles.resourceBtn} disabled={!selectedNode}
                  onClick={() => {
                    if (selectedNode) navigateToWorkspace(navigate, action.path as '/workspace/resources' | '/workspace/quiz', selectedNode.kpId)
                  }}>
                  <span className={`${styles.resourceIcon} ${styles[`icon${action.icon}`]}`} />
                  <span>{action.label}</span>
                </button>
              ))}
            </div>
          </section>

          <section className={styles.sidePanel}>
            <div className={styles.sectionTag}>◫ 路径节点列表</div>
            <div className={styles.pathList}>
              {nodes.map((item, i) => {
                const ns = nodeState(item)
                return (
                  <div
                    key={item.kpId}
                    className={styles.pathItem}
                    style={{ cursor: 'pointer', opacity: item.kpId === selectedKpId ? 1 : 0.7 }}
                    onClick={() => selectNode(item.kpId)}
                  >
                    <div className={styles.pathLeft}>
                      <span className={`${styles.pathIndex} ${styles[`index${ns}`]}`}>{item.order || i + 1}</span>
                      <span className={styles.pathName}>{item.title}</span>
                    </div>
                    <div className={styles.pathRight}>
                      <span className={`${styles.statusDot} ${styles[`dot${ns}`]}`} />
                      <span className={styles.pathStatus}>{nodeStatusText(item)}</span>
                    </div>
                  </div>
                )
              })}
              {nodes.length === 0 && !loading && (
                <p style={{ color: '#8b9ab6', fontSize: 13, padding: '8px 0' }}>暂无路径节点</p>
              )}
            </div>
          </section>
        </aside>
      </div>
    </section>
  )
}
