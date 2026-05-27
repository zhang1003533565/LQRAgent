import { DashboardView } from '@/admin/components/dev-console/dashboard'
import TraceFlowPanel from '@/admin/components/dev-console/dashboard/TraceFlowPanel'
import { DevConsolePlaceholder } from '@/admin/components/dev-console/placeholder'
import {
  AgentMonitorPanel,
  LearningPathPanel,
  ModelConfigPanel,
  ProfilePanel,
  KnowledgeGraphPanel,
  ResourcePanel,
  SystemConfigPanel,
  UploadQueuePanel,
  UserListPanel,
} from '@/admin/components/dev-console/panels'
import { LogPanel, QuickActions } from '@/admin/components/dev-console/logs'
import { useDevConsoleOverview } from '@/admin/components/dev-console/hooks/useDevConsoleQueries'
import { useDevConsoleStore } from '@/admin/components/dev-console/store/devConsoleStore'
import type { AgentRuntimeStatus } from '@/admin/components/dev-console/types/dev-console'
import type { DevConsoleNavId } from '@/admin/components/dev-console/types/dev-console'

function resolveMainContent(activeNav: DevConsoleNavId, agents: AgentRuntimeStatus[]) {
  switch (activeNav) {
    case 'dashboard':
      return <DashboardView />
    case 'trace':
      return (
        <div className="min-h-[520px]">
          <TraceFlowPanel />
        </div>
      )
    case 'agent-debug':
      return <AgentMonitorPanel />
    case 'users':
      return <UserListPanel />
    case 'upload':
      return <UploadQueuePanel />
    case 'model-config':
      return <ModelConfigPanel />
    case 'system-config':
      return <SystemConfigPanel />
    case 'profile':
      return <ProfilePanel />
    case 'knowledge':
      return <KnowledgeGraphPanel />
    case 'path':
      return <LearningPathPanel />
    case 'resources':
      return <ResourcePanel />
    default:
      return <DevConsolePlaceholder navId={activeNav} />
  }
}

export default function DevConsoleMain() {
  const activeNav = useDevConsoleStore((s) => s.activeNav)
  const { agents } = useDevConsoleOverview()

  return (
    <div className="flex min-h-0 flex-1">
      <main className="min-w-0 flex-1 overflow-y-auto p-4">
        {resolveMainContent(activeNav, agents)}
      </main>
      <aside className="flex w-72 shrink-0 flex-col border-l border-console-border bg-console-card/30 xl:w-80">
        <LogPanel />
        <QuickActions />
      </aside>
    </div>
  )
}
