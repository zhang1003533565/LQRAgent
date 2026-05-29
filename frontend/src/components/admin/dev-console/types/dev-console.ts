import type { AgentId } from '@/utils/types/agent-events'

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
  agent: string
  status: 'running' | 'idle' | 'failed'
  latencyMs: number
  tokens: number
  total?: number
  success?: number
  failed?: number
  successRate?: string
  source: 'mock' | 'api'
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
  | 'users'
  | 'profile'
  | 'knowledge'
  | 'path'
  | 'resources'
  | 'upload'
  | 'system-config'
  | 'model-config'
  // 智能体管理（8 个 Agent + 模型配置）
  | 'agent-orchestrator'
  | 'agent-qa'
  | 'agent-learningpath'
  | 'agent-resourcefacade'
  | 'agent-learnerprofile'
  | 'agent-qualityassessment'
  | 'agent-contentanalyzer'
  | 'agent-effectassessment'
  | 'agent-mediagen'
  | 'quiz-records'
  | 'study-behaviors'
