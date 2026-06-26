import { Loader2, RefreshCw, Search, Sparkles } from 'lucide-react'

type Props = {
  search: string
  loading?: boolean
  onSearchChange: (value: string) => void
  onRefresh: () => void
  onGenerate: () => void
}

export default function PageHeader({
  search,
  loading,
  onSearchChange,
  onRefresh,
  onGenerate,
}: Props) {
  return (
    <header className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
      <div>
        <h1 className="text-[34px] font-extrabold leading-[1.2] text-[#0F2A5F]">学习资源</h1>
        <p className="mt-1.5 text-sm text-[#64748B]">
          AI 根据你的学习路径和进度，推荐最适合的学习资料
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative w-full sm:w-[360px]">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-[#94A3B8]" />
          <input
            className="h-[42px] w-full rounded-xl border border-[#D8E4F5] bg-white pl-10 pr-4 text-sm text-[#334155] placeholder:text-[#94A3B8] outline-none transition-colors focus:border-[#93C5FD]"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="搜索资源、知识点或文件..."
          />
        </div>
        <button
          type="button"
          onClick={onRefresh}
          disabled={loading}
          className="inline-flex h-[42px] items-center gap-2 rounded-[10px] border border-[#D8E4F5] bg-white px-4 text-sm font-semibold text-[#2563EB] transition-all duration-200 hover:-translate-y-0.5 disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          刷新资源
        </button>
        <button
          type="button"
          onClick={onGenerate}
          className="inline-flex h-[42px] items-center gap-2 rounded-[10px] bg-gradient-to-br from-[#3B82F6] to-[#2563EB] px-4 text-sm font-semibold text-white shadow-[0_8px_20px_rgba(37,99,235,0.18)] transition-all duration-200 hover:-translate-y-0.5"
        >
          <Sparkles className="h-4 w-4" />
          生成学习资料
        </button>
      </div>
    </header>
  )
}
