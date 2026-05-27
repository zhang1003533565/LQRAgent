import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { ChatMessage } from '@/student/types/chat'
import MultiCardMessage from './MultiCardMessage'
import styles from './StreamingMessage.module.css'

interface Props {
  message: ChatMessage
}

export default function StreamingMessage({ message }: Props) {
  const isUser = message.role === 'user'
  const isMulti = message.contentType === 'multi_card' && message.cards?.length
  const timeLabel = new Date(message.createdAt).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  })

  return (
    <div className={`${styles.wrapper} ${isUser ? styles.user : styles.assistant}`}>
      <div className={styles.avatar}>{isUser ? '你' : 'AI'}</div>
      <div className={styles.content}>
        <div className={styles.bubble}>
          {isUser ? (
            <p>{message.content}</p>
          ) : isMulti ? (
            <MultiCardMessage cards={message.cards!} />
          ) : (
            <>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content || ' '}</ReactMarkdown>
              {message.streaming && <span className={styles.cursor} />}
            </>
          )}
        </div>
        <span className={`${styles.time} ${isUser ? styles.userTime : styles.assistantTime}`}>
          {timeLabel}
        </span>
      </div>
    </div>
  )
}
