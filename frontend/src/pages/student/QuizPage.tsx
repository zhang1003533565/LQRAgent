import { useState, useEffect, useMemo, useCallback } from 'react'
import { usePathStore } from '@/utils/store/pathStore'
import { getQuizQuestions, submitQuiz } from '@/api/student/quiz'
import type { LearningResource } from '@/utils/types/media-resource'
import QuizEmptyPage from './QuizEmptyPage'
import styles from './QuizPage.module.css'

interface Question {
  id: number
  title: string
  options: { key: string; text: string }[]
  resourceType: string
}

function parseQuestions(resources: LearningResource[]): Question[] {
  return resources.map((r, i) => {
    const lines = (r.content || '').split('\n').filter(Boolean)
    const opts: { key: string; text: string }[] = []
    for (const line of lines) {
      const m = line.match(/^([A-D])[.、)\s]\s*(.+)/)
      if (m) opts.push({ key: m[1], text: `${m[1]}. ${m[2]}` })
    }
    return {
      id: r.id || i + 1,
      title: opts.length > 0 ? lines.filter((l) => !l.match(/^[A-D][.、)\s]/))[0] || r.title : r.content || r.title,
      options: opts.length > 0
        ? opts
        : [
            { key: 'A', text: 'A. 选项 A' },
            { key: 'B', text: 'B. 选项 B' },
            { key: 'C', text: 'C. 选项 C' },
            { key: 'D', text: 'D. 选项 D' },
          ],
      resourceType: r.resourceType,
    }
  })
}

