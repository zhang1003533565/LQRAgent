import { motion } from 'framer-motion'
import ReactECharts from 'echarts-for-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/admin/dev-console/ui'
import { MOCK_SPARKLINES, MOCK_STAT_CARDS, formatTokens } from '@/components/admin/dev-console/mock/data'
import AnimatedNumber from './AnimatedNumber'

function sparkOption(values: number[], color: string) {
  return {
    grid: { left: 0, right: 0, top: 4, bottom: 0 },
    xAxis: { type: 'category', show: false, data: values.map((_, i) => i) },
    yAxis: { type: 'value', show: false },
    series: [
      {
        type: 'line',
        data: values,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2, color },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: `${color}55` },
              { offset: 1, color: `${color}00` },
            ],
          },
        },
      },
    ],
  }
}

export default function StatCards() {
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-5">
      {MOCK_STAT_CARDS.map((stat, i) => (
        <motion.div
          key={stat.key}
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: i * 0.05 }}
          whileHover={{ y: -2 }}
        >
          <Card className="overflow-hidden transition-shadow hover:border-console-blue/30">
            <CardHeader className="pb-0">
              <CardTitle className="text-xs text-console-muted">{stat.title}</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex items-end justify-between gap-2">
                <p className="text-2xl font-semibold tabular-nums" style={{ color: stat.color }}>
                  <AnimatedNumber
                    value={stat.value}
                    format={(n) => {
                      if (stat.key === 'tokens') return formatTokens(n)
                      if (stat.key === 'latency') return `${(n / 1000).toFixed(2)}s`
                      return Math.round(n).toLocaleString()
                    }}
                  />
                </p>
                <span
                  className={`text-xs font-medium ${
                    stat.deltaUp ? 'text-console-green' : 'text-console-orange'
                  }`}
                >
                  {stat.delta}
                </span>
              </div>
              <div className="mt-2 h-12">
                <ReactECharts
                  option={sparkOption(MOCK_SPARKLINES[stat.sparkKey], stat.color)}
                  style={{ height: 48, width: '100%' }}
                  opts={{ renderer: 'svg' }}
                />
              </div>
            </CardContent>
          </Card>
        </motion.div>
      ))}
    </div>
  )
}
