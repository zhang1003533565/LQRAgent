import styles from './QuizPage.module.css'

const progressDots = Array.from({ length: 8 }, (_, index) => index < 3)

const answerCards = [
  { index: 1, state: 'done' as const },
  { index: 2, state: 'done' as const },
  { index: 3, state: 'current' as const },
  { index: 4, state: 'idle' as const },
  { index: 5, state: 'idle' as const },
  { index: 6, state: 'idle' as const },
  { index: 7, state: 'idle' as const },
  { index: 8, state: 'idle' as const },
  { index: 10, state: 'idle' as const },
] as const

const options = [
  { key: 'A', text: 'A. x + 3' },
  { key: 'B', text: 'B. 2x + 3', active: true },
  { key: 'C', text: 'C. 2x' },
  { key: 'D', text: 'D. x² + 3' },
] as const

const stats = [
  { label: '总题数', value: '10' },
  { label: '已作答', value: '2' },
  { label: '正确率', value: '--' },
  { label: '预计用时', value: '15 分钟' },
] as const

export default function QuizPage() {
  return (
    <section className={styles.page}>
      <div className={styles.glow} />

      <header className={styles.topbar}>
        <div>
          <h1 className={styles.title}>答题</h1>
          <p className={styles.subtitle}>根据当前学习路径节点进行题目练习与提交评估</p>
        </div>
        <div className={styles.topMeta}>M6 · 答题</div>
        <div className={styles.topActions}>
          <button type="button" className={styles.secondaryBtn}>
            <span className={styles.btnIcon}>⇄</span>
            切换题组
          </button>
          <button type="button" className={styles.primaryBtn}>
            <span className={styles.btnIcon}>✓</span>
            提交答案
          </button>
        </div>
      </header>

      <section className={styles.infoPanel}>
        <div className={styles.infoGrid}>
          <div className={styles.infoBlock}>
            <span className={styles.infoLabel}>当前路径:</span>
            <strong>高等数学（上） &gt; 学习路径</strong>
          </div>
          <div className={styles.infoDivider} />
          <div className={styles.infoBlock}>
            <span className={styles.infoLabel}>当前节点:</span>
            <strong className={styles.infoDot}>核心概念学习</strong>
          </div>
          <div className={styles.infoDivider} />
          <div className={styles.infoBlock}>
            <span className={styles.infoLabel}>题组目标:</span>
            <strong>检验导数与微分基础概念、公式与应用理解</strong>
          </div>
        </div>
      </section>

      <section className={styles.filterPanel}>
        <div className={styles.filterRow}>
          <button type="button" className={styles.selectBtn}>
            题目类型： 单选题
            <span className={styles.chevron}>⌄</span>
          </button>
          <button type="button" className={styles.selectBtn}>
            难度： 混合
            <span className={styles.chevron}>⌄</span>
          </button>
          <div className={styles.progressWrap}>
            <span className={styles.progressText}>
              当前进度 <strong>3</strong> / 10
            </span>
            <div className={styles.progressTrack} aria-hidden="true">
              {progressDots.map((active, index) => (
                <span
                  key={index}
                  className={active ? `${styles.progressBar} ${styles.progressBarActive}` : styles.progressBar}
                />
              ))}
            </div>
          </div>
        </div>
      </section>

      <div className={styles.layout}>
        <main className={styles.mainColumn}>
          <section className={styles.panel}>
            <div className={styles.questionHead}>
              <div className={styles.questionMeta}>
                <span className={styles.questionIndex}>第 3 题</span>
                <span className={styles.questionCount}>/ 共 10 题</span>
              </div>
              <span className={styles.levelBadge}>中等</span>
            </div>

            <h2 className={styles.questionTitle}>
              已知函数 <em>f(x) = x² + 3x</em>，则 <em>f′(x)</em> 等于下列哪一项？
            </h2>

            <div className={styles.optionList}>
              {options.map((option) => (
                <button
                  key={option.key}
                  type="button"
                  className={option.active ? `${styles.optionCard} ${styles.optionCardActive}` : styles.optionCard}
                >
                  <span className={styles.optionBadge}>{option.key}</span>
                  <span className={styles.optionText}>{option.text}</span>
                  {option.active ? <span className={styles.optionCheck}>✓</span> : null}
                </button>
              ))}
            </div>

            <div className={styles.actionBar}>
              <div className={styles.actionGroup}>
                <span className={styles.actionHint}>作答记录 / 标记</span>
                <button type="button" className={styles.inlineBtn}>
                  <span>🔖</span>
                  标记此题
                </button>
                <button type="button" className={styles.inlineBtn}>
                  <span>🕒</span>
                  稍后再答
                </button>
              </div>
            </div>

            <div className={styles.navRow}>
              <button type="button" className={styles.ghostBtn}>
                <span className={styles.btnIcon}>←</span>
                上一题
              </button>
              <button type="button" className={styles.primaryNavBtn}>
                下一题
                <span className={styles.btnIcon}>→</span>
              </button>
            </div>
          </section>

          <section className={styles.previewPanel}>
            <div className={styles.previewHead}>
              <h3 className={styles.previewTitle}>简答题预览</h3>
              <span className={styles.previewTag}>示例</span>
            </div>
            <p className={styles.previewQuestion}>设函数 y = ln x，求 dy 的表达式。</p>
            <p className={styles.previewHint}>
              提示：可回顾微分的定义 <em>dy = f′(x)dx</em> 以及导数求法。
            </p>
          </section>
        </main>

        <aside className={styles.sideColumn}>
          <section className={styles.panel}>
            <h2 className={styles.panelTitle}>答题卡</h2>
            <div className={styles.answerGrid}>
              {answerCards.map((card) => (
                <button
                  key={card.index}
                  type="button"
                  className={`${styles.answerItem} ${styles[`answer${card.state[0].toUpperCase()}${card.state.slice(1)}`]}`}
                >
                  {card.index}
                </button>
              ))}
            </div>

            <div className={styles.legendRow}>
              <span className={styles.legendItem}>
                <i className={`${styles.legendDot} ${styles.legendDone}`} />
                已完成
              </span>
              <span className={styles.legendItem}>
                <i className={`${styles.legendDot} ${styles.legendCurrent}`} />
                当前
              </span>
              <span className={styles.legendItem}>
                <i className={`${styles.legendDot} ${styles.legendIdle}`} />
                未作答
              </span>
            </div>
          </section>

          <section className={styles.panel}>
            <h2 className={styles.panelTitle}>本次练习统计</h2>
            <div className={styles.statsGrid}>
              {stats.map((item) => (
                <article key={item.label} className={styles.statCard}>
                  <span className={styles.statLabel}>{item.label}</span>
                  <strong className={styles.statValue}>{item.value}</strong>
                </article>
              ))}
            </div>
          </section>

          <section className={styles.panel}>
            <h2 className={styles.panelTitle}>提交后更新画像</h2>
            <div className={styles.summaryCard}>
              <div className={styles.summaryIcon}>Q</div>
              <div>
                <p className={styles.summaryText}>
                  提交答案后，系统将自动触发画像摘要更新，并更新学习画像概览。
                </p>
                <p className={styles.summaryApi}>触发接口： GET /api/profile/summary</p>
              </div>
            </div>
            <button type="button" className={styles.summaryBtn}>
              查看学习画像
            </button>
          </section>

          <section className={styles.tipPanel}>
            <span className={styles.tipBullet}>!</span>
            <p className={styles.tipText}>作答提示：支持选择后统一提交，提交后将自动更新画像结果。</p>
          </section>
        </aside>
      </div>
    </section>
  )
}
