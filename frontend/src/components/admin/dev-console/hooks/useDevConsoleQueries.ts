import { useQuery } from '@tanstack/react-query'
import { getAdminStatus, getAgentStats, getAgentRuns } from '@/api/admin/admin'
import {
  MOCK_SERVICES,
} from '@/components/admin/dev-console/mock/data'
import type { AgentRuntimeStatus, RecentTask, ServiceHealth } from '@/components/admin/dev-console/types/dev-console'

/** 对接 GET /admin/status + GET /admin/agent-stats + GET /admin/agent-runs */
export function useDevConsoleOverview() {
  const statusQuery = useQuery({
    queryKey: ['dev-console', 'admin-status'],
    queryFn: getAdminStatus,
    refetchInterval: 15_000,
  })

  const agentStatsQuery = useQuery({
    queryKey: ['dev-console', 'agent-stats'],
    queryFn: getAgentStats,
    refetchInterval: 30_000,
  })

  const agentRunsQuery = useQuery({
    queryKey: ['dev-console', 'agent-runs'],
    queryFn: () => getAgentRuns(1, 10),
    refetchInterval: 15_000,
  })

  const services: ServiceHealth[] = statusQuery.data
    ? [
        { id: 'spring', name: 'Spring Boot', status: 'online', source: 'api' },
        {
          id: 'ai',
          name: 'AI Server',
          status: statusQuery.data.aiServerReachable ? 'online' : 'degraded',
          source: 'api',
        },
        { id: 'ws', name: 'WebSocket', status: 'connected', source: 'mock' },
        { id: 'mysql', name: 'MySQL', status: 'connected', source: 'mock' },
        ...MOCK_SERVICES.filter((s) => !['spring', 'ai', 'ws', 'mysql'].includes(s.id)),
      ]
    : MOCK_SERVICES

  // 将 agent-stats 转换为 AgentRuntimeStatus[]
  const agents: AgentRuntimeStatus[] = agentStatsQuery.data
    ? agentStatsQuery.data.stats.map((s) => ({
        agent: s.agent,
        status: s.failed > 0 && s.success === 0 ? 'failed' : 'idle',
        latencyMs: s.avgDurationMs,
        tokens: 0,
        total: s.total,
        success: s.success,
        failed: s.failed,
        successRate: s.successRate,
        source: 'api' as const,
      }))
    : []

  // 将 agent-runs 转换为 RecentTask[]
  const tasks: RecentTask[] = agentRunsQuery.data
    ? agentRunsQuery.data.items.slice(0, 8).map((run) => ({
        id: String(run.id),
        studentId: `user-${run.userId}`,
        description: `${run.agent} · ${run.intent || run.status}`,
        status: run.status === 'SUCCESS' ? 'completed' : run.status === 'FAILED' ? 'failed' : 'running',
        startedAt: run.createdAt,
        source: 'upload' as const,
      }))
    : []

  // 计算汇总指标
  const stats = agentStatsQuery.data?.stats || []
  const metrics = {
    requestsToday: stats.reduce((sum, s) => sum + s.total, 0),
    tokensToday: 0,
    agentCallsToday: stats.reduce((sum, s) => sum + s.total, 0),
    avgResponseMs: stats.length > 0
      ? Math.round(stats.reduce((sum, s) => sum + s.avgDurationMs, 0) / stats.length)
      : 0,
    onlineUsers: statusQuery.data?.userCount ?? 0,
    source: 'mock' as const,
  }

  return {
    metrics,
    services,
    tasks,
    agents,
    adminStatus: statusQuery.data,
    isLoading: statusQuery.isLoading || agentStatsQuery.isLoading,
    refetch: statusQuery.refetch,
  }
}
