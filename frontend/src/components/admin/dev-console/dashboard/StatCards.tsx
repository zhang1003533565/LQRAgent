import { motion } from 'framer-motion'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/admin/dev-console/ui'
import AnimatedNumber from './AnimatedNumber'
import type { DashboardMetrics } from '@/components/admin/dev-console/types/dev-console'

const STAT_DEFS = [
  { key: 'requests', title: '今日调用', color: '#8B5CF6', format: (n: number) => Math.round(n).toLocaleString() },
  { key: 'agents', title: 'Agent 调用', color: '#10B981', format: (n: number) => Math.round(n).toLocaleString() },
  { key: 'latency', title: '平均响应', color: '#F59E0B', format: (n: number) => `${(n / 1000).toFixed(2)}s` },
  { key: 'users', title: '用户数', color: '#06B6D4', format: (n: number) => Math.round(n).toLocaleString() },
]

interface StatCardsProps {
  metrics: DashboardMetrics
}

export default function StatCards({ metrics }: StatCardsProps) {
  const values: Record<string, number> = {
    requests: metrics.requestsToday,
    agents: metrics.agentCallsToday,
    latency: metrics.avgResponseMs,
    users: metrics.onlineUsers,
  }

  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {STAT_DEFS.map((stat, i) => (
        <motion.div
          key={stat.key}
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: i * 0.05 }}
        >
          <Card className="hover:border-console-blue/30 transition-colors">
            <CardHeader className="pb-0">
              <CardTitle className="text-xs text-console-muted">{stat.title}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-semibold tabular-nums" style={{ color: stat.color }}>
                <AnimatedNumber value={values[stat.key]} format={stat.format} />
              </p>
            </CardContent>
          </Card>
        </motion.div>
      ))}
    </div>
  )
}
