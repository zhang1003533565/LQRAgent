import {
  BookOpen,
  Bug,
  ClipboardList,
  FileText,
  Lightbulb,
  Map,
  Pencil,
  Sparkles,
  Upload,
} from 'lucide-react'
import { getCurrentPath } from '@/api/student/learningPath'
import { listUploadTasks } from '@/api/student/upload'
import { fetchProfileDetailRaw } from '@/api/student/profile'
import { listQuizSessions } from '@/api/student/quiz'
import { getQuizRecords } from '@/api/student/quiz'
import { usePathStore } from '@/utils/store/pathStore'
import type { QuickActionItem } from '@/utils/types/chatSidebarActions'
import type {
  ChatSidebarData,
  QuickToolItem,
  RecentLearningItem,
  TodayGoalData,
} from '@/utils/types/chatSidebar'
import type { PracticeSession } from '@/utils/types/quiz'

const QUIZ_DAILY_TARGET = 5

function isToday(iso: string): boolean {
  const d = new Date(iso)
  const now = new Date()
  return (
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  )
}

function formatRelativeTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffDays = Math.floor(diffMs / (24 * 60 * 60 * 1000))
  if (isToday(iso)) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  if (diffDays === 1) return '昨天'
  if (diffDays < 7) return `${diffDays}天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

async function buildTodayGoal(): Promise<TodayGoalData> {
  const path = usePathStore.getState().nodes.length
    ? { nodes: usePathStore.getState().nodes, goal: usePathStore.getState().goal }
    : await getCurrentPath().catch(() => null)

  const records = await getQuizRecords().catch(() => [])
  const todayQuiz = records.filter((r) => isToday(r.createdAt)).length

  const nodes = path?.nodes ?? []
  const completed = nodes.filter((n) => n.completed).length
  const total = nodes.length
  const activeNode = nodes.find((n) => n.status === 'ACTIVE' && !n.completed) || nodes.find((n) => !n.completed)

  const quizProgress = Math.min(100, Math.round((todayQuiz / QUIZ_DAILY_TARGET) * 100))
  const pathProgress = total > 0 ? Math.round((completed / total) * 100) : 0
  const progress = total > 0 ? Math.round((quizProgress + pathProgress) / 2) : quizProgress

  return {
    progress,
    currentStageTitle: activeNode?.title,
    items: [
      {
        label: '今日练习',
        current: todayQuiz,
        total: QUIZ_DAILY_TARGET,
        unit: '题',
        color: '#22C55E',
      },
      {
        label: '路径节点',
        current: completed,
        total: Math.max(total, 1),
        unit: '个',
        color: '#2563EB',
      },
      {
        label: '当前阶段',
        current: activeNode ? 1 : 0,
        total: 1,
        unit: activeNode ? '进行中' : '待开始',
        color: '#F59E0B',
      },
    ],
  }
}

async function buildQuickTools(): Promise<QuickToolItem[]> {
  const pathState = usePathStore.getState()
  const kpTitle =
    pathState.nodes.find((n) => n.kpId === pathState.selectedKpId)?.title ||
    pathState.nodes.find((n) => n.status === 'ACTIVE')?.title ||
    pathState.nodes[0]?.title

  let weakTopic = ''
  try {
    const detail = await fetchProfileDetailRaw()
    weakTopic = detail.weakTopics?.[0] || detail.knowledgeMap?.find((k) => (k.mastery ?? 0) < 40)?.title || ''
  } catch {
    // optional
  }

  const focus = kpTitle || weakTopic || 'Python 基础'

  const tools: QuickToolItem[] = [
    {
      id: 'plan',
      label: '制定计划',
      icon: Map,
      iconBg: 'bg-[#ECFDF5]',
      prompt: pathState.goal
        ? `基于我的学习目标「${pathState.goal.slice(0, 40)}」，帮我调整本周学习计划。`
        : '我想系统学习编程，请帮我制定一份可执行的学习计划。',
    },
    {
      id: 'explain',
      label: '生成讲解',
      icon: Lightbulb,
      iconBg: 'bg-[#EAF3FF]',
      prompt: `请用通俗的方式讲解「${focus}」，并给一个简单例子。`,
    },
    {
      id: 'quiz',
      label: '生成练习题',
      icon: Pencil,
      iconBg: 'bg-[#F5F3FF]',
      prompt: `请针对「${focus}」出 5 道练习题，附简要解析。`,
    },
    {
      id: 'summary',
      label: '学习总结',
      icon: ClipboardList,
      iconBg: 'bg-[#EFF6FF]',
      prompt: weakTopic
        ? `请总结我在「${weakTopic}」上的薄弱点，并给出复习建议。`
        : `请总结我最近关于「${focus}」的学习要点。`,
    },
    {
      id: 'code',
      label: '代码解释',
      icon: FileText,
      iconBg: 'bg-[#FFF7ED]',
      prompt: '请解释下面这段代码的作用与执行流程：\n\n```python\n# 在此粘贴代码\n```',
    },
    {
      id: 'more',
      label: '更多工具',
      icon: Sparkles,
      iconBg: 'bg-[#F8FAFC]',
      prompt: '你还有哪些学习工具可以帮我？',
    },
  ]

  return tools
}

