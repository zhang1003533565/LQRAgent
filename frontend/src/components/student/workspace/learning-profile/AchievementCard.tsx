import { Award, ChevronRight, Flame, Star, Zap } from 'lucide-react'
import { EmptyState } from './shared'
import type { LearningAchievement } from '@/utils/types/learningProfile'

const ICON_MAP: Record<string, React.ComponentType<{ className?: string }>> = {
  streak: Flame,
  quiz: Zap,
  upload: Award,
  mastery: Star,
}

function pickIcon(id: string) {
  const key = Object.keys(ICON_MAP).find((k) => id.includes(k))
  return key ? ICON_MAP[key] : Award
}

type Props = {
  achievements: LearningAchievement[]
}

export default function AchievementCard({ achievements }: Props) {
  const display = achievements.slice(0, 3)

  return (
    <section className="flex h-full min-h-[260px] flex-col rounded-[16px] border border-[#E6EEFA] bg-white p-4 shadow-[0_4px_16px_rgba(15,23,42,0.04)]">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-base font-bold text-[#0F2A5F]">本周收获</h2>
        <button
          type="button"
          className="inline-flex items-center gap-0.5 text-xs font-medium text-[#2563EB]"
        >
          查看全部
          <ChevronRight className="h-3.5 w-3.5" />
        </button>
      </div>

      {display.length === 0 ? (
        <EmptyState title="暂无成就记录" description="坚持学习后将解锁更多收获" />
      ) : (
        <div className="flex flex-1 flex-col gap-2">
          {display.map((item) => {
            const Icon = pickIcon(item.id)
            return (
              <div
                key={item.id}
                className={`flex items-center gap-3 rounded-[12px] border px-3 py-2.5 ${
                  item.achieved
                    ? 'border-[#DBEAFE] bg-gradient-to-r from-[#F8FBFF] to-white'
                    : 'border-[#EEF3FA] bg-[#FAFBFC] opacity-80'
                }`}
              >
                <span
                  className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${
                    item.achieved ? 'bg-[#EAF3FF] text-[#2563EB]' : 'bg-[#F1F5F9] text-[#94A3B8]'
                  }`}
                >
                  <Icon className="h-5 w-5" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold text-[#334155]">{item.title}</p>
                  <p className="truncate text-[11px] text-[#64748B]">{item.description}</p>
                </div>
                {item.target != null ? (
                  <p className="shrink-0 text-xs font-semibold text-[#2563EB]">
                    {item.progress ?? 0}/{item.target}
                  </p>
                ) : null}
              </div>
            )
          })}
        </div>
      )}
    </section>
  )
}
