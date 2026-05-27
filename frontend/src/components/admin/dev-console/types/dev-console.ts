import type { AgentId } from '@/components/user/types/agent-events'

/** Dev Console 专用类型（P3 Agent 监控 / 运行时 API 预留） */

export type ServiceStatus = 'online' | 'offline' | 'connected' | 'degraded'

export interface ServiceHealth {
  id: string
  name: string
  status: ServiceStatus
  /** 对接：GET /admin/status 部分字段 */
  source: 'api' | 'mock'
}

export interface DashboardMetrics {
  requestsToday: number
  tokensToday: number
  agentCallsToday: number
  avgResponseMs: number
  onlineUsers: number
  /** MOCK: 待对接 GET /admin/metrics/dashboard */
  source: 'mock'
}

export interface MetricSparkline {
  label: string
  values: number[]
}

export interface RecentTask {
  id: string
  studentId: string
  description: string
  status: 'running' | 'completed' | 'failed'
  startedAt: string
  source: 'mock' | 'upload'
}

export interface AgentRuntimeStatus {
  agent: AgentId
  status: 'running' | 'idle' | 'failed'
  latencyMs: number
  tokens: number
  /** MOCK: WS agent_step 聚合 或 GET /admin/agents/runtime */
  source: 'mock'
}

/** React Flow 要求 node.data 满足 Record<string, unknown> */
export interface TraceNodeData extends Record<string, unknown> {
  label: string
  agent?: AgentId
  status: 'success' | 'running' | 'pending' | 'failed'
  durationMs: number
  tokens: number
}

export type LogLevel = 'INFO' | 'WARN' | 'ERROR' | 'DEBUG'

export interface DevLogEntry {
  id: string
  level: LogLevel
  message: string
  timestamp: string
  /** 待对接：管理端 WS 或 GET /admin/logs/stream */
  source: 'mock' | 'ws'
}

export type DevConsoleNavId =
  | 'dashboard'
  | 'agent-debug'
  | 'trace'
  | 'logs'
  | 'users'
  | 'profile'
  | 'knowledge'
  | 'path'
  | 'resources'
  | 'upload'
  | 'system-config'
  | 'model-config'
  | 'prompts'
  | 'params'
