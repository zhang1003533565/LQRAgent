import type { PathOverviewStat } from '@/mock/learningPath'

type Props = {
  stats: PathOverviewStat[]
}

export default function PathOverviewCards({ stats }: Props) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-4 flex items-center gap-2">
        <svg viewBox="0 0 24 24" className="h-[18px] w-[18px] text-[#2563EB]" aria-hidden>
          <path
            d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M2 14h4M10 12h4M18 16h4"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        <h2 className="text-lg font-bold text-[#0F2A5F]">当前路径概览</h2>
      </div>
      <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">
        {stats.map((stat) => {
          const Icon = stat.icon
          return (
            <article
              key={stat.id}
              className="flex h-[78px] items-center gap-3 rounded-[14px] border border-[#E6EEFA] bg-white p-4 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_8px_24px_rgba(15,23,42,0.06)]"
            >
              <div
                className={`flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-[14px] ${stat.iconBg}`}
              >
                <Icon className={`h-5 w-5 ${stat.iconColor}`} />
              </div>
              <div className="min-w-0">
                <p className="text-[13px] text-[#64748B]">{stat.label}</p>
                <p className="truncate text-2xl font-extrabold text-[#0F2A5F]">{stat.value}</p>
              </div>
            </article>
          )
        })}
      </div>
    </section>
  )
}
