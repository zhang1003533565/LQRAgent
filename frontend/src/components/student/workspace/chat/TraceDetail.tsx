import React from 'react'
import type { TraceSpan } from '@/utils/types/trace'

interface TraceDetailProps {
  span: TraceSpan
  onClose: () => void
}

/**
 * 链路追踪详情组件
 * 显示单个 Span 的详细信息
 */
export const TraceDetail: React.FC<TraceDetailProps> = ({
  span,
  onClose,
}) => {
  const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`
    return `${(ms / 1000).toFixed(2)}s`
  }

  const formatTime = (date: Date) => {
    const d = new Date(date)
    return d.toLocaleString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      fractionalSecondDigits: 3,
    } as Intl.DateTimeFormatOptions)
  }

  return (
    <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
      {/* 头部 */}
      <div className="flex items-center justify-between mb-4">
        <h4 className="text-sm font-semibold text-gray-800">
          追踪详情
        </h4>
        <button
          onClick={onClose}
          className="p-1 text-gray-400 hover:text-gray-600 rounded"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      {/* 基本信息 */}
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="text-xs text-gray-500">Agent</label>
            <div className="text-sm font-medium text-gray-800">{span.agentId}</div>
          </div>
          <div>
            <label className="text-xs text-gray-500">状态</label>
            <div className={`text-sm font-medium ${
              span.status === 'SUCCESS' ? 'text-green-600' :
              span.status === 'ERROR' ? 'text-red-600' :
              'text-gray-800'
            }`}>
              {span.status}
            </div>
          </div>
        </div>

        {span.stepId && (
          <div>
            <label className="text-xs text-gray-500">步骤 ID</label>
            <div className="text-sm text-gray-800 font-mono">{span.stepId}</div>
          </div>
        )}

        <div>
          <label className="text-xs text-gray-500">Span ID</label>
          <div className="text-sm text-gray-800 font-mono">{span.spanId}</div>
        </div>

        {span.parentSpanId && (
          <div>
            <label className="text-xs text-gray-500">父 Span ID</label>
            <div className="text-sm text-gray-800 font-mono">{span.parentSpanId}</div>
          </div>
        )}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="text-xs text-gray-500">开始时间</label>
            <div className="text-sm text-gray-800">{formatTime(span.startTime)}</div>
          </div>
          <div>
            <label className="text-xs text-gray-500">耗时</label>
            <div className="text-sm text-gray-800">
              {span.durationMs != null ? formatDuration(span.durationMs) : '-'}
            </div>
          </div>
        </div>

        {/* 输入摘要 */}
        {span.inputSummary && (
          <div>
            <label className="text-xs text-gray-500">输入摘要</label>
            <div className="mt-1 p-2 bg-white rounded text-xs text-gray-700 font-mono max-h-32 overflow-y-auto">
              {span.inputSummary}
            </div>
          </div>
        )}

        {/* 输出摘要 */}
        {span.outputSummary && (
          <div>
            <label className="text-xs text-gray-500">输出摘要</label>
            <div className="mt-1 p-2 bg-white rounded text-xs text-gray-700 font-mono max-h-32 overflow-y-auto">
              {span.outputSummary}
            </div>
          </div>
        )}

        {/* 错误信息 */}
        {span.errorMessage && (
          <div>
            <label className="text-xs text-gray-500">错误信息</label>
            <div className="mt-1 p-2 bg-red-50 rounded text-xs text-red-700 font-mono">
              {span.errorMessage}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

export default TraceDetail