export default function QuizPage() {
  const { selectedKpId, nodes } = usePathStore()
  const [questions, setQuestions] = useState<Question[]>([])
  const [currentIdx, setCurrentIdx] = useState(0)
  const [answers, setAnswers] = useState<Record<number, string>>({})
  const [correctSet, setCorrectSet] = useState<Set<number>>(new Set())
  const [submitted, setSubmitted] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const kpId = selectedKpId || ''
  const currentNode = nodes.find((n) => n.kpId === kpId)

  useEffect(() => {
    if (!kpId) return
    setLoading(true)
    setError(null)
    getQuizQuestions(kpId)
      .then((res) => setQuestions(parseQuestions(res)))
      .catch(() => setError('加载题目失败'))
      .finally(() => setLoading(false))
  }, [kpId])

  const currentQuestion = questions[currentIdx]
  const total = questions.length
  const answeredCount = Object.keys(answers).length

  const stats = useMemo(() => {
    const correctCount = correctSet.size
    const accuracy = submitted && answeredCount > 0 ? `${Math.round((correctCount / answeredCount) * 100)}%` : '--'
    return [
      { label: '总题数', value: String(total || '—') },
      { label: '已作答', value: String(answeredCount) },
      { label: '正确率', value: accuracy },
      { label: '预计用时', value: `${Math.max(1, Math.ceil(total * 1.5))} 分钟` },
    ]
  }, [total, answeredCount, correctSet, submitted])

  const progressDots = useMemo(
    () => questions.map((_, i) => i < currentIdx || (i === currentIdx && answers[questions[i]?.id])),
    [questions, currentIdx, answers],
  )

  const answerCards = useMemo(
    () =>
      questions.map((q, i) => ({
        index: i + 1,
        state: (i === currentIdx ? 'current' : answers[q.id] ? 'done' : 'idle') as 'current' | 'done' | 'idle',
        id: q.id,
      })),
    [questions, currentIdx, answers],
  )

  const selectOption = useCallback(
    (key: string) => {
      if (!currentQuestion || submitted) return
      setAnswers((prev) => ({ ...prev, [currentQuestion.id]: key }))
    },
    [currentQuestion, submitted],
  )

  const handleSubmit = useCallback(async () => {
    if (!currentQuestion || !kpId) return
    setSubmitted(true)
    const correct = new Set<number>()
    for (const q of questions) {
      const ans = answers[q.id]
      if (ans) {
        try {
          const res = await submitQuiz({ kpId, resourceId: q.id, answer: ans })
          if (res.correct) correct.add(q.id)
        } catch {
          // best-effort
        }
      }
    }
    setCorrectSet(correct)
  }, [questions, answers, kpId, currentQuestion])

  if (!selectedKpId) {
    return <QuizEmptyPage />
  }

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
          <button type="button" className={styles.secondaryBtn} disabled={loading}>
            <span className={styles.btnIcon}>⇄</span>
            切换题组
          </button>
          <button
            type="button"
            className={styles.primaryBtn}
            onClick={handleSubmit}
            disabled={loading || submitted || answeredCount === 0}
          >
            <span className={styles.btnIcon}>✓</span>
            {submitted ? '已提交' : '提交答案'}
          </button>
        </div>
      </header>

      <section className={styles.infoPanel}>
        <div className={styles.infoGrid}>
          <div className={styles.infoBlock}>
            <span className={styles.infoLabel}>当前路径:</span>
            <strong>{currentNode?.title || '—'} &gt; 学习路径</strong>
          </div>
          <div className={styles.infoDivider} />
          <div className={styles.infoBlock}>
            <span className={styles.infoLabel}>当前节点:</span>
            <strong className={styles.infoDot}>{currentNode?.title || '—'}</strong>
          </div>
          <div className={styles.infoDivider} />
          <div className={styles.infoBlock}>
            <span className={styles.infoLabel}>题组目标:</span>
            <strong>检验当前知识点的理解与应用能力</strong>
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
              当前进度 <strong>{currentIdx + 1}</strong> / {total || '—'}
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
          {loading ? (
            <section className={styles.panel}>
              <div style={{ padding: 40, textAlign: 'center', color: '#8b9ab6' }}>加载题目中...</div>
            </section>
          ) : error ? (
            <section className={styles.panel}>
              <div style={{ padding: 40, textAlign: 'center', color: '#e53e3e' }}>{error}</div>
            </section>
          ) : currentQuestion ? (
            <section className={styles.panel}>
              <div className={styles.questionHead}>
                <div className={styles.questionMeta}>
                  <span className={styles.questionIndex}>第 {currentIdx + 1} 题</span>
                  <span className={styles.questionCount}>/ 共 {total} 题</span>
                </div>
                <span className={styles.levelBadge}>中等</span>
              </div>

              <h2 className={styles.questionTitle}>{currentQuestion.title}</h2>

              <div className={styles.optionList}>
                {currentQuestion.options.map((option) => {
                  const isSelected = answers[currentQuestion.id] === option.key
                  const isCorrect = submitted && correctSet.has(currentQuestion.id) && isSelected
                  const isWrong = submitted && isSelected && !correctSet.has(currentQuestion.id)
                  return (
                    <button
                      key={option.key}
                      type="button"
                      className={
                        isSelected
                          ? `${styles.optionCard} ${styles.optionCardActive}`
                          : styles.optionCard
                      }
                      style={
                        isWrong
                          ? { borderColor: '#e53e3e', background: 'rgba(229,62,62,0.06)' }
                          : isCorrect
                            ? { borderColor: '#38a169', background: 'rgba(56,161,105,0.06)' }
                            : undefined
                      }
                      onClick={() => selectOption(option.key)}
                    >
                      <span className={styles.optionBadge}>{option.key}</span>
                      <span className={styles.optionText}>{option.text}</span>
                      {isSelected && <span className={styles.optionCheck}>{isCorrect ? '✓' : isWrong ? '✗' : '✓'}</span>}
                    </button>
                  )
                })}
              </div>

              {submitted && (
                <div style={{ marginTop: 16, padding: '12px 16px', borderRadius: 12, background: correctSet.has(currentQuestion.id) ? 'rgba(56,161,105,0.08)' : 'rgba(229,62,62,0.08)', fontSize: 14, fontWeight: 700, color: correctSet.has(currentQuestion.id) ? '#38a169' : '#e53e3e' }}>
                  {correctSet.has(currentQuestion.id) ? '回答正确！' : '回答错误，已记录薄弱点'}
                </div>
              )}

              <div className={styles.actionBar}>
                <div className={styles.actionGroup}>
                  <span className={styles.actionHint}>作答记录 / 标记</span>
                  <button type="button" className={styles.inlineBtn} disabled={submitted}>
                    <span>🔖</span> 标记此题
                  </button>
                  <button type="button" className={styles.inlineBtn} disabled={submitted}>
                    <span>🕒</span> 稍后再答
                  </button>
                </div>
              </div>

              <div className={styles.navRow}>
                <button
                  type="button"
                  className={styles.ghostBtn}
                  onClick={() => setCurrentIdx((i) => Math.max(0, i - 1))}
                  disabled={currentIdx === 0}
                >
                  <span className={styles.btnIcon}>←</span> 上一题
                </button>
                <button
                  type="button"
                  className={styles.primaryNavBtn}
                  onClick={() => setCurrentIdx((i) => Math.min(total - 1, i + 1))}
                  disabled={currentIdx >= total - 1}
                >
                  下一题 <span className={styles.btnIcon}>→</span>
                </button>
              </div>
            </section>
          ) : (
            <section className={styles.panel}>
              <div style={{ padding: 40, textAlign: 'center', color: '#8b9ab6' }}>暂无题目</div>
            </section>
          )}

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
                  key={card.id}
                  type="button"
                  className={`${styles.answerItem} ${styles[`answer${card.state[0].toUpperCase()}${card.state.slice(1)}`]}`}
                  onClick={() => setCurrentIdx(card.index - 1)}
                >
                  {card.index}
                </button>
              ))}
            </div>

            <div className={styles.legendRow}>
              <span className={styles.legendItem}>
                <i className={`${styles.legendDot} ${styles.legendDone}`} /> 已完成
              </span>
              <span className={styles.legendItem}>
                <i className={`${styles.legendDot} ${styles.legendCurrent}`} /> 当前
              </span>
              <span className={styles.legendItem}>
                <i className={`${styles.legendDot} ${styles.legendIdle}`} /> 未作答
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
