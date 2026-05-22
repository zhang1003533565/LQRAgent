import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { ChatMessage } from '@/store/chatStore'
import styles from './StreamingMessage.module.css'

interface Props {
  message: ChatMessage
}

/**
 * 单条消息渲染，支持 Markdown 和流式光标。
 */
export default function StreamingMessage({ message }: Props) {
  const isUser = message.role === 'user'

  return (
    <div className={`${styles.wrapper} ${isUser ? styles.user : styles.assistant}`}>
      <div className={styles.avatar}>{isUser ? '你' : 'AI'}</div>
      <div className={styles.bubble}>
        {isUser ? (
          <p>{message.content}</p>
        ) : (
          <>
            <ReactMarkdown remarkPlugins={[remarkGfm]}>
              {message.content || ' '}
            </ReactMarkdown>
            {message.streaming && <span className={styles.cursor} />}
          </>
        )}
      </div>
    </div>
  )
}
