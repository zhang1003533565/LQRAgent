import { useState, useMemo } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { ChatMessage } from '@/utils/types/chat'
import { useChatStore } from '@/utils/store/chatStore'
import { looksLikeClarify } from '@/utils/chat/messageAgentSteps'
import AgentChainPanel from './AgentChainPanel'
import ClarifyMessageCard from './ClarifyMessageCard'
import MultiCardMessage from './MultiCardMessage'
import MermaidRenderer from './MermaidRenderer'
import RagSourcesCard from './RagSourcesCard'
import LearningPathCard from './LearningPathCard'
import QuizCard from './QuizCard'
import VideoPlayer from './VideoPlayer'
import CodeRunner from './CodeRunner'
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
  const updateMessage = useChatStore((s) => s.updateMessage)

  const isClarify = message.contentType === 'clarify'
    || (!isUser && looksLikeClarify(message.content))
  const isMulti = message.contentType === 'multi_card' && message.cards?.length
  const isLearningPath = message.contentType === 'learning_path'
  const isDiagram = message.contentType === 'diagram' && message.diagramCode
  const isImage = message.contentType === 'image' && message.imageUrl
  const isVideo = message.contentType === 'video' && message.videoUrl
  const isQuiz = message.contentType === 'quiz' && message.quizData?.questions?.length

  const [liked, setLiked] = useState<boolean | null>(null)
  const agentSteps = message.agentSteps ?? []

  const pendingMedia = useMemo(() => {
    if (isUser || isVideo || isImage) return null
    const mediaStep = agentSteps.find(
      (s) => s.agent === 'media_gen_agent' && (s.status === 'running' || s.status === 'pending'),
    )
    if (!mediaStep) return null
    const allMessages = useChatStore.getState().messages
    const lastUser = [...allMessages].reverse().find((m) => m.role === 'user')
    const userText = lastUser?.content || ''
    return /视频|动画|video|animation/i.test(userText) ? 'video' : 'image'
  }, [agentSteps, isUser, isVideo, isImage])

  const timeLabel = new Date(message.createdAt).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  })

  const handleCopy = () => {
    navigator.clipboard.writeText(message.content || '')
  }

  const handleChainCollapse = (collapsed: boolean) => {
    updateMessage(message.id, { agentStepsCollapsed: collapsed })
  }

  const renderBody = () => {
    if (isUser) {
      return <p style={{ margin: 0 }}>{message.content}</p>
    }

    if (isClarify) {
      return (
        <>
          <ClarifyMessageCard content={message.content} />
          {!looksLikeClarify(message.content) && (
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content || ' '}</ReactMarkdown>
          )}
        </>
      )
    }

    if (isLearningPath) {
      return (
        <>
          {message.content && (
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>
          )}
          <LearningPathCard />
        </>
      )
    }

    if (isDiagram) {
      return (
        <>
          {message.content && (
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>
          )}
          <div className={styles.diagramBlock}>
            <MermaidRenderer code={message.diagramCode!} />
          </div>
        </>
      )
    }

    if (isImage) {
      return (
        <a className={styles.imageFrame} href={message.imageUrl} target="_blank" rel="noreferrer">
          <img
            className={styles.generatedImage}
            src={message.imageUrl}
            alt="AI 生成的示意图"
          />
        </a>
      )
    }

    if (isVideo) {
      return (
        <>
          {message.content && (
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>
          )}
          <VideoPlayer url={message.videoUrl!} />
        </>
      )
    }

    if (isQuiz) {
      return <QuizCard data={message.quizData!} />
    }

    if (isMulti) {
      return <MultiCardMessage cards={message.cards!} />
    }

    return (
      <>
        <ReactMarkdown
          remarkPlugins={[remarkGfm]}
          components={{
            img({ src, alt }) {
              if (!src) return null
              return (
                <a href={src} target="_blank" rel="noreferrer">
                  <img className={styles.markdownImage} src={src} alt={alt ?? '图片'} />
                </a>
              )
            },
            code({ className, children, ...props }) {
              const match = /language-(\w+)/.exec(className || '')
              if (match?.[1] === 'mermaid') {
                return <MermaidRenderer code={String(children).replace(/\n$/, '')} />
              }
              const isInline = !String(children).includes('\n')
              if (isInline) {
                return (
                  <code className={className} {...props}>
                    {children}
                  </code>
                )
              }
              const lang = match?.[1] ?? 'text'
              return <CodeRunner code={String(children).replace(/\n$/, '')} language={lang} />
            },
          }}
        >
          {message.content || ' '}
        </ReactMarkdown>
        {message.streaming && <span className={styles.cursor} />}
      </>
    )
  }

  return (
    <div className={`${styles.wrapper} ${isUser ? styles.user : styles.assistant}`} data-streaming={message.streaming ? 'true' : 'false'}>
      <div className={`${styles.avatar} ${isUser ? styles.userAvatar : styles.aiAvatar}`}>
        {isUser ? '你' : <RobotIcon />}
      </div>
      <div className={`${styles.content} ${isQuiz ? styles.quizContent : ''}`}>
        <div className={`${styles.bubble} ${isQuiz ? styles.quizBubble : ''} ${isClarify ? styles.clarifyBubble : ''}`}>
          {!isUser && agentSteps.length > 0 && (
            <AgentChainPanel
              steps={agentSteps}
              streaming={message.streaming}
              collapsed={message.agentStepsCollapsed ?? false}
              onToggleCollapsed={handleChainCollapse}
            />
          )}
          {renderBody()}
        </div>

        {!isUser && pendingMedia && (
          <div className={styles.videoLoading}>
            <div className={styles.videoLoadingSpinner} />
            <div>
              <div className={styles.videoLoadingText}>
                {pendingMedia === 'video' ? '正在生成视频' : '正在生成图片'}
                <span className={styles.videoLoadingDots} />
              </div>
              <div className={styles.videoLoadingHint}>
                {pendingMedia === 'video'
                  ? '视频生成约需 30-90 秒，请耐心等待，完成后会自动展示'
                  : '图片生成中，请稍候'}
              </div>
            </div>
          </div>
        )}

        {!isUser && message.ragSources && message.ragSources.length > 0 && (
          <RagSourcesCard sources={message.ragSources} />
        )}

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
