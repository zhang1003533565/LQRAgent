import styles from './WorkspacePage.module.css'

const summaryCards = [
  { label: '总节点数', value: '7', unit: '个', tone: 'indigo' },
  { label: '已完成', value: '2', unit: '个', tone: 'green' },
  { label: '进行中', value: '1', unit: '个', tone: 'blue' },
  { label: '未开始', value: '4', unit: '个', tone: 'slate' },
  { label: '预计完成时间', value: '2025-06-05', unit: '', tone: 'violet' },
] as const

const pathNodes = [
  { id: 1, title: '目标确认', status: '已完成', state: 'done', x: 24, y: 24 },
  { id: 2, title: '知识诊断', status: '已完成', state: 'done', x: 212, y: 24 },
  { id: 3, title: '核心概念学习', status: '进行中', state: 'active', x: 404, y: 24 },
  { id: 4, title: '例题训练', status: '未开始', state: 'pending', x: 86, y: 126 },
  { id: 5, title: '阶段测验', status: '未开始', state: 'pending', x: 296, y: 126 },
  { id: 6, title: '查漏补缺', status: '未开始', state: 'pending', x: 506, y: 126 },
  { id: 7, title: '总结提升', status: '未开始', state: 'pending', x: 296, y: 228 },
] as const

const resourceActions = [
  { label: '生成讲解资料', icon: 'doc' },
  { label: '生成练习题', icon: 'edit' },
  { label: '查看关联资源', icon: 'folder' },
] as const

const pathList = [
  { id: 1, title: '目标确认', status: '已完成', state: 'done' },
  { id: 2, title: '知识诊断', status: '已完成', state: 'done' },
  { id: 3, title: '核心概念学习', status: '进行中', state: 'active' },
  { id: 4, title: '例题训练', status: '未开始', state: 'pending' },
  { id: 5, title: '阶段测验', status: '未开始', state: 'pending' },
  { id: 6, title: '查漏补缺', status: '未开始', state: 'pending' },
  { id: 7, title: '总结提升', status: '未开始', state: 'pending' },
] as const

