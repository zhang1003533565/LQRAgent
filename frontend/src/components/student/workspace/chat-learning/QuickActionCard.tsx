import type { QuickActionItem } from '@/mock/chatLearning'

type Props = {
  action: QuickActionItem
  onSelect: (prompt: string) => void
}

export default function QuickActionCard({ action, onSelect }: Props) {
  const Icon = action.icon
  return (
    <button
      type="button"
      onClick={() => onSelect(action.prompt)}
      className="group flex h-16 flex-1 min-w-[120px] items-center gap-3 rounded-[14px] border border-[#E6EEFA] bg-white px-3 text-left transition-all duration-200 hover:-translate-y-0.5 hover:border-[#BFD7FF] hover:shadow-[0_12px_32px_rgba(37,99,235,0.10)]"
    >
      <span
        className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px] ${action.iconBg}`}
      >
        <Icon className="h-[18px] w-[18px] text-[#2563EB]" strokeWidth={1.8} />
      </span>
      <span className="min-w-0">
        <span className="block truncate text-[13px] font-semibold text-[#0F172A]">{action.title}</span>
        <span className="mt-0.5 block truncate text-[12px] text-[#64748B]">{action.description}</span>
      </span>
    </button>
  )
}
