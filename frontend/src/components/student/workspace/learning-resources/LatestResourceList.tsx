import { Bookmark, ChevronRight, Star } from 'lucide-react'
import type { LatestResource } from '@/mock/learningResources'

type SortKey = 'latest' | 'favorite' | 'rating'

type Props = {
  resources: LatestResource[]
  sortKey: SortKey
  favorites: Set<string>
  searchQuery?: string
  onSortChange: (key: SortKey) => void
  onToggleFavorite: (id: string) => void
  onViewMore?: () => void
}

function highlightText(text: string | undefined, query: string) {
  const safe = text ?? ''
  if (!query.trim()) return safe
  const idx = safe.toLowerCase().indexOf(query.toLowerCase())
  if (idx < 0) return safe
  return (
    <>
      {safe.slice(0, idx)}
      <mark className="rounded bg-[#FEF3C7] px-0.5 text-[#92400E]">{safe.slice(idx, idx + query.length)}</mark>
      {safe.slice(idx + query.length)}
    </>
  )
}

export default function LatestResourceList({
  resources,
  sortKey,
  favorites,
  searchQuery = '',
  onSortChange,
  onToggleFavorite,
  onViewMore,
}: Props) {
  const sortTabs: { key: SortKey; label: string }[] = [
    { key: 'latest', label: '最新发布' },
    { key: 'favorite', label: '最多收藏' },
    { key: 'rating', label: '最高评分' },
  ]

  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <h2 className="text-lg font-bold text-[#0F2A5F]">最新资源</h2>
        <div className="flex flex-wrap gap-2">
          {sortTabs.map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => onSortChange(tab.key)}
              className={`h-7 rounded-full px-3 text-xs font-medium transition-colors ${
                sortKey === tab.key
                  ? 'bg-[#EAF3FF] text-[#2563EB]'
                  : 'text-[#64748B] hover:bg-[#F8FBFF]'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {resources.length === 0 ? (
        <div className="py-10 text-center">
          <p className="text-sm text-[#64748B]">暂未找到相关资源，可以尝试让 AI 为你生成</p>
        </div>
      ) : (
        <div className="space-y-0">
          {resources.map((item) => {
            const favorited = favorites.has(item.id) || item.favorited
            return (
              <article
                key={item.id}
                className="group flex min-h-[56px] items-center gap-3 border-b border-[#EEF3FA] px-2 py-3 transition-colors last:border-b-0 hover:rounded-[10px] hover:bg-[#F8FBFF]"
              >
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#EAF3FF] text-xs font-bold text-[#2563EB]">
                  {(item.typeLabel ?? '资').slice(0, 1)}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold text-[#0F2A5F]">
                    {highlightText(item.title, searchQuery)}
                  </p>
                  <p className="truncate text-xs text-[#64748B]">{item.description ?? ''}</p>
                </div>
                <span className="hidden shrink-0 rounded-full bg-[#F1F5F9] px-2 py-0.5 text-[11px] text-[#64748B] sm:inline">
                  {item.typeLabel ?? '学习资料'}
                </span>
                <span className="hidden w-16 shrink-0 text-xs text-[#64748B] md:inline">
                  {item.difficulty || item.meta}
                </span>
                <span className="hidden w-24 shrink-0 text-xs text-[#64748B] lg:inline">{item.date}</span>
                <span className="inline-flex w-12 shrink-0 items-center gap-0.5 text-xs font-medium text-[#F59E0B]">
                  <Star className="h-3 w-3 fill-current" />
                  {item.rating}
                </span>
                <button
                  type="button"
                  aria-label={favorited ? '取消收藏' : '收藏'}
                  onClick={() => onToggleFavorite(item.id)}
                  className="shrink-0 rounded-lg p-1.5 transition-colors hover:bg-[#FFF7ED]"
                >
                  <Bookmark
                    className={`h-4 w-4 ${favorited ? 'fill-[#F59E0B] text-[#F59E0B]' : 'text-[#94A3B8]'}`}
                  />
                </button>
              </article>
            )
          })}
        </div>
      )}

      <button
        type="button"
        onClick={onViewMore}
        className="mt-4 flex h-9 w-full items-center justify-center gap-1 rounded-[10px] text-sm font-medium text-[#2563EB] transition-colors hover:bg-[#F8FBFF]"
      >
        查看更多资源
        <ChevronRight className="h-4 w-4" />
      </button>
    </section>
  )
}
