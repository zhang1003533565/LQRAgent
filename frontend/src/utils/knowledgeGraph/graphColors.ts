import type { GraphStatus } from '@/utils/knowledgeGraph/graphStatus'
import type { EdgeDisplayCategory, ImportanceLevel } from '@/types/knowledgeGraph'

export const STATUS_META: Record<
  GraphStatus,
  { label: string; color: string; glow: string; bg: string; icon?: 'check' | 'progress' | 'alert' }
> = {
  mastered: { label: '已掌握', color: '#475569', glow: 'rgba(71,85,105,0.15)', bg: 'rgba(71,85,105,0.08)', icon: 'check' },
  learning: { label: '学习中', color: '#2563EB', glow: 'rgba(37,99,235,0.15)', bg: 'rgba(37,99,235,0.08)', icon: 'progress' },
  weak: { label: '薄弱', color: '#F97316', glow: 'rgba(249,115,22,0.15)', bg: 'rgba(249,115,22,0.08)', icon: 'alert' },
  unlearned: { label: '未学习', color: '#475569', glow: 'rgba(71,85,105,0.12)', bg: 'rgba(71,85,105,0.06)' },
}

export const IMPORTANCE_LABEL: Record<ImportanceLevel, string> = {
  core: '核心知识点',
  important: '重要知识点',
  normal: '普通知识点',
  minor: '辅助知识点',
}

export const CANVAS_BG = '#F8FAFC'

export const SELECTED_COLOR = '#F97316'
export const NODE_FILL = 'rgba(15, 42, 95, 0.72)'
export const DEFAULT_EDGE_COLOR = 'rgba(96, 165, 250, 0.28)'

export function getEdgeStyle(
  category: EdgeDisplayCategory,
  state: 'default' | 'highlight' | 'muted' | 'hover',
): { color: string; width: number; dash: number[] } {
  const base: Record<EdgeDisplayCategory, { color: string; width: number; dash: number[] }> = {
    path_main: { color: 'rgba(37, 99, 235, 0.96)', width: 2.8, dash: [] },
    path_segment: { color: 'rgba(96, 165, 250, 0.72)', width: 1.8, dash: [] },
    prerequisite: { color: 'rgba(96, 165, 250, 0.42)', width: 1.4, dash: [5, 6] },
    related: { color: 'rgba(96, 165, 250, 0.28)', width: 1.1, dash: [3, 5] },
  }
  const style = base[category]
  if (state === 'highlight') {
    return { ...style, color: category === 'path_main' ? 'rgba(59,130,246,1)' : 'rgba(147,197,253,0.95)', width: style.width + 0.8 }
  }
  if (state === 'hover') {
    return { ...style, color: 'rgba(249,115,22,0.85)', width: style.width + 0.5 }
  }
  if (state === 'muted') {
    return { ...style, color: 'rgba(71, 85, 105, 0.22)', width: Math.max(1, style.width - 0.3) }
  }
  return style
}

export function getDifficultyText(value?: number): string {
  if (!value) return '★★★☆☆'
  return `${'★'.repeat(Math.min(value, 5))}${'☆'.repeat(Math.max(0, 5 - value))}`
}
