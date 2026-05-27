import { motion } from 'framer-motion'
import { AGENT_LABELS } from '@/student/constants/agent-labels'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/admin/components/dev-console/ui'
import type { AgentRuntimeStatus } from '@/admin/components/dev-console/types/dev-console'

interface AgentStatusPanelProps {
  agents: AgentRuntimeStatus[]
}

export default function AgentStatusPanel({ agents }: AgentStatusPanelProps) {
  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>Agent 状态</CardTitle>
        <p className="text-xs text-console-muted">
          与 WS <code className="text-console-blue">agent_step</code> / P3 监控 API 对齐
        </p>
      </CardHeader>
      <CardContent className="space-y-2">
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
              <p className="text-sm">{AGENT_LABELS[row.agent]}</p>
            </div>
            <ConsoleBadge variant={row.status === 'running' ? 'success' : 'muted'}>
              {row.status}
            </ConsoleBadge>
            <span className="font-mono text-xs text-console-muted">{row.latencyMs}ms</span>
            <span className="font-mono text-xs text-console-blue">{row.tokens} tok</span>
          </motion.div>
        ))}
      </CardContent>
    </Card>
  )
}
