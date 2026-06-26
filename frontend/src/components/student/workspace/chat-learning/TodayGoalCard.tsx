import { Pencil } from 'lucide-react'
import type { TodayGoalData } from '@/utils/types/chatSidebar'

type Props = {
  data: TodayGoalData
  loading?: boolean
}

export default function TodayGoalCard({ data, loading }: Props) {
  const { progress, items, currentStageTitle } = data
  const radius = 46
  const circumference = 2 * Math.PI * radius
  const offset = circumference - (progress / 100) * circumference

  return (
    <section className="h-[210px] shrink-0 rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-bold text-[#0F172A]">今日学习目标</h2>
        {currentStageTitle ? (
          <span className="max-w-[120px] truncate text-[11px] text-[#64748B]" title={currentStageTitle}>
            {currentStageTitle}
          </span>
        ) : null}
      </div>
      {loading ? (
        <div className="flex h-[108px] items-center justify-center text-sm text-[#94A3B8]">加载中…</div>
      ) : (
        <div className="flex items-center gap-5">
          <div className="relative h-[108px] w-[108px] shrink-0">
            <svg className="h-full w-full -rotate-90" viewBox="0 0 108 108" aria-hidden>
              <circle cx="54" cy="54" r={radius} fill="none" stroke="#E8EEF7" strokeWidth="10" />
              <circle
                cx="54"
                cy="54"
                r={radius}
                fill="none"
                stroke="#2563EB"
                strokeWidth="10"
                strokeLinecap="round"
                strokeDasharray={circumference}
                strokeDashoffset={offset}
                className="transition-[stroke-dashoffset] duration-500"
              />
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-[11px] text-[#64748B]">学习进度</span>
              <span className="text-[28px] font-bold leading-none text-[#0F172A]">{progress}%</span>
            </div>
          </div>
          <ul className="min-w-0 flex-1 space-y-3">
            {items.map((item) => (
              <li key={item.label} className="flex items-center gap-2 text-[13px]">
                <span
                  className="h-2 w-2 shrink-0 rounded-full"
                  style={{ backgroundColor: item.color }}
                />
                <span className="truncate text-[#64748B]">{item.label}</span>
                <span className="ml-auto shrink-0 font-medium text-[#334155]">
                  {item.current} / {item.total} {item.unit}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}
