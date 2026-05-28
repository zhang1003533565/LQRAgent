import type { LucideIcon } from 'lucide-react'
import {
  Bot,
  BookOpen,
  GitBranch,
  LayoutDashboard,
  MessageSquare,
  Network,
  Route,
  Settings,
  Upload,
  UserCog,
  Users,
  BrainCircuit,
  BookMarked,
  FileSearch,
  BarChart3,
  ImageIcon,
  ShieldCheck,
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
      { id: 'agent-debug', label: 'Agent 监控', icon: Bot },
      { id: 'trace', label: 'Trace 调用链', icon: GitBranch },
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
    title: '智能体管理',
    items: [
      { id: 'agent-orchestrator', label: '协调 Orchestrator', icon: BrainCircuit },
      { id: 'agent-qa', label: '答疑 QaAgent', icon: MessageSquare },
      { id: 'agent-learningpath', label: '路径规划 LearningPath', icon: Route },
      { id: 'agent-resourcefacade', label: '资源生成 ResourceFacade', icon: BookMarked },
      { id: 'agent-learnerprofile', label: '学生画像 LearnerProfile', icon: UserCog },
      { id: 'agent-qualityassessment', label: '质量评估 QualityAssessment', icon: ShieldCheck },
      { id: 'agent-contentanalyzer', label: '内容分析 ContentAnalyzer', icon: FileSearch },
      { id: 'agent-effectassessment', label: '效果评估 EffectAssessment', icon: BarChart3 },
      { id: 'agent-mediagen', label: '媒体生成 MediaGeneration', icon: ImageIcon },
    ],
  },
  {
    title: '系统管理',
    items: [
      { id: 'model-config', label: '模型配置', icon: Settings },
      { id: 'system-config', label: '系统配置', icon: Settings },
    ],
  },
]

export const DEV_CONSOLE_NAV_LABEL: Record<DevConsoleNavId, string> = {
  dashboard: '概览',
  'agent-debug': 'Agent 监控',
  trace: 'Trace 调用链',
  users: '用户管理',
  upload: '上传队列',
  profile: '学习画像',
  knowledge: '知识图谱',
  path: '学习路径',
  resources: '资源管理',
  'model-config': '模型配置',
  'system-config': '系统配置',
  'agent-orchestrator': '协调 Orchestrator',
  'agent-qa': '答疑 QaAgent',
  'agent-learningpath': '路径规划',
  'agent-resourcefacade': '资源生成',
  'agent-learnerprofile': '学生画像',
  'agent-qualityassessment': '质量评估',
  'agent-contentanalyzer': '内容分析',
  'agent-effectassessment': '效果评估',
  'agent-mediagen': '媒体生成',
}
