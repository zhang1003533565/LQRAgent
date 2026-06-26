import { Map, MessageSquare } from 'lucide-react'

type Props = {
  title?: string
  description?: string
  onGoPath: () => void
  onGoChat: () => void
}

export default function NoPathGuide({
  title = '还没有学习路径',
  description = '请先在聊天中描述学习目标并生成路径，或前往学习路径页手动创建',
  onGoPath,
  onGoChat,
}: Props) {
  return (
    <section className="flex min-h-[360px] flex-col items-center justify-center rounded-[18px] border border-dashed border-[#D8E4F5] bg-white/80 p-10 text-center shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-5 flex h-20 w-20 items-center justify-center rounded-[20px] bg-gradient-to-br from-[#DBEAFE] to-[#EFF6FF]">
        <Map className="h-10 w-10 text-[#2563EB]" />
      </div>
      <h3 className="mb-2 text-xl font-bold text-[#0F2A5F]">{title}</h3>
      <p className="mb-6 max-w-md text-sm leading-relaxed text-[#64748B]">{description}</p>
      <div className="flex flex-wrap justify-center gap-3">
        <button
          type="button"
          onClick={onGoChat}
          className="inline-flex h-11 items-center gap-2 rounded-xl bg-gradient-to-br from-[#3B82F6] to-[#2563EB] px-5 text-sm font-bold text-white shadow-[0_8px_20px_rgba(37,99,235,0.18)]"
        >
          <MessageSquare className="h-4 w-4" />
          去聊天生成
        </button>
        <button
          type="button"
          onClick={onGoPath}
          className="inline-flex h-11 items-center gap-2 rounded-xl border border-[#D8E4F5] px-5 text-sm font-semibold text-[#2563EB]"
        >
          去学习路径
        </button>
      </div>
    </section>
  )
}
