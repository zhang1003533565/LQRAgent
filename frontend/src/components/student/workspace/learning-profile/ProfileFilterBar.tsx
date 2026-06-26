import { useEffect, useRef, useState } from 'react'
import { Check, ChevronDown } from 'lucide-react'
import type { ProfileRange } from '@/utils/types/learningProfile'

type Props = {
  pathOptions: Array<{ id: string; title: string }>
  pathSummary?: string
  pathNodeTitles?: string[]
  learningPathId?: string
  range: ProfileRange
  onPathChange: (id: string) => void
  onRangeChange: (range: ProfileRange) => void
}

const RANGES: Array<{ value: ProfileRange; label: string }> = [
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
  { value: '90d', label: '近 90 天' },
  { value: 'all', label: '全部' },
]

const VISIBLE_NODES = 8

export default function ProfileFilterBar({
  pathOptions,
  pathSummary,
  pathNodeTitles = [],
  learningPathId,
  range,
  onPathChange,
  onRangeChange,
}: Props) {
  const [open, setOpen] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  const selectedId = learningPathId || 'all'
  const selectedLabel =
    selectedId === 'all'
      ? '全部学习数据'
      : pathOptions.find((p) => p.id === selectedId)?.title || '当前学习路径'

  const summaryText = pathSummary || '汇总全部学习路径、答题练习与上传资料数据'
  const visibleNodes = expanded ? pathNodeTitles : pathNodeTitles.slice(0, VISIBLE_NODES)
  const hiddenCount = Math.max(0, pathNodeTitles.length - VISIBLE_NODES)

  useEffect(() => {
    const onClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [])

  const options = [{ id: 'all', title: '全部学习数据' }, ...pathOptions]

  return (
    <section className="w-full rounded-[16px] border border-[#E6EEFA] bg-white p-4 shadow-[0_4px_16px_rgba(15,23,42,0.03)]">
      <div className="grid w-full grid-cols-1 gap-3 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
        <div className="w-full">
          <p className="text-sm font-semibold text-[#64748B]">学习路径</p>
          <p className="mt-1 line-clamp-2 text-sm leading-relaxed text-[#334155]">{summaryText}</p>
        </div>

        <div ref={menuRef} className="relative w-full md:w-[168px]">
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            className="flex h-9 w-full items-center justify-between gap-2 rounded-xl border border-[#E6EEFA] bg-[#F8FBFF] px-3 text-sm font-medium text-[#334155] hover:border-[#BFDBFE]"
          >
            <span className="truncate">{selectedLabel}</span>
            <ChevronDown className={`h-4 w-4 shrink-0 text-[#64748B] ${open ? 'rotate-180' : ''}`} />
          </button>
          {open ? (
            <div className="absolute left-0 right-0 z-30 mt-1.5 rounded-xl border border-[#E6EEFA] bg-white py-1 shadow-lg">
              {options.map((opt) => {
                const active = selectedId === opt.id
                return (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => {
                      onPathChange(opt.id)
                      setOpen(false)
                    }}
                    className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-[#F8FBFF] ${
                      active ? 'bg-[#F8FBFF] font-semibold text-[#2563EB]' : 'text-[#334155]'
                    }`}
                  >
                    <span className="truncate">{opt.title}</span>
                    {active ? <Check className="h-4 w-4 shrink-0" /> : null}
                  </button>
                )
              })}
            </div>
          ) : null}
        </div>
      </div>

      {pathNodeTitles.length > 0 ? (
        <div className="mt-3 flex w-full flex-wrap gap-1.5">
          {visibleNodes.map((title, index) => (
            <span
              key={`${title}-${index}`}
              className="inline-block max-w-[200px] truncate rounded-full bg-[#F8FBFF] px-2.5 py-1 text-xs text-[#475569]"
              title={title}
            >
              {title}
            </span>
          ))}
          {!expanded && hiddenCount > 0 ? (
            <button
              type="button"
              onClick={() => setExpanded(true)}
              className="rounded-full bg-[#EAF3FF] px-2.5 py-1 text-xs font-medium text-[#2563EB]"
            >
              +{hiddenCount}
            </button>
          ) : null}
          {expanded && hiddenCount > 0 ? (
            <button
              type="button"
              onClick={() => setExpanded(false)}
              className="rounded-full px-2.5 py-1 text-xs text-[#64748B] hover:bg-[#F8FBFF]"
            >
              收起
            </button>
          ) : null}
        </div>
      ) : null}

      <div className="mt-3 flex w-full flex-wrap items-center gap-1.5 border-t border-[#F1F5F9] pt-3">
        <span className="text-xs font-semibold text-[#94A3B8]">统计范围</span>
        {RANGES.map((r) => (
          <button
            key={r.value}
            type="button"
            onClick={() => onRangeChange(r.value)}
            className={`rounded-full px-3 py-1.5 text-xs font-semibold ${
              range === r.value
                ? 'bg-[#EAF3FF] text-[#2563EB]'
                : 'bg-[#F8FBFF] text-[#64748B] hover:bg-[#EEF3FA]'
            }`}
          >
            {r.label}
          </button>
        ))}
      </div>
    </section>
  )
}
