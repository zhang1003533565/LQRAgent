import { useMemo } from 'react'
import ReactECharts from 'echarts-for-react'
import { TrendingUp } from 'lucide-react'
import { createLineChartOption } from '@/utils/echarts'
import { EmptyState } from './shared'
import type { LearningTrendPoint, ProfileRange, TrendMetric } from '@/utils/types/learningProfile'

const METRICS: Array<{ value: TrendMetric; label: string }> = [
  { value: 'mastery', label: '掌握度' },
  { value: 'accuracy', label: '正确率' },
  { value: 'duration', label: '学习时长' },
  { value: 'questions', label: '完成题数' },
]

const RANGES: Array<{ value: ProfileRange; label: string }> = [
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
  { value: '90d', label: '近 90 天' },
  { value: 'all', label: '全部' },
]

function pickValue(point: LearningTrendPoint, metric: TrendMetric): number | undefined {
  switch (metric) {
    case 'accuracy':
      return point.accuracyRate
    case 'duration':
      return point.learningDurationMinutes
    case 'nodes':
      return point.completedNodeCount
    case 'questions':
      return point.completedQuestionCount
    default:
      return point.overallMasteryRate
  }
}

type Props = {
  trends: LearningTrendPoint[]
  metric: TrendMetric
  range: ProfileRange
  trendDelta?: number | null
  onMetricChange: (metric: TrendMetric) => void
}

export default function GrowthTrendCard({
  trends,
  metric,
  range,
  trendDelta,
  onMetricChange,
}: Props) {
  const metricLabel = METRICS.find((m) => m.value === metric)?.label ?? '掌握度'
  const rangeLabel = RANGES.find((r) => r.value === range)?.label ?? '近 30 天'

  const option = useMemo(() => {
    const dates = trends.map((t) => {
      const d = t.date.slice(5).replace('-', '/')
      return d
    })
    const values = trends.map((t) => pickValue(t, metric) ?? 0)
    return createLineChartOption({
      grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
      xAxis: { data: dates, boundaryGap: false },
      yAxis: { min: 0, max: metric === 'mastery' || metric === 'accuracy' ? 100 : undefined },
      series: [
        {
          name: metricLabel,
          data: values,
          smooth: true,
          lineStyle: { color: '#2563EB', width: 2.5 },
          itemStyle: { color: '#2563EB' },
          symbol: 'circle',
          symbolSize: 6,
          areaStyle: { color: 'rgba(37, 99, 235, 0.06)' },
        },
      ],
    })
  }, [trends, metric, metricLabel])

  return (
    <section className="flex h-full min-h-[260px] flex-col rounded-[16px] border border-[#E6EEFA] bg-white p-4 shadow-[0_4px_16px_rgba(15,23,42,0.04)]">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-[#0F2A5F]">成长轨迹</h2>
          <p className="text-xs text-[#64748B]">
            {rangeLabel}
            {metricLabel}变化
          </p>
        </div>
        <div className="flex flex-wrap items-center justify-end gap-2">
          {trendDelta != null && trendDelta !== 0 ? (
            <span
              className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold ${
                trendDelta > 0 ? 'bg-[#DCFCE7] text-[#16A34A]' : 'bg-[#FEE2E2] text-[#EF4444]'
              }`}
            >
              <TrendingUp className={`h-3.5 w-3.5 ${trendDelta < 0 ? 'rotate-180' : ''}`} />
              较上期 {trendDelta > 0 ? '↑' : '↓'} {Math.abs(trendDelta)}%
            </span>
          ) : null}
          <div className="flex flex-wrap gap-1">
            {METRICS.map((m) => (
              <button
                key={m.value}
                type="button"
                onClick={() => onMetricChange(m.value)}
                className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${
                  metric === m.value
                    ? 'bg-[#EAF3FF] text-[#2563EB]'
                    : 'text-[#64748B] hover:bg-[#F8FBFF]'
                }`}
              >
                {m.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {trends.length === 0 ? (
        <EmptyState title="暂无趋势数据" description="持续学习后可查看成长变化" />
      ) : (
        <ReactECharts option={option} style={{ height: 200, flex: 1 }} opts={{ renderer: 'svg' }} />
      )}
    </section>
  )
}
