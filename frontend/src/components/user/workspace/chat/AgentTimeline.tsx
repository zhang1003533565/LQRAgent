import { useAgentTraceStore } from '@/components/user/store/agentTraceStore'
import { AGENT_LABELS } from '@/components/user/constants/agent-labels'
import { EmptyState } from '@/components/user/ui'
import styles from './AgentTimeline.module.css'

export default function AgentTimeline() {
  const steps = useAgentTraceStore((s) => s.steps)

  if (steps.length === 0) {
    return (
      <div className={styles.wrap}>
        <p className={styles.heading}>智能体协作</p>
        <EmptyState
          title="等待本轮任务"
          description="发送消息后，协调、路径、资源等 Agent 步骤将显示在此"
        />
      </div>
    )
  }

  return (
    <div className={styles.wrap}>
      <p className={styles.heading}>智能体协作</p>
      <ol className={styles.list}>
        {steps.map((step) => (
          <li
            key={step.id}
            className={`${styles.item} ${styles[step.status]}`}
          >
            <span className={styles.dot} />
            <div className={styles.body}>
              <span className={styles.agent}>
                {AGENT_LABELS[step.agent] ?? step.agent}
              </span>
              <span className={styles.label}>{step.label}</span>
              {step.detail && (
                <span className={styles.detail}>{step.detail}</span>
              )}
            </div>
            <span className={styles.status}>{statusText(step.status)}</span>
          </li>
        ))}
      </ol>
    </div>
  )
}

function statusText(status: string) {
  if (status === 'running') return '进行中'
  if (status === 'done') return '完成'
  if (status === 'failed') return '失败'
  return status
}
