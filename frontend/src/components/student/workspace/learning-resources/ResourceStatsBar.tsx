import type { ResourceCategory, ResourceStatItem } from '@/utils/types/learningResources'

type Props = {
  stats: ResourceStatItem[]
  activeCategory: ResourceCategory
  onSelect: (category: ResourceCategory) => void
}

export default function ResourceStatsBar({ stats, activeCategory, onSelect }: Props) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white px-5 py-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="grid grid-cols-2 gap-0 md:grid-cols-3 xl:grid-cols-6">
        {stats.map((item, index) => {
          const Icon = item.icon
          const active = activeCategory === item.id
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onSelect(item.id)}
              className={`flex items-center gap-3 px-3 py-2 text-left transition-colors ${
                index > 0 ? 'xl:border-l xl:border-[#EEF3FA]' : ''
              } ${index % 2 === 1 ? 'md:border-l md:border-[#EEF3FA] xl:border-l-0' : ''} ${
                index >= 2 ? 'md:border-t md:border-[#EEF3FA] xl:border-t-0' : ''
              } ${index >= 3 ? 'xl:border-l xl:border-[#EEF3FA]' : ''} ${
                active ? 'rounded-xl bg-[#F8FBFF]' : 'hover:bg-[#F8FBFF]'
              }`}
            >
              <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${item.iconBg}`}>
                <Icon className={`h-5 w-5 ${item.iconColor}`} />
              </div>
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-[#334155]">{item.label}</p>
                <p className="text-[13px] text-[#64748B]">{item.count.toLocaleString()}</p>
              </div>
            </button>
          )
        })}
      </div>
    </section>
  )
}
