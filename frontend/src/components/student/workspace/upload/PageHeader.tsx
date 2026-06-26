import { History } from 'lucide-react'

type Props = {
  onOpenRecords?: () => void
}

export default function PageHeader({ onOpenRecords }: Props) {
  return (
    <header className="flex items-start justify-between gap-4">
      <div>
        <h1 className="text-[34px] font-extrabold leading-[1.2] text-[#0F2A5F]">上传学习资料</h1>
        <p className="mt-1.5 max-w-[640px] text-sm leading-relaxed text-[#64748B]">
          上传课件、笔记或文档，AI 将自动解析内容并生成知识图谱，助力你的个性化学习
        </p>
      </div>
      <button
        type="button"
        onClick={onOpenRecords}
        className="inline-flex h-[42px] shrink-0 items-center gap-2 rounded-[10px] border border-[#D8E4F5] bg-white px-4 text-sm font-semibold text-[#2563EB] shadow-sm"
      >
        <History className="h-4 w-4" />
        上传记录
      </button>
    </header>
  )
}
