import { motion } from 'framer-motion'
import { useDevConsoleOverview } from '@/components/admin/dev-console/hooks/useDevConsoleQueries'
import AgentStatusPanel from './AgentStatusPanel'
import RecentTasksCard from './RecentTasksCard'
import StatCards from './StatCards'
import SystemStatusCard from './SystemStatusCard'
import TraceFlowPanel from './TraceFlowPanel'

export default function DashboardView() {
  const { metrics, services, tasks, agents, isLoading } = useDevConsoleOverview()

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="space-y-4"
    >
      <StatCards metrics={metrics} />

      <div className="grid gap-4 lg:grid-cols-2">
        <SystemStatusCard services={services} />
        <RecentTasksCard tasks={tasks} />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <AgentStatusPanel agents={agents} />
        <TraceFlowPanel />
      </div>

      {isLoading && (
        <p className="text-center text-xs text-console-muted">正在同步 GET /admin/status …</p>
      )}
    </motion.div>
  )
}
