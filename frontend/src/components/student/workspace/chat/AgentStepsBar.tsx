/**
 * 智能体步骤进度条 — 紧凑水平布局，集成在消息区域内
 */
import { useAgentTraceStore } from '@/utils/store/agentTraceStore'
import { AGENT_LABELS } from '@/utils/constants/agent-labels'

export default function AgentStepsBar() {
  const steps = useAgentTraceStore((s) => s.steps)

  if (steps.length === 0) return null

  // 只显示最新的几个步骤（最多 5 个）
  const visible = steps.slice(-5)

  return (
    <div className="flex items-center gap-2 rounded-xl border border-gray-100 bg-white/70 px-4 py-2 backdrop-blur-sm">
      <span className="text-[11px] font-medium text-gray-400">智能体步骤</span>
      <div className="flex items-center gap-0.5">
        {visible.map((step, i) => {
          const isLast = i === visible.length - 1
          const label = AGENT_LABELS[step.agent] || step.agent
          return (
            <div key={step.id} className="flex items-center">
              {i > 0 && (
                <div className={`mx-0.5 h-px w-3 ${isLast ? 'bg-gray-200' : 'bg-emerald-300'}`} />
              )}
              <div className="group relative flex flex-col items-center">
                <div className={`flex h-5 w-5 items-center justify-center rounded-full text-[9px] font-bold text-white transition-all ${
                  step.status === 'done' ? 'bg-emerald-500' :
                  step.status === 'running' ? 'bg-blue-500 animate-pulse shadow-md shadow-blue-200' :
                  step.status === 'failed' ? 'bg-red-400' :
                  'bg-gray-200'
                }`}>
                  {step.status === 'done' ? '✓' : step.status === 'running' ? '●' : step.status === 'failed' ? '✕' : ''}
                </div>
                <span className="mt-0.5 max-w-[56px] truncate text-[9px] text-gray-400">
                  {label}
                </span>
                {step.detail && (
                  <div className="pointer-events-none absolute -top-7 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-md bg-gray-800 px-2 py-1 text-[10px] text-white opacity-0 shadow-lg transition-opacity group-hover:opacity-100 z-10">
                    {step.detail}
                  </div>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
