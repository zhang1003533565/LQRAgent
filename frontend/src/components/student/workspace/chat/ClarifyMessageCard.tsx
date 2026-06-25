/**
 * Clarify 需求确认 — 编号问题列表样式
 */
import styles from './ClarifyMessageCard.module.css'

interface Props {
  content: string
}

function parseQuestions(content: string): { questions: string[]; footer: string } {
  const lines = content.split('\n')
  const questions: string[] = []
  const footerLines: string[] = []
  let inFooter = false

  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed) continue
    const match = trimmed.match(/^\d+[.)]\s*(.+)$/)
    if (match && !inFooter) {
      questions.push(match[1])
    } else {
      inFooter = true
      footerLines.push(trimmed)
    }
  }

  return { questions, footer: footerLines.join('\n') }
}

export default function ClarifyMessageCard({ content }: Props) {
  const { questions, footer } = parseQuestions(content)

  if (questions.length < 2) {
    return null
  }

  return (
    <div className={styles.card}>
      <div className={styles.badge}>再聊几句</div>
      <p className={styles.hint}>想给你排更合适的路线，还差下面几项信息：</p>
      <ol className={styles.list}>
        {questions.map((q, i) => (
          <li key={i} className={styles.item}>
            <span className={styles.num}>{i + 1}</span>
            <span>{q}</span>
          </li>
        ))}
      </ol>
      {footer && <p className={styles.footer}>{footer}</p>}
    </div>
  )
}
