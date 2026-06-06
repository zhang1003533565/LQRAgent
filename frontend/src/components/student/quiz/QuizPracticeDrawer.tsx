import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import {
  getNextQuizQuestion,
  getQuizQuestionDetail,
  listQuizQuestions,
  submitQuiz,
  type QuizQuestionDetail,
  type QuizResult,
} from '@/api/student/quiz'
import styles from './QuizPracticeDrawer.module.css'

type ChoiceKey = 'A' | 'B' | 'C' | 'D'

type RouteState = {
  backgroundLocation?: Location
}

type QuizPracticeDrawerProps = {
  questionId?: string
}

function formatQuestionType(type: string) {
  if (type === 'single') return '单选题'
  if (type === 'judge') return '判断题'
  if (type === 'fill') return '填空题'
  if (type === 'code_reading') return '代码阅读'
  return '题目'
}

function formatDifficulty(difficulty: number) {
  if (difficulty === 1) return '简单'
  if (difficulty === 2) return '中等'
  if (difficulty === 3) return '困难'
  return '未知'
}

function normalizeAnswer(value?: string | null) {
  return (value || '').trim().toUpperCase()
}

function splitAnalysis(value?: string | null) {
  return (value || '')
    .split(/\r?\n+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function normalizeOptionLabel(key: ChoiceKey, label: string) {
  const pattern = new RegExp(`^${key}[.．、\\s]+`, 'i')
  return label.replace(pattern, '').trim()
}

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={styles.headerIcon}>
      <path d="M14.5 6.5L9 12l5.5 5.5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function StarIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={styles.actionIcon}>
      <path d="M12 4.2l2.18 4.43 4.89.71-3.53 3.44.83 4.86L12 15.34l-4.37 2.3.83-4.86L4.93 9.34l4.89-.71L12 4.2Z" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
    </svg>
  )
}

function SettingIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={styles.actionIcon}>
      <path d="M9.4 4.4h5.2l.6 2.1a6.9 6.9 0 0 1 1.4.82l2.1-.63 2.6 4.5-1.55 1.56c.05.33.08.67.08 1.02s-.03.69-.08 1.02l1.55 1.56-2.6 4.5-2.1-.63a6.9 6.9 0 0 1-1.4.82l-.6 2.1H9.4l-.6-2.1a6.9 6.9 0 0 1-1.4-.82l-2.1.63-2.6-4.5 1.55-1.56A6.8 6.8 0 0 1 4.17 12c0-.35.03-.69.08-1.02L2.7 9.42l2.6-4.5 2.1.63c.43-.33.9-.61 1.4-.82l.6-2.1Z" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <circle cx="12" cy="12" r="2.8" fill="none" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 20 20" aria-hidden="true" className={styles.resultIcon}>
      <path d="M4.5 10.2 8 13.7 15.5 6.2" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 20 20" aria-hidden="true" className={styles.resultIcon}>
      <path d="M5.2 5.2 14.8 14.8M14.8 5.2 5.2 14.8" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

