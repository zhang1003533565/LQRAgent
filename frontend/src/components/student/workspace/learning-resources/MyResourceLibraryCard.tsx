import type { MyLibraryItem } from '@/utils/types/learningResources'

const TONE_STYLE = {
  orange: 'bg-[#FFF7ED] text-[#EA580C]',
  blue: 'bg-[#EAF3FF] text-[#2563EB]',
  cyan: 'bg-[#ECFEFF] text-[#06B6D4]',
  purple: 'bg-[#F5F3FF] text-[#8B5CF6]',
} as const

type Props = {
  items: MyLibraryItem[]
  onItemClick?: (id: string) => void
}

export default function MyResourceLibraryCard({ items, onItemClick }: Props) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h2 className="mb-4 text-lg font-bold text-[#0F2A5F]">我的资源库</h2>
      <ul className="space-y-1">
        {items.map((item) => {
          const Icon = item.icon
          return (
            <li key={item.id}>
              <button
                type="button"
                onClick={() => onItemClick?.(item.id)}
                className="flex h-[38px] w-full items-center gap-3 rounded-lg px-1 transition-colors hover:bg-[#F8FBFF]"
              >
                <div className={`flex h-[30px] w-[30px] items-center justify-center rounded-lg ${TONE_STYLE[item.tone]}`}>
                  <Icon className="h-4 w-4" />
                </div>
                <span className="flex-1 text-left text-sm text-[#334155]">{item.label}</span>
                <span className="text-sm font-semibold text-[#64748B]">{item.count}</span>
              </button>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