function sessionToRecent(session: PracticeSession): RecentLearningItem {
  return {
    id: `quiz-${session.id}`,
    title: session.title,
    time: formatRelativeTime(session.startedAt),
    icon: Pencil,
    href: `/workspace/quiz/session/${session.id}`,
  }
}

async function buildRecentLearning(): Promise<RecentLearningItem[]> {
  const merged: Array<{ sortAt: string; item: RecentLearningItem }> = []

  try {
    const sessions = await listQuizSessions()
    for (const session of sessions.slice(0, 8)) {
      merged.push({ sortAt: session.startedAt, item: sessionToRecent(session) })
    }
  } catch {
    // fallback handled by caller if empty
  }

  try {
    const uploads = await listUploadTasks()
    for (const task of uploads.slice(0, 5)) {
      merged.push({
        sortAt: task.createdAt,
        item: {
          id: `upload-${task.id}`,
          title: task.fileName,
          time: formatRelativeTime(task.createdAt),
          icon: Upload,
          href: '/workspace/upload',
        },
      })
    }
  } catch {
    // optional
  }

  const pathNodes = usePathStore.getState().nodes.filter((n) => n.completed)
  for (const node of pathNodes.slice(-3)) {
    merged.push({
      sortAt: new Date().toISOString(),
      item: {
        id: `path-${node.kpId}`,
        title: node.title,
        time: '路径',
        icon: BookOpen,
        href: `/workspace/resources?kpId=${encodeURIComponent(node.kpId)}`,
      },
    })
  }

  return merged
    .sort((a, b) => b.sortAt.localeCompare(a.sortAt))
    .slice(0, 6)
    .map((entry) => entry.item)
}

export async function loadChatSidebarData(): Promise<ChatSidebarData> {
  const [todayGoal, quickTools, recentLearning] = await Promise.all([
    buildTodayGoal(),
    buildQuickTools(),
    buildRecentLearning(),
  ])

  return { todayGoal, quickTools, recentLearning }
}

export function buildWelcomeQuickActions(): QuickActionItem[] {
  const pathState = usePathStore.getState()
  const focus =
    pathState.nodes.find((n) => n.kpId === pathState.selectedKpId)?.title ||
    pathState.nodes.find((n) => n.status === 'ACTIVE')?.title ||
    'Python 基础'

  return [
    {
      id: 'concept',
      title: '解释一个概念',
      description: `搞懂「${focus}」`,
      icon: BookOpen,
      iconBg: 'bg-[#EAF3FF]',
      prompt: `请用通俗的方式解释一下「${focus}」，并给我一个小例子。`,
    },
    {
      id: 'debug',
      title: '代码调试',
      description: '找出代码中的问题',
      icon: Bug,
      iconBg: 'bg-[#F5F3FF]',
      prompt: '帮我检查下面这段代码有什么问题，并给出修改建议。',
    },
    {
      id: 'plan',
      title: '学习建议',
      description: '制定学习计划',
      icon: Map,
      iconBg: 'bg-[#ECFDF5]',
      prompt: pathState.goal
        ? `基于我的学习目标「${pathState.goal.slice(0, 48)}」，帮我优化下一步计划。`
        : '我想系统学习编程，请帮我制定一份可执行的学习计划。',
    },
    {
      id: 'quiz',
      title: '生成练习',
      description: '针对知识点出题',
      icon: Pencil,
      iconBg: 'bg-[#FFF7ED]',
      prompt: `请针对「${focus}」出 5 道练习题，难度适中。`,
    },
    {
      id: 'summary',
      title: '学习总结',
      description: '知识点归纳总结',
      icon: ClipboardList,
      iconBg: 'bg-[#EFF6FF]',
      prompt: `请帮我总结最近关于「${focus}」的学习要点与常见易错点。`,
    },
  ]
}
