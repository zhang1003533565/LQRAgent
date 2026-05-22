import { useState } from 'react'
import { useWebSocket } from '@/hooks/useWebSocket'
import MainTabBar, { type LearnTab } from '../context/MainTabBar'
import PathSection from '../context/PathSection'
import ResourceSection from '../context/ResourceSection'
import QuizSection from '../context/QuizSection'
import AgentTimeline from './AgentTimeline'
import ChatMessageList from './ChatMessageList'
import ChatComposer from './ChatComposer'
import styles from './ChatView.module.css'

/** 聊天页：主区 Tab 切换对话 / 路径 / 资源 / 练习 */
export default function ChatView() {
  const [tab, setTab] = useState<LearnTab>('chat')
  const { send } = useWebSocket()

  return (
    <div className={styles.view}>
      <MainTabBar active={tab} onChange={setTab} />

      {tab === 'chat' && (
        <div className={styles.chat}>
          <AgentTimeline />
          <ChatMessageList />
          <ChatComposer onSend={send} />
        </div>
      )}
      {tab === 'path' && <PathSection />}
      {tab === 'resources' && <ResourceSection />}
      {tab === 'quiz' && <QuizSection />}
    </div>
  )
}
