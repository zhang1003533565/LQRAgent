import { useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import type { LearningChapter } from '@/mock/learningPath'
import { chapterProgress } from '@/utils/learningPath/chapterUtils'
import LearningNodeItem from './LearningNodeItem'

type Props = {
  chapters: LearningChapter[]
  selectedNodeId: string | null
  onSelectNode: (nodeId: string) => void
  defaultExpandedChapterId?: string | null
}

export default function ChapterAccordion({
  chapters,
  selectedNodeId,
  onSelectNode,
  defaultExpandedChapterId,
}: Props) {
  const initialExpanded =
    defaultExpandedChapterId ??
    chapters.find((ch) => ch.nodes.some((n) => n.status === 'current'))?.id ??
    chapters[0]?.id ??
    null

  const [expandedId, setExpandedId] = useState<string | null>(initialExpanded)

  const toggle = (chapterId: string) => {
    setExpandedId((prev) => (prev === chapterId ? null : chapterId))
  }

  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="space-y-2.5">
        {chapters.map((chapter) => {
          const expanded = expandedId === chapter.id
          const { done, total } = chapterProgress(chapter)
          const isActiveChapter = chapter.nodes.some((n) => n.status === 'current')

          return (
            <div key={chapter.id} className="overflow-hidden">
              <button
                type="button"
                onClick={() => toggle(chapter.id)}
                className="flex h-[52px] w-full items-center justify-between rounded-[14px] border border-[#D8E8FF] bg-[#F8FBFF] px-4 transition-colors hover:bg-[#F1F7FF]"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <span
                    className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-sm font-bold ${
                      isActiveChapter
                        ? 'bg-[#2563EB] text-white'
                        : 'bg-[#EAF3FF] text-[#2563EB]'
                    }`}
                  >
                    {chapter.index}
                  </span>
                  <div className="min-w-0 text-left">
                    <p className="truncate text-[17px] font-bold text-[#0F2A5F]">{chapter.title}</p>
                    {chapter.description ? (
                      <p className="truncate text-[13px] text-[#64748B]">{chapter.description}</p>
                    ) : null}
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-3">
                  <span className="text-sm font-bold text-[#0F2A5F]">
                    {done}/{total}
                  </span>
                  {expanded ? (
                    <ChevronUp className="h-4 w-4 text-[#64748B]" />
                  ) : (
                    <ChevronDown className="h-4 w-4 text-[#64748B]" />
                  )}
                </div>
              </button>

              <div
                className={`grid transition-all duration-200 ease-out ${
                  expanded ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
                }`}
              >
                <div className="overflow-hidden">
                  <div className="relative mt-2 space-y-2 pl-6">
                    <div className="absolute bottom-4 left-[11px] top-4 w-px bg-[#D8E8FF]" />
                    {chapter.nodes.map((node) => (
                      <LearningNodeItem
                        key={node.id}
                        node={node}
                        selected={selectedNodeId === node.id}
                        onSelect={onSelectNode}
                      />
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}
