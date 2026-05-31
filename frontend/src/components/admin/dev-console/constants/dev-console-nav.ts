import type { LucideIcon } from 'lucide-react'
import {
  LayoutDashboard,
  Network,
  BookOpen,
  Upload,
  Database,
  Users,
  UserCog,
  Route,
  ClipboardList,
  Activity,
  Bot,
  Settings,
  Server,
} from 'lucide-react'
import type { DevConsoleNavId } from '@/components/admin/dev-console/types/dev-console'

export interface DevConsoleNavItem {
  id: DevConsoleNavId
  label: string
  icon: LucideIcon
}

export interface DevConsoleNavGroup {
  title: string
  items: DevConsoleNavItem[]
}

/** 侧栏菜单：5 组 ~15 项 */
export const DEV_CONSOLE_NAV: DevConsoleNavGroup[] = [
  {
    title: '概览',
    items: [
      { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
    ],
  },
  {
    title: '内容管理',
    items: [
      { id: 'knowledge', label: '知识图谱', icon: Network },
      { id: 'resources', label: '学习资源', icon: BookOpen },
      { id: 'upload', label: '上传队列', icon: Upload },
      { id: 'knowledge-base', label: '知识库', icon: Database },
    ],
  },
  {
    title: '学员管理',
    items: [
      { id: 'users', label: '用户列表', icon: Users },
      { id: 'profile', label: '学习画像', icon: UserCog },
      { id: 'path', label: '学习路径', icon: Route },
      { id: 'quiz-records', label: '答题记录', icon: ClipboardList },
      { id: 'study-behaviors', label: '学习行为', icon: Activity },
    ],
  },
  {
    title: '智能体',
    items: [
      { id: 'agents', label: 'Agent 管理', icon: Bot },
    ],
  },
  {
    title: '系统',
    items: [
      { id: 'model-config', label: '模型配置', icon: Settings },
      { id: 'system-config', label: '系统配置', icon: Server },
    ],
  },
]

export const DEV_CONSOLE_NAV_LABEL: Record<DevConsoleNavId, string> = {
  dashboard: 'Dashboard',
  knowledge: '知识图谱',
  resources: '学习资源',
  upload: '上传队列',
  'knowledge-base': '知识库',
  users: '用户列表',
  profile: '学习画像',
  path: '学习路径',
  'quiz-records': '答题记录',
  'study-behaviors': '学习行为',
  agents: 'Agent 管理',
  'model-config': '模型配置',
  'system-config': '系统配置',
}
