import type { LucideIcon } from 'lucide-react'
import {
  BookOpen,
  CalendarDays,
  CheckCircle,
  Layers,
  PlayCircle,
} from 'lucide-react'

export type NodeDifficulty = '简单' | '中等' | '困难'
export type LearningNodeStatus = 'current' | 'completed' | 'locked' | 'pending'

export interface LearningPathNodeItem {
  id: string
  order: number
  title: string
  description: string
  status: LearningNodeStatus
  difficulty: NodeDifficulty
  durationMinutes: number
  objectives: string[]
}

export interface LearningChapter {
  id: string
  index: number
  title: string
  description?: string
  nodes: LearningPathNodeItem[]
}

export interface PathOverviewStat {
  id: string
  label: string
  value: string
  icon: LucideIcon
  iconBg: string
  iconColor: string
}

export const MOCK_GOAL =
  '我想学 Python 补充信息：有一点基础，调整建议：建议将 Python 基础语法回顾和数据结构与字符串处理合并...'

export const MOCK_CYCLE_OPTIONS = ['1 周', '2 周', '4 周', '8 周']

export const MOCK_OVERVIEW: PathOverviewStat[] = [
  {
    id: 'total',
    label: '总节点数',
    value: '31',
    icon: Layers,
    iconBg: 'bg-[#EAF3FF]',
    iconColor: 'text-[#2563EB]',
  },
  {
    id: 'done',
    label: '已完成',
    value: '0',
    icon: CheckCircle,
    iconBg: 'bg-[#DCFCE7]',
    iconColor: 'text-[#16A34A]',
  },
  {
    id: 'active',
    label: '进行中',
    value: '1',
    icon: PlayCircle,
    iconBg: 'bg-[#EAF3FF]',
    iconColor: 'text-[#2563EB]',
  },
  {
    id: 'pending',
    label: '待学习',
    value: '30',
    icon: BookOpen,
    iconBg: 'bg-[#FFF7ED]',
    iconColor: 'text-[#D97706]',
  },
  {
    id: 'eta',
    label: '预计完成时间',
    value: '2026-08-12',
    icon: CalendarDays,
    iconBg: 'bg-[#F5F3FF]',
    iconColor: 'text-[#8B5CF6]',
  },
]

export const MOCK_CHAPTERS: LearningChapter[] = [
  {
    id: 'ch1',
    index: 1,
    title: '第一章 认识 Python',
    description: 'Python 简介、环境搭建与基础语法入门',
    nodes: [
      {
        id: 'kp-intro',
        order: 1,
        title: 'Python 简介与环境搭建',
        description: '认识 Python 的特点，完成开发环境的安装与配置',
        status: 'current',
        difficulty: '简单',
        durationMinutes: 40,
        objectives: [
          '了解 Python 的发展历史与应用场景',
          '完成 Python 开发环境的安装与配置',
          '运行第一个 Python 程序并理解执行过程',
        ],
      },
      {
        id: 'kp-io',
        order: 2,
        title: '输入输出',
        description: '学习程序的输入与输出方法',
        status: 'locked',
        difficulty: '简单',
        durationMinutes: 25,
        objectives: ['掌握 input 与 print 的用法', '理解格式化输出', '完成简单交互程序'],
      },
      {
        id: 'kp-variables',
        order: 3,
        title: '变量与数据类型',
        description: '理解变量的概念与常见数据类型',
        status: 'locked',
        difficulty: '简单',
        durationMinutes: 45,
        objectives: ['理解变量命名规则', '掌握 int/float/str/bool', '使用 type() 查看类型'],
      },
      {
        id: 'kp-operators',
        order: 4,
        title: '运算符',
        description: '掌握算术、比较、逻辑等常用运算符',
        status: 'locked',
        difficulty: '简单',
        durationMinutes: 30,
        objectives: ['掌握算术与比较运算', '理解逻辑运算符', '完成运算符综合练习'],
      },
    ],
  },
  {
    id: 'ch2',
    index: 2,
    title: '第二章 数据与变量',
    nodes: Array.from({ length: 6 }, (_, i) => ({
      id: `ch2-n${i + 1}`,
      order: i + 5,
      title: `数据与变量 · 节点 ${i + 1}`,
      description: '深入理解 Python 数据类型与变量操作',
      status: 'locked' as const,
      difficulty: '中等' as const,
      durationMinutes: 35,
      objectives: ['掌握核心概念', '完成章节练习', '巩固薄弱点'],
    })),
  },
  {
    id: 'ch3',
    index: 3,
    title: '第三章 条件与循环',
    nodes: Array.from({ length: 6 }, (_, i) => ({
      id: `ch3-n${i + 1}`,
      order: i + 11,
      title: `条件与循环 · 节点 ${i + 1}`,
      description: '掌握 if/for/while 控制程序流程',
      status: 'locked' as const,
      difficulty: '中等' as const,
      durationMinutes: 40,
      objectives: ['理解分支结构', '掌握循环写法', '避免常见逻辑错误'],
    })),
  },
  {
    id: 'ch4',
    index: 4,
    title: '第四章 函数与模块',
    nodes: Array.from({ length: 7 }, (_, i) => ({
      id: `ch4-n${i + 1}`,
      order: i + 17,
      title: `函数与模块 · 节点 ${i + 1}`,
      description: '学习函数定义、参数传递与模块导入',
      status: 'locked' as const,
      difficulty: '困难' as const,
      durationMinutes: 50,
      objectives: ['定义与调用函数', '理解作用域', '使用标准库模块'],
    })),
  },
  {
    id: 'ch5',
    index: 5,
    title: '第五章 进阶基础',
    nodes: Array.from({ length: 8 }, (_, i) => ({
      id: `ch5-n${i + 1}`,
      order: i + 24,
      title: `进阶基础 · 节点 ${i + 1}`,
      description: '面向对象、文件操作与异常处理入门',
      status: 'locked' as const,
      difficulty: '困难' as const,
      durationMinutes: 55,
      objectives: ['理解 OOP 基础', '读写文件', '处理常见异常'],
    })),
  },
]

export const MOCK_TODAY_GOAL = { label: '完成 1 个学习任务', current: 0, total: 1 }

export const MOCK_ACHIEVEMENTS = [
  { id: 'streak', label: '连续学习', value: '1 天', tone: 'orange' as const },
  { id: 'badge', label: '获得勋章', value: '0 个', tone: 'orange' as const },
  { id: 'tasks', label: '任务完成', value: '0 个', tone: 'green' as const },
]
