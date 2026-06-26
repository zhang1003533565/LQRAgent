import { RECENT_LEARNING } from '@/mock/chatLearning'
import { useNavigate } from 'react-router-dom'

export default function RecentLearningCard() {
  const navigate = useNavigate()

  return (
    <section className="flex min-h-0 flex-1 flex-col rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h2 className="mb-4 shrink-0 text-lg font-bold text-[#0F172A]">最近学习</h2>
      <ul className="min-h-0 flex-1 space-y-1">
        {RECENT_LEARNING.map((item) => {
          const Icon = item.icon
          return (
            <li key={item.id}>
              <button
                type="button"
                onClick={() => navigate('/workspace/resources')}
                className="flex h-[42px] w-full items-center gap-3 rounded-[10px] px-1 text-left transition-colors hover:bg-[#F8FBFF]"
              >
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[8px] bg-[#EAF3FF]">
                  <Icon className="h-3.5 w-3.5 text-[#2563EB]" />
                </span>
                <span className="min-w-0 flex-1 truncate text-sm text-[#334155]">{item.title}</span>
                <span className="shrink-0 text-xs text-[#94A3B8]">{item.time}</span>
              </button>
            </li>
          )
        })}
      </ul>
      <button
        type="button"
        onClick={() => navigate('/workspace/resources')}
        className="mt-3 h-9 shrink-0 rounded-[10px] text-center text-sm font-medium text-[#2563EB] transition-colors hover:bg-[#F8FBFF]"
      >
        查看全部历史记录 →
      </button>
    </section>
  )
}
