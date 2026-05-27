import type { LucideIcon } from 'lucide-react'
import {
  Bot,
  BookOpen,
  FileCode2,
  GitBranch,
  LayoutDashboard,
  Network,
  Route,
  Settings,
  SlidersHorizontal,
  Terminal,
  Upload,
  UserCog,
  Users,
  Wrench,
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

/** 侧栏菜单（与 docs 前端功能模块 + 后端 admin API 对齐） */
export const DEV_CONSOLE_NAV: DevConsoleNavGroup[] = [
  {
    title: '调试中心',
    items: [
      { id: 'dashboard', label: '概览 Dashboard', icon: LayoutDashboard },
      { id: 'agent-debug', label: 'Agent 调试', icon: Bot },
      { id: 'trace', label: 'Trace 调用链', icon: GitBranch },
      { id: 'logs', label: '实时日志', icon: Terminal },
    ],
  },
  {
    title: '数据管理',
    items: [
      { id: 'users', label: '用户管理', icon: Users },
      { id: 'upload', label: '上传队列', icon: Upload },
      { id: 'profile', label: '学习画像', icon: UserCog },
      { id: 'knowledge', label: '知识图谱', icon: Network },
      { id: 'path', label: '学习路径', icon: Route },
      { id: 'resources', label: '资源管理', icon: BookOpen },
    ],
  },
  {
    title: '系统管理',
    items: [
      { id: 'system-config', label: '系统配置', icon: Settings },
      { id: 'model-config', label: '模型配置', icon: Wrench },
      { id: 'prompts', label: 'Prompt 管理', icon: FileCode2 },
      { id: 'params', label: '参数设置', icon: SlidersHorizontal },
    ],
  },
]

export const DEV_CONSOLE_NAV_LABEL: Record<DevConsoleNavId, string> = {
  dashboard: '概览',
  'agent-debug': 'Agent 调试',
  trace: 'Trace 调用链',
  logs: '实时日志',
  users: '用户管理',
  upload: '上传队列',
  profile: '学习画像',
  knowledge: '知识图谱',
  path: '学习路径',
  resources: '资源管理',
  'system-config': '系统配置',
  'model-config': '模型配置',
  prompts: 'Prompt 管理',
  params: '参数设置',
}
