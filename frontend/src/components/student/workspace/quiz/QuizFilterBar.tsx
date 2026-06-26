import type { QuizCatalogFilters } from '@/utils/types/quiz'

const TYPE_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'single_choice', label: '单选题' },
  { value: 'multiple_choice', label: '多选题' },
  { value: 'true_false', label: '判断题' },
  { value: 'fill_blank', label: '填空题' },
  { value: 'coding', label: '编程题' },
]

const DIFFICULTY_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'easy', label: '基础' },
  { value: 'medium', label: '进阶' },
  { value: 'hard', label: '挑战' },
]

const STATUS_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'not_started', label: '未开始' },
  { value: 'in_progress', label: '进行中' },
  { value: 'completed', label: '已完成' },
  { value: 'recommended', label: '推荐' },
]

type Props = {
  filters: QuizCatalogFilters
  onChange: (next: QuizCatalogFilters) => void
  onReset: () => void
}

function SelectRow({
  label,
  options,
  value,
  onChange,
}: {
  label: string
  options: { value: string; label: string }[]
  value?: string
  onChange: (v: string) => void
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-xs font-semibold text-[#64748B]">{label}</span>
      {options.map((opt) => (
        <button
          key={opt.value}
          type="button"
          onClick={() => onChange(opt.value)}
          className={`h-7 rounded-full px-3 text-xs font-medium transition-colors ${
            (value || 'all') === opt.value
              ? 'bg-[#EAF3FF] text-[#2563EB]'
              : 'bg-[#F1F5F9] text-[#64748B] hover:bg-[#EAF3FF]'
          }`}
        >
          {opt.label}
        </button>
      ))}
    </div>
  )
}

export default function QuizFilterBar({ filters, onChange, onReset }: Props) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="space-y-3">
        <SelectRow
          label="题型"
          options={TYPE_OPTIONS}
          value={filters.type}
          onChange={(type) => onChange({ ...filters, type })}
        />
        <SelectRow
          label="难度"
          options={DIFFICULTY_OPTIONS}
          value={filters.difficulty}
          onChange={(difficulty) => onChange({ ...filters, difficulty })}
        />
        <SelectRow
          label="状态"
          options={STATUS_OPTIONS}
          value={filters.status}
          onChange={(status) => onChange({ ...filters, status })}
        />
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold text-[#64748B]">学习路径</span>
          {[
            { value: 'all', label: '全部路径' },
            { value: 'current', label: '当前学习路径' },
          ].map((opt) => (
            <button
              key={opt.value}
              type="button"
              onClick={() => onChange({ ...filters, learningPathId: opt.value })}
              className={`h-7 rounded-full px-3 text-xs font-medium ${
                (filters.learningPathId || 'all') === opt.value
                  ? 'bg-[#EAF3FF] text-[#2563EB]'
                  : 'bg-[#F1F5F9] text-[#64748B]'
              }`}
            >
              {opt.label}
            </button>
          ))}
          <button
            type="button"
            onClick={onReset}
            className="ml-auto text-xs font-medium text-[#2563EB] hover:underline"
          >
            重置
          </button>
        </div>
      </div>
    </section>
  )
}
