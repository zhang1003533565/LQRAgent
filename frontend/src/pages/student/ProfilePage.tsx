import styles from './ProfilePage.module.css'

const scoreCards = [
  { title: '知识掌握', value: 82, status: '上升', icon: '🎓', tone: 'blue' },
  { title: '应用能力', value: 80, status: '上升', icon: '◔', tone: 'violet' },
  { title: '学习效率', value: 75, status: '稳定', icon: '◷', tone: 'teal' },
  { title: '思维能力', value: 76, status: '上升', icon: '⌘', tone: 'green' },
  { title: '学习习惯', value: 70, status: '待提升', icon: '🗓', tone: 'orange' },
  { title: '学习态度', value: 85, status: '上升', icon: '☆', tone: 'amber' },
] as const

const radarPoints = [
  { label: '知识掌握', x: 180, y: 58 },
  { label: '应用能力', x: 288, y: 122 },
  { label: '学习效率', x: 258, y: 255 },
  { label: '思维能力', x: 180, y: 306 },
  { label: '学习习惯', x: 101, y: 255 },
  { label: '学习态度', x: 72, y: 122 },
] as const

const updates = [
  {
    time: '05-16 10:24',
    title: '完成答题练习，应用能力 +3',
    desc: '涉及 12 题，正确率 83%',
  },
  {
    time: '05-15 16:18',
    title: '上传资料分析，知识掌握 +2',
    desc: '分析文件：微数笔记.pdf',
  },
  {
    time: '05-13 21:30',
    title: '学习路径完成节点，学习效率 +1',
    desc: '完成“极限与连续”节点',
  },
  {
    time: '05-12 14:05',
    title: '参与讨论互动，思维能力 +1',
    desc: '参与问答 2 次，获得赞同 4 次',
  },
  {
    time: '05-11 09:47',
    title: '每日学习打卡，学习态度 +1',
    desc: '连续学习打卡第 7 天',
  },
] as const

const pathCards = [
  { title: '微积分应用专题', desc: '提升应用能力与建模思维', progress: 42, action: '前往路径', icon: '∠' },
  { title: '多元函数综合训练', desc: '强化解题技巧与思维能力', progress: 35, action: '继续练习', icon: '∫' },
  { title: '复习巩固计划', desc: '优化学习习惯，强化记忆', progress: 28, action: '查看资源', icon: '▤' },
] as const

const dimensions = [
  { label: '知识掌握', value: 82, tone: 'blue', icon: '🎓' },
  { label: '应用能力', value: 80, tone: 'violet', icon: '◔' },
  { label: '学习效率', value: 75, tone: 'teal', icon: '◷' },
  { label: '思维能力', value: 76, tone: 'green', icon: '⌘' },
  { label: '学习习惯', value: 70, tone: 'orange', icon: '🗓' },
  { label: '学习态度', value: 85, tone: 'amber', icon: '☆' },
] as const

const dimensionColumns = [dimensions.slice(0, 3), dimensions.slice(3, 6)] as const

function toneClass(tone: string) {
  return tone === 'blue'
    ? styles.toneBlue
    : tone === 'violet'
      ? styles.toneViolet
      : tone === 'teal'
        ? styles.toneTeal
        : tone === 'green'
          ? styles.toneGreen
          : tone === 'orange'
            ? styles.toneOrange
            : styles.toneAmber
}

