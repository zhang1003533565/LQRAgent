import { Award, CheckSquare, Flame, Trophy } from 'lucide-react'
import { MOCK_ACHIEVEMENTS } from '@/mock/learningPath'

const ICON_MAP = {
  streak: Flame,
  badge: Award,
  tasks: CheckSquare,
} as const

const TONE_STYLE = {
  orange: 'bg-[#FFF7ED] text-[#EA580C]',
  green: 'bg-[#F0FDF4] text-[#16A34A]',
} as const

export default function AchievementCard() {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-4 flex items-center gap-2">
        <Trophy className="h-[18px] w-[18px] text-[#2563EB]" />
        <h2 className="text-lg font-bold text-[#0F2A5F]">学习成就</h2>
      </div>
      <div className="grid grid-cols-3 gap-3">
        {MOCK_ACHIEVEMENTS.map((item) => {
          const Icon = ICON_MAP[item.id as keyof typeof ICON_MAP]
          return (
            <article
              key={item.id}
              className="flex h-[92px] flex-col items-center justify-center rounded-[14px] border border-[#E6EEFA] bg-white text-center transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_8px_20px_rgba(15,23,42,0.05)]"
            >
              <div
                className={`mb-2 flex h-8 w-8 items-center justify-center rounded-full ${TONE_STYLE[item.tone]}`}
              >
                <Icon className="h-4 w-4" />
              </div>
              <p className="text-[11px] text-[#64748B]">{item.label}</p>
              <p className="text-sm font-bold text-[#0F2A5F]">{item.value}</p>
            </article>
          )
        })}
      </div>
    </section>
  )
}
