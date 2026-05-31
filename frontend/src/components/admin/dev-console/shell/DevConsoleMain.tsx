import { DashboardView } from '@/components/admin/dev-console/dashboard'
import { DevConsolePlaceholder } from '@/components/admin/dev-console/placeholder'
import {
  AgentManagementPanel,
  KnowledgeBasePanel,
  LearningPathPanel,
  ProfilePanel,
  KnowledgeGraphPanel,
  ResourcePanel,
  ModelConfigPanel,
  SystemConfigPanel,
  UploadQueuePanel,
  UserListPanel,
  QuizRecordPanel,
  StudyBehaviorPanel,
} from '@/components/admin/dev-console/panels'
import { LogPanel, QuickActions } from '@/components/admin/dev-console/logs'
import { useDevConsoleStore } from '@/components/admin/dev-console/store/devConsoleStore'
import type { DevConsoleNavId } from '@/components/admin/dev-console/types/dev-console'

function resolveMainContent(activeNav: DevConsoleNavId) {
  switch (activeNav) {
    case 'dashboard': return <DashboardView />
    // 内容管理
    case 'knowledge': return <KnowledgeGraphPanel />
    case 'resources': return <ResourcePanel />
    case 'upload': return <UploadQueuePanel />
    case 'knowledge-base': return <KnowledgeBasePanel />
    // 学员管理
    case 'users': return <UserListPanel />
    case 'profile': return <ProfilePanel />
    case 'path': return <LearningPathPanel />
    case 'quiz-records': return <QuizRecordPanel />
    case 'study-behaviors': return <StudyBehaviorPanel />
    // 智能体
    case 'agents': return <AgentManagementPanel />
    // 系统
    case 'model-config': return <ModelConfigPanel />
    case 'system-config': return <SystemConfigPanel />
    default: return <DevConsolePlaceholder navId={activeNav} />
  }
}

export default function DevConsoleMain() {
  const activeNav = useDevConsoleStore((s) => s.activeNav)

  return (
    <div className="flex min-h-0 flex-1">
      <main className="min-w-0 flex-1 overflow-y-auto p-4">
        {resolveMainContent(activeNav)}
      </main>
      <aside className="flex w-72 shrink-0 flex-col border-l border-console-border bg-console-card/30 xl:w-80">
        <LogPanel />
        <QuickActions />
      </aside>
    </div>
  )
}
