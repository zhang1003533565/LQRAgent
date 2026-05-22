import { useState, useRef, useEffect, type FormEvent } from 'react'
import { useChatStore } from '@/store/chatStore'
import { useWebSocket } from '@/hooks/useWebSocket'
import StreamingMessage from './StreamingMessage'
import styles from './ChatPanel.module.css'

/**
 * 聊天主面板：消息列表 + 输入框。
 */
export default function ChatPanel() {
  const [input, setInput] = useState('')
  const messages = useChatStore((s) => s.messages)
  const addMessage = useChatStore((s) => s.addMessage)
  const isConnected = useChatStore((s) => s.isConnected)
  const { send } = useWebSocket()
  const bottomRef = useRef<HTMLDivElement>(null)

  // 新消息时自动滚到底部
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const content = input.trim()
    if (!content) return

    // 添加用户消息
    addMessage({
      id: crypto.randomUUID(),
      role: 'user',
      content,
      createdAt: new Date(),
    })

    // 添加 assistant 占位消息（流式填充）
    addMessage({
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '',
      streaming: true,
      createdAt: new Date(),
    })

    send(content)
    setInput('')
  }

  return (
    <div className={styles.container}>
      <div className={styles.messages}>
        {messages.length === 0 && (
          <div className={styles.empty}>
            <p>你好！输入学习目标开始学习，例如：</p>
            <p className={styles.hint}>「我想学习 Python 装饰器」</p>
          </div>
        )}
        {messages.map((msg) => (
          <StreamingMessage key={msg.id} message={msg} />
        ))}
        <div ref={bottomRef} />
      </div>

      <form onSubmit={handleSubmit} className={styles.inputArea}>
        <div className={styles.status}>
          <span className={isConnected ? styles.online : styles.offline} />
          {isConnected ? '已连接' : '连接中...'}
        </div>
        <div className={styles.inputRow}>
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                handleSubmit(e as unknown as FormEvent)
              }
            }}
            placeholder="输入问题或学习目标（Enter 发送，Shift+Enter 换行）"
            rows={3}
            className={styles.textarea}
          />
          <button type="submit" className={styles.sendBtn} disabled={!input.trim()}>
            发送
          </button>
        </div>
      </form>
    </div>
  )
}
