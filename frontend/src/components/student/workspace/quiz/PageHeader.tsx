import { Loader2, RefreshCw, Search, SlidersHorizontal, Sparkles } from 'lucide-react'

type Props = {
  search: string
  filterLabel?: string
  generating?: boolean
  onSearchChange: (v: string) => void
  onFilterClick?: () => void
  onGenerate?: () => void
}

export default function PageHeader({
  search,
  filterLabel = '筛选',
  generating,
  onSearchChange,
  onFilterClick,
  onGenerate,
}: Props) {
  return (
    <header className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
      <div>
        <h1 className="text-[34px] font-extrabold leading-[1.2] text-[#0F2A5F]">答题练习</h1>
        <p className="mt-1.5 text-sm text-[#64748B]">通过练习巩固知识点，提升你的学习掌握度</p>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative w-full sm:w-[320px]">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-[#94A3B8]" />
          <input
            className="h-[42px] w-full rounded-xl border border-[#D8E4F5] bg-white pl-10 pr-4 text-sm outline-none focus:border-[#93C5FD]"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="搜索题目、知识点或关键词..."
          />
        </div>
        <button
          type="button"
          onClick={onFilterClick}
          className="inline-flex h-[42px] items-center gap-2 rounded-[10px] border border-[#D8E4F5] bg-white px-4 text-sm font-semibold text-[#2563EB]"
        >
          <SlidersHorizontal className="h-4 w-4" />
          {filterLabel}
        </button>
        <button
          type="button"
          onClick={onGenerate}
          disabled={generating}
          className="inline-flex h-[42px] items-center gap-2 rounded-[10px] bg-gradient-to-br from-[#3B82F6] to-[#2563EB] px-4 text-sm font-semibold text-white shadow-[0_8px_20px_rgba(37,99,235,0.18)] disabled:opacity-50"
        >
          {generating ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
          生成练习
        </button>
      </div>
    </header>
  )
}

export function RefreshButton({ loading, onClick }: { loading?: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={loading}
      className="inline-flex h-[42px] items-center gap-2 rounded-[10px] border border-[#D8E4F5] bg-white px-4 text-sm font-semibold text-[#2563EB]"
    >
      <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
      刷新
    </button>
  )
}
