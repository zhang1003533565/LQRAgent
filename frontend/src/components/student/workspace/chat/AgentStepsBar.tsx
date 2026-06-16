/**
 * 智能体协作进度条 — 展示 Agent 调用链、耗时、状态
 */
import { useMemo, useState, useEffect } from 'react'
import { useAgentTraceStore, type AgentTraceStep } from '@/utils/store/agentTraceStore'
import { AGENT_LABELS } from '@/utils/constants/agent-labels'
import styles from './AgentStepsBar.module.css'

const STATUS_ICONS: Record<string, string> = {
  done: '✓',
  running: '●',
  failed: '✕',
  pending: '',
}

const STATUS_CLASSES: Record<string, string> = {
  done: styles.statusDone,
  running: styles.statusRunning,
  failed: styles.statusFailed,
  pending: styles.statusPending,
}

function formatElapsed(step: AgentTraceStep): string {
  const ms = new Date().getTime() - new Date(step.updatedAt).getTime()
  if (ms < 1000) return '<1s'
  if (ms < 60000) return `${Math.round(ms / 1000)}s`
  return `${Math.round(ms / 60000)}m`
}

export default function AgentStepsBar() {
  const steps = useAgentTraceStore((s) => s.steps)
  // 每秒刷新一次耗时显示
  const [, setTick] = useState(0)
  useEffect(() => {
    const timer = setInterval(() => setTick((t) => t + 1), 1000)
    return () => clearInterval(timer)
  }, [])

  const visible = useMemo(() => steps.slice(-8), [steps])

  // 计算进度摘要
  const doneCount = visible.filter((s) => s.status === 'done').length
  const runningStep = visible.find((s) => s.status === 'running')
  const hasPipeline = visible.some((s) => s.id?.startsWith('pipeline-'))

  if (visible.length === 0) return null

  return (
    <div className={styles.bar}>
      <span className={styles.title}>智能体协作</span>
      {hasPipeline && (
        <span className={styles.progressText}>
          {doneCount}/{visible.length} 步完成
          {runningStep && ` · 正在执行：${runningStep.label}`}
        </span>
      )}
      <div className={styles.chain}>
        {visible.map((step, i) => {
          const label = AGENT_LABELS[step.agent] || step.label || step.agent
          const icon = STATUS_ICONS[step.status] ?? ''
          const statusClass = STATUS_CLASSES[step.status] ?? styles.statusPending
          const elapsed = formatElapsed(step)

          return (
            <div key={step.id} className={styles.stepWrapper}>
              {i > 0 && (
                <div className={styles.arrow}>
                  <svg width="16" height="8" viewBox="0 0 16 8" fill="none">
                    <path d="M0 4h12M10 1l3 3-3 3" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </div>
              )}
              <div className={styles.step}>
                <div className={`${styles.dot} ${statusClass}`}>
                  {icon}
                </div>
                <span className={styles.label}>{label}</span>
                {elapsed && <span className={styles.elapsed}>{elapsed}</span>}
                {step.detail && (
                  <div className={styles.tooltip}>
                    {step.detail}
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
