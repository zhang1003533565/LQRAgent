import { useChatStore } from '@/store/chatStore'
import { useChatAutoScroll } from '@/hooks/useChatAutoScroll'
import StreamingMessage from './StreamingMessage'
import styles from './ChatPanel.module.css'

const SUGGESTIONS = [
  '我想学习 Python 装饰器',
  '解释一下列表推导式',
  '帮我制定本周学习计划',
]

export default function ChatMessageList() {
  const messages = useChatStore((s) => s.messages)
  const bottomRef = useChatAutoScroll([messages])

  return (
    <div className={styles.messages}>
      {messages.length === 0 && (
        <div className={styles.empty}>
          <p>你好！输入学习目标开始学习，例如：</p>
          {SUGGESTIONS.map((s) => (
            <p key={s} className={styles.hint}>
              「{s}」
            </p>
          ))}
        </div>
      )}
      {messages.map((msg) => (
        <StreamingMessage key={msg.id} message={msg} />
      ))}
      <div ref={bottomRef} />
    </div>
  )
}
