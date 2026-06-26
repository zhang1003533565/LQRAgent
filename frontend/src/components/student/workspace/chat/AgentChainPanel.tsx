/**
 * 聊天气泡内的智能体调用链 — 流式时展开，正文完成后默认折叠
 */
import { useEffect, useMemo, useState } from 'react'
import type { MessageAgentStep } from '@/utils/types/chat'
import { AGENT_LABELS } from '@/utils/constants/agent-labels'
import { splitAgentSteps, getChildAgentSteps } from '@/utils/chat/messageAgentSteps'
import styles from './AgentChainPanel.module.css'

const STATUS_ICON: Record<string, string> = {
  done: '✓',
  running: '●',
  failed: '✕',
  pending: '○',
}

interface Props {
  steps: MessageAgentStep[]
  streaming?: boolean
  collapsed?: boolean
  onToggleCollapsed?: (collapsed: boolean) => void
}

function stepTime(step: MessageAgentStep): number {
  const d = step.updatedAt instanceof Date ? step.updatedAt : new Date(step.updatedAt)
  return d.getTime()
}

function formatElapsed(step: MessageAgentStep, now: number): string {
  const ms = now - stepTime(step)
  if (ms < 1000) return '<1s'
  if (ms < 60000) return `${Math.round(ms / 1000)}s`
  return `${Math.round(ms / 60000)}m`
}

function StepRow({
  step,
  index,
  now,
  children,
  allSteps,
}: {
  step: MessageAgentStep
  index: number
  now: number
  children?: MessageAgentStep[]
  allSteps: MessageAgentStep[]
}) {
  const label = step.label || AGENT_LABELS[step.agent as keyof typeof AGENT_LABELS] || step.agent
  const icon = STATUS_ICON[step.status] ?? '○'
  const childSteps = children ?? getChildAgentSteps(allSteps, step.id)

  return (
    <div className={styles.stepGroup}>
      <div className={styles.row}>
        {index > 0 && <div className={styles.connector} />}
        <div className={`${styles.node} ${styles[`status_${step.status}`] ?? ''}`}>
          <span className={styles.nodeIcon}>{icon}</span>
        </div>
        <div className={styles.meta}>
          <span className={styles.label}>{label}</span>
          <span className={styles.metaRight}>
            {step.status === 'running' && <span className={styles.runningTag}>执行中</span>}
            {step.status === 'done' && <span className={styles.doneTag}>完成</span>}
            {step.status === 'failed' && <span className={styles.failedTag}>失败</span>}
            {step.status === 'pending' && <span className={styles.pendingTag}>等待</span>}
            <span className={styles.elapsed}>{formatElapsed(step, now)}</span>
          </span>
        </div>
        {step.detail && !childSteps.length && (
          <div className={styles.stepDetail}>{step.detail}</div>
        )}
      </div>
      {childSteps.length > 0 && (
        <div className={styles.childTimeline}>
          {childSteps.map((child) => {
            const childLabel = child.label || AGENT_LABELS[child.agent as keyof typeof AGENT_LABELS] || child.agent
            return (
              <div key={child.id} className={styles.childRow}>
                <span className={styles.childDot}>·</span>
                <span className={styles.childLabel}>{childLabel}</span>
                {child.detail && (
                  <span className={styles.childDetail} title={child.detail}>
                    {child.detail.length > 48 ? `${child.detail.slice(0, 48)}…` : child.detail}
                  </span>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default function AgentChainPanel({
  steps,
  streaming = false,
  collapsed = false,
  onToggleCollapsed,
}: Props) {
  const [, setTick] = useState(0)
  const [manualExpanded, setManualExpanded] = useState<boolean | null>(null)

  useEffect(() => {
    if (!streaming) return undefined
    const timer = setInterval(() => setTick((t) => t + 1), 1000)
    return () => clearInterval(timer)
  }, [streaming])

  const { main, background, all } = useMemo(() => splitAgentSteps(steps), [steps])
  const visible = main
  const now = Date.now()

  if (visible.length === 0 && background.length === 0) return null

  const doneCount = visible.filter((s) => s.status === 'done').length
  const totalCount = visible.length
  const runningStep = visible.find((s) => s.status === 'running')
  const expanded = streaming ? true : (manualExpanded ?? !collapsed)

  const summaryLabels = visible
    .slice(0, 4)
    .map((s) => s.label || AGENT_LABELS[s.agent as keyof typeof AGENT_LABELS] || s.agent)
    .join(' → ')

  const handleToggle = () => {
    if (streaming) return
    const next = !expanded
    setManualExpanded(next)
    onToggleCollapsed?.(!next)
  }

  return (
    <div className={`${styles.panel} ${expanded ? styles.expanded : styles.collapsed}`}>
      <button
        type="button"
        className={styles.header}
        onClick={handleToggle}
        aria-expanded={expanded}
      >
        <span className={styles.headerIcon}>⛓</span>
        <span className={styles.headerText}>
          {streaming ? (
            <>
              智能体协作中
              {runningStep && ` · ${runningStep.label}`}
            </>
          ) : (
            <>
              {totalCount > 0
                ? `${doneCount}/${totalCount} 步完成`
                : '处理完成'}
              {!expanded && summaryLabels && ` · ${summaryLabels}`}
            </>
          )}
        </span>
        {!streaming && (
          <span className={styles.chevron}>{expanded ? '▴' : '▾'}</span>
        )}
        {streaming && runningStep && (
          <span className={styles.liveDot} />
        )}
      </button>

      {expanded && (
        <div className={styles.timeline}>
          {visible.map((step, i) => (
            <StepRow key={step.id} step={step} index={i} now={now} allSteps={all} />
          ))}
          {background.length > 0 && (
            <div className={styles.backgroundRow}>
              <span className={styles.backgroundLabel}>后台</span>
              <span className={styles.backgroundText}>
                {background.map((s) => s.label).join(' · ')}
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
