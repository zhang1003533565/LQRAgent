import { useState, type FormEvent } from 'react'
import { useChatStore } from '@/utils/store/chatStore'
import styles from './ChatPanel.module.css'

interface Props {
  onSend: (content: string) => void
}

export default function ChatComposer({ onSend }: Props) {
  const [input, setInput] = useState('')
  const addMessage = useChatStore((s) => s.addMessage)

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const content = input.trim()
    if (!content) return

    useChatStore.getState().finalizeStuckStreaming()

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
      agentSteps: [],
      agentStepsCollapsed: false,
      createdAt: new Date(),
    })

    onSend(content)
    setInput('')
  }

  return (
    <div className={styles.composerWrap}>
      <form onSubmit={handleSubmit} className={styles.inputRow}>
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              handleSubmit(e as unknown as FormEvent)
            }
          }}
          placeholder="输入你的问题..."
          rows={2}
          className={styles.textarea}
        />
        <div className={styles.bottomBar}>
          <div className={styles.tools}>
            <button type="button" className={styles.toolBtn} title="图片">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="m21 15-5-5L5 21"/></svg>
              <span>图片</span>
            </button>
            <button type="button" className={styles.toolBtn} title="工具">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>
              <span>工具</span>
            </button>
          </div>
          <button type="submit" className={styles.sendBtn} disabled={!input.trim()}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m22 2-7 20-4-9-9-4z"/><path d="m22 2-11 11"/></svg>
          </button>
        </div>
      </form>
    </div>
  )
}
