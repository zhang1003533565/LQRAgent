import type { KnowledgeTopic } from '@/mock/learningResources'

type Props = {
  topics: KnowledgeTopic[]
  activeId: string | null
  onSelect: (id: string | null) => void
}

export default function KnowledgeResourceTabs({ topics, activeId, onSelect }: Props) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h2 className="mb-4 text-lg font-bold text-[#0F2A5F]">按知识点浏览</h2>
      <div className="flex gap-3 overflow-x-auto pb-1">
        <button
          type="button"
          onClick={() => onSelect(null)}
          className={`flex h-[54px] min-w-[120px] shrink-0 items-center gap-2.5 rounded-xl border px-3 transition-all ${
            activeId === null
              ? 'border-[#BFD7FF] bg-[#F8FBFF]'
              : 'border-[#E6EEFA] bg-white hover:border-[#BFD7FF] hover:bg-[#F8FBFF]'
          }`}
        >
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#EAF3FF] text-xs font-bold text-[#2563EB]">
            全
          </div>
          <div className="text-left">
            <p className="text-sm font-bold text-[#0F2A5F]">全部</p>
            <p className="text-xs text-[#64748B]">浏览全部</p>
          </div>
        </button>
        {topics.map((topic) => {
          const Icon = topic.icon
          const active = activeId === topic.id
          return (
            <button
              key={topic.id}
              type="button"
              onClick={() => onSelect(topic.id)}
              className={`flex h-[54px] min-w-[150px] shrink-0 items-center gap-2.5 rounded-xl border px-3 transition-all ${
                active
                  ? 'border-[#BFD7FF] bg-[#F8FBFF]'
                  : 'border-[#E6EEFA] bg-white hover:border-[#BFD7FF] hover:bg-[#F8FBFF]'
              }`}
            >
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#EAF3FF]">
                <Icon className="h-4 w-4 text-[#2563EB]" />
              </div>
              <div className="text-left">
                <p className="text-sm font-bold text-[#0F2A5F]">{topic.title}</p>
                <p className="text-xs text-[#64748B]">{topic.count} 资源</p>
              </div>
            </button>
          )
        })}
      </div>
    </section>
  )
}
