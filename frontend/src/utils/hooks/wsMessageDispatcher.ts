import { useChatStore, flushPendingChunks } from '@/utils/store/chatStore'
import { useArtifactStore } from '@/utils/store/artifactStore'
import { usePathStore } from '@/utils/store/pathStore'
import { useProfileStore } from '@/utils/store/profileStore'
import { chatApi } from '@/api/student/chat'
import { STEP_LABELS, AGENT_LABELS } from '@/utils/constants/agent-labels'
import {
  upsertMessageAgentStep,
  buildAgentStepInput,
  serializeAgentSteps,
  looksLikeClarify,
  resolveStepId,
  normalizeStepLabel,
  finalizeAgentSteps,
  CONSULTATION_PARENT_STEP_ID,
} from '@/utils/chat/messageAgentSteps'
import type { AgentId, WsRawMessage } from '@/utils/types/agent-events'
import type { ArtifactKind, MediaImagePayload, MediaVideoPayload, QuizArtifactPayload, RagSource } from '@/utils/types/artifact'
import type { LearningPathArtifactPayload } from '@/utils/types/artifact'
import type { MultiCardBlock } from '@/utils/types/multi-card'
import type { ProfileSummary } from '@/utils/types/profile'

function isDbMessageId(id: string): boolean {
  return /^\d+$/.test(id)
}

function saveMessageMetadata(messageId: string, metadata: Record<string, unknown>) {
  if (!isDbMessageId(messageId)) return
  chatApi.updateMessageMetadata(messageId, metadata).catch(() => {})
}

