import { motion } from 'framer-motion'
import { useDevConsoleOverview } from '@/components/admin/dev-console/hooks/useDevConsoleQueries'
import { useDevConsoleStore } from '@/components/admin/dev-console/store/devConsoleStore'
import AgentStatusPanel from './AgentStatusPanel'
import RecentTasksCard from './RecentTasksCard'
import StatCards from './StatCards'
import SystemStatusCard from './SystemStatusCard'
import type { DevConsoleNavId } from '@/components/admin/dev-console/types/dev-console'

const QUICK_ACTIONS: { label: string; icon: string; nav: DevConsoleNavId }[] = [
  { label: '知识库管理', icon: '📚', nav: 'knowledge-base' },
  { label: '上传队列', icon: '📤', nav: 'upload' },
  { label: '用户管理', icon: '👥', nav: 'users' },
  { label: '模型配置', icon: '⚙️', nav: 'model-config' },
  { label: 'Agent 管理', icon: '🤖', nav: 'agents' },
  { label: '系统配置', icon: '🔧', nav: 'system-config' },
]

export default function DashboardView() {
  const { metrics, services, tasks, agents, isLoading } = useDevConsoleOverview()
  const setActiveNav = useDevConsoleStore((s) => s.setActiveNav)

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="space-y-4"
    >
      <StatCards metrics={metrics} />

      <div className="grid gap-4 lg:grid-cols-3">
        {/* 服务状态 */}
        <SystemStatusCard services={services} />

        {/* 快捷操作 */}
        <div className="rounded-lg border border-console-border bg-console-card p-4">
          <h3 className="mb-3 text-xs font-medium uppercase tracking-wider text-console-muted">快捷操作</h3>
          <div className="grid grid-cols-2 gap-2">
            {QUICK_ACTIONS.map(a => (
              <button
                key={a.nav}
                onClick={() => setActiveNav(a.nav)}
                className="flex items-center gap-2 rounded-md border border-console-border/60 px-3 py-2 text-sm text-console-text hover:bg-console-border/20 transition-colors"
              >
                <span>{a.icon}</span>
                <span>{a.label}</span>
              </button>
            ))}
          </div>
        </div>

        {/* 最近任务 */}
        <RecentTasksCard tasks={tasks} />
      </div>

      {/* Agent 状态 */}
      <AgentStatusPanel agents={agents} />

      {isLoading && (
        <p className="text-center text-xs text-console-muted">正在同步数据…</p>
      )}
    </motion.div>
  )
}
