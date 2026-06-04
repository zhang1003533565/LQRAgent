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
  | 'profile_agent'
  | 'learning_path_agent'
  | 'resource_agent'
  | 'quality_agent'
  | 'effect_agent'
  | 'qa_agent'
  | 'content_analysis_agent'
  | 'intelligent_qa'
  | 'learner_profile'
  | 'content_analyzer'
  | 'path_planner'
  | 'resource_facade'
  | 'quality_assessment'
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
