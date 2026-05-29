import { useAgentTraceStore } from '@/utils/store/agentTraceStore'
import { AGENT_LABELS } from '@/utils/constants/agent-labels'
import styles from './AgentTimeline.module.css'

export default function AgentTimeline() {
  const steps = useAgentTraceStore((s) => s.steps)

  return (
    <div className={styles.wrap}>
      <p className={styles.heading}>智能体协作</p>

      {steps.length === 0 ? (
        <div className={styles.emptyHint}>
          <div className={styles.emptyIcon}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <path d="M12 6v6l4 2" />
            </svg>
          </div>
          <p className={styles.emptyTitle}>等待本轮任务</p>
          <p className={styles.emptyDesc}>发送消息后，协调、路径、资源等 Agent 步骤将显示在此</p>
        </div>
      ) : (
        <ol className={styles.list}>
          {steps.map((step) => (
            <li
              key={step.id}
              className={`${styles.item} ${styles[step.status]}`}
            >
              <div className={styles.dotWrap}>
                <span className={styles.dot} />
              </div>
              <div className={styles.body}>
                <span className={styles.agent}>
                  {AGENT_LABELS[step.agent] ?? step.agent}
                </span>
                <span className={styles.label}>{step.label}</span>
                {step.detail && (
                  <span className={styles.detail}>{step.detail}</span>
                )}
              </div>
              <span className={styles.badge}>{statusText(step.status)}</span>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

function statusText(status: string) {
  if (status === 'running') return '进行中'
  if (status === 'done') return '完成'
  if (status === 'failed') return '失败'
  return status
}
