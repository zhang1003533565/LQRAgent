import { DashboardView } from '@/components/admin/dev-console/dashboard'
import TraceFlowPanel from '@/components/admin/dev-console/dashboard/TraceFlowPanel'
import { DevConsolePlaceholder } from '@/components/admin/dev-console/placeholder'
import {
  AgentMonitorPanel,
  LearningPathPanel,
  ProfilePanel,
  KnowledgeGraphPanel,
  ResourcePanel,
  SystemConfigPanel,
  UploadQueuePanel,
  UserListPanel,
  AgentOrchestratorPanel,
  AgentQaPanel,
  AgentLearningPathPanel,
  AgentResourceFacadePanel,
  AgentLearnerProfilePanel,
  AgentQualityAssessmentPanel,
  AgentContentAnalyzerPanel,
  AgentEffectAssessmentPanel,
  AgentMediaGenPanel,
} from '@/components/admin/dev-console/panels'
import { LogPanel, QuickActions } from '@/components/admin/dev-console/logs'
import { useDevConsoleOverview } from '@/components/admin/dev-console/hooks/useDevConsoleQueries'
import { useDevConsoleStore } from '@/components/admin/dev-console/store/devConsoleStore'
import type { AgentRuntimeStatus } from '@/components/admin/dev-console/types/dev-console'
import type { DevConsoleNavId } from '@/components/admin/dev-console/types/dev-console'

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
    // 智能体管理
    case 'agent-orchestrator':
      return <AgentOrchestratorPanel />
    case 'agent-qa':
      return <AgentQaPanel />
    case 'agent-learningpath':
      return <AgentLearningPathPanel />
    case 'agent-resourcefacade':
      return <AgentResourceFacadePanel />
    case 'agent-learnerprofile':
      return <AgentLearnerProfilePanel />
    case 'agent-qualityassessment':
      return <AgentQualityAssessmentPanel />
    case 'agent-contentanalyzer':
      return <AgentContentAnalyzerPanel />
    case 'agent-effectassessment':
      return <AgentEffectAssessmentPanel />
    case 'agent-mediagen':
      return <AgentMediaGenPanel />
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
