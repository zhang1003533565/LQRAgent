import { ChevronRight } from 'lucide-react'

type Props = {
  percent: number
  legend: { label: string; percent: number; color: string }[]
  onViewCoverage?: () => void
}

export default function ResourceCoverageCard({ percent, legend, onViewCoverage }: Props) {
  const radius = 46
  const circumference = 2 * Math.PI * radius
  const offset = circumference - (percent / 100) * circumference

  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h2 className="mb-4 text-lg font-bold text-[#0F2A5F]">学习路径资源映射</h2>
      <div className="flex items-center gap-4">
        <div className="relative h-[110px] w-[110px] shrink-0">
          <svg className="h-full w-full -rotate-90" viewBox="0 0 110 110" aria-hidden>
            <circle cx="55" cy="55" r={radius} fill="none" stroke="#E8EEF7" strokeWidth="10" />
            <circle
              cx="55"
              cy="55"
              r={radius}
              fill="none"
              stroke="#22C55E"
              strokeWidth="10"
              strokeLinecap="round"
              strokeDasharray={circumference}
              strokeDashoffset={offset}
            />
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-2xl font-extrabold text-[#0F2A5F]">{percent}%</span>
          </div>
        </div>
        <ul className="min-w-0 flex-1 space-y-2">
          {legend.map((item) => (
            <li key={item.label} className="flex items-center gap-2 text-xs text-[#64748B]">
              <span className="h-2 w-2 rounded-full" style={{ backgroundColor: item.color }} />
              <span className="flex-1">{item.label}</span>
              <span className="font-semibold text-[#334155]">{item.percent}%</span>
            </li>
          ))}
        </ul>
      </div>
      <button
        type="button"
        onClick={onViewCoverage}
        className="mt-4 inline-flex h-9 w-full items-center justify-center gap-1 rounded-[10px] border border-[#D8E4F5] bg-white text-sm font-medium text-[#2563EB] transition-colors hover:bg-[#F8FBFF]"
      >
        查看完整覆盖情况
        <ChevronRight className="h-4 w-4" />
      </button>
    </section>
  )
}
