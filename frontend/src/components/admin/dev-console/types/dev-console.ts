/** Dev Console 专用类型 */

export type ServiceStatus = 'online' | 'offline' | 'connected' | 'degraded'

export interface ServiceHealth {
  id: string
  name: string
  status: ServiceStatus
  source: 'api' | 'mock'
}

export interface DashboardMetrics {
  requestsToday: number
  tokensToday: number
  agentCallsToday: number
  avgResponseMs: number
  onlineUsers: number
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

export type LogLevel = 'INFO' | 'WARN' | 'ERROR' | 'DEBUG'

export interface DevLogEntry {
  id: string
  level: LogLevel
  message: string
  timestamp: string
  source: 'mock' | 'ws'
}

/** 精简后的导航 ID：5 组 ~15 项 */
export type DevConsoleNavId =
  | 'dashboard'
  // 内容管理
  | 'knowledge'
  | 'resources'
  | 'upload'
  | 'knowledge-base'
  // 学员管理
  | 'users'
  | 'profile'
  | 'path'
  | 'quiz-records'
  | 'study-behaviors'
  // 智能体
  | 'agents'
  // 系统
  | 'model-config'
  | 'system-config'
