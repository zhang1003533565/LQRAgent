import { AlertCircle, CheckCircle2, FileStack, Loader2 } from 'lucide-react'
import { formatBytes } from '@/utils/upload/uploadConstants'
import type { UploadStats } from '@/utils/types/upload'
import { ErrorState, LoadingSkeleton } from './shared'

type Props = {
  data?: UploadStats | null
  loading?: boolean
  error?: string | null
  onRetry?: () => void
}

export default function UploadStatsCard({ data, loading, error, onRetry }: Props) {
  if (loading) return <LoadingSkeleton rows={2} />
  if (error) return <ErrorState message={error} onRetry={onRetry} />

  const items = [
    { label: '总文件', value: data?.totalFiles ?? 0, icon: FileStack, color: 'text-[#2563EB]' },
    { label: '已解析', value: data?.parsedFiles ?? 0, icon: CheckCircle2, color: 'text-[#22C55E]' },
    { label: '解析中', value: data?.processingFiles ?? 0, icon: Loader2, color: 'text-[#F59E0B]' },
    { label: '解析失败', value: data?.failedFiles ?? 0, icon: AlertCircle, color: 'text-[#EF4444]' },
  ]

  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5">
      <h3 className="text-base font-bold text-[#0F2A5F]">资料处理状态</h3>
      <div className="mt-4 grid grid-cols-2 gap-3">
        {items.map((item) => (
          <div key={item.label} className="rounded-xl border border-[#EEF3FA] bg-[#F8FBFF] p-3">
            <item.icon className={`h-5 w-5 ${item.color}`} />
            <p className="mt-2 text-2xl font-extrabold text-[#0F2A5F]">{item.value}</p>
            <p className="text-xs text-[#64748B]">{item.label}</p>
          </div>
        ))}
      </div>
      <p className="mt-3 text-xs text-[#64748B]">
        总占用：{formatBytes(data?.totalSizeBytes ?? 0)}
      </p>
    </section>
  )
}