function syncAssistantMessageId(dbMessageId: string) {
  const msgs = useChatStore.getState().messages
  const last = [...msgs].reverse().find((m) => m.role === 'assistant')
  if (!last || last.id === dbMessageId) return

  useChatStore.setState((state) => ({
    messages: state.messages.map((m) => {
      if (m.id !== last.id) return m
      return { ...m, id: dbMessageId }
    }),
  }))
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

  if ((kind === 'video' || kind === 'media_video') && payload) {
    const p = payload as MediaVideoPayload
    if (p.url) {
      const msgs = useChatStore.getState().messages
      const last = [...msgs].reverse().find((m) => m.role === 'assistant')
      if (last) {
        useChatStore.getState().updateMessage(last.id, {
          contentType: 'video',
          videoUrl: p.url,
          streaming: false,
        })
        saveMessageMetadata(last.id, { contentType: 'video', videoUrl: p.url })
      }
    }
    return
  }

  if (kind === 'quiz' && payload) {
    const p = payload as QuizArtifactPayload
    if (p.questions?.length) {
      const msgs = useChatStore.getState().messages
      const last = [...msgs].reverse().find((m) => m.role === 'assistant')
      if (last) {
        useChatStore.getState().updateMessage(last.id, {
          contentType: 'quiz',
          quizData: { title: p.title, topic: p.topic, difficulty: p.difficulty, questions: p.questions },
          streaming: false,
        })
        saveMessageMetadata(last.id, { contentType: 'quiz', quizData: p })
      }
    }
    return
  }

  if (kind === 'rag_sources' && payload) {
    const raw = payload as RagSource[] | { sources?: RagSource[] }
    const sources = Array.isArray(raw) ? raw : raw.sources
    if (sources?.length) {
      const msgs = useChatStore.getState().messages
      const last = [...msgs].reverse().find((m) => m.role === 'assistant')
      if (last) {
        useChatStore.getState().updateMessage(last.id, { ragSources: sources })
      }
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
  const patchSummary = useProfileStore.getState().patchSummary

  function upsertStep(step: Parameters<typeof upsertMessageAgentStep>[0]) {
    upsertMessageAgentStep(step)
  }

  if (data.session_id) setSessionId(String(data.session_id))

  switch (data.type) {
    case 'session_created':
      if (data.session_id) {
        setSessionId(String(data.session_id))
        useChatStore.getState().bumpSessionList()
      }
      break
    case 'chunk':
      appendToLastMessage(data.content ?? '')
      break
    case 'agent_step':
      if (data.agent && data.label && data.status) {
        upsertStep(buildAgentStepInput({
          stepId: data.stepId,
          id: resolveStepId({ stepId: data.stepId, agent: data.agent }),
          agent: data.agent,
          label: normalizeStepLabel(data.stepId, data.label),
          status: data.status,
          detail: data.detail,
        }))
      } else if (data.stepId && data.agentId) {
        const stepLabel = STEP_LABELS[data.stepId] || AGENT_LABELS[data.agentId as AgentId] || data.stepId
        upsertStep(buildAgentStepInput({
          stepId: data.stepId,
          id: `pipeline-${data.stepId}`,
          agent: data.agentId,
          label: stepLabel,
          status: data.success === true ? 'done' : data.success === false ? 'failed' : 'running',
          detail: data.taskId,
        }))
      }
      break
    case 'pipeline_start':
      if (data.steps && Array.isArray(data.steps)) {
        for (const s of data.steps) {
          const stepLabel = STEP_LABELS[s.stepId] || AGENT_LABELS[s.agentId as AgentId] || s.stepId
          upsertStep(buildAgentStepInput({
            stepId: s.stepId,
            id: `pipeline-${s.stepId}`,
            agent: s.agentId,
            label: stepLabel,
            status: 'pending',
            detail: data.taskId,
          }))
        }
      }
      break
    case 'consultation_start':
      upsertStep(buildAgentStepInput({
        id: CONSULTATION_PARENT_STEP_ID,
        stepId: 'path_consult',
        agent: 'learning_path_agent',
        label: `路径协商（最多 ${data.maxRounds ?? 2} 轮）`,
        status: 'running',
      }))
      break
    case 'consultation_round':
      if (data.agentId && data.role) {
        if (data.textDelta) {
          appendToLastMessage(`【协商第 ${data.round ?? 1} 轮·${data.role}】${data.textDelta}\n`)
        }
        const roleLabel =
          data.role === 'constraints' ? '画像约束'
          : data.role === 'draft' ? '路径草案'
          : data.role === 'revise' ? '难度调整'
          : data.role === 'approve' ? '评审通过'
          : data.role
        upsertStep(buildAgentStepInput({
          id: `consultation-r${data.round}-${data.role}`,
          parentId: CONSULTATION_PARENT_STEP_ID,
          agent: data.agentId,
          label: `第 ${data.round ?? 1} 轮 · ${roleLabel}`,
          status: 'done',
          detail: data.summary,
        }))
      }
      break
    case 'consultation_end':
      upsertStep(buildAgentStepInput({
        id: CONSULTATION_PARENT_STEP_ID,
        stepId: 'path_consult',
        agent: 'learning_path_agent',
        label: '路径协商',
        status: 'running',
        detail: data.stopReason,
      }))
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
      flushPendingChunks()
      const msgs = useChatStore.getState().messages
      const last = [...msgs].reverse().find((m) => m.role === 'assistant')
      const dbMessageId = data.message_id != null ? String(data.message_id) : null

      if (last) {
        const hasArtifact = Boolean(
          last.imageUrl ||
          last.videoUrl ||
          last.quizData ||
          last.diagramCode ||
          last.cards?.length ||
          last.contentType === 'image' ||
          last.contentType === 'video' ||
          last.contentType === 'quiz' ||
          last.contentType === 'diagram' ||
          last.contentType === 'learning_path' ||
          last.contentType === 'multi_card',
        )

        if (!last.content?.trim() && !hasArtifact) {
          updateMessage(last.id, {
            content: '回答生成失败或超时，请重试。',
            streaming: false,
            agentStepsCollapsed: true,
          })
        } else {
          const clarifyType =
            last.contentType === 'clarify' || looksLikeClarify(last.content)
              ? ('clarify' as const)
              : undefined

          if (!last.content?.trim() && hasArtifact) {
            const fallback = last.imageUrl
              ? '图片已生成。'
              : last.videoUrl
                ? '视频已生成。'
                : ''
            updateMessage(last.id, {
              ...(fallback ? { content: fallback } : {}),
              streaming: false,
              agentStepsCollapsed: true,
              ...(clarifyType ? { contentType: clarifyType } : {}),
            })
          } else if (last.imageUrl && last.contentType === 'image' && last.content && last.content.length > 120) {
            updateMessage(last.id, {
              content: '图片已生成。',
              streaming: false,
              agentStepsCollapsed: true,
            })
          } else {
            const finalizedSteps = finalizeAgentSteps(last.agentSteps)
            updateMessage(last.id, {
              streaming: false,
              agentStepsCollapsed: true,
              agentSteps: finalizedSteps,
              ...(clarifyType && last.contentType !== 'clarify' ? { contentType: clarifyType } : {}),
            })
          }
        }
      }

      if (dbMessageId) {
        syncAssistantMessageId(dbMessageId)
        const synced = useChatStore.getState().messages
          .slice()
          .reverse()
          .find((m) => m.role === 'assistant')
        if (synced && (synced.imageUrl || synced.videoUrl || synced.quizData || synced.diagramCode
            || synced.agentSteps?.length)) {
          saveMessageMetadata(dbMessageId, {
            contentType: synced.contentType,
            imageUrl: synced.imageUrl,
            videoUrl: synced.videoUrl,
            quizData: synced.quizData,
            diagramCode: synced.diagramCode,
            diagramFormat: synced.diagramFormat,
            ragSources: synced.ragSources,
            agentSteps: serializeAgentSteps(synced.agentSteps),
            ...(synced.imageUrl && synced.content && synced.content.length > 120
              ? { content: '图片已生成。' }
              : {}),
          })
        }
      }

      useChatStore.getState().bumpSessionList()
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
      useChatStore.getState().bumpSessionList()
      break
    }
    default:
      break
  }
}
