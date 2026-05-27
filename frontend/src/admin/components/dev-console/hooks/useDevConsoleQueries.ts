import { useQuery } from '@tanstack/react-query'
import { getAdminStatus } from '@/api/admin/admin'
import {
  MOCK_AGENT_RUNTIME,
  MOCK_METRICS,
  MOCK_RECENT_TASKS,
  MOCK_SERVICES,
} from '@/admin/components/dev-console/mock/data'
import type { ServiceHealth } from '@/admin/components/dev-console/types/dev-console'

/** 对接已有 GET /admin/status，其余指标仍为 MOCK */
export function useDevConsoleOverview() {
  const statusQuery = useQuery({
    queryKey: ['dev-console', 'admin-status'],
    queryFn: getAdminStatus,
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

  return {
    metrics: MOCK_METRICS,
    services,
    tasks: MOCK_RECENT_TASKS,
    agents: MOCK_AGENT_RUNTIME,
    adminStatus: statusQuery.data,
    isLoading: statusQuery.isLoading,
    refetch: statusQuery.refetch,
  }
}
