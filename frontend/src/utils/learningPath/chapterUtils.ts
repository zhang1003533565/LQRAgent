import type { PathNode } from '@/utils/types/learning-path'
import type {
  LearningChapter,
  LearningNodeStatus,
  LearningPathNodeItem,
  NodeDifficulty,
} from '@/mock/learningPath'

const CHAPTER_TITLES = [
  { title: '第一章 认识 Python', description: 'Python 简介、环境搭建与基础语法入门' },
  { title: '第二章 数据与变量', description: '深入理解数据类型与变量操作' },
  { title: '第三章 条件与循环', description: '掌握分支与循环控制程序流程' },
  { title: '第四章 函数与模块', description: '函数定义、参数传递与模块导入' },
  { title: '第五章 进阶基础', description: '面向对象、文件操作与异常处理' },
]

const DIFFICULTIES: NodeDifficulty[] = ['简单', '简单', '中等', '中等', '困难']

function inferDifficulty(order: number): NodeDifficulty {
  return DIFFICULTIES[Math.min(Math.floor((order - 1) / 3), DIFFICULTIES.length - 1)]
}

function defaultObjectives(title: string): string[] {
  return [
    `理解「${title}」的核心概念`,
    '完成本节配套练习',
    '能够独立解决相关问题',
  ]
}

export function buildChaptersFromPathNodes(
  nodes: PathNode[],
  selectedKpId: string | null,
): LearningChapter[] {
  if (nodes.length === 0) return []

  const sorted = [...nodes].sort((a, b) => a.order - b.order)
  const chapterCount = Math.min(5, Math.max(1, Math.ceil(sorted.length / 6)))
  const chunkSize = Math.ceil(sorted.length / chapterCount)

  const firstIncompleteIdx = sorted.findIndex(
    (n) => !n.completed && n.status !== 'COMPLETED',
  )

  const chapters: LearningChapter[] = []

  for (let c = 0; c < chapterCount; c++) {
    const slice = sorted.slice(c * chunkSize, (c + 1) * chunkSize)
    if (slice.length === 0) continue

    const chapterNodes: LearningPathNodeItem[] = slice.map((node, idx) => {
      const globalIdx = c * chunkSize + idx
      let status: LearningNodeStatus = 'pending'

      if (node.completed || node.status === 'COMPLETED') {
        status = 'completed'
      } else if (selectedKpId === node.kpId) {
        status = 'current'
      } else if (globalIdx === firstIncompleteIdx) {
        status = 'current'
      } else if (globalIdx > firstIncompleteIdx && firstIncompleteIdx >= 0) {
        status = 'locked'
      } else if (node.status === 'ACTIVE') {
        status = 'current'
      }

      return {
        id: node.kpId,
        order: node.order || globalIdx + 1,
        title: node.title,
        description: node.description || `学习「${node.title}」相关内容`,
        status,
        difficulty: inferDifficulty(node.order || globalIdx + 1),
        durationMinutes: 30 + ((node.order || globalIdx) % 4) * 10,
        objectives: defaultObjectives(node.title),
      }
    })

    const meta = CHAPTER_TITLES[c] ?? {
      title: `第 ${c + 1} 章`,
      description: '继续推进你的学习路径',
    }

    chapters.push({
      id: `chapter-${c + 1}`,
      index: c + 1,
      title: meta.title,
      description: meta.description,
      nodes: chapterNodes,
    })
  }

  return chapters
}

export function findNodeInChapters(
  chapters: LearningChapter[],
  nodeId: string | null,
): LearningPathNodeItem | null {
  if (!nodeId) return null
  for (const ch of chapters) {
    const found = ch.nodes.find((n) => n.id === nodeId)
    if (found) return found
  }
  return null
}

export function computeOverviewFromChapters(chapters: LearningChapter[]) {
  const all = chapters.flatMap((c) => c.nodes)
  const total = all.length
  const completed = all.filter((n) => n.status === 'completed').length
  const current = all.filter((n) => n.status === 'current').length
  const pending = total - completed - current

  const estimatedDays = Math.max(7, Math.ceil(pending * 1.5))
  const d = new Date()
  d.setDate(d.getDate() + estimatedDays)
  const eta = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

  return { total, completed, current, pending, eta }
}

export function chapterProgress(chapter: LearningChapter) {
  const done = chapter.nodes.filter((n) => n.status === 'completed').length
  return { done, total: chapter.nodes.length }
}
