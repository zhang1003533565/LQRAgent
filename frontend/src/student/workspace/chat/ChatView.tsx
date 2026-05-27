import styles from './ChatView.module.css'

const steps = [
  { label: '理解问题', status: 'done' },
  { label: '检索知识', status: 'done' },
  { label: '推理分析', status: 'done' },
  { label: '生成回答', status: 'active' },
  { label: '完成', status: 'pending' },
] as const

export default function ChatView() {
  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>聊天学习</h1>
      </header>

      <div className={styles.scrollBody}>
        <div className={styles.content}>
          <div className={styles.userRow}>
            <div className={styles.userBubble}>为什么微分方程的解可以用级数展开？</div>
            <div className={styles.userAvatar}>
              <svg viewBox="0 0 56 56" aria-hidden="true">
                <circle cx="28" cy="28" r="28" fill="url(#user-avatar-bg)" />
                <defs>
                  <linearGradient id="user-avatar-bg" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stopColor="#8fd3ff" />
                    <stop offset="100%" stopColor="#5f8dff" />
                  </linearGradient>
                </defs>
                <circle cx="28" cy="19" r="8" fill="#fff" />
                <path d="M14 44c2.8-8.4 8.1-12.5 14-12.5S39.2 35.6 42 44" fill="#fff" />
              </svg>
            </div>
            <span className={styles.userTime}>10:29</span>
          </div>

          <div className={styles.assistantRow}>
            <div className={styles.assistantAvatar}>
              <svg viewBox="0 0 56 56" aria-hidden="true">
                <circle cx="28" cy="28" r="28" fill="#e9f3ff" stroke="#9cc3ff" />
                <rect x="16" y="18" width="24" height="20" rx="8" fill="#2d78ff" />
                <circle cx="23" cy="28" r="2" fill="#fff" />
                <circle cx="33" cy="28" r="2" fill="#fff" />
                <path d="M24 34h8" stroke="#fff" strokeWidth="2" strokeLinecap="round" />
                <path
                  d="M28 12v6M19 18l-3-3M37 18l3-3"
                  stroke="#1e3257"
                  strokeWidth="2"
                  strokeLinecap="round"
                />
              </svg>
            </div>

            <div className={styles.answerCard}>
              <p className={styles.answerText}>
                这是因为在某些条件下，微分方程的解在某一点的邻域内是解析的。根据柯西-哈达玛定理
                （Cauchy-Hadamard Theorem），如果解是解析函数，则可以表示为幂级数的形式……
              </p>

              <section className={styles.stepsCard}>
                <h2 className={styles.cardTitle}>智能体步骤</h2>
                <div className={styles.timeline}>
                  <div className={styles.timelineTrack} aria-hidden="true">
                    <div className={styles.timelineProgress} />
                  </div>
                  <div className={styles.timelineSteps}>
                    {steps.map((step) => (
                      <div key={step.label} className={styles.timelineStep}>
                        <span
                          className={
                            step.status === 'done'
                              ? `${styles.stepDot} ${styles.stepDone}`
                              : step.status === 'active'
                                ? `${styles.stepDot} ${styles.stepActive}`
                                : `${styles.stepDot} ${styles.stepPending}`
                          }
                        >
                          {step.status === 'done' ? (
                            <svg viewBox="0 0 20 20" aria-hidden="true">
                              <path
                                d="M5 10.5 8.5 14 15 7.5"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2.2"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                              />
                            </svg>
                          ) : null}
                        </span>
                        <span className={styles.stepLabel}>{step.label}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </section>
              <span className={styles.answerTime}>10:30</span>
            </div>
          </div>

          <section className={styles.analysisGrid}>
            <article className={styles.infoCard}>
              <h2 className={styles.cardHeading}>解析说明（Markdown）</h2>
              <div className={styles.markdownBlock}>
                <p>
                  设 <strong>y(x)=Σ[n=0→∞] aₙ(x - x₀)ⁿ</strong>，将其代入微分方程，若系数满足关于
                  <strong>aₙ</strong> 的递推关系，则可确定解的形式。
                </p>
                <ul className={styles.markdownList}>
                  <li>适用于线性微分方程</li>
                  <li>在收敛半径内成立</li>
                </ul>
              </div>
            </article>

            <article className={styles.chartCard}>
              <h2 className={styles.cardHeading}>级数展开示意图</h2>
              <div className={styles.chartWrap}>
                <svg viewBox="0 0 360 250" className={styles.chartSvg} aria-hidden="true">
                  <path d="M60 200H310" stroke="#2f3b52" strokeWidth="1.8" />
                  <path d="M100 220V28" stroke="#2f3b52" strokeWidth="1.8" />
                  <path d="M310 200l-7-4v8l7-4Z" fill="#2f3b52" />
                  <path d="M100 28l-4 7h8l-4-7Z" fill="#2f3b52" />
                  <path
                    d="M160 40C120 112 110 145 65 200"
                    fill="none"
                    stroke="#61d7c4"
                    strokeWidth="2.8"
                    strokeDasharray="10 8"
                  />
                  <path
                    d="M240 72C214 126 176 136 86 182"
                    fill="none"
                    stroke="#ecc15a"
                    strokeWidth="2.8"
                    strokeDasharray="10 8"
                  />
                  <path
                    d="M292 148C238 118 176 124 116 170"
                    fill="none"
                    stroke="#80b1ff"
                    strokeWidth="2.8"
                    strokeDasharray="10 8"
                  />
                  <path
                    d="M60 148C98 102 134 96 172 120C202 138 234 170 290 110"
                    fill="none"
                    stroke="#4385ff"
                    strokeWidth="3.4"
                  />
                  <path d="M180 196V204" stroke="#2f3b52" strokeWidth="1.8" />
                  <text x="174" y="224" className={styles.axisText}>
                    x₀
                  </text>
                  <text x="80" y="24" className={styles.axisText}>
                    y
                  </text>
                  <text x="300" y="220" className={styles.axisText}>
                    x
                  </text>
                  <g className={styles.legendText}>
                    <line x1="258" y1="38" x2="280" y2="38" stroke="#4385ff" strokeWidth="2.8" />
                    <text x="292" y="42">n = 1</text>
                    <line
                      x1="258"
                      y1="68"
                      x2="280"
                      y2="68"
                      stroke="#61d7c4"
                      strokeWidth="2.8"
                      strokeDasharray="8 7"
                    />
                    <text x="292" y="72">n = 2</text>
                    <line
                      x1="258"
                      y1="98"
                      x2="280"
                      y2="98"
                      stroke="#ecc15a"
                      strokeWidth="2.8"
                      strokeDasharray="8 7"
                    />
                    <text x="292" y="102">n = 3</text>
                    <line
                      x1="258"
                      y1="128"
                      x2="280"
                      y2="128"
                      stroke="#80b1ff"
                      strokeWidth="2.8"
                      strokeDasharray="8 7"
                    />
                    <text x="292" y="132">n = 4</text>
                  </g>
                </svg>
              </div>
            </article>

            <article className={styles.resourceCard}>
              <h2 className={styles.cardHeading}>相关资料</h2>
              <div className={styles.fileCard}>
                <div className={styles.fileIcon}>PDF</div>
                <div className={styles.fileMeta}>
                  <strong>微分方程与级数解.pdf</strong>
                  <span>1.2 MB</span>
                </div>
                <button type="button" className={styles.downloadButton} aria-label="下载资料">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path
                      d="M12 5v9m0 0 4-4m-4 4-4-4M6 17.5h12"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="1.9"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </button>
              </div>

              <h3 className={styles.subHeading}>生成图像</h3>
              <div className={styles.previewCard}>
                <div className={styles.formulaPreview}>
                  <span>y'' + p(x)y' + q(x)y = 0</span>
                  <span>y = Σ aₙ(x - x₀)ⁿ</span>
                </div>
                <button type="button" className={styles.zoomButton}>
                  查看大图
                </button>
              </div>
            </article>
          </section>

          <div className={styles.actions}>
            <button type="button" className={styles.actionButton}>
              <span className={styles.actionIcon}>⎘</span>
              <span>复制</span>
            </button>
            <button type="button" className={styles.actionButton}>
              <span className={styles.actionIcon}>👍</span>
              <span>点赞</span>
            </button>
            <button type="button" className={styles.actionButton}>
              <span className={styles.actionIcon}>👎</span>
              <span>不满意</span>
            </button>
            <button type="button" className={styles.actionButton}>
              <span className={styles.actionIcon}>···</span>
              <span>更多</span>
            </button>
          </div>
        </div>
      </div>

      <section className={styles.composer}>
        <div className={styles.composerInput}>输入你的问题...</div>
        <div className={styles.composerFooter}>
          <div className={styles.toolButtons}>
            <button type="button" className={styles.toolButton}>
              <span className={styles.toolGlyph}>📎</span>
            </button>
            <button type="button" className={styles.toolButtonWide}>
              <span className={styles.toolGlyph}>🖼️</span>
              <span>图片</span>
            </button>
            <button type="button" className={styles.toolButtonWide}>
              <span className={styles.toolGlyph}>✓</span>
              <span>工具</span>
            </button>
          </div>

          <button type="button" className={styles.sendButton} aria-label="发送消息">
            <svg viewBox="0 0 28 28" aria-hidden="true">
              <path d="M5 14 22 6l-5 16-4.5-5-4.5-3Z" fill="currentColor" />
            </svg>
          </button>
        </div>
      </section>
    </section>
  )
}
