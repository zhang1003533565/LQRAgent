import { ChevronRight, Lightbulb } from 'lucide-react'
import { formatBytes } from '@/utils/upload/uploadConstants'
import type { StorageUsage } from '@/utils/types/upload'

type Props = {
  storage?: StorageUsage | null
  compact?: boolean
}

export default function UploadTipsCard({ storage, compact }: Props) {
  return (
    <section
      className={`shrink-0 rounded-2xl border border-[#E6EEFA] bg-[#FFFBEB] shadow-[0_8px_24px_rgba(15,23,42,0.04)] ${
        compact ? 'p-3.5' : 'p-5'
      }`}
    >
      <div className="flex items-center gap-2">
        <span className={`flex items-center justify-center rounded-lg bg-[#FEF3C7] ${compact ? 'h-7 w-7' : 'h-8 w-8'}`}>
          <Lightbulb className="h-3.5 w-3.5 text-[#F59E0B]" />
        </span>
        <h3 className={`font-bold text-[#0F2A5F] ${compact ? 'text-sm' : 'text-base'}`}>
          上传小贴士
        </h3>
      </div>
      <ul className={`space-y-1 text-[#64748B] ${compact ? 'mt-2 text-[11px] leading-relaxed' : 'mt-3 text-sm leading-relaxed'}`}>
        <li>· 建议上传课件、笔记、习题，便于 AI 提取知识点</li>
        <li>· 上传后 AI 会自动解析并映射到知识图谱</li>
        <li>· 内容仅用于个人学习，不会对外公开</li>
      </ul>
      <button
        type="button"
        className="mt-2 inline-flex items-center gap-0.5 text-[11px] font-medium text-[#2563EB] hover:underline"
      >
        查看支持格式与大小
        {storage?.maxFileSizeBytes ? `（最大 ${formatBytes(storage.maxFileSizeBytes)}）` : null}
        <ChevronRight className="h-3 w-3" />
      </button>
    </section>
  )
}
