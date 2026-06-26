import { Map } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { DEMO_LEARNING_PATH } from '@/mock/chatLearning'

type Props = {
  title?: string
  stages?: string[]
  summary?: string
}

export default function AiLearningPlanCard({
  title = DEMO_LEARNING_PATH.title,
  stages = DEMO_LEARNING_PATH.stages,
  summary = DEMO_LEARNING_PATH.summary,
}: Props) {
  const navigate = useNavigate()

  return (
    <div className="mt-3 rounded-[14px] border border-[#D8E8FF] bg-[#F8FBFF] p-4">
      <div className="mb-3 flex items-center gap-2">
        <span className="flex h-8 w-8 items-center justify-center rounded-[10px] bg-[#EAF3FF]">
          <Map className="h-4 w-4 text-[#2563EB]" />
        </span>
        <h4 className="text-[15px] font-bold text-[#1D4ED8]">{title}</h4>
      </div>
      <ul className="space-y-2">
        {stages.map((stage, index) => (
          <li key={stage}>
            <button
              type="button"
              onClick={() => navigate('/workspace/learning-path')}
              className="flex h-[38px] w-full items-center gap-3 rounded-[10px] bg-white px-3 text-left transition-colors hover:bg-[#F1F7FF]"
            >
              <span className="flex h-[22px] w-[22px] shrink-0 items-center justify-center rounded-full bg-[#EAF3FF] text-xs font-semibold text-[#2563EB]">
                {index + 1}
              </span>
              <span className="truncate text-sm text-[#334155]">{stage}</span>
            </button>
          </li>
        ))}
      </ul>
      <p className="mt-3 text-[13px] leading-relaxed text-[#64748B]">{summary}</p>
    </div>
  )
}
