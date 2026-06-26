import { useMemo } from 'react'
import ReactECharts from 'echarts-for-react'
import { Sparkles } from 'lucide-react'
import { EmptyState } from './shared'
import type { AbilityDimension } from '@/utils/types/learningProfile'

type Props = {
  dimensions: AbilityDimension[]
  hint?: string
  onGoPractice?: () => void
}

export default function AbilityRadarCard({ dimensions, hint, onGoPractice }: Props) {
  const hasAverage = dimensions.some((d) => d.averageScore != null)

  const option = useMemo(
    () => ({
      radar: {
        center: ['50%', '54%'],
        radius: '58%',
        indicator: dimensions.map((d) => ({
          name: d.name,
          max: d.maxScore ?? 100,
        })),
        axisName: { color: '#64748B', fontSize: 11, fontWeight: 600 },
        splitLine: { lineStyle: { color: '#EEF3FA' } },
        axisLine: { lineStyle: { color: '#EEF3FA' } },
        splitArea: { areaStyle: { color: ['transparent', 'transparent'] } },
      },
      series: [
        {
          type: 'radar',
          data: [
            {
              value: dimensions.map((d) => d.score),
              name: '当前得分',
              areaStyle: { color: 'rgba(37, 99, 235, 0.14)' },
              lineStyle: { color: '#2563EB', width: 2 },
              itemStyle: { color: '#2563EB' },
              symbolSize: 4,
            },
            ...(hasAverage
              ? [
                  {
                    value: dimensions.map((d) => d.averageScore ?? 0),
                    name: '平均水平',
                    lineStyle: { color: '#94A3B8', type: 'dashed', width: 1.5 },
                    itemStyle: { color: '#94A3B8' },
                    symbolSize: 2,
                  },
                ]
              : []),
          ],
        },
      ],
    }),
    [dimensions, hasAverage],
  )

  return (
    <section className="flex min-h-0 min-w-0 flex-col overflow-hidden rounded-[16px] border border-[#E6EEFA] bg-white shadow-[0_4px_16px_rgba(15,23,42,0.04)]">
      <div className="flex shrink-0 items-start justify-between gap-3 px-5 pt-5">
        <div className="min-w-0">
          <h2 className="text-base font-bold text-[#0F2A5F]">能力画像总览</h2>
          <p className="mt-0.5 text-xs text-[#64748B]">六维能力模型可视化</p>
        </div>
        {hasAverage ? (
          <div className="flex shrink-0 flex-wrap justify-end gap-3 text-[11px] text-[#64748B]">
            <span className="flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-[#2563EB]" />
              当前得分
            </span>
            <span className="flex items-center gap-1.5">
              <span className="inline-block h-0.5 w-3.5 border-t-2 border-dashed border-[#94A3B8]" />
              平均水平
            </span>
          </div>
        ) : null}
      </div>

      {dimensions.length === 0 ? (
        <div className="flex flex-1 items-center justify-center px-5 pb-5">
          <EmptyState
            title="完成更多学习和练习后，将生成能力画像"
            action={
              onGoPractice ? (
                <button
                  type="button"
                  onClick={onGoPractice}
                  className="rounded-xl bg-[#2563EB] px-4 py-2 text-sm font-semibold text-white"
                >
                  去答题
                </button>
              ) : undefined
            }
          />
        </div>
      ) : (
        <>
          <div className="min-h-[240px] w-full min-w-0 flex-1 overflow-hidden px-1">
            <ReactECharts
              option={option}
              style={{ width: '100%', height: 240 }}
              opts={{ renderer: 'svg' }}
            />
          </div>
          <div className="mx-4 mb-4 flex shrink-0 items-start gap-2 rounded-xl bg-[#EAF3FF] px-3.5 py-2.5">
            <Sparkles className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#2563EB]" />
            <p className="text-[13px] leading-relaxed text-[#334155]">
              {hint || '能力分析数据积累中，持续学习后将生成更精准洞察'}
            </p>
          </div>
        </>
      )}
    </section>
  )
}
