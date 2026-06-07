import React from 'react'
import type { TraceSpan } from '@/utils/types/trace'

interface TraceSpanItemProps {
  span: TraceSpan
  index: number
  total: number
  onClick?: (span: TraceSpan) => void
}

const STATUS_COLORS: Record<string, string> = {
  RUNNING: 'bg-blue-500',
  SUCCESS: 'bg-green-500',
  ERROR: 'bg-red-500',
  TIMEOUT: 'bg-yellow-500',
}

const STATUS_TEXT: Record<string, string> = {
  RUNNING: '运行中',
  SUCCESS: '成功',
  ERROR: '失败',
  TIMEOUT: '超时',
}

/**
 * 单个链路追踪跨度组件
 */
export const TraceSpanItem: React.FC<TraceSpanItemProps> = ({
  span,
  index,
  total,
  onClick,
}) => {
  const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`
    return `${(ms / 1000).toFixed(2)}s`
  }

  const formatDate = (date: Date) => {
    const d = new Date(date)
    return d.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  }

  return (
    <div
      className={`
        flex items-start gap-3 p-3 rounded-lg cursor-pointer
        hover:bg-gray-50 transition-colors
        ${index < total - 1 ? 'border-b border-gray-100' : ''}
      `}
      onClick={() => onClick?.(span)}
    >
      {/* 时间线圆点 */}
      <div className="relative flex flex-col items-center">
        <div
          className={`w-3 h-3 rounded-full ${STATUS_COLORS[span.status] || 'bg-gray-400'}`}
        />
        {index < total - 1 && (
          <div className="w-0.5 h-8 bg-gray-200 mt-1" />
        )}
      </div>

      {/* 内容 */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-gray-800">
            {span.agentId}
          </span>
          <span className={`px-1.5 py-0.5 text-xs rounded ${
            span.status === 'SUCCESS'
              ? 'bg-green-100 text-green-700'
              : span.status === 'ERROR'
              ? 'bg-red-100 text-red-700'
              : span.status === 'RUNNING'
              ? 'bg-blue-100 text-blue-700'
              : 'bg-yellow-100 text-yellow-700'
          }`}>
            {STATUS_TEXT[span.status] || span.status}
          </span>
        </div>

        {span.stepId && (
          <div className="text-xs text-gray-500 mt-1">
            步骤: {span.stepId}
          </div>
        )}

        <div className="flex items-center gap-3 mt-1 text-xs text-gray-400">
          <span>{formatDate(span.startTime)}</span>
          {span.durationMs != null && (
            <span className="flex items-center gap-1">
              <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              {formatDuration(span.durationMs)}
            </span>
          )}
        </div>

        {span.errorMessage && (
          <div className="text-xs text-red-500 mt-1 truncate">
            {span.errorMessage}
          </div>
        )}
      </div>
    </div>
  )
}

export default TraceSpanItem
