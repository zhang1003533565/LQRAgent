import {
  BookOpen,
  Check,
  ChevronRight,
  Clock,
  Code2,
  Lock,
} from 'lucide-react'
import type { LearningPathNodeItem } from '@/utils/types/learning-path-ui'

type Props = {
  node: LearningPathNodeItem
  selected?: boolean
  onSelect: (nodeId: string) => void
}

const STATUS_LABEL: Record<LearningPathNodeItem['status'], string> = {
  current: '当前学习',
  completed: '已掌握',
  locked: '锁定',
  pending: '未开始',
}

const STATUS_STYLE: Record<LearningPathNodeItem['status'], string> = {
  current: 'bg-[#DBEAFE] text-[#2563EB]',
  completed: 'bg-[#DCFCE7] text-[#16A34A]',
  locked: 'bg-[#F1F5F9] text-[#94A3B8]',
  pending: 'bg-[#F1F5F9] text-[#64748B]',
}

const DIFFICULTY_STYLE: Record<LearningPathNodeItem['difficulty'], string> = {
  简单: 'bg-[#DCFCE7] text-[#16A34A]',
  中等: 'bg-[#FEF3C7] text-[#D97706]',
  困难: 'bg-[#FEE2E2] text-[#EF4444]',
}

export default function LearningNodeItem({ node, selected, onSelect }: Props) {
  const isCurrent = node.status === 'current' || selected
  const isLocked = node.status === 'locked'
  const clickable = !isLocked

  const handleClick = () => {
    if (!clickable) return
    onSelect(node.id)
  }

  return (
    <div
      role="button"
      tabIndex={clickable ? 0 : -1}
      title={isLocked ? '完成前置节点后解锁' : undefined}
      onClick={handleClick}
      onKeyDown={(e) => {
        if (clickable && (e.key === 'Enter' || e.key === ' ')) {
          e.preventDefault()
          onSelect(node.id)
        }
      }}
      className={`group flex h-16 items-center gap-3 rounded-xl border px-3 transition-all duration-200 ${
        isCurrent
          ? 'border-[#BFD7FF] bg-gradient-to-r from-[#EFF6FF] to-white shadow-[0_4px_16px_rgba(37,99,235,0.06)]'
          : 'border-[#E6EEFA] bg-white hover:-translate-y-0.5 hover:shadow-[0_8px_20px_rgba(15,23,42,0.05)]'
      } ${clickable ? 'cursor-pointer' : 'cursor-not-allowed opacity-80'}`}
    >
      <div
        className={`flex h-[26px] w-[26px] shrink-0 items-center justify-center rounded-full text-xs font-bold ${
          node.status === 'completed'
            ? 'bg-[#22C55E] text-white'
            : node.status === 'current' || selected
              ? 'bg-[#2563EB] text-white'
              : node.status === 'locked'
                ? 'bg-[#E2E8F0] text-[#94A3B8]'
                : 'bg-[#CBD5E1] text-[#334155]'
        }`}
      >
        {node.status === 'completed' ? (
          <Check className="h-3.5 w-3.5" />
        ) : node.status === 'locked' ? (
          <Lock className="h-3 w-3" />
        ) : (
          node.order
        )}
      </div>

      <div
        className={`flex h-[38px] w-[38px] shrink-0 items-center justify-center rounded-[10px] ${
          isCurrent ? 'bg-[#DBEAFE]' : 'bg-[#F1F5F9]'
        }`}
      >
        {node.status === 'locked' ? (
          <BookOpen className="h-[18px] w-[18px] text-[#94A3B8]" />
        ) : (
          <Code2 className={`h-[18px] w-[18px] ${isCurrent ? 'text-[#2563EB]' : 'text-[#64748B]'}`} />
        )}
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate text-[15px] font-bold text-[#0F2A5F]">{node.title}</p>
        <p className="truncate text-[13px] text-[#64748B]">{node.description}</p>
      </div>

      <div className="hidden shrink-0 items-center gap-2 sm:flex">
        <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${STATUS_STYLE[node.status]}`}>
          {STATUS_LABEL[node.status]}
        </span>
        <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${DIFFICULTY_STYLE[node.difficulty]}`}>
          {node.difficulty}
        </span>
        <span className="inline-flex items-center gap-1 text-[13px] text-[#64748B]">
          <Clock className="h-3.5 w-3.5" />
          {node.durationMinutes} 分钟
        </span>
        {clickable ? (
          <ChevronRight className="h-4 w-4 text-[#94A3B8] transition-transform group-hover:translate-x-0.5" />
        ) : null}
      </div>
    </div>
  )
}
