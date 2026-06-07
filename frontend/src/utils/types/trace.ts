/**
 * 链路追踪类型定义
 */

export interface TraceSpan {
  traceId: string
  spanId: string
  parentSpanId?: string
  agentId: string
  stepId?: string
  status: 'RUNNING' | 'SUCCESS' | 'ERROR' | 'TIMEOUT'
  startTime: Date
  endTime?: Date
  durationMs?: number
  inputSummary?: string
  outputSummary?: string
  errorMessage?: string
}

export interface TraceTimeline {
  traceId: string
  spans: TraceSpan[]
  totalDurationMs: number
  status: 'RUNNING' | 'SUCCESS' | 'ERROR'
}

export interface TraceSpanProps {
  span: TraceSpan
  index: number
  total: number
  onClick?: (span: TraceSpan) => void
}
