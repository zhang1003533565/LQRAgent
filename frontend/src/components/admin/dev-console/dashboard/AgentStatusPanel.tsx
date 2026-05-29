import { motion } from 'framer-motion'
import { AGENT_LABELS } from '@/utils/constants/agent-labels'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/components/admin/dev-console/ui'
import type { AgentRuntimeStatus } from '@/components/admin/dev-console/types/dev-console'

interface AgentStatusPanelProps {
  agents: AgentRuntimeStatus[]
}

export default function AgentStatusPanel({ agents }: AgentStatusPanelProps) {
  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>Agent 状态</CardTitle>
        <p className="text-xs text-console-muted">
          来源：GET /admin/agent-stats
        </p>
      </CardHeader>
      <CardContent className="space-y-2">
        {agents.length === 0 && (
          <p className="text-center text-xs text-console-muted py-4">
            暂无 Agent 调用记录
          </p>
        )}
        {agents.map((row, i) => (
          <motion.div
            key={row.agent}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: i * 0.05 }}
            className="grid grid-cols-[1fr_auto_auto_auto] items-center gap-3 rounded-md border border-console-border/60 px-3 py-2 text-sm"
          >
            <div>
              <p className="font-mono text-xs text-console-muted">{row.agent}</p>
              <p className="text-sm">{(AGENT_LABELS as Record<string, string>)[row.agent] || row.agent}</p>
            </div>
            <ConsoleBadge variant={row.status === 'failed' ? 'danger' : 'muted'}>
              {row.status}
            </ConsoleBadge>
            <span className="font-mono text-xs text-console-muted">{row.latencyMs}ms</span>
            {row.total != null && (
              <span className="font-mono text-xs text-console-blue">
                {row.success}/{row.total} ({row.successRate})
              </span>
            )}
          </motion.div>
        ))}
      </CardContent>
    </Card>
  )
}
