import { useState, useEffect, useMemo } from 'react'
import { usePathStore } from '@/utils/store/pathStore'
import { getResources, generateResource } from '@/api/student/resources'
import type { LearningResource, ResourceType } from '@/utils/types/media-resource'
import styles from './LearningResourcesPage.module.css'

const TABS: { label: string; icon: string; type: ResourceType | 'ALL' }[] = [
  { label: '讲义', icon: 'doc', type: 'LESSON' },
  { label: '代码', icon: 'code', type: 'CODE_CASE' },
  { label: '题目列表', icon: 'list', type: 'QUIZ' },
  { label: '示意图', icon: 'image', type: 'ILLUSTRATION' },
  { label: '短视频', icon: 'video', type: 'VIDEO_CLIP' },
]

function ResourceIcon({ kind }: { kind: string }) {
  return <span className={`${styles.iconBadge} ${styles[`icon${kind}`]}`} />
}

export default function LearningResourcesPage() {
  const { selectedKpId, nodes } = usePathStore()
  const [resources, setResources] = useState<LearningResource[]>([])
  const [activeTab, setActiveTab] = useState<ResourceType | 'ALL'>('ALL')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [loading, setLoading] = useState(false)
  const [generating, setGenerating] = useState(false)

  const kpId = selectedKpId || nodes[0]?.kpId || ''
  const currentNode = nodes.find((n) => n.kpId === kpId)

  useEffect(() => {
    if (!kpId) return
    setLoading(true)
    getResources(kpId)
      .then((res) => {
        setResources(res)
        if (res.length > 0) setSelectedId(res[0].id)
      })
      .catch(() => setResources([]))
      .finally(() => setLoading(false))
  }, [kpId])

  const filtered = useMemo(
    () => (activeTab === 'ALL' ? resources : resources.filter((r) => r.resourceType === activeTab)),
    [resources, activeTab],
  )

  const selected = useMemo(() => resources.find((r) => r.id === selectedId) ?? null, [resources, selectedId])

  const stats = useMemo(() => {
    const counts: Record<string, number> = { total: resources.length }
    for (const r of resources) {
      counts[r.resourceType] = (counts[r.resourceType] || 0) + 1
    }
    return [
      { label: '已生成资源', value: String(counts.total), tone: 'blue' },
      { label: '讲义', value: String(counts.LESSON || 0), tone: 'indigo' },
      { label: '代码', value: String(counts.CODE_CASE || 0), tone: 'cyan' },
      { label: '题目', value: String(counts.QUIZ || 0), tone: 'violet' },
      { label: '示意图', value: String(counts.ILLUSTRATION || 0), tone: 'green' },
    ]
  }, [resources])

  const menuItems = useMemo(() => {
    return filtered.map((r, i) => ({
      id: r.id,
      title: r.title,
      type: r.resourceType,
      state: r.id === selectedId ? 'active' : i === 0 && selectedId === null ? 'active' : 'done',
    }))
  }, [filtered, selectedId])

  async function handleGenerate(type: ResourceType) {
    if (!kpId || generating) return
    setGenerating(true)
    try {
      const res = await generateResource({ kpId, resourceType: type })
      setResources((prev) => [...prev, res])
      setSelectedId(res.id)
    } catch {
      // best-effort
    } finally {
      setGenerating(false)
    }
  }

  function renderContent(r: LearningResource) {
    if (r.resourceType === 'CODE_CASE') {
      return (
        <div className={styles.codeCard}>
          <div className={styles.codeHead}>
            <h3>{r.title}</h3>
            <button type="button" className={styles.copyBtn} onClick={() => navigator.clipboard.writeText(r.content || '')}>
              复制
            </button>
          </div>
          <pre className={styles.codeBlock}>{r.content || '// 暂无代码内容'}</pre>
        </div>
      )
    }

    if (r.resourceType === 'QUIZ') {
      const lines = (r.content || '').split('\n').filter(Boolean)
      return (
        <div className={styles.exerciseCard}>
          <div className={styles.exerciseHead}>
            <h3>{r.title}</h3>
          </div>
          <div className={styles.exerciseList}>
            {lines.map((line, i) => (
              <div key={i} className={styles.exerciseRow}>
                <div className={styles.exerciseMain}>
                  <span className={styles.exerciseIndex}>{i + 1}</span>
                  <span className={styles.exerciseTitle}>{line}</span>
                </div>
                <div className={styles.exerciseMeta}>
                  <button type="button" className={styles.startBtn}>
                    开始练习
                  </button>
                </div>
              </div>
            ))}
            {lines.length === 0 && (
              <p style={{ color: '#8b9ab6', fontSize: 13, padding: '8px 0' }}>暂无题目内容</p>
            )}
          </div>
        </div>
      )
    }

    if (r.resourceType === 'ILLUSTRATION' && r.mediaUrl) {
      return (
        <div>
          <h3 style={{ margin: '0 0 12px', fontSize: 16, fontWeight: 800, color: '#1f3d71' }}>{r.title}</h3>
          <img src={r.mediaUrl} alt={r.title} style={{ width: '100%', borderRadius: 14 }} />
        </div>
      )
    }

    // LESSON, SUMMARY, EXTENDED_READING, etc. — render as markdown
    return (
      <div>
        <div className={styles.articleHead}>
          <h2 className={styles.articleTitle}>{r.title}</h2>
        </div>
        <div className={styles.articleBlock}>
          <div style={{ fontSize: 14, lineHeight: 1.8, color: '#526989', whiteSpace: 'pre-wrap' }}>
            {r.content || '暂无内容'}
          </div>
        </div>
      </div>
    )
  }

  if (!kpId) {
    return (
      <section className={styles.page}>
        <div className={styles.glow} />
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '60%', color: '#8b9ab6', fontSize: 16 }}>
          请先在「学习路径」页面生成学习路径，再查看资源
        </div>
      </section>
    )
  }

  return (
    <section className={styles.page}>
      <div className={styles.glow} />

      <header className={styles.topbar}>
        <div>
          <h1 className={styles.title}>学习资源展示</h1>
          <p className={styles.subtitle}>围绕当前学习路径节点自动生成多类型学习资源</p>
        </div>
        <div className={styles.topMeta}>◎ M5 · 资源展示</div>
        <div className={styles.topActions}>
          <button type="button" className={styles.secondaryBtn} onClick={() => kpId && getResources(kpId).then(setResources)} disabled={loading}>
            <span className={styles.btnIcon}>↻</span>
            刷新资源
          </button>
          <button
            type="button"
            className={styles.primaryBtn}
            onClick={() => handleGenerate('LESSON')}
            disabled={generating}
          >
            <span className={styles.btnIcon}>✦</span>
            {generating ? '生成中...' : '重新生成资源'}
          </button>
        </div>
      </header>

      <section className={styles.toolbarPanel}>
        <div className={styles.currentInfo}>
          <div className={styles.infoBlock}>
            <span className={styles.infoLabel}>当前路径：</span>
            <strong>{currentNode?.title || '—'} &gt; 学习路径</strong>
          </div>
          <div className={styles.infoDivider} />
          <div className={styles.infoBlock}>
            <span className={styles.infoLabel}>当前节点：</span>
            <strong>{currentNode?.title || '—'}</strong>
          </div>
          <div className={styles.infoDivider} />
          <div className={styles.infoBlock}>
            <span className={styles.infoLabel}>学习目标：</span>
            <span>{currentNode?.description || '理解并掌握核心知识点'}</span>
          </div>
        </div>

        <div className={styles.filterRow}>
          <button type="button" className={styles.selectBtn}>
            {currentNode?.title || '全部节点'}
            <span className={styles.chevron}>⌄</span>
          </button>
          <div className={styles.searchBox}>
            <span className={styles.searchIcon}>⌕</span>
            <span>搜索资源</span>
          </div>
          <div className={styles.tabGroup}>
            <button
              type="button"
              className={activeTab === 'ALL' ? `${styles.tabBtn} ${styles.tabActive}` : styles.tabBtn}
              onClick={() => setActiveTab('ALL')}
            >
              全部
            </button>
            {TABS.map((tab) => (
              <button
                key={tab.label}
                type="button"
                className={activeTab === tab.type ? `${styles.tabBtn} ${styles.tabActive}` : styles.tabBtn}
                onClick={() => setActiveTab(tab.type)}
              >
                <ResourceIcon kind={tab.icon} />
                {tab.label}
              </button>
            ))}
          </div>
        </div>
      </section>

      <div className={styles.layout}>
        <aside className={styles.menuColumn}>
          <section className={styles.panel}>
            <h2 className={styles.panelTitle}>资源目录</h2>
            <div className={styles.menuList}>
              {menuItems.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className={item.state === 'active' ? `${styles.menuItem} ${styles.menuItemActive}` : styles.menuItem}
                  onClick={() => setSelectedId(item.id)}
                >
                  <div className={styles.menuLeft}>
                    <ResourceIcon kind="doc" />
                    <div>
                      <p className={styles.menuTitle}>{item.title}</p>
                      <span className={styles.menuType}>{item.type}</span>
                    </div>
                  </div>
                  <div className={styles.menuRight}>
                    <span className={`${styles.menuDot} ${styles[`dot${item.state}`]}`} />
                  </div>
                </button>
              ))}
              {menuItems.length === 0 && !loading && (
                <p style={{ color: '#8b9ab6', fontSize: 13, padding: '8px 0' }}>暂无资源</p>
              )}
            </div>
          </section>
        </aside>

        <main className={styles.contentColumn}>
          <section className={styles.panel}>
            {loading ? (
              <div style={{ padding: 40, textAlign: 'center', color: '#8b9ab6' }}>加载资源中...</div>
            ) : selected ? (
              renderContent(selected)
            ) : (
              <div style={{ padding: 40, textAlign: 'center', color: '#8b9ab6' }}>选择左侧资源查看内容</div>
            )}
          </section>
        </main>

        <aside className={styles.sideColumn}>
          <section className={styles.panel}>
            <h2 className={styles.panelTitle}>示意图预览</h2>
            <div className={styles.chartCard}>
              {resources.find((r) => r.resourceType === 'ILLUSTRATION' && r.mediaUrl) ? (
                <img
                  src={resources.find((r) => r.resourceType === 'ILLUSTRATION' && r.mediaUrl)!.mediaUrl}
                  alt="示意图"
                  style={{ width: '100%', borderRadius: 12 }}
                />
              ) : (
                <svg viewBox="0 0 300 170" className={styles.chartSvg} aria-hidden="true">
                  <path d="M36 142H280" stroke="#1f3356" strokeWidth="1.8" />
                  <path d="M78 152V24" stroke="#1f3356" strokeWidth="1.8" />
                  <path d="M280 142l-6-4v8l6-4Z" fill="#1f3356" />
                  <path d="M78 24l-4 6h8l-4-6Z" fill="#1f3356" />
                  <path d="M44 104C70 36 122 118 164 88C188 70 218 18 252 28" fill="none" stroke="#2f78ff" strokeWidth="2.8" />
                  <path d="M48 112L242 52" fill="none" stroke="#ff5d52" strokeWidth="2.2" />
                  <circle cx="170" cy="74" r="4.2" fill="#1f3356" />
                  <text x="214" y="28" className={styles.chartText}>y = f(x)</text>
                  <text x="234" y="63" className={styles.chartText}>切线</text>
                  <text x="182" y="84" className={styles.chartText}>y - f(x₀) = f′(x₀)(x - x₀)</text>
                  <text x="130" y="150" className={styles.chartText}>x₀</text>
                  <text x="286" y="150" className={styles.chartText}>x</text>
                  <text x="86" y="24" className={styles.chartText}>y</text>
                  <text x="108" y="66" className={styles.chartText}>P(x₀, f(x₀))</text>
                </svg>
              )}
            </div>
          </section>

          <section className={styles.panel}>
            <div className={styles.videoHead}>
              <h2 className={styles.panelTitle}>短视频预览</h2>
              <span className={styles.videoBadge}>P4 / 即将开放</span>
            </div>
            <div className={styles.videoCard}>
              <div className={styles.videoPlay}>▶</div>
              <div className={styles.videoTimeline}>
                <span>00:00</span>
                <span>00:00</span>
              </div>
              <p className={styles.videoHint}>短视频生成功能暂未开放</p>
            </div>
          </section>

          <section className={styles.panel}>
            <h2 className={styles.panelTitle}>资源统计</h2>
            <div className={styles.statsGrid}>
              {stats.map((item) => (
                <article key={item.label} className={styles.statCard}>
                  <ResourceIcon kind="doc" />
                  <p className={styles.statLabel}>{item.label}</p>
                  <p className={styles.statValue}>{item.value}</p>
                </article>
              ))}
            </div>
            <button type="button" className={styles.exportBtn}>
              ⭳ 一键打包导出
            </button>
          </section>
        </aside>
      </div>
    </section>
  )
}
