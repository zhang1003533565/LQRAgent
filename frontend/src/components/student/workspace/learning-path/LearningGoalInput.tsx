import { Calendar, ChevronDown, Loader2, Target, WandSparkles } from 'lucide-react'

type Props = {
  goal: string
  cycle: string
  cycleOptions: string[]
  loading?: boolean
  onGoalChange: (value: string) => void
  onCycleChange: (value: string) => void
  onGenerate: () => void
}

export default function LearningGoalInput({
  goal,
  cycle,
  cycleOptions,
  loading,
  onGoalChange,
  onCycleChange,
  onGenerate,
}: Props) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-4 flex items-center gap-2">
        <Target className="h-[18px] w-[18px] text-[#2563EB]" />
        <h2 className="text-base font-bold text-[#0F2A5F]">输入学习目标</h2>
      </div>
      <div className="flex flex-col gap-3.5 lg:flex-row lg:items-center">
        <div className="min-w-0 flex-[68]">
          <input
            id="learning-goal-input"
            className="h-[52px] w-full rounded-xl border border-[#D8E4F5] bg-white px-4 text-sm text-[#334155] placeholder:text-[#94A3B8] outline-none transition-colors focus:border-[#93C5FD]"
            value={goal}
            onChange={(e) => onGoalChange(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !loading && onGenerate()}
            placeholder="我想学 Python 补充信息：有一点基础..."
          />
        </div>
        <div className="flex-[16]">
          <label className="mb-1 block text-xs text-[#64748B]">学习周期</label>
          <div className="relative">
            <Calendar className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-[#64748B]" />
            <select
              className="h-[52px] w-full appearance-none rounded-xl border border-[#D8E4F5] bg-white pl-10 pr-9 text-[15px] font-semibold text-[#334155] outline-none transition-colors focus:border-[#93C5FD]"
              value={cycle}
              onChange={(e) => onCycleChange(e.target.value)}
            >
              {cycleOptions.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
            <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#64748B]" />
          </div>
        </div>
        <div className="flex-[16] lg:pt-5">
          <button
            type="button"
            onClick={onGenerate}
            disabled={loading || !goal.trim()}
            className="inline-flex h-[52px] w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-br from-[#3B82F6] to-[#2563EB] text-[15px] font-bold text-white shadow-[0_8px_20px_rgba(37,99,235,0.18)] transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_12px_28px_rgba(37,99,235,0.24)] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {loading ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <WandSparkles className="h-4 w-4" />
            )}
            {loading ? '生成中...' : '生成学习路径'}
          </button>
        </div>
      </div>
    </section>
  )
}
