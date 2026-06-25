import type { MessageAgentStep } from '@/utils/types/chat'
import { useChatStore } from '@/utils/store/chatStore'
import type { AgentId, AgentStepStatus } from '@/utils/types/agent-events'

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
    agent: AgentId | string
    label: string
    status: AgentStepStatus
    detail?: string
  },
): Omit<MessageAgentStep, 'updatedAt'> {
  return {
    id: input.id ?? `${input.agent}-${input.label}`,
    agent: input.agent,
    label: input.label,
    status: input.status,
    detail: input.detail,
  }
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
