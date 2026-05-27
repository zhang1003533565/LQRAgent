import styles from './LearningResourcesPage.module.css'

const tabs = [
  { label: '讲义', active: true, icon: 'doc' },
  { label: '代码', active: false, icon: 'code' },
  { label: '题目列表', active: false, icon: 'list' },
  { label: '示意图', active: false, icon: 'image' },
  { label: '短视频', active: false, icon: 'video' },
] as const

const resourceMenu = [
  { title: '讲义内容总览', type: 'Markdown', state: 'active' },
  { title: '核心公式讲解', type: 'Markdown', state: 'done' },
  { title: 'Python 求导示例代码', type: 'Code', state: 'done' },
  { title: '导数练习题 1-5', type: '题目', state: 'done' },
  { title: '函数变化示意图', type: '图片', state: 'done' },
  { title: '微课短视频（即将开放）', type: 'P4', state: 'pending', badge: '即将开放' },
] as const

const exerciseRows = [
  { id: 1, title: '求函数 f(x) = x³ - 3x² + 2 的导函数。', level: '简单' },
  { id: 2, title: '求函数 y = ln x + x² 的导函数。', level: '中等' },
  { id: 3, title: '已知 y = sin x / x，求 y′。', level: '困难' },
] as const

const stats = [
  { label: '已生成资源', value: '12', tone: 'blue' },
  { label: '讲义', value: '3', tone: 'indigo' },
  { label: '代码', value: '2', tone: 'cyan' },
  { label: '题目', value: '5', tone: 'violet' },
  { label: '示意图', value: '2', tone: 'green' },
] as const

function ResourceIcon({ kind }: { kind: string }) {
  return <span className={`${styles.iconBadge} ${styles[`icon${kind}`]}`} />
}

export default function LearningResourcesPage() {
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
          <button type="button" className={styles.secondaryBtn}>
            <span className={styles.btnIcon}>↻</span>
            刷新资源
          </button>
          <button type="button" className={styles.primaryBtn}>
            <span className={styles.btnIcon}>✦</span>
            重新生成资源
          </button>
        </div>
      </header>

      <section className={styles.toolbarPanel}>
        <div className={styles.currentInfo}>
          <div className={styles.infoBlock}>
            <span className={styles.infoLabel}>当前路径：</span>
            <strong>高等数学（上） &gt; 学习路径</strong>
          </div>
          <div className={styles.infoDivider} />
          <div className={styles.infoBlock}>
            <span className={styles.infoLabel}>当前节点：</span>
            <strong>核心概念学习</strong>
          </div>
          <div className={styles.infoDivider} />
          <div className={styles.infoBlock}>
            <span className={styles.infoLabel}>学习目标：</span>
            <span>理解并掌握导数与微分的定义、性质及基本公式</span>
          </div>
        </div>

        <div className={styles.filterRow}>
          <button type="button" className={styles.selectBtn}>
            核心概念学习
            <span className={styles.chevron}>⌄</span>
          </button>
          <div className={styles.searchBox}>
            <span className={styles.searchIcon}>⌕</span>
            <span>搜索资源（如：导数公式、求导代码 等）</span>
          </div>
          <div className={styles.tabGroup}>
            {tabs.map((tab) => (
              <button
                key={tab.label}
                type="button"
                className={tab.active ? `${styles.tabBtn} ${styles.tabActive}` : styles.tabBtn}
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
              {resourceMenu.map((item) => (
                <button
                  key={item.title}
                  type="button"
                  className={
                    item.state === 'active' ? `${styles.menuItem} ${styles.menuItemActive}` : styles.menuItem
                  }
                >
                  <div className={styles.menuLeft}>
                    <ResourceIcon kind="doc" />
                    <div>
                      <p className={styles.menuTitle}>{item.title}</p>
                      <span className={styles.menuType}>{item.type}</span>
                    </div>
                  </div>
                  <div className={styles.menuRight}>
                    {item.badge ? <span className={styles.pendingBadge}>{item.badge}</span> : null}
                    <span className={`${styles.menuDot} ${styles[`dot${item.state}`]}`} />
                  </div>
                </button>
              ))}
            </div>
          </section>
        </aside>

        <main className={styles.contentColumn}>
          <section className={styles.panel}>
            <div className={styles.articleHead}>
              <h2 className={styles.articleTitle}>导数与微分基础讲义</h2>
            </div>

            <div className={styles.articleBlock}>
              <h3>一、核心概念</h3>
              <ul>
                <li>导数：函数在某点的瞬时变化率，反映函数的局部变化趋势。</li>
                <li>微分：函数在某点处的线性近似，适用于近似计算与误差分析。</li>
              </ul>
            </div>

            <div className={styles.articleBlock}>
              <h3>二、常用公式</h3>
              <div className={styles.formulaCard}>
                <p>导数定义：f′(x) = limₕ→0 (f(x+h)-f(x)) / h</p>
                <p>幂函数求导：(xⁿ)′ = nxⁿ⁻¹</p>
                <p>链式法则：[(f(g(x)))]′ = f′(g(x)) · g′(x)</p>
              </div>
            </div>

            <div className={styles.tipCard}>
              <strong>★ 三、学习建议</strong>
              <p>建议先理解导数的几何意义，再熟记公式并多做练习题巩固。</p>
            </div>

            <div className={styles.codeCard}>
              <div className={styles.codeHead}>
                <h3>示例代码</h3>
                <button type="button" className={styles.copyBtn}>
                  复制
                </button>
              </div>
              <pre className={styles.codeBlock}>{`import sympy as sp
x = sp.symbols('x')
f = x**3 - 2*x**2 + x
f_prime = sp.diff(f, x)
print("f(x) =", f)
print("f'(x) =", f_prime)

# 在 x=2 处求值
x_val = 2
f_prime_val = f_prime.subs(x, x_val)
print("f'(2) =", f_prime_val)`}</pre>
            </div>

            <div className={styles.exerciseCard}>
              <div className={styles.exerciseHead}>
                <h3>题目列表</h3>
              </div>
              <div className={styles.exerciseList}>
                {exerciseRows.map((row) => (
                  <div key={row.id} className={styles.exerciseRow}>
                    <div className={styles.exerciseMain}>
                      <span className={styles.exerciseIndex}>{row.id}</span>
                      <span className={styles.exerciseTitle}>{row.title}</span>
                    </div>
                    <div className={styles.exerciseMeta}>
                      <span className={`${styles.levelTag} ${styles[`level${row.level}`]}`}>{row.level}</span>
                      <button type="button" className={styles.startBtn}>
                        开始练习
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </section>
        </main>

        <aside className={styles.sideColumn}>
          <section className={styles.panel}>
            <h2 className={styles.panelTitle}>示意图预览</h2>
            <div className={styles.chartCard}>
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
