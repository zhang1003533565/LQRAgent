import { AlertTriangle, Route, ThumbsUp } from 'lucide-react'
import type { LearningInsight } from '@/utils/types/learningProfile'

function InsightCard({
  title,
  icon: Icon,
  iconBg,
  titleColor,
  border,
  bg,
  items,
  numbered,
  onAction,
}: {
  title: string
  icon: React.ComponentType<{ className?: string }>
  iconBg: string
  titleColor: string
  border: string
  bg: string
  items: LearningInsight[]
  numbered?: boolean
  onAction?: (insight: LearningInsight) => void
}) {
  return (
    <section className={`flex h-full min-h-[160px] flex-col rounded-[16px] border p-4 ${border} ${bg}`}>
      <div className="mb-4 flex items-center gap-3">
        <span className={`flex h-9 w-9 items-center justify-center rounded-xl ${iconBg}`}>
          <Icon className={`h-5 w-5 ${titleColor}`} />
        </span>
        <h3 className={`text-base font-bold ${titleColor}`}>{title}</h3>
      </div>
      {items.length === 0 ? (
        <p className="text-sm text-[#64748B]">数据积累后将自动生成分析</p>
      ) : (
        <ul className="space-y-2.5">
          {items.slice(0, 3).map((item, index) => (
            <li key={item.id} className="flex items-start gap-2 text-sm leading-relaxed text-[#334155]">
              {numbered ? (
                <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-[#2563EB] text-[10px] font-bold text-white">
                  {index + 1}
                </span>
              ) : (
                <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-current opacity-60" />
              )}
              <span>
                {item.content}
                {item.action ? (
                  <button
                    type="button"
                    onClick={() => onAction?.(item)}
                    className="ml-1 text-xs font-medium text-[#2563EB] hover:underline"
                  >
                    {item.action.label}
                  </button>
                ) : null}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

type Props = {
  insights: LearningInsight[]
  onAction?: (insight: LearningInsight) => void
}

export default function InsightCardsSection({ insights, onAction }: Props) {
  const strengths = insights
    .filter((i) => i.type === 'strength')
    .sort((a, b) => (a.priority ?? 99) - (b.priority ?? 99))
  const weaknesses = insights
    .filter((i) => i.type === 'weakness' || i.type === 'risk')
    .sort((a, b) => (a.priority ?? 99) - (b.priority ?? 99))
  const suggestions = insights
    .filter((i) => i.type === 'suggestion')
    .sort((a, b) => (a.priority ?? 99) - (b.priority ?? 99))

  return (
    <div className="grid grid-cols-1 items-stretch gap-3 lg:grid-cols-3">
      <InsightCard
        title="我的学习优势"
        icon={ThumbsUp}
        iconBg="bg-[#DCFCE7]"
        titleColor="text-[#047857]"
        border="border-[#DCFCE7]"
        bg="bg-gradient-to-br from-white to-[#ECFDF5]"
        items={strengths}
      />
      <InsightCard
        title="需要重点提升"
        icon={AlertTriangle}
        iconBg="bg-[#FFEDD5]"
        titleColor="text-[#EA580C]"
        border="border-[#FED7AA]"
        bg="bg-gradient-to-br from-white to-[#FFF7ED]"
        items={weaknesses}
        onAction={onAction}
      />
      <InsightCard
        title="下一阶段建议"
        icon={Route}
        iconBg="bg-[#DBEAFE]"
        titleColor="text-[#1D4ED8]"
        border="border-[#DBEAFE]"
        bg="bg-gradient-to-br from-white to-[#EFF6FF]"
        items={suggestions}
        numbered
        onAction={onAction}
      />
    </div>
  )
}
