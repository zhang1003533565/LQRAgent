/** WebSocket 服务端事件类型（与项目精简开发指南 §3 对齐） */

export type WsInboundType =
  | 'chunk'
  | 'agent_step'
  | 'artifact'
  | 'profile_patch'
  | 'done'
  | 'error'

export type AgentId =
  | 'orchestrator'
  | 'content_analyzer'
  | 'path_planner'
  | 'resource_facade'
  | 'quality_assessment'
  | 'learner_profile'
  | 'qa_agent'
  | 'effect_assessment'

export type AgentStepStatus = 'running' | 'done' | 'failed'

export interface AgentStepEvent {
  type: 'agent_step'
  agent: AgentId
  label: string
  status: AgentStepStatus
  detail?: string
  session_id?: string
}

export interface ChunkEvent {
  type: 'chunk'
  content?: string
  session_id?: string
}

export interface DoneEvent {
  type: 'done'
  session_id?: string
}

export interface ErrorEvent {
  type: 'error'
  content?: string
  session_id?: string
}

/** 解析后的 WS 消息（各 handler 按 type 分发） */
export interface WsRawMessage {
  type: string
  content?: string
  session_id?: string
  agent?: AgentId
  label?: string
  status?: AgentStepStatus
  detail?: string
  kind?: string
  payload?: unknown
}
