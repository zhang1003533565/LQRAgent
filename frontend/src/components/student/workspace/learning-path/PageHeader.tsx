import { RefreshCw, Sparkles } from 'lucide-react'

type Props = {
  onRestore: () => void
  onRegenerate: () => void
  restoreDisabled?: boolean
  regenerateDisabled?: boolean
  regenerateLabel?: string
}

export default function PageHeader({
  onRestore,
  onRegenerate,
  restoreDisabled,
  regenerateDisabled,
  regenerateLabel = '重新生成路径',
}: Props) {
  return (
    <header className="flex min-h-[72px] items-center justify-between gap-4">
      <div>
        <h1 className="text-[34px] font-extrabold leading-[1.2] text-[#0F2A5F]">学习路径</h1>
        <p className="mt-1.5 text-sm text-[#64748B]">根据学习目标自动生成阶段化学习计划</p>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <button
          type="button"
          onClick={onRestore}
          disabled={restoreDisabled}
          className="inline-flex h-10 items-center gap-2 rounded-[10px] border border-[#D8E4F5] bg-white px-4 text-sm font-semibold text-[#2563EB] transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_8px_20px_rgba(37,99,235,0.08)] disabled:cursor-not-allowed disabled:opacity-50"
        >
          <RefreshCw className="h-4 w-4" />
          恢复当前路径
        </button>
        <button
          type="button"
          onClick={onRegenerate}
          disabled={regenerateDisabled}
          className="inline-flex h-10 items-center gap-2 rounded-[10px] bg-gradient-to-br from-[#3B82F6] to-[#2563EB] px-4 text-sm font-semibold text-white shadow-[0_8px_20px_rgba(37,99,235,0.18)] transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_12px_28px_rgba(37,99,235,0.24)] disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Sparkles className="h-4 w-4" />
          {regenerateLabel}
        </button>
      </div>
    </header>
  )
}