export default function LearningPathPage() {
  return (
    <section className={styles.page}>
      <div className={styles.heroGlow} />

      <header className={styles.topbar}>
        <div>
          <h1 className={styles.title}>学习路径</h1>
          <p className={styles.subtitle}>根据学习目标自动生成阶段化学习计划</p>
        </div>
        <div className={styles.topActions}>
          <button type="button" className={styles.secondaryBtn}>
            <span className={styles.btnIcon}>◔</span>
            恢复当前路径
          </button>
          <button type="button" className={styles.primaryBtn}>
            <span className={styles.btnIcon}>✦</span>
            重新生成路径
          </button>
        </div>
      </header>

      <div className={styles.layout}>
        <div className={styles.mainColumn}>
          <section className={styles.panel}>
            <div className={styles.generator}>
              <div className={styles.generatorHead}>
                <div className={styles.sectionTag}>◎ 输入学习目标</div>
              </div>
              <div className={styles.generatorRow}>
                <div className={styles.inputWrap}>
                  <label className={styles.inputLabel}>想扣：</label>
                  <input
                    className={styles.input}
                    value="两周内完成高等数学导数与微分章节复习"
                    readOnly
                  />
                </div>
                <div className={styles.selectWrap}>
                  <label className={styles.inputLabel}>学习周期</label>
                  <button type="button" className={styles.selectBtn}>
                    <span className={styles.selectIcon}>🗓</span>
                    2 周
                    <span className={styles.chevron}>⌄</span>
                  </button>
                </div>
                <button type="button" className={styles.generateBtn}>
                  <span className={styles.btnIcon}>⇪</span>
                  生成学习路径
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
            <div className={styles.flowCanvas}>
              <svg className={styles.flowLines} viewBox="0 0 720 360" aria-hidden="true">
                <path d="M142 84 H212" className={styles.lineDone} />
                <path d="M330 84 H404" className={styles.lineActive} />
                <path d="M514 84 C580 84 582 84 582 120 V142 C582 168 560 168 538 168 H510" className={styles.lineGhost} />
                <path d="M156 186 H86 C58 186 58 150 58 132 C58 114 58 102 84 102" className={styles.lineGhost} />
                <path d="M254 186 H296" className={styles.lineGhost} />
                <path d="M464 186 H506" className={styles.lineGhost} />
                <path d="M566 228 V254 C566 286 544 286 520 286 H424" className={styles.lineGhost} />
              </svg>

              {pathNodes.map((node) => (
                <article
                  key={node.id}
                  className={`${styles.nodeCard} ${styles[`node${node.state}`]}`}
                  style={{ left: `${node.x}px`, top: `${node.y}px` }}
                >
                  <div className={`${styles.nodeIndex} ${styles[`index${node.state}`]}`}>{node.id}</div>
                  <h3 className={styles.nodeTitle}>{node.title}</h3>
                  <p className={styles.nodeStatus}>
                    <span className={`${styles.statusDot} ${styles[`dot${node.state}`]}`} />
                    {node.status}
                  </p>
                </article>
              ))}
            </div>

            <div className={styles.flowHint}>
              <span className={styles.infoDot}>i</span>
              支持从当前路径恢复薄弱学习
            </div>
          </section>
        </div>

        <aside className={styles.sideColumn}>
          <section className={styles.sidePanel}>
            <div className={styles.sectionTag}>✣ 节点详情</div>
            <div className={styles.detailHeader}>
              <div>
                <div className={styles.detailMetaRow}>
                  <span className={styles.detailNumber}>3</span>
                  <h2 className={styles.detailTitle}>核心概念学习</h2>
                </div>
                <p className={styles.detailLead}>排序时长</p>
              </div>
              <span className={styles.progressPill}>进行中</span>
            </div>

            <div className={styles.infoList}>
              <div className={styles.infoItem}>
                <span className={styles.infoKey}>推荐时长</span>
                <span className={styles.infoValue}>约 4-6 小时</span>
              </div>
              <div className={styles.infoBlock}>
                <h3>学习目标</h3>
                <p>理解并掌握导数与微分的定义、性质及基本公式。</p>
              </div>
              <div className={styles.infoBlock}>
                <h3>前置知识</h3>
                <p>函数与极限的基本概念、初等函数的求导法则</p>
              </div>
              <div className={styles.infoBlock}>
                <h3>节点描述</h3>
                <p>通过系统学习导数与微分的核心概念，为后续应用打下坚实基础。</p>
              </div>
            </div>
          </section>

          <section className={styles.sidePanel}>
            <div className={styles.sectionTag}>▣ 资源生成</div>
            <div className={styles.resourceGrid}>
              {resourceActions.map((action) => (
                <button key={action.label} type="button" className={styles.resourceBtn}>
                  <span className={`${styles.resourceIcon} ${styles[`icon${action.icon}`]}`} />
                  <span>{action.label}</span>
                </button>
              ))}
            </div>
          </section>

          <section className={styles.sidePanel}>
            <div className={styles.sectionTag}>◫ 路径节点列表</div>
            <div className={styles.pathList}>
              {pathList.map((item) => (
                <div key={item.id} className={styles.pathItem}>
                  <div className={styles.pathLeft}>
                    <span className={`${styles.pathIndex} ${styles[`index${item.state}`]}`}>{item.id}</span>
                    <span className={styles.pathName}>{item.title}</span>
                  </div>
                  <div className={styles.pathRight}>
                    <span className={`${styles.statusDot} ${styles[`dot${item.state}`]}`} />
                    <span className={styles.pathStatus}>{item.status}</span>
                  </div>
                </div>
              ))}
            </div>
          </section>
        </aside>
      </div>
    </section>
  )
}
