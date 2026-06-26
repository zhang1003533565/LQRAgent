import { ChevronRight } from 'lucide-react'

type Props = {
  items: { day: string; title: string; typeLabel: string }[]
  onViewPlan?: () => void
}

export default function WeeklyResourcePlanCard({ items, onViewPlan }: Props) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h2 className="mb-4 text-lg font-bold text-[#0F2A5F]">本周学习资源推荐</h2>
      <ul className="space-y-2">
        {items.map((item) => (
          <li key={item.day} className="flex h-9 items-center gap-2 text-xs">
            <span className="w-10 shrink-0 font-semibold text-[#64748B]">{item.day}</span>
            <span className="min-w-0 flex-1 truncate font-medium text-[#334155]">{item.title}</span>
            <span className="shrink-0 rounded-full bg-[#F1F5F9] px-2 py-0.5 text-[11px] text-[#64748B]">
              {item.typeLabel}
            </span>
          </li>
        ))}
      </ul>
      <button
        type="button"
        onClick={onViewPlan}
        className="mt-4 inline-flex h-9 w-full items-center justify-center gap-1 rounded-[10px] bg-[#F8FBFF] text-sm font-medium text-[#2563EB] transition-colors hover:bg-[#EAF3FF]"
      >
        查看完整学习计划
        <ChevronRight className="h-4 w-4" />
      </button>
    </section>
  )
}
