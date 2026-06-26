import type { QuickToolItem } from '@/utils/types/chatSidebar'

type Props = {
  tools: QuickToolItem[]
  loading?: boolean
  onToolSelect: (prompt: string) => void
}

export default function QuickToolsCard({ tools, loading, onToolSelect }: Props) {
  return (
    <section className="h-[250px] shrink-0 rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h2 className="mb-4 text-lg font-bold text-[#0F172A]">快捷工具</h2>
      {loading ? (
        <div className="flex h-[160px] items-center justify-center text-sm text-[#94A3B8]">加载中…</div>
      ) : (
        <div className="grid grid-cols-3 gap-3">
          {tools.map((tool) => {
            const Icon = tool.icon
            return (
              <button
                key={tool.id}
                type="button"
                onClick={() => onToolSelect(tool.prompt)}
                className="flex h-[76px] flex-col items-center justify-center gap-2 rounded-xl border border-[#E6EEFA] bg-white transition-all duration-200 hover:border-[#BFD7FF] hover:bg-[#F8FBFF]"
              >
                <span className={`flex h-8 w-8 items-center justify-center rounded-[10px] ${tool.iconBg}`}>
                  <Icon className="h-4 w-4 text-[#2563EB]" strokeWidth={1.8} />
                </span>
                <span className="text-[13px] font-medium text-[#334155]">{tool.label}</span>
              </button>
            )
          })}
        </div>
      )}
    </section>
  )
}
