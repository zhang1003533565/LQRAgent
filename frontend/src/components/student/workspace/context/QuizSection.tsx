import { useState, useEffect } from 'react'
import { usePathStore } from '@/utils/store/pathStore'
import { getQuizQuestions, submitQuiz } from '@/api/student/quiz'
import type { LearningResource } from '@/utils/types/media-resource'
import { EmptyState } from '@/components/student/ui'
import styles from './QuizSection.module.css'

interface ParsedOption {
  label: string
  text: string
}

function parseQuizContent(resource: LearningResource): {
  stem: string
  options: ParsedOption[]
  correctAnswer: string
} {
  const content = resource.content || ''
  const lines = content.split('\n').filter((l) => l.trim())

  let stem = ''
  const options: ParsedOption[] = []
  let correctAnswer = ''

  for (const line of lines) {
    const trimmed = line.trim()
    if (/^[A-D][.、．]\s*/.test(trimmed)) {
      const match = trimmed.match(/^([A-D])[.、．]\s*(.+)/)
      if (match) {
        options.push({ label: match[1], text: match[2].trim() })
      }
    } else if (/^答案[：:]/.test(trimmed)) {
      correctAnswer = trimmed.replace(/^答案[：:]\s*/, '').trim()
    } else if (!stem && trimmed) {
      stem = trimmed
    }
  }

  return { stem, options, correctAnswer }
}

export default function QuizSection() {
  const selectedKpId = usePathStore((s) => s.selectedKpId)
  const [loading, setLoading] = useState(false)
  const [quizzes, setQuizzes] = useState<LearningResource[]>([])
  const [currentIndex, setCurrentIndex] = useState(0)
  const [selectedOption, setSelectedOption] = useState<string | null>(null)
  const [submitted, setSubmitted] = useState(false)
  const [result, setResult] = useState<{ correct: boolean; correctAnswer: string } | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!selectedKpId) {
      setQuizzes([])
      setCurrentIndex(0)
      return
    }
    setLoading(true)
    setError('')
    getQuizQuestions(selectedKpId)
      .then((data) => {
        setQuizzes(data)
        setCurrentIndex(0)
        setSelectedOption(null)
        setSubmitted(false)
        setResult(null)
      })
      .catch(() => setError('加载题目失败'))
      .finally(() => setLoading(false))
  }, [selectedKpId])

  const currentQuiz = quizzes[currentIndex] ?? null
  const parsed = currentQuiz ? parseQuizContent(currentQuiz) : null

  async function handleSubmit() {
    if (!currentQuiz || !selectedOption || !parsed) return
    setSubmitted(true)
    try {
      const res = await submitQuiz({
        questionId: currentQuiz.id,
        kpId: selectedKpId ?? undefined,
        answer: selectedOption,
      })
      setResult({
        correct: res.correct,
        correctAnswer: res.correctAnswer || parsed.correctAnswer,
      })
      if (!res.correct) {
        usePathStore.getState().refresh()
      }
    } catch {
      setError('提交失败，请重试')
    }
  }

  function handleNext() {
    setSelectedOption(null)
    setSubmitted(false)
    setResult(null)
    if (currentIndex < quizzes.length - 1) {
      setCurrentIndex(currentIndex + 1)
    }
  }

  if (!selectedKpId) {
    return (
      <div className={styles.page}>
        <EmptyState title="请先在「学习路径」中选择一个节点" />
      </div>
    )
  }

  if (loading) {
    return (
      <div className={styles.page}>
        <p className={styles.loading}>加载中...</p>
      </div>
    )
  }

  if (error && quizzes.length === 0) {
    return (
      <div className={styles.page}>
        <p className={styles.error}>{error}</p>
      </div>
    )
  }

  if (!currentQuiz || !parsed || parsed.options.length === 0) {
    return (
      <div className={styles.page}>
        <EmptyState title="暂无题目" description="请先生成学习资源" />
      </div>
    )
  }

  return (
    <div className={styles.page}>
      <p className={styles.progress}>
        第 {currentIndex + 1} / {quizzes.length} 题
      </p>

      <p className={styles.stem}>{parsed.stem}</p>

      <ul className={styles.options}>
        {parsed.options.map((opt) => (
          <li key={opt.label}>
            <label
              className={`${styles.option} ${
                submitted
                  ? opt.label === parsed.correctAnswer
                    ? styles.optionCorrect
                    : opt.label === selectedOption
                      ? styles.optionWrong
                      : ''
                  : selectedOption === opt.label
                    ? styles.optionSelected
                    : ''
              }`}
            >
              <input
                type="radio"
                name="quiz"
                checked={selectedOption === opt.label}
                onChange={() => !submitted && setSelectedOption(opt.label)}
                disabled={submitted}
              />
              <span className={styles.optionLabel}>{opt.label}</span>
              <span>{opt.text}</span>
            </label>
          </li>
        ))}
      </ul>

      {result && (
        <div className={styles.result}>
          <p className={result.correct ? styles.correct : styles.wrong}>
            {result.correct ? '回答正确！' : '回答有误'}
          </p>
          {!result.correct && result.correctAnswer && (
            <p className={styles.correctAnswer}>
              正确答案：{result.correctAnswer}
            </p>
          )}
        </div>
      )}

      {!submitted ? (
        <button
          type="button"
          className={styles.submit}
          disabled={!selectedOption}
          onClick={handleSubmit}
        >
          提交答案
        </button>
      ) : (
        <button
          type="button"
          className={styles.submit}
          onClick={handleNext}
        >
          {currentIndex < quizzes.length - 1 ? '下一题' : '完成'}
        </button>
      )}
    </div>
  )
}
