import { ChevronRight, Sparkles } from 'lucide-react'
import ProgressRing from './ProgressRing'
import { EmptyState } from './shared'
import type {
  LearningInsight,
  LearningProfileOverview,
  RecentLearningActivity,
  WeakKnowledgePoint,
} from '@/utils/types/learningProfile'

function SummaryCard({ overview }: { overview: LearningProfileOverview }) {
  return (
    <section className="shrink-0 rounded-2xl border border-[#E6EEFA] bg-white p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h3 className="text-sm font-bold text-[#0F2A5F]">学习状态摘要</h3>
      <div className="mt-3 flex items-center gap-3">
        <ProgressRing percent={overview.overallMasteryRate} size={88} />
        <div className="min-w-0 flex-1 text-xs text-[#64748B]">
          <p>
            等级：<span className="font-bold text-[#334155]">{overview.masteryLevel || '—'}</span>
          </p>
          <p className="mt-1">
            路径节点：{overview.completedLearningPathNodes ?? 0}/{overview.totalLearningPathNodes ?? 0}
          </p>
          {overview.accuracyRate != null ? (
            <p className="mt-1">正确率：{overview.accuracyRate}%</p>
          ) : null}
        </div>
      </div>
      <div className="mt-3 grid grid-cols-2 gap-2 text-center">
        <div className="rounded-xl bg-[#F8FBFF] p-2">
          <p className="text-base font-extrabold text-[#0F2A5F]">{overview.continuousLearningDays}</p>
          <p className="text-[10px] text-[#64748B]">连续学习天</p>
        </div>
        <div className="rounded-xl bg-[#F8FBFF] p-2">
          <p className="text-base font-extrabold text-[#0F2A5F]">{overview.completedQuestions ?? '—'}</p>
          <p className="text-[10px] text-[#64748B]">完成题目</p>
        </div>
      </div>
    </section>
  )
}

function WeakPointsCard({
  items,
  onPractice,
}: {
  items: WeakKnowledgePoint[]
  onPractice?: (kpId: string) => void
}) {
  return (
    <section className="shrink-0 rounded-2xl border border-[#E6EEFA] bg-white p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h3 className="text-sm font-bold text-[#0F2A5F]">薄弱知识点</h3>
      {items.length === 0 ? (
        <p className="mt-3 text-xs text-[#64748B]">暂未发现明显薄弱点</p>
      ) : (
        <ul className="mt-2 space-y-2">
          {items.slice(0, 5).map((item) => (
            <li key={item.knowledgePointId} className="rounded-xl border border-[#EEF3FA] px-3 py-2">
              <div className="flex items-center justify-between gap-2">
                <span className="truncate text-xs font-semibold text-[#334155]">{item.name}</span>
                <span className="text-[10px] text-[#EF4444]">{item.masteryRate}%</span>
              </div>
              {item.weaknessReason ? (
                <p className="mt-0.5 text-[10px] text-[#64748B]">{item.weaknessReason}</p>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {items[0] ? (
        <button
          type="button"
          onClick={() => onPractice?.(items[0].knowledgePointId)}
          className="mt-3 w-full rounded-xl bg-[#2563EB] py-2 text-xs font-semibold text-white"
        >
          一键强化练习
        </button>
      ) : null}
    </section>
  )
}

function NextActionCard({
  insight,
  onAction,
}: {
  insight?: LearningInsight
  onAction?: () => void
}) {
  return (
    <section className="shrink-0 rounded-2xl border border-[#E6EEFA] bg-gradient-to-br from-white to-[#EFF6FF] p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="flex items-center gap-2">
        <Sparkles className="h-4 w-4 text-[#2563EB]" />
        <h3 className="text-sm font-bold text-[#0F2A5F]">AI 下一步行动</h3>
      </div>
      {insight ? (
        <>
          <p className="mt-2 text-xs leading-relaxed text-[#334155]">{insight.content}</p>
          {insight.action ? (
            <button
              type="button"
              onClick={onAction}
              className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-[#2563EB]"
            >
              {insight.action.label}
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          ) : null}
        </>
      ) : (
        <p className="mt-2 text-xs text-[#64748B]">完成更多学习后将生成个性化建议</p>
      )}
    </section>
  )
}

function ActivitiesCard({ items }: { items: RecentLearningActivity[] }) {
  return (
    <section className="shrink-0 rounded-2xl border border-[#E6EEFA] bg-white p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h3 className="text-sm font-bold text-[#0F2A5F]">最近学习活动</h3>
      {items.length === 0 ? (
        <EmptyState title="暂无活动记录" />
      ) : (
        <ul className="mt-2 space-y-2">
          {items.map((item) => (
            <li key={item.id} className="text-xs">
              <p className="truncate font-semibold text-[#334155]">{item.title}</p>
              <p className="truncate text-[10px] text-[#94A3B8]">
                {new Date(item.occurredAt).toLocaleString('zh-CN', {
                  month: 'short',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

type Props = {
  overview?: LearningProfileOverview | null
  weakPoints: WeakKnowledgePoint[]
  nextInsight?: LearningInsight
  activities: RecentLearningActivity[]
  onPractice?: (kpId: string) => void
  onNextAction?: () => void
}

export default function ProfileAuxPanel({
  overview,
  weakPoints,
  nextInsight,
  activities,
  onPractice,
  onNextAction,
}: Props) {
  if (!overview) return null

  return (
    <aside className="hidden h-full w-[280px] shrink-0 flex-col overflow-hidden border-l border-[#E6EEFA] bg-[#F8FBFF] px-4 py-5 xl:flex 2xl:w-[300px]">
      <div className="flex h-full flex-col justify-between gap-3 overflow-hidden">
        <SummaryCard overview={overview} />
        <WeakPointsCard items={weakPoints} onPractice={onPractice} />
        <NextActionCard insight={nextInsight} onAction={onNextAction} />
        <ActivitiesCard items={activities} />
      </div>
    </aside>
  )
}
