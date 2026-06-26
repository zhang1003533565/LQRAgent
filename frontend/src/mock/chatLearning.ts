import type { LucideIcon } from 'lucide-react'
import {
  BookOpen,
  Bug,
  ClipboardList,
  FileText,
  Lightbulb,
  Map,
  Pencil,
  Sparkles,
} from 'lucide-react'

export const CHAT_LEARNING_USER = {
  name: 'student1',
  role: '学生',
  avatarLetter: 'S',
}

export type QuickActionItem = {
  id: string
  title: string
  description: string
  icon: LucideIcon
  iconBg: string
  prompt: string
}

export const QUICK_ACTIONS: QuickActionItem[] = [
  {
    id: 'concept',
    title: '解释一个概念',
    description: '帮我理解知识点',
    icon: BookOpen,
    iconBg: 'bg-[#EAF3FF]',
    prompt: '请用通俗的方式解释一下 Python 列表推导式，并给我一个小例子。',
  },
  {
    id: 'debug',
    title: '代码调试',
    description: '找出代码中的问题',
    icon: Bug,
    iconBg: 'bg-[#F5F3FF]',
    prompt: '帮我检查下面这段 Python 代码有什么问题，并给出修改建议。',
  },
  {
    id: 'plan',
    title: '学习建议',
    description: '制定学习计划',
    icon: Map,
    iconBg: 'bg-[#ECFDF5]',
    prompt: '我想系统学习 Python 面向对象，零基础，请帮我制定 2 周的学习计划。',
  },
  {
    id: 'quiz',
    title: '生成练习',
    description: '针对知识点出题',
    icon: Pencil,
    iconBg: 'bg-[#FFF7ED]',
    prompt: '请出 5 道 Python 变量与数据类型的练习题，难度中等。',
  },
  {
    id: 'summary',
    title: '学习总结',
    description: '知识点归纳总结',
    icon: ClipboardList,
    iconBg: 'bg-[#EFF6FF]',
    prompt: '请帮我总结今天学过的 Python 函数与模块相关知识点。',
  },
]

export const DEMO_LEARNING_PATH = {
  title: '学习路径已为你优化',
  stages: [
    'Python 基础语法回顾与进阶',
    '数据类型与变量高级用法',
    '函数与模块深度学习',
    '数据结构：列表、字典、集合',
    '字符串处理与正则表达式',
    '面向对象编程基础',
    '文件操作与异常处理',
    '项目实战：小程序开发',
  ],
  summary:
    '这条路径共 8 个阶段，大约需要 2~3 周完成。如果你想更快进阶，我可以帮你制定每天的学习计划哦～',
}

export const TODAY_GOAL = {
  progress: 40,
  items: [
    { label: '学习时长', current: 45, total: 120, unit: '分钟', color: '#2563EB' },
    { label: '完成练习', current: 2, total: 5, unit: '题', color: '#22C55E' },
    { label: '知识点', current: 3, total: 8, unit: '个', color: '#F59E0B' },
  ],
}

export type QuickToolItem = {
  id: string
  label: string
  icon: LucideIcon
  iconBg: string
  prompt: string
}

export const QUICK_TOOLS: QuickToolItem[] = [
  { id: 'explain', label: '生成讲解', icon: Lightbulb, iconBg: 'bg-[#EAF3FF]', prompt: '请针对我当前正在学的知识点生成一段讲解。' },
  { id: 'quiz', label: '生成练习题', icon: Pencil, iconBg: 'bg-[#F5F3FF]', prompt: '请根据我最近在学的内容出 5 道练习题。' },
  { id: 'summary', label: '学习总结', icon: ClipboardList, iconBg: 'bg-[#ECFDF5]', prompt: '请总结我最近的学习记录，并指出薄弱点。' },
  { id: 'code', label: '代码解释', icon: FileText, iconBg: 'bg-[#FFF7ED]', prompt: '请解释下面这段代码的作用与执行流程：\n\n```python\n# 在此粘贴代码\n```' },
  { id: 'card', label: '知识卡片', icon: BookOpen, iconBg: 'bg-[#EFF6FF]', prompt: '请把当前知识点整理成一张便于复习的知识卡片。' },
  { id: 'more', label: '更多工具', icon: Sparkles, iconBg: 'bg-[#F8FAFC]', prompt: '你还有哪些学习工具可以帮我？' },
]

export const RECENT_LEARNING = [
  { id: '1', title: 'Python 函数详解', time: '16:00', icon: BookOpen },
  { id: '2', title: '列表推导式练习', time: '昨天', icon: Pencil },
  { id: '3', title: '文件读写操作', time: '昨天', icon: FileText },
  { id: '4', title: '异常处理机制', time: '2天前', icon: Lightbulb },
]

export function getGreeting(name: string): string {
  const hour = new Date().getHours()
  const period = hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好'
  return `${period}，${name}! 👋`
}
