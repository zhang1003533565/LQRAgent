import { AlertCircle, Inbox } from 'lucide-react'

export function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-center">
      <Inbox className="mb-3 h-10 w-10 text-[#94A3B8]" />
      <p className="text-sm font-semibold text-[#334155]">{title}</p>
      {description ? <p className="mt-1 text-xs text-[#64748B]">{description}</p> : null}
    </div>
  )
}

export function ErrorState({
  message,
  onRetry,
}: {
  message: string
  onRetry?: () => void
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-10 text-center">
      <AlertCircle className="h-9 w-9 text-[#EF4444]" />
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
        <div key={i} className="h-16 animate-pulse rounded-xl bg-[#E8EEF7]" />
      ))}
    </div>
  )
}
