import { CalendarCheck } from 'lucide-react'

type Props = {
  label: string
  current: number
  total: number
}

export default function PathTodayGoalCard({ label, current, total }: Props) {
  const pct = total > 0 ? Math.min(100, Math.round((current / total) * 100)) : 0

  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-4 flex items-center gap-2">
        <CalendarCheck className="h-[18px] w-[18px] text-[#2563EB]" />
        <h2 className="text-lg font-bold text-[#0F2A5F]">今日目标</h2>
      </div>
      <div className="mb-3 flex items-center justify-between text-sm">
        <span className="text-[#64748B]">{label}</span>
        <span className="font-bold text-[#0F2A5F]">
          {current}/{total}
        </span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-[#E8EEF7]">
        <div
          className="h-full rounded-full bg-[#2563EB] transition-all duration-500"
          style={{ width: `${pct}%` }}
        />
      </div>
    </section>
  )
}
