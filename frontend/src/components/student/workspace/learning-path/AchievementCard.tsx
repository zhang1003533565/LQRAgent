import { useEffect, useState } from 'react'
import { Award, CheckSquare, Flame, Trophy } from 'lucide-react'
import { fetchProfileAchievements, mapApiAchievements } from '@/api/student/profile'
import { useProfileStore } from '@/utils/store/profileStore'
import type { LearningAchievement } from '@/utils/types/learningProfile'

const ICON_MAP = {
  streak: Flame,
  badge: Award,
  tasks: CheckSquare,
} as const

const TONE_STYLE = {
  orange: 'bg-[#FFF7ED] text-[#EA580C]',
  green: 'bg-[#F0FDF4] text-[#16A34A]',
} as const

function pickDisplayIcon(id: string) {
  if (id.includes('streak') || id.includes('continuous')) return ICON_MAP.streak
  if (id.includes('badge') || id.includes('medal')) return ICON_MAP.badge
  return ICON_MAP.tasks
}

function pickTone(id: string): keyof typeof TONE_STYLE {
  return id.includes('streak') || id.includes('continuous') ? 'orange' : 'green'
}

function formatValue(item: LearningAchievement): string {
  if (item.target != null) {
    return item.achieved ? '已达成' : `${item.progress ?? 0}/${item.target}`
  }
  return item.achieved ? '已达成' : '进行中'
}

export default function AchievementCard() {
  const profileRevision = useProfileStore((s) => s.revision)
  const [achievements, setAchievements] = useState<LearningAchievement[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    void fetchProfileAchievements()
      .then((items) => mapApiAchievements(items))
      .then((data) => {
        if (!cancelled) setAchievements(data.slice(0, 3))
      })
      .catch(() => {
        if (!cancelled) setAchievements([])
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [profileRevision])

  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-4 flex items-center gap-2">
        <Trophy className="h-[18px] w-[18px] text-[#2563EB]" />
        <h2 className="text-lg font-bold text-[#0F2A5F]">学习成就</h2>
      </div>
      {loading ? (
        <div className="grid grid-cols-3 gap-3">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="h-[92px] animate-pulse rounded-[14px] border border-[#E6EEFA] bg-[#F8FAFC]"
            />
          ))}
        </div>
      ) : achievements.length === 0 ? (
        <p className="py-6 text-center text-sm text-[#64748B]">坚持学习后将解锁成就</p>
      ) : (
        <div className="grid grid-cols-3 gap-3">
          {achievements.map((item) => {
            const Icon = pickDisplayIcon(item.id)
            const tone = pickTone(item.id)
            return (
              <article
                key={item.id}
                className="flex h-[92px] flex-col items-center justify-center rounded-[14px] border border-[#E6EEFA] bg-white text-center transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_8px_20px_rgba(15,23,42,0.05)]"
              >
                <div
                  className={`mb-2 flex h-8 w-8 items-center justify-center rounded-full ${TONE_STYLE[tone]}`}
                >
                  <Icon className="h-4 w-4" />
                </div>
                <p className="line-clamp-1 px-1 text-[11px] text-[#64748B]">{item.title}</p>
                <p className="text-sm font-bold text-[#0F2A5F]">{formatValue(item)}</p>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}
