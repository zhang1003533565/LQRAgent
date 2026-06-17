import { useState } from 'react'
import type { QuizData, QuizQuestion } from '@/utils/types/chat'
import http from '@/api/http'
import styles from './QuizCard.module.css'

interface Props {
  data: QuizData
}

interface QuestionState {
  selected: string
  submitted: boolean
  correct: boolean | null
}

export default function QuizCard({ data }: Props) {
  const [states, setStates] = useState<Record<number, QuestionState>>({})
  const [grading, setGrading] = useState(false)
  const [gradeResult, setGradeResult] = useState<string | null>(null)

  const updateState = (qid: number, patch: Partial<QuestionState>) => {
    setStates(prev => {
      const current = prev[qid] ?? { selected: '', submitted: false, correct: null }
      return {
        ...prev,
        [qid]: { ...current, ...patch },
      }
    })
  }

  const handleSubmit = async () => {
    const unanswered = data.questions.filter(q => !states[q.id]?.selected?.trim())
    if (unanswered.length > 0) {
      setGradeResult(`还有 ${unanswered.length} 题未作答，请先完成所有题目。`)
      return
    }

    setGrading(true)
    setGradeResult(null)

    const answers = data.questions.map(q => ({
      id: q.id,
      type: q.type,
      stem: q.stem,
      answer: q.answer ?? '',
      userAnswer: states[q.id]?.selected ?? '',
    }))

    try {
      const res = await http.post<{ data: { success: boolean; content?: string; error?: string } }>(
        '/admin/agent-test',
        {
          message: `请批改以下练习题答案：\n${JSON.stringify(answers, null, 2)}`,
          agentId: 'assessment_agent',
        },
      )
      const result = res.data.data
      if (result.success && result.content) {
        setGradeResult(result.content)
      } else {
        setGradeResult(result.error ?? '批改失败，请重试。')
      }
    } catch (e) {
      setGradeResult(`批改请求失败: ${e instanceof Error ? e.message : '未知错误'}`)
    } finally {
      setGrading(false)
    }
  }

  const renderQuestion = (q: QuizQuestion) => {
    const state = states[q.id] ?? { selected: '', submitted: false, correct: null }

    return (
      <div key={q.id} className={styles.question}>
        <div className={styles.stem}>
          <span className={styles.qType}>{q.type}</span>
          <span className={styles.qNum}>第 {q.id} 题</span>
          <span className={styles.qDiff}>{q.difficulty ?? data.difficulty ?? ''}</span>
        </div>
        <p className={styles.stemText}>{q.stem}</p>

        {q.type === '选择题' && q.options ? (
          <div className={styles.options}>
            {q.options.map((opt, i) => (
              <label
                key={i}
                className={`${styles.option} ${state.selected === opt ? styles.selected : ''}`}
              >
                <input
                  type="radio"
                  name={`q-${q.id}`}
                  value={opt}
                  checked={state.selected === opt}
                  onChange={() => updateState(q.id, { selected: opt })}
                  className={styles.radio}
                />
                {opt}
              </label>
            ))}
          </div>
        ) : q.type === '判断题' ? (
          <div className={styles.options}>
            {['正确', '错误'].map(opt => (
              <label
                key={opt}
                className={`${styles.option} ${state.selected === opt ? styles.selected : ''}`}
              >
                <input
                  type="radio"
                  name={`q-${q.id}`}
                  value={opt}
                  checked={state.selected === opt}
                  onChange={() => updateState(q.id, { selected: opt })}
                  className={styles.radio}
                />
                {opt}
              </label>
            ))}
          </div>
        ) : (
          <textarea
            className={styles.textarea}
            placeholder={q.type === '填空题' ? '输入答案...' : '输入你的回答...'}
            value={state.selected}
            onChange={e => updateState(q.id, { selected: e.target.value })}
            rows={q.type === '编程题' ? 6 : 3}
          />
        )}

        {q.explanation && state.submitted && (
          <div className={styles.explanation}>
            <strong>解析：</strong> {q.explanation}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <h3 className={styles.title}>{data.title ?? `${data.topic ?? ''} 练习题`}</h3>
        <span className={styles.count}>{data.questions.length} 题</span>
      </div>

      <div className={styles.questions}>
        {data.questions.map(renderQuestion)}
      </div>

      {!gradeResult && (
        <button
          className={styles.submitBtn}
          onClick={handleSubmit}
          disabled={grading}
        >
          {grading ? '批改中...' : '提交作业'}
        </button>
      )}

      {gradeResult && (
        <div className={styles.result}>
          <strong>批改结果：</strong>
          <div className={styles.resultContent}>{gradeResult}</div>
        </div>
      )}
    </div>
  )
}
