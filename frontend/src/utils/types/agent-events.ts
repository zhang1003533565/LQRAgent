/** WebSocket 服务端事件类型（与项目精简开发指南 §3 对齐） */

export type WsInboundType =
  | 'chunk'
  | 'agent_step'
  | 'artifact'
  | 'profile_patch'
  | 'pipeline_start'
  | 'pipeline_complete'
  | 'pipeline_error'
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
  | 'effect_assessment'  | 'knowledge_state_agent'
  | 'spaced_repetition_agent'
  | 'difficulty_agent'
  | 'learning_style_agent'
  | 'diagram_agent'
  | 'summary_agent'
  | 'recommendation_agent'
  | 'assessment_agent'
  | 'intervention_agent'
  | 'motivation_agent'
  | 'resource_agent'
  | 'lesson_agent'
  | 'quiz_agent'
  | 'code_agent'
  | 'video_agent'
  | 'content_quality_agent'
  | 'pedagogy_quality_agent'
  | 'fact_check_agent'
  | 'profile_agent'
  | 'behavior_agent'
  | 'preference_agent'
  | 'knowledge_graph_agent'
  | 'prerequisite_agent

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
  stepId?: string
  stepCount?: number
  pipelineName?: string
  taskId?: string
  success?: boolean
  durationMs?: number
  steps?: { stepId: string; agentId: string; action: string }[]
  error?: string
}
