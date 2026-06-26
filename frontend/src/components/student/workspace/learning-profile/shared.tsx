import { AlertCircle, Inbox } from 'lucide-react'

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description?: string
  action?: React.ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center py-10 text-center">
      <Inbox className="mb-3 h-9 w-9 text-[#94A3B8]" />
      <p className="text-sm font-semibold text-[#334155]">{title}</p>
      {description ? <p className="mt-1 text-xs text-[#64748B]">{description}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-8 text-center">
      <AlertCircle className="h-8 w-8 text-[#EF4444]" />
      <p className="text-sm text-[#64748B]">{message}</p>
      {onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          className="rounded-lg border border-[#D8E4F5] px-4 py-2 text-sm font-medium text-[#2563EB]"
        >
          重试
        </button>
      ) : null}
    </div>
  )
}

export function LoadingSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="h-14 animate-pulse rounded-xl bg-[#E8EEF7]" />
      ))}
    </div>
  )
}

export function MetricCard({
  title,
  value,
  hint,
  icon: Icon,
  color,
  bg,
}: {
  title: string
  value: string
  hint?: string
  icon: React.ComponentType<{ className?: string }>
  color: string
  bg: string
}) {
  return (
    <div className="flex h-[112px] items-center justify-between rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="min-w-0 flex-1">
        <p className="text-sm text-[#64748B]">{title}</p>
        <p className="mt-1 truncate text-[30px] font-extrabold leading-none text-[#0F2A5F]">{value}</p>
        {hint ? <p className="mt-1 truncate text-xs text-[#94A3B8]">{hint}</p> : null}
      </div>
      <div className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl ${bg}`}>
        <Icon className={`h-6 w-6 ${color}`} />
      </div>
    </div>
  )
}
