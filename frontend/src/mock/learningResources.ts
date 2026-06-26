import type { LucideIcon } from 'lucide-react'
import {
  ClipboardCheck,
  Code2,
  FileText,
  Folder,
  Network,
  Pencil,
  PlaySquare,
} from 'lucide-react'

export type ResourceCategory =
  | 'all'
  | 'video'
  | 'document'
  | 'quiz'
  | 'project'
  | 'mindmap'

export type ResourceDifficulty = '基础' | '初级' | '中级' | '高级'

export interface ResourceStatItem {
  id: ResourceCategory
  label: string
  count: number
  icon: LucideIcon
  iconBg: string
  iconColor: string
}

export interface RecommendedResource {
  id: string
  type: string
  typeLabel: string
  title: string
  description: string
  difficulty: ResourceDifficulty
  meta: string
  rating: number
  coverGradient: string
  icon: LucideIcon
  knowledgeId: string
  category: ResourceCategory
}

export interface KnowledgeTopic {
  id: string
  title: string
  count: number
  icon: LucideIcon
}

export interface LatestResource {
  id: string
  title: string
  description: string
  typeLabel: string
  difficulty?: string
  meta: string
  date: string
  rating: number
  knowledgeId: string
  category: ResourceCategory
  favorited?: boolean
}

export interface MyLibraryItem {
  id: string
  label: string
  count: number
  icon: LucideIcon
  tone: 'orange' | 'blue' | 'cyan' | 'purple'
}

export interface CoverageLegend {
  label: string
  percent: number
  color: string
}

export interface WeeklyPlanItem {
  day: string
  title: string
  typeLabel: string
}

export const MOCK_RESOURCE_STATS: ResourceStatItem[] = [
  { id: 'all', label: '全部资源', count: 1248, icon: Folder, iconBg: 'bg-[#EAF3FF]', iconColor: 'text-[#2563EB]' },
  { id: 'video', label: '视频课程', count: 328, icon: PlaySquare, iconBg: 'bg-[#F5F3FF]', iconColor: 'text-[#8B5CF6]' },
  { id: 'document', label: '文档资料', count: 412, icon: FileText, iconBg: 'bg-[#ECFDF5]', iconColor: 'text-[#22C55E]' },
  { id: 'quiz', label: '练习题库', count: 236, icon: Pencil, iconBg: 'bg-[#FFF7ED]', iconColor: 'text-[#F59E0B]' },
  { id: 'project', label: '项目案例', count: 128, icon: Code2, iconBg: 'bg-[#EFF6FF]', iconColor: 'text-[#3B82F6]' },
  { id: 'mindmap', label: '思维导图', count: 64, icon: Network, iconBg: 'bg-[#FDF2F8]', iconColor: 'text-[#EC4899]' },
]

export const MOCK_RECOMMENDED: RecommendedResource[] = [
  {
    id: 'rec-1',
    type: 'video',
    typeLabel: '视频课程',
    title: 'Python基础语法精讲',
    description: '从零基础到掌握 Python 核心语法',
    difficulty: '中级',
    meta: '12讲 · 3.2小时',
    rating: 4.9,
    coverGradient: 'from-[#3B82F6] to-[#93C5FD]',
    icon: PlaySquare,
    knowledgeId: 'python-basic',
    category: 'video',
  },
  {
    id: 'rec-2',
    type: 'document',
    typeLabel: '文档资料',
    title: 'Python语法速查手册',
    description: '常用语法汇总与示例，方便随时查阅',
    difficulty: '基础',
    meta: 'PDF · 86页 · 1.2MB',
    rating: 4.8,
    coverGradient: 'from-[#34D399] to-[#A7F3D0]',
    icon: FileText,
    knowledgeId: 'python-basic',
    category: 'document',
  },
  {
    id: 'rec-3',
    type: 'quiz',
    typeLabel: '练习题库',
    title: 'Python基础练习题集',
    description: '包含 100+ 基础题，附详细解析',
    difficulty: '初级',
    meta: '108题 · 练习',
    rating: 4.7,
    coverGradient: 'from-[#FDBA74] to-[#FED7AA]',
    icon: ClipboardCheck,
    knowledgeId: 'flow-control',
    category: 'quiz',
  },
  {
    id: 'rec-4',
    type: 'project',
    typeLabel: '项目案例',
    title: '学生成绩管理系统',
    description: '基于 Python 的智能批改实战项目',
    difficulty: '中级',
    meta: '项目 · 2.1小时',
    rating: 4.8,
    coverGradient: 'from-[#A78BFA] to-[#DDD6FE]',
    icon: Code2,
    knowledgeId: 'functions',
    category: 'project',
  },
]