export default function ProfilePage() {
  return (
    <section className={styles.page}>
      <div className={styles.glow} />

      <header className={styles.topbar}>
        <div>
          <h1 className={styles.title}>学习画像</h1>
          <p className={styles.subtitle}>基于你的学习数据，生成多维度能力画像与动态分析</p>
        </div>
        <div className={styles.topMeta}>M8 · 学习画像</div>
        <div className={styles.topActions}>
          <button type="button" className={styles.secondaryBtn}>
            <span className={styles.btnIcon}>↻</span>
            刷新画像
          </button>
          <button type="button" className={styles.secondaryBtn}>
            <span className={styles.btnIcon}>⇩</span>
            导出画像
          </button>
        </div>
      </header>

      <section className={styles.metricsRow}>
        <article className={`${styles.metricCard} ${styles.scoreCard}`}>
          <h2 className={styles.metricTitle}>综合评分</h2>
          <div className={styles.scoreWrap}>
            <strong className={styles.scoreValue}>78</strong>
            <span className={styles.scoreUnit}>/100</span>
          </div>
          <p className={styles.metricTrend}>最近 7 天较上次提升 2 分 ↑</p>
        </article>

        {scoreCards.map((card) => (
          <article key={card.title} className={styles.metricCard}>
            <div className={styles.metricHead}>
              <span className={`${styles.metricIcon} ${toneClass(card.tone)}`}>{card.icon}</span>
              <h2 className={styles.metricTitle}>{card.title}</h2>
            </div>
            <strong className={styles.metricValue}>{card.value}</strong>
            <p
              className={
                card.status === '待提升'
                  ? styles.metricWarn
                  : card.status === '稳定'
                    ? styles.metricStable
                    : styles.metricUp
              }
            >
              {card.status === '稳定' ? '→ ' : card.status === '待提升' ? '↓ ' : '↑ '}
              {card.status}
            </p>
          </article>
        ))}
      </section>

      <div className={styles.midGrid}>
        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>能力雷达图</h2>
          <div className={styles.radarArea}>
            <div className={styles.radarChartBlock}>
              <svg viewBox="0 0 430 360" className={styles.radarSvg} aria-hidden="true">
                <g stroke="#d7e4fb" strokeWidth="1.5" fill="none">
                  <polygon points="180,28 321,110 287,272 180,334 73,272 39,110" />
                  <polygon points="180,74 283,132 259,248 180,294 101,248 77,132" />
                  <polygon points="180,116 251,156 234,235 180,265 126,235 109,156" />
                  <polygon points="180,154 220,176 211,220 180,237 149,220 140,176" />
                <line x1="180" y1="28" x2="180" y2="334" />
                <line x1="39" y1="110" x2="321" y2="272" />
                <line x1="321" y1="110" x2="39" y2="272" />
              </g>
              <polygon
                points="180,58 262,129 248,232 180,274 114,230 96,154"
                fill="rgba(47,120,255,0.18)"
                stroke="#2f78ff"
                strokeWidth="3"
                />
                {radarPoints.map((point) => (
                  <g key={point.label}>
                    <circle cx={point.x} cy={point.y} r="5" fill="#2f78ff" />
                  </g>
                ))}
                <text x="180" y="86" textAnchor="middle" className={styles.radarValueLabel}>
                  100
                </text>
              <text x="180" y="128" textAnchor="middle" className={styles.radarValueLabel}>
                50
              </text>
                <text x="180" y="170" textAnchor="middle" className={styles.radarValueLabel}>
                  25
                </text>
              </svg>
              <div className={styles.radarLegend}>
                {radarPoints.map((point) => (
                  <span key={point.label} className={styles.radarLegendItem}>
                    <span className={styles.radarLegendDot} />
                    {point.label}
                  </span>
                ))}
              </div>
            </div>

            <div className={styles.radarSummary}>
              <h3 className={styles.summaryTitle}>画像摘要</h3>
              <span className={styles.summaryPill}>理解应用型</span>
              <p className={styles.summaryText}>
                你在知识掌握与学习态度方面表现优秀，应用能力与思维能力稳步提升。学习效率与学习习惯仍有提升空间。
              </p>

              <div className={styles.completionHead}>
                <span className={styles.completionLabel}>画像完整度</span>
                <strong className={styles.completionValue}>92%</strong>
              </div>
              <div className={styles.completionTrack}>
                <span className={styles.completionFill} style={{ width: '92%' }} />
              </div>
              <p className={styles.completionHint}>
                数据基于最近 30 天学习行为
                <span className={styles.hintInfo}>i</span>
              </p>
            </div>
          </div>
        </section>

        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>画像解读</h2>
          <div className={styles.insightBlock}>
            <div className={styles.insightHead}>
              <span className={`${styles.insightIcon} ${styles.toneGreen}`}>👍</span>
              <h3>当前优势</h3>
            </div>
            <ul className={styles.insightList}>
              <li>学习态度积极，持续性强，任务完成度高</li>
              <li>知识掌握扎实，基础概念理解较好</li>
            </ul>
          </div>
          <div className={styles.insightBlock}>
            <div className={styles.insightHead}>
              <span className={`${styles.insightIcon} ${styles.toneOrange}`}>⚠</span>
              <h3>薄弱环节</h3>
            </div>
            <ul className={styles.insightList}>
              <li>学习习惯需进一步优化，如复习频率不足</li>
              <li>学习效率偶有波动，专注时长不稳定</li>
            </ul>
          </div>
          <div className={styles.insightBlock}>
            <div className={styles.insightHead}>
              <span className={`${styles.insightIcon} ${styles.toneBlue}`}>💡</span>
              <h3>学习建议</h3>
            </div>
            <ul className={styles.insightList}>
              <li>建议制定固定复习计划，巩固学习习惯</li>
              <li>采用番茄学习法，提升专注度与效率</li>
            </ul>
          </div>
          <div className={styles.insightBlock}>
            <div className={styles.insightHead}>
              <span className={`${styles.insightIcon} ${styles.toneViolet}`}>⚑</span>
              <h3>推荐下一步</h3>
            </div>
            <ul className={styles.insightList}>
              <li>完成微积分应用专题学习路径</li>
              <li>进行中等难度综合练习，提升应用能力</li>
            </ul>
          </div>
        </section>

        <section className={styles.panel}>
          <div className={styles.timelineHead}>
            <h2 className={styles.panelTitle}>最近更新</h2>
            <span className={styles.timelineStatus}>已接收 profile_patch</span>
          </div>
          <div className={styles.timeline}>
            {updates.map((item) => (
              <article key={item.time} className={styles.timelineItem}>
                <span className={styles.timelineDot} />
                <div>
                  <p className={styles.timelineTime}>{item.time}</p>
                  <h3 className={styles.timelineTitle}>{item.title}</h3>
                  <p className={styles.timelineDesc}>{item.desc}</p>
                </div>
              </article>
            ))}
          </div>
          <button type="button" className={styles.linkBtn}>
            查看全部更新
            <span className={styles.linkArrow}>›</span>
          </button>
        </section>
      </div>

      <div className={styles.bottomGrid}>
        <section className={styles.panel}>
          <div className={styles.bottomHeadCompact}>
            <h2 className={styles.panelTitle}>
              关联学习路径
              <span className={styles.bottomMetaInline}>（为你推荐）</span>
            </h2>
          </div>
          <div className={styles.pathGrid}>
            {pathCards.map((card) => (
              <article key={card.title} className={styles.pathCard}>
                <div className={styles.pathTop}>
                  <span className={styles.pathIcon}>{card.icon}</span>
                  <div>
                    <h3>{card.title}</h3>
                    <p>{card.desc}</p>
                  </div>
                </div>
                <div className={styles.pathBottom}>
                  <div className={styles.pathProgress}>
                    <div className={styles.progressHead}>
                      <span>进度 {card.progress}%</span>
                    </div>
                    <div className={styles.progressTrack}>
                      <span className={styles.progressFill} style={{ width: `${card.progress}%` }} />
                    </div>
                  </div>
                  <button type="button" className={styles.pathBtn}>
                    {card.action}
                  </button>
                </div>
              </article>
            ))}
          </div>
        </section>

        <section className={styles.panel}>
          <div className={styles.bottomHeadCompact}>
            <h2 className={styles.panelTitle}>
              六维度概览
              <span className={styles.bottomMetaInline}>（侧栏摘要）</span>
            </h2>
          </div>
          <div className={styles.dimensionGrid}>
            {dimensionColumns.map((column, columnIndex) => (
              <div key={columnIndex} className={styles.dimensionColumn}>
                {column.map((item) => (
                  <div key={item.label} className={styles.dimensionRow}>
                    <div className={styles.dimensionLabel}>
                      <span className={`${styles.dimensionIcon} ${toneClass(item.tone)}`}>{item.icon}</span>
                      <span>{item.label}</span>
                    </div>
                    <div className={styles.dimensionTrack}>
                      <span className={`${styles.dimensionFill} ${toneClass(item.tone)}`} style={{ width: `${item.value}%` }} />
                    </div>
                    <strong className={styles.dimensionValue}>{item.value}</strong>
                  </div>
                ))}
              </div>
            ))}
          </div>
          <p className={styles.dimensionHint}>
            完整画像请查看上方雷达图与解读
            <span className={styles.hintInfo}>i</span>
          </p>
        </section>
      </div>
    </section>
  )
}
