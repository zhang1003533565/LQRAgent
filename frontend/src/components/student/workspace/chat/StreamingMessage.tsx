import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { ChatMessage } from '@/utils/types/chat'
import MultiCardMessage from './MultiCardMessage'
import MermaidRenderer from './MermaidRenderer'
import RagSourcesCard from './RagSourcesCard'
import LearningPathCard from './LearningPathCard'
import styles from './StreamingMessage.module.css'

interface Props {
  message: ChatMessage
}

function RobotIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="8" width="18" height="12" rx="2" />
      <path d="M12 2v4" />
      <circle cx="12" cy="2" r="1" fill="#3b82f6" stroke="none" />
      <circle cx="9" cy="14" r="1.5" fill="#3b82f6" stroke="none" />
      <circle cx="15" cy="14" r="1.5" fill="#3b82f6" stroke="none" />
      <path d="M9 18h6" />
    </svg>
  )
}

export default function StreamingMessage({ message }: Props) {
  const isUser = message.role === 'user'
  const isMulti = message.contentType === 'multi_card' && message.cards?.length
  const isLearningPath = message.contentType === 'learning_path'
  const isImage = message.contentType === 'image' && message.imageUrl
  const [liked, setLiked] = useState<boolean | null>(null)

  const timeLabel = new Date(message.createdAt).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  })

  const handleCopy = () => {
    navigator.clipboard.writeText(message.content || '')
  }

  return (
    <div className={`${styles.wrapper} ${isUser ? styles.user : styles.assistant}`} data-streaming={message.streaming ? 'true' : 'false'}>
      <div className={`${styles.avatar} ${isUser ? styles.userAvatar : styles.aiAvatar}`}>
        {isUser ? '你' : <RobotIcon />}
      </div>
      <div className={styles.content}>
        <div className={styles.bubble}>
          {isUser ? (
            <p style={{ margin: 0 }}>{message.content}</p>
          ) : isLearningPath ? (
            <>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content || ' '}</ReactMarkdown>
              <LearningPathCard />
            </>
          ) : isImage ? (
            <div style={{ maxWidth: '100%', borderRadius: 8, overflow: 'hidden' }}>
              <img
                src={message.imageUrl}
                alt="AI 生成的示意图"
                style={{
                  display: 'block',
                  maxWidth: '100%',
                  height: 'auto',
                  borderRadius: 8,
                  boxShadow: '0 1px 4px rgba(0,0,0,0.1)'
                }}
              />
            </div>
          ) : isMulti ? (
            <MultiCardMessage cards={message.cards!} />
          ) : (
            <>
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                  code({ className, children, ...props }) {
                    const match = /language-(\w+)/.exec(className || '')
                    if (match?.[1] === 'mermaid') {
                      return <MermaidRenderer code={String(children).replace(/\n$/, '')} />
                    }
                    return (
                      <code className={className} {...props}>
                        {children}
                      </code>
                    )
                  },
                }}
              >
                {message.content || ' '}
              </ReactMarkdown>
              {message.streaming && <span className={styles.cursor} />}
            </>
          )}
        </div>

        {/* RAG 引用来源卡片 */}
        {!isUser && message.ragSources && message.ragSources.length > 0 && (
          <RagSourcesCard sources={message.ragSources} />
        )}

        {/* 操作按钮 — 仅 AI 消息显示 */}
        {!isUser && message.content && !message.streaming && (
          <div className={styles.actions}>
            <button className={styles.actionBtn} onClick={handleCopy}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
              复制
            </button>
            <button className={`${styles.actionBtn} ${liked === true ? styles.active : ''}`} onClick={() => setLiked(liked === true ? null : true)}>
              👍
            </button>
            <button className={`${styles.actionBtn} ${liked === false ? styles.active : ''}`} onClick={() => setLiked(liked === false ? null : false)}>
              👎
            </button>
          </div>
        )}

        <span className={`${styles.time} ${isUser ? styles.userTime : styles.assistantTime}`}>
          {timeLabel}
        </span>
      </div>
    </div>
  )
}