export const MOCK_KNOWLEDGE_TOPICS: KnowledgeTopic[] = [
  { id: 'python-basic', title: 'Python 基础', count: 128, icon: Code2 },
  { id: 'data-structure', title: '数据结构', count: 156, icon: Network },
  { id: 'flow-control', title: '流程控制', count: 132, icon: Pencil },
  { id: 'functions', title: '函数与模块', count: 98, icon: FileText },
  { id: 'oop', title: '面向对象', count: 120, icon: Folder },
  { id: 'file-io', title: '文件与异常', count: 86, icon: ClipboardCheck },
]

export const MOCK_LATEST_RESOURCES: LatestResource[] = [
  {
    id: 'lat-1',
    title: 'Python 3.12 新特性详解',
    description: '全面介绍 Python 3.12 的新功能和优化',
    typeLabel: '视频课程',
    difficulty: '中级',
    meta: '45分钟',
    date: '2024-06-16',
    rating: 4.9,
    knowledgeId: 'python-basic',
    category: 'video',
  },
  {
    id: 'lat-2',
    title: '列表推导式完全指南',
    description: '深入理解列表推导式的用法和技巧',
    typeLabel: '文档资料',
    difficulty: '中级',
    meta: '15页',
    date: '2024-06-15',
    rating: 4.8,
    knowledgeId: 'data-structure',
    category: 'document',
  },
  {
    id: 'lat-3',
    title: '函数与模块专项练习',
    description: '50道精选题，提升函数与模块的使用能力',
    typeLabel: '练习题库',
    difficulty: '基础',
    meta: '50题',
    date: '2024-06-15',
    rating: 4.7,
    knowledgeId: 'functions',
    category: 'quiz',
  },
  {
    id: 'lat-4',
    title: '爬虫实战：获取天气数据',
    description: '使用 requests 和 BeautifulSoup 构建简单爬虫',
    typeLabel: '项目案例',
    difficulty: '高级',
    meta: '项目',
    date: '2024-06-14',
    rating: 4.9,
    knowledgeId: 'file-io',
    category: 'project',
  },
  {
    id: 'lat-5',
    title: 'Python 编程从入门到实践',
    description: '经典 Python 入门书籍，适合系统学习',
    typeLabel: '电子书籍',
    meta: 'PDF · 35MB',
    date: '2024-06-14',
    rating: 4.8,
    knowledgeId: 'python-basic',
    category: 'document',
  },
]

export const MOCK_MY_LIBRARY: MyLibraryItem[] = [
  { id: 'fav', label: '收藏的资源', count: 23, icon: Folder, tone: 'orange' },
  { id: 'recent', label: '最近学习', count: 12, icon: PlaySquare, tone: 'blue' },
  { id: 'download', label: '下载的资源', count: 8, icon: FileText, tone: 'cyan' },
  { id: 'notes', label: '我的笔记', count: 15, icon: Pencil, tone: 'purple' },
]

export const MOCK_COVERAGE = {
  percent: 68,
  legend: [
    { label: '已掌握', percent: 68, color: '#22C55E' },
    { label: '学习中', percent: 20, color: '#2563EB' },
    { label: '未学习', percent: 12, color: '#CBD5E1' },
  ] as CoverageLegend[],
}

export const MOCK_WEEKLY_PLAN: WeeklyPlanItem[] = [
  { day: '周一', title: 'Python基础语法精讲', typeLabel: '视频课程' },
  { day: '周二', title: '列表与字典操作详解', typeLabel: '文档资料' },
  { day: '周三', title: '函数基础练习题', typeLabel: '练习题库' },
  { day: '周四', title: '文件操作实战案例', typeLabel: '项目案例' },
  { day: '周五', title: '异常处理机制讲解', typeLabel: '视频课程' },
]

export const GENERATE_MATERIAL_TYPES = [
  { id: 'LESSON', label: '讲义' },
  { id: 'QUIZ', label: '练习题' },
  { id: 'ILLUSTRATION', label: '思维导图' },
  { id: 'CODE_CASE', label: '代码示例' },
  { id: 'VIDEO_CLIP', label: '学习视频脚本' },
] as const

export const GENERATE_DIFFICULTIES = ['基础', '进阶', '挑战'] as const
