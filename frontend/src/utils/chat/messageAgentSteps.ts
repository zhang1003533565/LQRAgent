import type { MessageAgentStep } from '@/utils/types/chat'
import { useChatStore } from '@/utils/store/chatStore'
import type { AgentId, AgentStepStatus } from '@/utils/types/agent-events'
import { STEP_LABELS } from '@/utils/constants/agent-labels'

/** 路径协商步骤 ID，与 pipeline-path_consult 对齐 */
export const CONSULTATION_PARENT_STEP_ID = 'pipeline-path_consult'

const PIPELINE_STEP_IDS = new Set(Object.keys(STEP_LABELS))
const BACKGROUND_AGENTS = new Set(['learner_profile'])

/** 当前流式 assistant 消息，若无则取最后一条 assistant */
export function getActiveAssistantMessage() {
  const msgs = useChatStore.getState().messages
  const streaming = [...msgs].reverse().find((m) => m.role === 'assistant' && m.streaming)
  if (streaming) return streaming
  return [...msgs].reverse().find((m) => m.role === 'assistant')
}

export function upsertMessageAgentStep(
  step: Omit<MessageAgentStep, 'updatedAt'> & { updatedAt?: Date },
) {
  const target = getActiveAssistantMessage()
  if (!target) return

  const id = step.id
  const entry: MessageAgentStep = {
    id,
    agent: step.agent,
    label: step.label,
    status: step.status,
    detail: step.detail,
    parentId: step.parentId,
    updatedAt: step.updatedAt ?? new Date(),
  }

  const prev = target.agentSteps ?? []
  const idx = prev.findIndex((s) => s.id === id)
  const next = idx >= 0
    ? prev.map((s, i) => (i === idx ? entry : s))
    : [...prev, entry]

  useChatStore.getState().updateMessage(target.id, { agentSteps: next })
}

export function buildAgentStepInput(
  input: {
    id?: string
    stepId?: string
    agent: AgentId | string
    label: string
    status: AgentStepStatus
    detail?: string
    parentId?: string
  },
): Omit<MessageAgentStep, 'updatedAt'> {
  const id = input.id ?? resolveStepId({ stepId: input.stepId, agent: input.agent })
  const label = normalizeStepLabel(input.stepId, input.label)
  return {
    id,
    agent: input.agent,
    label,
    status: input.status,
    detail: input.detail,
    parentId: input.parentId,
  }
}

/** 与 pipeline_start / step 回调统一的步骤 ID */
export function resolveStepId(data: { stepId?: string; agent?: string }): string {
  if (data.stepId && PIPELINE_STEP_IDS.has(data.stepId)) {
    return `pipeline-${data.stepId}`
  }
  if (data.stepId) {
    return data.stepId
  }
  return data.agent ?? 'unknown'
}

/** 将「已完成」等泛化文案还原为步骤名 */
export function normalizeStepLabel(stepId: string | undefined, label: string): string {
  if (!stepId || !PIPELINE_STEP_IDS.has(stepId)) {
    return label
  }
  const canonical = STEP_LABELS[stepId]
  if (label === '已完成' || label === '执行失败') {
    return canonical
  }
  if (label.endsWith('…')) {
    return `${canonical}…`
  }
  return label
}

function stepStatusPriority(status: MessageAgentStep['status']): number {
  switch (status) {
    case 'done': return 4
    case 'failed': return 3
    case 'running': return 2
    case 'pending': return 1
    default: return 0
  }
}

function stepTimestamp(step: MessageAgentStep): number {
  const d = step.updatedAt instanceof Date ? step.updatedAt : new Date(step.updatedAt)
  return d.getTime()
}

/** 合并重复步骤、去掉未执行的 pending、按 Pipeline 顺序排列 */
export function normalizeAgentSteps(steps: MessageAgentStep[]): MessageAgentStep[] {
  if (!steps.length) return []

  const merged = new Map<string, MessageAgentStep>()
  for (const raw of steps) {
    let stepId = raw.id
    if (PIPELINE_STEP_IDS.has(stepId)) {
      stepId = `pipeline-${stepId}`
    }
    const bareStepId = stepId.startsWith('pipeline-') ? stepId.slice('pipeline-'.length) : undefined
    const normalized: MessageAgentStep = {
      ...raw,
      id: stepId,
      label: normalizeStepLabel(bareStepId, raw.label),
    }
    const existing = merged.get(stepId)
    if (!existing || stepStatusPriority(normalized.status) >= stepStatusPriority(existing.status)) {
      merged.set(stepId, normalized)
    }
  }

  let list = Array.from(merged.values()).filter(
    (s) => !(s.status === 'pending' && s.id.startsWith('pipeline-')),
  )

  const hasPipeline = list.some((s) => s.id.startsWith('pipeline-'))
  if (hasPipeline) {
    list = list.filter((s) => s.id !== 'orchestrator')
  }

  const pipelineOrder = Object.keys(STEP_LABELS)
  list.sort((a, b) => {
    const aPipe = a.id.startsWith('pipeline-')
    const bPipe = b.id.startsWith('pipeline-')
    if (aPipe && bPipe) {
      const ai = pipelineOrder.indexOf(a.id.replace('pipeline-', ''))
      const bi = pipelineOrder.indexOf(b.id.replace('pipeline-', ''))
      return ai - bi
    }
    if (aPipe && !bPipe) return -1
    if (!aPipe && bPipe) return 1
    return stepTimestamp(a) - stepTimestamp(b)
  })

  return list
}

export function splitAgentSteps(steps: MessageAgentStep[]) {
  const normalized = normalizeAgentSteps(steps)
  const topLevel = normalized.filter((s) => !s.parentId)
  return {
    main: topLevel.filter((s) => !BACKGROUND_AGENTS.has(s.agent)),
    background: topLevel.filter((s) => BACKGROUND_AGENTS.has(s.agent)),
    all: normalized,
  }
}

/** 获取某父步骤下的协商子步骤（按出现顺序） */
export function getChildAgentSteps(steps: MessageAgentStep[], parentId: string): MessageAgentStep[] {
  return steps.filter((s) => s.parentId === parentId)
}

/** done 时收尾：去掉悬空 pending，合并重复项 */
export function finalizeAgentSteps(steps?: MessageAgentStep[]): MessageAgentStep[] {
  return normalizeAgentSteps(steps ?? [])
}

export function serializeAgentSteps(steps?: MessageAgentStep[]) {
  if (!steps?.length) return undefined
  return steps.map((s) => ({
    ...s,
    updatedAt: s.updatedAt instanceof Date ? s.updatedAt.toISOString() : s.updatedAt,
  }))
}

export function hydrateAgentSteps(raw: unknown): MessageAgentStep[] | undefined {
  if (!Array.isArray(raw)) return undefined
  return raw.map((s) => ({
    ...s,
    updatedAt: s.updatedAt ? new Date(s.updatedAt) : new Date(),
  })) as MessageAgentStep[]
}

/** 检测 Clarify 编号问题样式 */
export function looksLikeClarify(content: string): boolean {
  if (!content?.trim()) return false
  const numbered = content.split('\n').filter((l) => /^\d+[.)]\s/.test(l.trim()))
  return numbered.length >= 2 && /请告诉我|补充|更多信息|量身定制|简单回复|安排学习路径/.test(content)
}
