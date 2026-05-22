import { useState } from 'react'
import { PlaceholderBanner } from '@/components/ui'
import styles from './QuizSection.module.css'

const MOCK = {
  stem: 'Python 中 @decorator 语法的作用是什么？',
  options: [
    '定义类方法',
    '在不修改原函数代码的情况下扩展函数行为',
    '导入模块',
    '创建虚拟环境',
  ],
  answerIndex: 1,
}

export default function QuizSection() {
  const [selected, setSelected] = useState<number | null>(null)
  const [submitted, setSubmitted] = useState(false)

  return (
    <div className={styles.page}>
      <PlaceholderBanner
        label="答题 API 未实现"
        hint="POST /api/quiz/submit 就绪后对接"
      />

      <p className={styles.stem}>{MOCK.stem}</p>
      <ul className={styles.options}>
        {MOCK.options.map((opt, i) => (
          <li key={i}>
            <label className={styles.option}>
              <input
                type="radio"
                name="quiz"
                checked={selected === i}
                onChange={() => setSelected(i)}
                disabled={submitted}
              />
              {opt}
            </label>
          </li>
        ))}
      </ul>

      {!submitted ? (
        <button
          type="button"
          className={styles.submit}
          disabled={selected === null}
          onClick={() => setSubmitted(true)}
        >
          提交答案
        </button>
      ) : (
        <p
          className={
            selected === MOCK.answerIndex ? styles.correct : styles.wrong
          }
        >
          {selected === MOCK.answerIndex
            ? '回答正确（占位反馈）'
            : '回答有误（占位）'}
        </p>
      )}
    </div>
  )
}