export default function QuizPracticeDrawer({ questionId: questionIdProp }: QuizPracticeDrawerProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const { questionId: routeQuestionId } = useParams()
  const [detail, setDetail] = useState<QuizQuestionDetail | null>(null)
  const [selectedAnswer, setSelectedAnswer] = useState('')
  const [submitResult, setSubmitResult] = useState<QuizResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [nextLoading, setNextLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [questionOrder, setQuestionOrder] = useState<number | null>(null)
  const [questionTotal, setQuestionTotal] = useState<number | null>(null)
  const [hasNextQuestion, setHasNextQuestion] = useState<boolean | null>(null)

  const currentQuestionId = Number(questionIdProp || routeQuestionId || 0)
  const routeState = location.state as RouteState | null

  useEffect(() => {
    if (!currentQuestionId) {
      setDetail(null)
      setError('题目参数不正确')
      return
    }

    let active = true
    setLoading(true)
    setError(null)
    setSubmitResult(null)
    setSelectedAnswer('')

    getQuizQuestionDetail(currentQuestionId)
      .then((res) => {
        if (!active) return
        setDetail(res)
      })
      .catch(() => {
        if (!active) return
        setDetail(null)
        setError('题目加载失败，请稍后重试')
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [currentQuestionId])

  useEffect(() => {
    if (!currentQuestionId) {
      setQuestionOrder(null)
      setQuestionTotal(null)
      setHasNextQuestion(null)
      return
    }

    let active = true

    listQuizQuestions({ page: 1, size: 1000 })
      .then((res) => {
        if (!active) return
        const items = res.items || []
        const index = items.findIndex((item) => item.id === currentQuestionId)
        const total = typeof res.total === 'number' ? res.total : items.length
        setQuestionOrder(index >= 0 ? index + 1 : null)
        setQuestionTotal(total)
        setHasNextQuestion(index >= 0 ? index < total - 1 : null)
      })
      .catch(() => {
        if (!active) return
        setQuestionOrder(null)
        setQuestionTotal(null)
        setHasNextQuestion(null)
      })

    return () => {
      active = false
    }
  }, [currentQuestionId])

  const options = useMemo(
    () =>
      [
        detail?.optionA ? { key: 'A' as ChoiceKey, label: normalizeOptionLabel('A', detail.optionA) } : null,
        detail?.optionB ? { key: 'B' as ChoiceKey, label: normalizeOptionLabel('B', detail.optionB) } : null,
        detail?.optionC ? { key: 'C' as ChoiceKey, label: normalizeOptionLabel('C', detail.optionC) } : null,
        detail?.optionD ? { key: 'D' as ChoiceKey, label: normalizeOptionLabel('D', detail.optionD) } : null,
      ].filter(Boolean) as Array<{ key: ChoiceKey; label: string }>,
    [detail],
  )

  const isJudge = detail?.questionType === 'judge'
  const isFill = detail?.questionType === 'fill'
  const isChoice = !isFill && !isJudge && options.length > 0
  const correctValue = normalizeAnswer(submitResult?.correctAnswer)
  const userValue = normalizeAnswer(submitResult?.answer || selectedAnswer)
  const analysisLines = splitAnalysis(submitResult?.analysis || detail?.analysis)
  const submitted = Boolean(submitResult)
  const answeredCorrectly = submitResult?.correct === true

  const progressValue =
    questionOrder && questionTotal && questionTotal > 0
      ? Math.max(1, Math.min(100, Math.round((questionOrder / questionTotal) * 100)))
      : 0

  const handleClose = () => {
    if (routeState?.backgroundLocation) {
      navigate(-1)
      return
    }
    navigate('/workspace/quiz')
  }

  const handleSubmit = async () => {
    if (!detail || !selectedAnswer.trim() || submitting) return
    setSubmitting(true)
    setError(null)

    try {
      const result = await submitQuiz({
        questionId: detail.id,
        kpId: detail.knowledgePoint || undefined,
        answer: selectedAnswer.trim(),
      })
      setSubmitResult(result)
    } catch {
      setError('提交失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  const handleNext = async () => {
    if (!detail || nextLoading) return

    if (hasNextQuestion === false) {
      return
    }

    setNextLoading(true)
    setError(null)

    try {
      const next = await getNextQuizQuestion(detail.id)
      if (!next.hasNext || !next.nextQuestionId) {
        setHasNextQuestion(false)
        return
      }

      navigate(`/workspace/quiz/practice/${next.nextQuestionId}`, {
        replace: true,
        state: routeState?.backgroundLocation
          ? { backgroundLocation: routeState.backgroundLocation }
          : undefined,
      })
    } catch {
      setError('获取下一题失败，请稍后重试')
    } finally {
      setNextLoading(false)
    }
  }

  return (
    <section className={styles.page}>
      <div className={styles.dimPane} onClick={handleClose} />

      <aside className={styles.drawer}>
        <header className={styles.header}>
          <button type="button" className={styles.backButton} onClick={handleClose}>
            <BackIcon />
          </button>

          <div className={styles.headerInfo}>
            <h1 className={styles.title}>Python 基础练习</h1>
            <p className={styles.meta}>
              {questionOrder && questionTotal
                ? `第 ${questionOrder} 题 / 共 ${questionTotal} 题`
                : detail
                  ? `题目 ID ${detail.id}`
                  : '正在进入练习'}
            </p>
          </div>

          <div className={styles.headerActions}>
            <button type="button" className={styles.iconButton}>
              <StarIcon />
              <span>收藏</span>
            </button>
            <button type="button" className={styles.iconButton}>
              <SettingIcon />
              <span>设置</span>
            </button>
          </div>
        </header>

        <div className={styles.progressRow}>
          <div className={styles.badges}>
            <span className={styles.badgeBlue}>{detail ? formatQuestionType(detail.questionType) : '题目'}</span>
            <span className={styles.badgeGreen}>{detail ? formatDifficulty(detail.difficulty) : '加载中'}</span>
          </div>
          <span className={styles.percent}>{progressValue}%</span>
        </div>

        <div className={styles.progressTrack}>
          <span className={styles.progressValue} style={{ width: `${progressValue}%` }} />
        </div>

        <div className={styles.cardStack}>
          <article
            className={
              submitted
                ? answeredCorrectly
                  ? `${styles.questionCard} ${styles.successCard}`
                  : `${styles.questionCard} ${styles.errorCard}`
                : styles.questionCard
            }
          >
            <div className={styles.cardTop}>
              <div className={styles.questionMeta}>
                <span className={submitted ? (answeredCorrectly ? styles.orderBubbleSuccess : styles.orderBubbleError) : styles.orderBubbleSuccess}>
                  {questionOrder || 1}
                </span>
                <span className={styles.typePill}>{detail ? formatQuestionType(detail.questionType) : '题目'}</span>
                <span className={submitted && !answeredCorrectly ? styles.levelPillError : styles.levelPillSuccess}>
                  {detail ? formatDifficulty(detail.difficulty) : '加载中'}
                </span>
              </div>

              {submitted ? (
                <div className={answeredCorrectly ? styles.cardStatusSuccess : styles.cardStatusError}>
                  {answeredCorrectly ? <CheckIcon /> : <CloseIcon />}
                </div>
              ) : null}
            </div>

            {loading ? (
              <p className={styles.questionStem}>题目加载中...</p>
            ) : error && !detail ? (
              <p className={styles.questionStem}>{error}</p>
            ) : detail ? (
              <>
                <h2 className={styles.questionStem}>{detail.title}</h2>

                {detail.codeContent ? (
                  <pre className={styles.codeBlock}>
                    <code>{detail.codeContent}</code>
                  </pre>
                ) : null}

                {isChoice ? (
                  <div className={styles.optionList}>
                    {options.map((option) => {
                      const selected = userValue === option.key
                      const correct = correctValue === option.key
                      const showCorrect = submitted && correct
                      const showWrong = submitted && selected && !correct

                      return (
                        <button
                          key={option.key}
                          type="button"
                          className={
                            showCorrect
                              ? `${styles.optionRow} ${styles.optionRowCorrect}`
                              : showWrong
                                ? `${styles.optionRow} ${styles.optionRowWrong}`
                                : selected
                                  ? `${styles.optionRow} ${styles.optionRowSelected}`
                                  : styles.optionRow
                          }
                          onClick={() => !submitted && setSelectedAnswer(option.key)}
                          disabled={submitted}
                        >
                          <span className={selected ? `${styles.radioMark} ${styles.radioMarkActive}` : styles.radioMark} />
                          <span className={styles.optionKey}>{option.key}</span>
                          <span className={styles.optionText}>{option.label}</span>
                          {showCorrect ? <CheckIcon /> : showWrong ? <CloseIcon /> : null}
                        </button>
                      )
                    })}
                  </div>
                ) : isFill ? (
                  <div className={styles.fillAnswerWrap}>
                    <input
                      type="text"
                      className={styles.fillInput}
                      value={selectedAnswer}
                      onChange={(event) => !submitted && setSelectedAnswer(event.target.value)}
                      placeholder="请输入答案"
                      disabled={submitted}
                    />
                  </div>
                ) : (
                  <div className={styles.judgeActions}>
                    <button
                      type="button"
                      className={
                        selectedAnswer === '正确'
                          ? `${styles.judgeButtonSuccess} ${styles.judgeButtonActive}`
                          : `${styles.judgeButtonSuccess} ${styles.judgeButtonNeutral}`
                      }
                      onClick={() => !submitted && setSelectedAnswer('正确')}
                      disabled={submitted}
                    >
                      <CheckIcon />
                      正确
                    </button>
                    <button
                      type="button"
                      className={
                        selectedAnswer === '错误'
                          ? `${styles.judgeButtonError} ${styles.judgeButtonActive}`
                          : `${styles.judgeButtonError} ${styles.judgeButtonNeutral}`
                      }
                      onClick={() => !submitted && setSelectedAnswer('错误')}
                      disabled={submitted}
                    >
                      <CloseIcon />
                      错误
                    </button>
                  </div>
                )}

                {submitted ? (
                  <div className={answeredCorrectly ? `${styles.analysisBox} ${styles.analysisBoxSuccess}` : `${styles.analysisBox} ${styles.analysisBoxError}`}>
                    <div className={styles.analysisTitleRow}>
                      <span className={answeredCorrectly ? styles.analysisIconSuccess : styles.analysisIconError}>
                        {answeredCorrectly ? <CheckIcon /> : <CloseIcon />}
                      </span>
                      <strong className={styles.analysisTitle}>{answeredCorrectly ? '回答正确' : '回答错误'}</strong>
                    </div>

                    <div className={styles.analysisBody}>
                      <p className={styles.analysisText}>你的答案：{submitResult?.answer || '-'}</p>
                      <p className={styles.analysisText}>正确答案：{submitResult?.correctAnswer || '-'}</p>
                      {analysisLines.length > 0
                        ? analysisLines.map((line) => (
                            <p key={line} className={styles.analysisText}>
                              {line}
                            </p>
                          ))
                        : <p className={styles.analysisText}>当前题目暂未配置解析。</p>}
                    </div>
                  </div>
                ) : (
                  <div className={`${styles.analysisBox} ${styles.analysisBoxSuccess}`}>
                    <div className={styles.analysisTitleRow}>
                      <span className={styles.analysisIconSuccess}>
                        <CheckIcon />
                      </span>
                      <strong className={styles.analysisTitle}>练习提示</strong>
                    </div>

                    <div className={styles.analysisBody}>
                      <p className={styles.analysisText}>
                        {isJudge
                          ? '请先判断题干描述是否正确，再提交答案。'
                          : isFill
                            ? '请在输入框中填写答案，然后提交查看解析。'
                            : '请选择你认为正确的选项，然后提交答案查看解析。'}
                      </p>
                      {detail.knowledgePoint ? <p className={styles.analysisText}>知识点：{detail.knowledgePoint}</p> : null}
                    </div>
                  </div>
                )}

                {error && detail ? <p className={styles.errorText}>{error}</p> : null}

                <div className={styles.footerActions}>
                  <button
                    type="button"
                    className={styles.submitButton}
                    onClick={handleSubmit}
                    disabled={!selectedAnswer.trim() || submitted || loading || submitting}
                  >
                    {submitting ? '提交中...' : submitted ? '已提交' : '提交答案'}
                  </button>
                  <button
                    type="button"
                    className={styles.nextButton}
                    onClick={handleNext}
                    disabled={loading || nextLoading}
                  >
                    {hasNextQuestion === false
                      ? '查看AI总结'
                      : nextLoading
                        ? '加载中...'
                        : '下一题'}
                  </button>
                </div>
              </>
            ) : null}
          </article>
        </div>
      </aside>
    </section>
  )
}
