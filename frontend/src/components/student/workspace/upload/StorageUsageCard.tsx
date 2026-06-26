import { ChevronRight } from 'lucide-react'
import { formatBytes } from '@/utils/upload/uploadConstants'
import type { StorageUsage } from '@/utils/types/upload'
import ProgressRing from './ProgressRing'
import { ErrorState, LoadingSkeleton } from './shared'

type Props = {
  data?: StorageUsage | null
  loading?: boolean
  error?: string | null
  onRetry?: () => void
  compact?: boolean
}

export default function StorageUsageCard({ data, loading, error, onRetry, compact }: Props) {
  if (loading) {
    return (
      <section className="shrink-0 rounded-2xl border border-[#E6EEFA] bg-white p-3.5">
        <LoadingSkeleton rows={1} />
      </section>
    )
  }
  if (error) return <ErrorState message={error} onRetry={onRetry} />

  const percent =
    data && data.totalBytes > 0
      ? Math.round((data.usedBytes / data.totalBytes) * 100)
      : 0

  if (compact) {
    return (
      <section className="shrink-0 rounded-2xl border border-[#E6EEFA] bg-white p-3.5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
        <h3 className="text-sm font-bold text-[#0F2A5F]">空间使用情况</h3>
        <div className="mt-2.5 flex items-center gap-3">
          <ProgressRing percent={percent} size={72} compact />
          <div className="min-w-0 flex-1">
            <p className="text-xs text-[#64748B]">
              已使用{' '}
              <span className="font-bold text-[#334155]">
                {formatBytes(data?.usedBytes ?? 0)} / {formatBytes(data?.totalBytes ?? 0)}
              </span>
            </p>
            <button
              type="button"
              className="mt-1.5 inline-flex items-center gap-0.5 text-[11px] font-medium text-[#2563EB] hover:underline"
            >
              扩容空间
              <ChevronRight className="h-3 w-3" />
            </button>
          </div>
        </div>
      </section>
    )
  }

  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h3 className="text-base font-bold text-[#0F2A5F]">空间使用情况</h3>
      <div className="mt-4 flex flex-col items-center">
        <ProgressRing percent={percent} size={110} />
        <p className="mt-3 text-sm text-[#64748B]">
          已使用{' '}
          <span className="font-bold text-[#334155]">
            {formatBytes(data?.usedBytes ?? 0)} / {formatBytes(data?.totalBytes ?? 0)}
          </span>
        </p>
        <button
          type="button"
          className="mt-3 inline-flex items-center gap-0.5 text-xs font-medium text-[#2563EB] hover:underline"
        >
          扩容空间
          <ChevronRight className="h-3.5 w-3.5" />
        </button>
      </div>
    </section>
  )
}
