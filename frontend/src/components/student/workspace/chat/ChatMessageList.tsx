import type { ChatMessage } from '@/utils/types/chat'
import { useChatStore } from '@/utils/store/chatStore'
import { useChatAutoScroll } from '@/utils/hooks/useChatAutoScroll'
import { submitChatMessage } from '@/utils/chat/sendChatMessage'
import StreamingMessage from './StreamingMessage'
import styles from './ChatPanel.module.css'

const SUGGESTIONS = [
  { icon: '🎯', text: '我想学习 Python 装饰器' },
  { icon: '💡', text: '解释一下列表推导式' },
  { icon: '📋', text: '帮我制定本周学习计划' },
]

function isRenderableMessage(msg: ChatMessage): boolean {
  if (msg.role !== 'assistant') return true
  if (msg.streaming) return true
  if (msg.content?.trim()) return true
  if (msg.imageUrl || msg.videoUrl || msg.quizData || msg.cards?.length || msg.ragSources?.length) {
    return true
  }
  if (msg.contentType && msg.contentType !== 'text') return true
  return false
}

export default function ChatMessageList() {
  const messages = useChatStore((s) => s.messages)
  const loadingMessages = useChatStore((s) => s.loadingMessages)
  const bottomRef = useChatAutoScroll([messages], loadingMessages)
  return (
    <div className={styles.messages}>
      {messages.length === 0 && (
        <div className={styles.empty}>
          <div className={styles.emptyIcon}>
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#2f77ff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
          </div>
          <div>
            <p className={styles.emptyTitle}>你好，我是 Edu.AI 学习助手</p>
            <p className={styles.emptySubtitle}>告诉我你想学什么，我来帮你制定学习路径</p>
          </div>
          <div className={styles.suggestions}>
            {SUGGESTIONS.map((s) => (
              <button
                key={s.text}
                type="button"
                className={styles.suggestionCard}
                onClick={() => submitChatMessage(s.text)}
              >
                <span className={styles.suggestionIcon}>{s.icon}</span>
                {s.text}
              </button>
            ))}
          </div>
        </div>
      )}
      {messages.filter(isRenderableMessage).map((msg) => (
        <StreamingMessage key={msg.id} message={msg} />
      ))}
      <div ref={bottomRef} />
    </div>
  )
}
