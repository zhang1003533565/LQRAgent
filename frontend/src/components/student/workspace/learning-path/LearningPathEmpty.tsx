import { Map, WandSparkles } from 'lucide-react'

type Props = {
  onGenerate: () => void
  loading?: boolean
}

export default function LearningPathEmpty({ onGenerate, loading }: Props) {
  return (
    <section className="flex min-h-[360px] flex-col items-center justify-center rounded-[18px] border border-dashed border-[#D8E4F5] bg-white/80 p-10 text-center shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-5 flex h-20 w-20 items-center justify-center rounded-[20px] bg-gradient-to-br from-[#DBEAFE] to-[#EFF6FF]">
        <Map className="h-10 w-10 text-[#2563EB]" />
      </div>
      <h3 className="mb-2 text-xl font-bold text-[#0F2A5F]">还没有学习路径</h3>
      <p className="mb-6 max-w-md text-sm leading-relaxed text-[#64748B]">
        输入学习目标，AI 将为你生成专属阶段化学习路径
      </p>
      <button
        type="button"
        onClick={onGenerate}
        disabled={loading}
        className="inline-flex h-11 items-center gap-2 rounded-xl bg-gradient-to-br from-[#3B82F6] to-[#2563EB] px-6 text-sm font-bold text-white shadow-[0_8px_20px_rgba(37,99,235,0.18)] transition-all duration-200 hover:-translate-y-0.5 disabled:opacity-50"
      >
        <WandSparkles className="h-4 w-4" />
        {loading ? '生成中...' : '生成学习路径'}
      </button>
    </section>
  )
}
