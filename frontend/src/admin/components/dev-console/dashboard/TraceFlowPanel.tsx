import { useMemo } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MarkerType,
  Position,
  type Edge,
  type Node,
  type NodeProps,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { motion } from 'framer-motion'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/admin/components/dev-console/ui'
import { MOCK_TRACE_META, MOCK_TRACE_NODES } from '@/admin/components/dev-console/mock/data'
import type { TraceNodeData } from '@/admin/types/dev-console'

const STATUS_COLOR: Record<TraceNodeData['status'], string> = {
  success: '#10B981',
  running: '#3B82F6',
  pending: '#64748B',
  failed: '#EF4444',
}

type TraceFlowNode = Node<TraceNodeData, 'traceNode'>

function TraceNode({ data }: NodeProps<TraceFlowNode>) {
  const color = STATUS_COLOR[data.status]
  return (
    <motion.div
      animate={data.status === 'running' ? { boxShadow: [`0 0 0 0 ${color}00`, `0 0 12px 2px ${color}66`] } : {}}
      transition={{ repeat: data.status === 'running' ? Infinity : 0, duration: 1.5 }}
      className="min-w-[140px] rounded-lg border px-3 py-2 text-left"
      style={{ borderColor: color, background: '#121A2B' }}
    >
      <p className="text-xs font-medium text-console-text">{data.label}</p>
      {data.durationMs > 0 && (
        <p className="mt-1 font-mono text-[10px] text-console-muted">
          {(data.durationMs / 1000).toFixed(2)}s · {data.tokens} tok
        </p>
      )}
    </motion.div>
  )
}

const nodeTypes = { traceNode: TraceNode }

export default function TraceFlowPanel() {
  const { nodes, edges } = useMemo(() => {
    const nodes: TraceFlowNode[] = MOCK_TRACE_NODES.map((d, i) => ({
      id: String(i),
      type: 'traceNode',
      position: { x: i * 180, y: 40 },
      data: d,
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
    }))
    const edges: Edge[] = MOCK_TRACE_NODES.slice(0, -1).map((_, i) => ({
      id: `e${i}`,
      source: String(i),
      target: String(i + 1),
      animated: true,
      markerEnd: { type: MarkerType.ArrowClosed, color: '#475569' },
      style: { stroke: '#475569' },
    }))
    return { nodes, edges }
  }, [])

  return (
    <Card className="h-full min-h-[320px]">
      <CardHeader>
        <CardTitle>Trace 调用链</CardTitle>
        <p className="text-xs text-console-muted">React Flow · MOCK 样例 trace</p>
      </CardHeader>
      <CardContent>
        <div className="h-[220px] rounded-md border border-console-border bg-console-bg/50">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            fitView
            proOptions={{ hideAttribution: true }}
            nodesDraggable={false}
            panOnDrag={false}
            zoomOnScroll={false}
          >
            <Background color="#1E293B" gap={16} />
            <Controls showInteractive={false} />
          </ReactFlow>
        </div>
        <div className="mt-3 grid grid-cols-2 gap-2 text-xs md:grid-cols-4">
          <div>
            <span className="text-console-muted">Trace ID</span>
            <p className="font-mono">{MOCK_TRACE_META.traceId}</p>
          </div>
          <div className="col-span-2">
            <span className="text-console-muted">请求</span>
            <p className="truncate">{MOCK_TRACE_META.request}</p>
          </div>
          <div>
            <span className="text-console-muted">状态</span>
            <ConsoleBadge variant="success" className="mt-0.5">
              {MOCK_TRACE_META.status}
            </ConsoleBadge>
          </div>
          <div>
            <span className="text-console-muted">总耗时</span>
            <p>{(MOCK_TRACE_META.totalMs / 1000).toFixed(2)}s</p>
          </div>
          <div>
            <span className="text-console-muted">总 Token</span>
            <p>{MOCK_TRACE_META.totalTokens}</p>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
