import { useChatStore } from '@/utils/store/chatStore'
import { useAgentTraceStore } from '@/utils/store/agentTraceStore'
import { useArtifactStore } from '@/utils/store/artifactStore'
import { usePathStore } from '@/utils/store/pathStore'
import { useProfileStore } from '@/utils/store/profileStore'
import { chatApi } from '@/api/student/chat'
import { STEP_LABELS, AGENT_LABELS } from '@/utils/constants/agent-labels'
import type { AgentId, AgentStepStatus, WsRawMessage } from '@/utils/types/agent-events'
import type { ArtifactKind, MediaImagePayload, RagSource } from '@/utils/types/artifact'
import type { LearningPathArtifactPayload } from '@/utils/types/artifact'
import type { MultiCardBlock } from '@/utils/types/multi-card'
import type { ProfileSummary } from '@/utils/types/profile'

function saveMessageMetadata(messageId: string, metadata: Record<string, unknown>) {
  chatApi.updateMessageMetadata(messageId, metadata).catch(() => {})
}

function handleArtifact(kind: ArtifactKind, payload: unknown) {
  const artifact = useArtifactStore.getState()
  artifact.setLastKind(kind)

  if (kind === 'learning_path') {
    const p = payload as LearningPathArtifactPayload
    if (p?.nodes) {
      usePathStore.getState().setPath({
        goal: p.goal ?? '',
        nodes: p.nodes,
        planDescription: p.planDescription ?? '',
      })
      const msgs = useChatStore.getState().messages
      const last = [...msgs].reverse().find((m) => m.role === 'assistant')
      if (last) {
        useChatStore.getState().updateMessage(last.id, {
          contentType: 'learning_path',
          streaming: false,
        })
        saveMessageMetadata(last.id, { contentType: 'learning_path' })
      }
    }
    return
  }

  if (kind === 'diagram' && payload) {
    const p = payload as { topic?: string; diagram?: string; format?: string }
    if (p.diagram) {
      const msgs = useChatStore.getState().messages
      const last = [...msgs].reverse().find((m) => m.role === 'assistant')
      if (last) {
        useChatStore.getState().updateMessage(last.id, {
          contentType: 'diagram',
          diagramCode: p.diagram,
          diagramFormat: p.format || 'mermaid',
          streaming: false,
        })
        saveMessageMetadata(last.id, {
          contentType: 'diagram',
          diagramCode: p.diagram,
          diagramFormat: p.format || 'mermaid',
        })
      }
    }
    return
  }

  if (kind === 'multi_card' && Array.isArray(payload)) {
    artifact.setMultiCardBlocks(payload as MultiCardBlock[])
    const msgs = useChatStore.getState().messages
    const last = [...msgs].reverse().find((m) => m.role === 'assistant')
    if (last) {
      useChatStore.getState().updateMessage(last.id, {
        contentType: 'multi_card',
        cards: payload as MultiCardBlock[],
        streaming: false,
      })
      saveMessageMetadata(last.id, { contentType: 'multi_card', cards: payload })
    }
  }

  if (kind === 'rag_sources' && Array.isArray(payload)) {
    const sources = payload as RagSource[]
    const msgs = useChatStore.getState().messages
    const last = [...msgs].reverse().find((m) => m.role === 'assistant')
    if (last) {
      useChatStore.getState().updateMessage(last.id, { ragSources: sources })
      saveMessageMetadata(last.id, { ragSources: sources })
    }
  }

  if (kind === 'media_image' && payload) {
    const p = payload as MediaImagePayload
    if (p.url) {
      const msgs = useChatStore.getState().messages
      const last = [...msgs].reverse().find((m) => m.role === 'assistant')
      if (last) {
        useChatStore.getState().updateMessage(last.id, {
          contentType: 'image',
          imageUrl: p.url,
          streaming: false,
        })
        saveMessageMetadata(last.id, { contentType: 'image', imageUrl: p.url })
      }
    }
    return
  }
}

export function dispatchWsMessage(data: WsRawMessage) {
  const {
    appendToLastMessage,
    setStreaming,
    setSessionId,
    addMessage,
    updateMessage,
  } = useChatStore.getState()
  const upsertStep = useAgentTraceStore.getState().upsertStep
  const patchSummary = useProfileStore.getState().patchSummary

  if (data.session_id) setSessionId(data.session_id)

  switch (data.type) {
    case 'session_created':
      if (data.session_id) setSessionId(data.session_id)
      break
    case 'chunk':
      appendToLastMessage(data.content ?? '')
      break
    case 'agent_step':
      if (data.agent && data.label && data.status) {
        upsertStep({
          agent: data.agent as AgentId,
          label: data.label,
          status: data.status as AgentStepStatus,
          detail: data.detail,
        })
      } else if (data.stepId && data.agentId) {
        // 来自 OrchestratorCore 的 Pipeline 步骤回调
        const stepLabel = STEP_LABELS[data.stepId] || AGENT_LABELS[data.agentId as AgentId] || data.stepId
        upsertStep({
          id: `pipeline-${data.stepId}`,
          agent: data.agentId as AgentId,
          label: stepLabel,
          status: data.success === true ? 'done' : data.success === false ? 'failed' : 'running',
          detail: data.taskId,
        })
      }
      break
    case 'pipeline_start':
      if (data.steps && Array.isArray(data.steps)) {
        for (const s of data.steps) {
          const stepLabel = STEP_LABELS[s.stepId] || AGENT_LABELS[s.agentId as AgentId] || s.stepId
          upsertStep({
            id: `pipeline-${s.stepId}`,
            agent: s.agentId as AgentId,
            label: stepLabel,
            status: 'pending' as AgentStepStatus,
            detail: data.taskId,
          })
        }
      }
      break
    case 'pipeline_complete':
      break
    case 'pipeline_error':
      break
    case 'artifact':
      if (data.kind) {
        handleArtifact(data.kind as ArtifactKind, data.payload)
      }
      break
    case 'profile_patch':
      if (data.payload && typeof data.payload === 'object') {
        patchSummary(data.payload as Partial<ProfileSummary>)
      }
      break
    case 'done': {
      const msgs = useChatStore.getState().messages
      const last = [...msgs].reverse().find((m) => m.role === 'assistant')
      if (last) setStreaming(last.id, false)
      break
    }
    case 'error': {
      const msgs = useChatStore.getState().messages
      const last = [...msgs].reverse().find((m) => m.role === 'assistant')
      if (last) {
        updateMessage(last.id, {
          content: (last.content || '') + `\n\n⚠️ ${data.content ?? '发生错误'}`,
          streaming: false,
        })
      } else {
        addMessage({
          id: crypto.randomUUID(),
          role: 'assistant',
          content: data.content ?? '发生错误',
          createdAt: new Date(),
        })
      }
      break
    }
    default:
      break
  }
}
