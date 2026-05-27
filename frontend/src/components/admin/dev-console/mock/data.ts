import type { AgentId } from '@/components/user/types/agent-events'
import { AGENT_LABELS } from '@/components/user/constants/agent-labels'
import type {
  AgentRuntimeStatus,
  DashboardMetrics,
  DevLogEntry,
  MetricSparkline,
  RecentTask,
  ServiceHealth,
  TraceNodeData,
} from '@/components/admin/dev-console/types/dev-console'

/** MOCK: 仪表盘指标 — 待 GET /admin/metrics/dashboard */
export const MOCK_METRICS: DashboardMetrics = {
  requestsToday: 1248,
  tokensToday: 2_340_000,
  agentCallsToday: 3578,
  avgResponseMs: 1320,
  onlineUsers: 23,
  source: 'mock',
}

export const MOCK_SPARKLINES: Record<string, number[]> = {
  requests: [820, 910, 880, 1020, 1100, 1180, 1248],
  tokens: [1.6, 1.7, 1.8, 1.9, 2.0, 2.2, 2.34].map((v) => v * 1e6),
  agents: [2800, 3000, 3100, 3200, 3400, 3500, 3578],
  latency: [1.5, 1.45, 1.4, 1.38, 1.35, 1.33, 1.32],
  users: [15, 17, 18, 19, 21, 22, 23],
}

export const MOCK_STAT_CARDS: {
  key: string
  title: string
  value: number
  unit?: string
  delta: string
  deltaUp: boolean
  color: string
  sparkKey: keyof typeof MOCK_SPARKLINES
}[] = [
  {
    key: 'requests',
    title: '今日请求数',
    value: MOCK_METRICS.requestsToday,
    delta: '+18.6%',
    deltaUp: true,
    color: '#8B5CF6',
    sparkKey: 'requests',
  },
  {
    key: 'tokens',
    title: '今日 Token 消耗',
    value: MOCK_METRICS.tokensToday,
    unit: '',
    delta: '+22.7%',
    deltaUp: true,
    color: '#3B82F6',
    sparkKey: 'tokens',
  },
  {
    key: 'agents',
    title: 'Agent 调用次数',
    value: MOCK_METRICS.agentCallsToday,
    delta: '+15.3%',
    deltaUp: true,
    color: '#10B981',
    sparkKey: 'agents',
  },
  {
    key: 'latency',
    title: '平均响应时间',
    value: MOCK_METRICS.avgResponseMs,
    unit: 'ms',
    delta: '-0.23s',
    deltaUp: false,
    color: '#F59E0B',
    sparkKey: 'latency',
  },
  {
    key: 'users',
    title: '在线用户数',
    value: MOCK_METRICS.onlineUsers,
    delta: '+5',
    deltaUp: true,
    color: '#06B6D4',
    sparkKey: 'users',
  },
]

/** 部分字段可由 getAdminStatus() 覆盖 */
export const MOCK_SERVICES: ServiceHealth[] = [
  { id: 'spring', name: 'Spring Boot', status: 'online', source: 'mock' },
  { id: 'ai', name: 'AI Server', status: 'online', source: 'mock' },
  { id: 'ws', name: 'WebSocket', status: 'connected', source: 'mock' },
  { id: 'mysql', name: 'MySQL', status: 'connected', source: 'mock' },
  { id: 'redis', name: 'Redis', status: 'online', source: 'mock' },
  { id: 'vector', name: '向量数据库', status: 'online', source: 'mock' },
]

/** MOCK: 编排任务 — 可对齐上传队列 / 学习路径生成任务 */
export const MOCK_RECENT_TASKS: RecentTask[] = [
  {
    id: 't1',
    studentId: 'student1',
    description: '学习路径生成',
    status: 'running',
    startedAt: new Date(Date.now() - 120_000).toISOString(),
    source: 'mock',
  },
  {
    id: 't2',
    studentId: 'student2',
    description: 'Quiz 生成',
    status: 'running',
    startedAt: new Date(Date.now() - 300_000).toISOString(),
    source: 'mock',
  },
  {
    id: 't3',
    studentId: 'student3',
    description: '问答处理',
    status: 'completed',
    startedAt: new Date(Date.now() - 600_000).toISOString(),
    source: 'mock',
  },
]

const AGENT_IDS: AgentId[] = [
  'orchestrator',
  'path_planner',
  'resource_facade',
  'quality_assessment',
  'learner_profile',
  'qa_agent',
]

/** MOCK: Agent 运行时 — 与 types/agent-events AgentId 一致 */
export const MOCK_AGENT_RUNTIME: AgentRuntimeStatus[] = AGENT_IDS.map((agent, i) => ({
  agent,
  status: i === 0 ? 'running' : i < 3 ? 'idle' : 'idle',
  latencyMs: [512, 890, 1240, 680, 420, 2100][i] ?? 500,
  tokens: [320, 890, 1240, 156, 89, 2100][i] ?? 0,
  source: 'mock' as const,
}))

export const MOCK_TRACE_META = {
  traceId: 'tr_8f3a2c91',
  request: '我想学习 Python 装饰器',
  status: 'success' as const,
  totalMs: 4580,
  totalTokens: 1523,
}

/** Trace 节点 — 对齐项目精简开发指南 §4.2 调度链 */
export const MOCK_TRACE_NODES: TraceNodeData[] = [
  { label: '用户请求', status: 'success', durationMs: 0, tokens: 0 },
  { label: AGENT_LABELS.orchestrator, agent: 'orchestrator', status: 'success', durationMs: 2440, tokens: 320 },
  { label: AGENT_LABELS.path_planner, agent: 'path_planner', status: 'success', durationMs: 890, tokens: 890 },
  { label: AGENT_LABELS.resource_facade, agent: 'resource_facade', status: 'success', durationMs: 1240, tokens: 1240 },
  { label: AGENT_LABELS.quality_assessment, agent: 'quality_assessment', status: 'success', durationMs: 680, tokens: 156 },
  { label: AGENT_LABELS.learner_profile, agent: 'learner_profile', status: 'success', durationMs: 420, tokens: 89 },
]

export const MOCK_INITIAL_LOGS: DevLogEntry[] = [
  {
    id: '1',
    level: 'INFO',
    message: 'WebSocket 已连接',
    timestamp: new Date().toISOString(),
    source: 'mock',
  },
  {
    id: '2',
    level: 'INFO',
    message: '调用 agent: path_planner',
    timestamp: new Date().toISOString(),
    source: 'mock',
  },
  {
    id: '3',
    level: 'INFO',
    message: '资源生成完成',
    timestamp: new Date().toISOString(),
    source: 'mock',
  },
  {
    id: '4',
    level: 'WARN',
    message: 'embedding 请求超时，重试 (1/3)',
    timestamp: new Date().toISOString(),
    source: 'mock',
  },
]

export function formatTokens(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(2)}M`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`
  return String(n)
}

export const METRIC_SPARKLINES: MetricSparkline[] = Object.entries(MOCK_SPARKLINES).map(
  ([label, values]) => ({ label, values }),
)
