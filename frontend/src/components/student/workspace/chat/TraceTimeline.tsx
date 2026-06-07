import React, { useState } from 'react'
import type { TraceTimeline as TraceTimelineType, TraceSpan } from '@/utils/types/trace'
import { TraceSpanItem } from './TraceSpanItem'
import { TraceDetail } from './TraceDetail'

interface TraceTimelineProps {
  timeline: TraceTimelineType | null
  loading?: boolean
  className?: string
}

/**
 * 链路追踪时间线组件
 * 显示完整的追踪时间线和详情
 */
export const TraceTimeline: React.FC<TraceTimelineProps> = ({
  timeline,
  loading = false,
  className = '',
}) => {
  const [selectedSpan, setSelectedSpan] = useState<TraceSpan | null>(null)

  if (loading) {
    return (
      <div className="flex items-center justify-center py-8">
        <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-500" />
      </div>
    )
  }

  if (!timeline || timeline.spans.length === 0) {
    return (
      <div className="text-center py-8 text-gray-400 text-sm">
        <svg
          className="w-12 h-12 mx-auto mb-3 text-gray-300"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.5}
            d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
          />
        </svg>
        <p>暂无追踪数据</p>
      </div>
    )
  }

  const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`
    return `${(ms / 1000).toFixed(2)}s`
  }

  return (
    <div className={`flex flex-col h-full ${className}`}>
      {/* 标题栏 */}
      <div className="px-4 py-3 border-b border-gray-200">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-gray-700">链路追踪</h2>
          <div className="flex items-center gap-2 text-xs text-gray-500">
            <span>Trace ID: {timeline.traceId.slice(0, 8)}...</span>
            <span className="text-gray-300">|</span>
            <span>总耗时: {formatDuration(timeline.totalDurationMs)}</span>
          </div>
        </div>
      </div>

      {/* 主体内容 */}
      <div className="flex-1 overflow-hidden flex">
        {/* 时间线列表 */}
        <div className={`${selectedSpan ? 'w-1/2' : 'w-full'} overflow-y-auto border-r border-gray-100 transition-all`}>
          {timeline.spans.map((span, index) => (
            <TraceSpanItem
              key={span.spanId}
              span={span}
              index={index}
              total={timeline.spans.length}
              onClick={setSelectedSpan}
            />
          ))}
        </div>

        {/* 详情面板 */}
        {selectedSpan && (
          <div className="w-1/2 overflow-y-auto p-4 bg-gray-50">
            <TraceDetail
              span={selectedSpan}
              onClose={() => setSelectedSpan(null)}
            />
          </div>
        )}
      </div>

      {/* 底部状态 */}
      <div className="px-4 py-2 border-t border-gray-200 text-xs text-gray-400">
        共 {timeline.spans.length} 个跨度
        {timeline.status === 'RUNNING' && (
          <span className="ml-2 text-blue-500">● 进行中</span>
        )}
      </div>
    </div>
  )
}

export default TraceTimeline
