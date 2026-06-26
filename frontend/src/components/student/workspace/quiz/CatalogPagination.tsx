import { ChevronLeft, ChevronRight } from 'lucide-react'

type Props = {
  page: number
  totalPages: number
  total: number
  pageSize: number
  onPageChange: (page: number) => void
}

export default function CatalogPagination({
  page,
  totalPages,
  total,
  pageSize,
  onPageChange,
}: Props) {
  if (total <= pageSize) return null

  const pages = Array.from({ length: totalPages }, (_, i) => i + 1)
  const showPages =
    totalPages <= 7
      ? pages
      : pages.filter((p) => p === 1 || p === totalPages || Math.abs(p - page) <= 1)

  return (
    <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-[#EEF3FA] pt-4">
      <p className="text-xs text-[#64748B]">
        共 <span className="font-semibold text-[#334155]">{total}</span> 个章节，每页 {pageSize} 个
      </p>
      <div className="flex items-center gap-1">
        <button
          type="button"
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
          className="flex h-8 w-8 items-center justify-center rounded-lg border border-[#E6EEFA] text-[#64748B] disabled:opacity-40 hover:bg-[#F8FBFF]"
          aria-label="上一页"
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
        {showPages.map((p, index) => {
          const prev = showPages[index - 1]
          const gap = prev != null && p - prev > 1
          return (
            <span key={p} className="flex items-center gap-1">
              {gap ? <span className="px-1 text-xs text-[#94A3B8]">…</span> : null}
              <button
                type="button"
                onClick={() => onPageChange(p)}
                className={`min-w-[32px] rounded-lg px-2 py-1 text-xs font-semibold ${
                  p === page
                    ? 'bg-[#2563EB] text-white'
                    : 'border border-[#E6EEFA] text-[#64748B] hover:bg-[#F8FBFF]'
                }`}
              >
                {p}
              </button>
            </span>
          )
        })}
        <button
          type="button"
          disabled={page >= totalPages}
          onClick={() => onPageChange(page + 1)}
          className="flex h-8 w-8 items-center justify-center rounded-lg border border-[#E6EEFA] text-[#64748B] disabled:opacity-40 hover:bg-[#F8FBFF]"
          aria-label="下一页"
        >
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  )
}
