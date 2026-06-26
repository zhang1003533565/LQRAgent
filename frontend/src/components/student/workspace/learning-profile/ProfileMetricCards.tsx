import { CalendarCheck, Target, TrendingUp, Trophy } from 'lucide-react'
import ProgressRing from './ProgressRing'
import type { AbilityDimension, LearningProfileOverview } from '@/utils/types/learningProfile'

type Props = {
  overview: LearningProfileOverview
  dimensions?: AbilityDimension[]
  masteryTrendDelta?: number | null
}

const CARD =
  'flex min-h-[118px] items-start justify-between gap-3 rounded-[16px] border border-[#E6EEFA] bg-white px-4 py-4 shadow-[0_4px_16px_rgba(15,23,42,0.04)]'

export default function ProfileMetricCards({
  overview,
  dimensions = [],
  masteryTrendDelta,
}: Props) {
  const strongest = dimensions.find((d) => d.name === overview.strongestDimension)
  const strengthHint =
    strongest && strongest.averageScore != null && strongest.score > strongest.averageScore
      ? `高于平均水平 ${strongest.score - strongest.averageScore} 分`
      : strongest
        ? '当前相对优势方向'
        : '数据积累中'

  return (
    <div className="grid min-w-0 grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <div className={CARD}>
        <div className="min-w-0 flex-1">
          <p className="text-[13px] text-[#64748B]">综合掌握度</p>
          <p className="mt-0.5 text-[32px] font-extrabold leading-none tracking-tight text-[#0F2A5F]">
            {overview.overallMasteryRate}%
          </p>
          {masteryTrendDelta != null && masteryTrendDelta !== 0 ? (
            <p
              className={`mt-1.5 flex items-center gap-1 text-[11px] font-semibold ${
                masteryTrendDelta > 0 ? 'text-[#22C55E]' : 'text-[#EF4444]'
              }`}
            >
              <TrendingUp className={`h-3 w-3 ${masteryTrendDelta < 0 ? 'rotate-180' : ''}`} />
              较上期 {masteryTrendDelta > 0 ? '↑' : '↓'} {Math.abs(masteryTrendDelta)}%
            </p>
          ) : (
            <p className="mt-1.5 text-[11px] leading-snug text-[#94A3B8]">基于当前学习数据</p>
          )}
        </div>
        <ProgressRing percent={overview.overallMasteryRate} size={64} stroke={6} showCenterLabel={false} />
      </div>

      <div className={CARD}>
        <div className="min-w-0 flex-1">
          <p className="text-[13px] text-[#64748B]">连续学习</p>
          <p className="mt-0.5 text-[32px] font-extrabold leading-none text-[#0F2A5F]">
            {overview.continuousLearningDays}
            <span className="ml-0.5 text-base font-bold text-[#64748B]">天</span>
          </p>
          {overview.longestContinuousLearningDays ? (
            <p className="mt-1.5 text-[11px] leading-snug text-[#94A3B8]">
              最长连续 {overview.longestContinuousLearningDays} 天
            </p>
          ) : (
            <p className="mt-1.5 text-[11px] leading-snug text-[#94A3B8]">坚持打卡提升连续天数</p>
          )}
        </div>
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-[#DCFCE7]">
          <CalendarCheck className="h-5 w-5 text-[#22C55E]" />
        </div>
      </div>

      <div className={CARD}>
        <div className="min-w-0 flex-1 pr-1">
          <p className="text-[13px] text-[#64748B]">优势方向</p>
          <p className="mt-0.5 truncate text-lg font-extrabold text-[#0F2A5F]">
            {overview.strongestDimension || '—'}
          </p>
          <p className="mt-1.5 truncate text-[11px] leading-snug text-[#94A3B8]">{strengthHint}</p>
        </div>
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-[#FFEDD5]">
          <Trophy className="h-5 w-5 text-[#F59E0B]" />
        </div>
      </div>

      <div className={CARD}>
        <div className="min-w-0 flex-1 pr-1">
          <p className="text-[13px] text-[#64748B]">当前建议</p>
          <p className="mt-0.5 line-clamp-3 text-sm font-bold leading-snug text-[#0F2A5F]">
            {overview.currentSuggestion || '完成学习后将生成建议'}
          </p>
        </div>
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-[#EDE9FE]">
          <Target className="h-5 w-5 text-[#8B5CF6]" />
        </div>
      </div>
    </div>
  )
}
