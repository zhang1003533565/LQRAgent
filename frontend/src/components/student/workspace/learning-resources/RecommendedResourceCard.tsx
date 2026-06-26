import { ChevronRight, Star } from 'lucide-react'
import type { RecommendedResource } from '@/mock/learningResources'

type Props = {
  resource: RecommendedResource
  onClick?: () => void
}

export default function RecommendedResourceCard({ resource, onClick }: Props) {
  const Icon = resource.icon
  return (
    <article
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') onClick?.()
      }}
      className="group flex h-[170px] cursor-pointer flex-col overflow-hidden rounded-[14px] border border-[#E6EEFA] bg-white transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_12px_32px_rgba(37,99,235,0.10)]"
    >
      <div className={`relative flex h-[88px] items-center justify-center bg-gradient-to-br ${resource.coverGradient}`}>
        <span className="absolute left-3 top-3 rounded-full bg-white/90 px-2 py-0.5 text-[11px] font-semibold text-[#334155]">
          {resource.typeLabel}
        </span>
        <Icon className="h-8 w-8 text-white/90" />
      </div>
      <div className="flex flex-1 flex-col justify-between p-3">
        <div>
          <h3 className="line-clamp-1 text-[15px] font-bold text-[#0F2A5F]">{resource.title}</h3>
          <p className="mt-1 line-clamp-1 text-xs text-[#64748B]">{resource.description}</p>
        </div>
        <div className="flex items-center justify-between text-xs text-[#64748B]">
          <span>{resource.meta}</span>
          <span className="inline-flex items-center gap-0.5 font-medium text-[#F59E0B]">
            <Star className="h-3 w-3 fill-current" />
            {resource.rating}
          </span>
        </div>
      </div>
    </article>
  )
}

export function RecommendedResourceSection({
  resources,
  onResourceClick,
  onViewMore,
}: {
  resources: RecommendedResource[]
  onResourceClick?: (id: string) => void
  onViewMore?: () => void
}) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-4 flex items-center justify-between">
        <div>
          <h2 className="text-lg font-bold text-[#0F2A5F]">为你推荐</h2>
          <p className="mt-0.5 text-xs text-[#64748B]">基于当前学习路径与薄弱知识点推荐</p>
        </div>
        <button
          type="button"
          onClick={onViewMore}
          className="inline-flex items-center gap-0.5 text-sm font-medium text-[#2563EB] hover:text-[#1D4ED8]"
        >
          查看更多
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {resources.map((item) => (
          <RecommendedResourceCard
            key={item.id}
            resource={item}
            onClick={() => onResourceClick?.(item.id)}
          />
        ))}
      </div>
    </section>
  )
}
