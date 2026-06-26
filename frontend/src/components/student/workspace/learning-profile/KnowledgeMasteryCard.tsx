import { BookOpen, ChevronRight } from 'lucide-react'
import { EmptyState } from './shared'
import type { KnowledgeMasteryItem, MasteryStatus } from '@/utils/types/learningProfile'

const STATUS_STYLE: Record<MasteryStatus, string> = {
  weak: 'bg-[#FEE2E2] text-[#EF4444]',
  normal: 'bg-[#FEF3C7] text-[#D97706]',
  good: 'bg-[#DBEAFE] text-[#2563EB]',
  mastered: 'bg-[#DCFCE7] text-[#16A34A]',
}

const STATUS_LABEL: Record<MasteryStatus, string> = {
  weak: '待加强',
  normal: '一般',
  good: '良好',
  mastered: '熟练',
}

function iconBg(status: MasteryStatus) {
  if (status === 'weak') return 'bg-[#FEE2E2] text-[#EF4444]'
  if (status === 'mastered') return 'bg-[#DCFCE7] text-[#22C55E]'
  return 'bg-[#EAF3FF] text-[#2563EB]'
}

type Props = {
  items: KnowledgeMasteryItem[]
  onViewGraph?: () => void
  onItemClick?: (item: KnowledgeMasteryItem) => void
}

export default function KnowledgeMasteryCard({ items, onViewGraph, onItemClick }: Props) {
  const masteredCount = items.filter(
    (i) => i.status === 'mastered' || i.status === 'good',
  ).length

  return (
    <section className="flex max-h-[520px] min-h-[320px] min-w-0 flex-col overflow-hidden rounded-[16px] border border-[#E6EEFA] bg-white shadow-[0_4px_16px_rgba(15,23,42,0.04)] lg:max-h-[400px]">
      <div className="flex shrink-0 items-start justify-between gap-3 px-5 pb-3 pt-5">
        <div className="min-w-0">
          <h2 className="text-base font-bold text-[#0F2A5F]">知识点掌握分布</h2>
          <p className="mt-0.5 text-xs text-[#64748B]">
            已掌握 {masteredCount}/{items.length} 个知识点
          </p>
        </div>
        {onViewGraph ? (
          <button
            type="button"
            onClick={onViewGraph}
            className="inline-flex shrink-0 items-center gap-0.5 whitespace-nowrap text-xs font-medium text-[#2563EB] hover:underline"
          >
            查看知识图谱
            <ChevronRight className="h-3.5 w-3.5" />
          </button>
        ) : null}
      </div>

      {items.length === 0 ? (
        <div className="flex flex-1 items-center justify-center px-5 pb-5">
          <EmptyState
            title="暂无知识点掌握数据"
            description="完成学习路径或答题后将生成掌握分析"
          />
        </div>
      ) : (
        <>
          <div className="hidden shrink-0 items-center gap-3 px-5 pb-2 text-[11px] font-semibold text-[#94A3B8] md:flex">
            <span className="min-w-0 flex-[1.2]">知识模块</span>
            <span className="min-w-0 flex-1">掌握进度</span>
            <span className="w-9 shrink-0 text-center">进度</span>
            <span className="w-14 shrink-0 text-right">掌握程度</span>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-4 md:px-4">
            <ul className="space-y-0.5">
              {items.map((item) => (
                <li key={item.knowledgePointId}>
                  <button
                    type="button"
                    onClick={() => onItemClick?.(item)}
                    className="flex w-full min-w-0 items-center gap-2 rounded-xl px-2 py-2.5 text-left transition hover:bg-[#F8FBFF] md:gap-3"
                  >
                    <div className="flex min-w-0 flex-[1.2] items-center gap-2">
                      <span
                        className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${iconBg(item.status)}`}
                      >
                        <BookOpen className="h-3.5 w-3.5" />
                      </span>
                      <span className="truncate text-[13px] font-medium text-[#334155]">
                        {item.name}
                      </span>
                    </div>
                    <div className="hidden min-w-0 flex-1 items-center md:flex">
                      <div className="h-1.5 w-full overflow-hidden rounded-full bg-[#E8EEF7]">
                        <div
                          className="h-full rounded-full bg-[#2563EB]"
                          style={{ width: `${item.masteryRate}%` }}
                        />
                      </div>
                    </div>
                    <span className="hidden w-9 shrink-0 text-center text-xs text-[#64748B] md:block">
                      {item.masteryRate}%
                    </span>
                    <span className="ml-auto shrink-0 md:ml-0 md:w-14 md:text-right">
                      <span
                        className={`inline-flex whitespace-nowrap rounded-full px-2 py-0.5 text-[10px] font-semibold ${STATUS_STYLE[item.status]}`}
                      >
                        {STATUS_LABEL[item.status]}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </>
      )}
    </section>
  )
}
