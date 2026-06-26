import { Loader2 } from 'lucide-react'

export default function LearningPathSkeleton() {
  return (
    <div className="space-y-4">
      <div className="h-[140px] animate-pulse rounded-[18px] border border-[#E6EEFA] bg-white" />
      <div className="space-y-2.5">
        {Array.from({ length: 3 }).map((_, i) => (
          <div
            key={i}
            className="h-[52px] animate-pulse rounded-[14px] border border-[#E6EEFA] bg-[#F8FBFF]"
          />
        ))}
      </div>
      <div className="flex items-center justify-center gap-2 py-6 text-sm text-[#64748B]">
        <Loader2 className="h-4 w-4 animate-spin text-[#2563EB]" />
        正在生成学习路径...
      </div>
    </div>
  )
}
