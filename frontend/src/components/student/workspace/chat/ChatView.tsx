import { useWebSocket } from '@/utils/hooks/useWebSocket'
import ChatMessageList from './ChatMessageList'
import ChatComposer from './ChatComposer'
import AgentStepsBar from './AgentStepsBar'
import styles from './ChatView.module.css'

export default function ChatView() {
  const { send } = useWebSocket()

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>聊天学习</h1>
      </header>

      <div className={styles.scrollBody}>
        <div className={styles.content}>
          <AgentStepsBar />
          <ChatMessageList />
        </div>
      </div>

      <ChatComposer onSend={send} />
    </section>
  )
}
