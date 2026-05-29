import { motion } from 'framer-motion'
import ReactECharts from 'echarts-for-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/admin/dev-console/ui'
import { MOCK_SPARKLINES, formatTokens } from '@/components/admin/dev-console/mock/data'
import AnimatedNumber from './AnimatedNumber'
import type { DashboardMetrics } from '@/components/admin/dev-console/types/dev-console'

const STAT_DEFS = [
  {
    key: 'requests',
    title: '今日请求数',
    color: '#8B5CF6',
    sparkKey: 'requests' as const,
    format: (n: number) => Math.round(n).toLocaleString(),
  },
  {
    key: 'tokens',
    title: '今日 Token 消耗',
    color: '#3B82F6',
    sparkKey: 'tokens' as const,
    format: (n: number) => formatTokens(n),
  },
  {
    key: 'agents',
    title: 'Agent 调用次数',
    color: '#10B981',
    sparkKey: 'agents' as const,
    format: (n: number) => Math.round(n).toLocaleString(),
  },
  {
    key: 'latency',
    title: '平均响应时间',
    color: '#F59E0B',
    sparkKey: 'latency' as const,
    format: (n: number) => `${(n / 1000).toFixed(2)}s`,
  },
  {
    key: 'users',
    title: '在线用户数',
    color: '#06B6D4',
    sparkKey: 'users' as const,
    format: (n: number) => Math.round(n).toLocaleString(),
  },
]

function sparkOption(values: number[], color: string) {
  return {
    grid: { left: 0, right: 0, top: 4, bottom: 0 },
    xAxis: { type: 'category', show: false, data: values.map((_, i) => i) },
    yAxis: { type: 'value', show: false },
    series: [
      {
        type: 'line',
        data: values,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: `${color}55` },
              { offset: 1, color: `${color}00` },
            ],
          },
        },
      },
    ],
  }
}

interface StatCardsProps {
  metrics: DashboardMetrics
}

export default function StatCards({ metrics }: StatCardsProps) {
  const values: Record<string, number> = {
    requests: metrics.requestsToday,
    tokens: metrics.tokensToday,
    agents: metrics.agentCallsToday,
    latency: metrics.avgResponseMs,
    users: metrics.onlineUsers,
  }

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-5">
      {STAT_DEFS.map((stat, i) => (
        <motion.div
          key={stat.key}
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: i * 0.05 }}
          whileHover={{ y: -2 }}
        >
          <Card className="overflow-hidden transition-shadow hover:border-console-blue/30">
            <CardHeader className="pb-0">
              <CardTitle className="text-xs text-console-muted">{stat.title}</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex items-end justify-between gap-2">
                <p className="text-2xl font-semibold tabular-nums" style={{ color: stat.color }}>
                  <AnimatedNumber
                    value={values[stat.key]}
                    format={stat.format}
                  />
                </p>
              </div>
              <div className="mt-2 h-12">
                <ReactECharts
                  option={sparkOption(MOCK_SPARKLINES[stat.sparkKey], stat.color)}
                  style={{ height: 48, width: '100%' }}
                  opts={{ renderer: 'svg' }}
                />
              </div>
            </CardContent>
          </Card>
        </motion.div>
      ))}
    </div>
  )
}
