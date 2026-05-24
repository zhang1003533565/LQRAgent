import { useState, type FormEvent } from 'react'
import { useChatStore } from '@/student/store/chatStore'
import styles from './ChatPanel.module.css'

interface Props {
  onSend: (content: string) => void
}

export default function ChatComposer({ onSend }: Props) {
  const [input, setInput] = useState('')
  const addMessage = useChatStore((s) => s.addMessage)
  const isConnected = useChatStore((s) => s.isConnected)

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const content = input.trim()
    if (!content) return

    addMessage({
      id: crypto.randomUUID(),
      role: 'user',
      content,
      createdAt: new Date(),
    })
    addMessage({
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '',
      streaming: true,
      createdAt: new Date(),
    })

    onSend(content)
    setInput('')
  }

  return (
    <form onSubmit={handleSubmit} className={styles.inputArea}>
      <div className={styles.status}>
        <span className={isConnected ? styles.online : styles.offline} />
        {isConnected ? '已连接' : '未连接（后端 WS 就绪后自动恢复）'}
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
  )
}
