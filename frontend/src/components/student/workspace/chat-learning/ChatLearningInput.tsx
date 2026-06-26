import { useEffect, useState, type FormEvent } from 'react'
import {
  ChevronDown,
  Code2,
  Image,
  Paperclip,
  Send,
  Wrench,
} from 'lucide-react'
import { useChatStore } from '@/utils/store/chatStore'

type Props = {
  draft: string
  onDraftChange: (value: string) => void
  onSend: (content: string) => void
}

export default function ChatLearningInput({ draft, onDraftChange, onSend }: Props) {
  const [focused, setFocused] = useState(false)
  const addMessage = useChatStore((s) => s.addMessage)
  const canSend = draft.trim().length > 0

  useEffect(() => {
    if (draft) setFocused(true)
  }, [draft])

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const content = draft.trim()
    if (!content) return

    useChatStore.getState().finalizeStuckStreaming()

    addMessage({
      id: crypto.randomUUID(),
      role: 'user',
      content,
      createdAt: new Date(),
    })
    addMessage({
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '',
      streaming: true,
      agentSteps: [],
      agentStepsCollapsed: false,
      createdAt: new Date(),
    })

    onSend(content)
    onDraftChange('')
  }

  return (
    <div className="shrink-0 rounded-[18px] border border-[#E6EEFA] bg-white p-3.5 shadow-[0_8px_24px_rgba(15,23,42,0.06)]">
      <form onSubmit={handleSubmit} className="flex flex-col gap-2">
        <textarea
          value={draft}
          onChange={(e) => onDraftChange(e.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          data-chat-learning-input
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              handleSubmit(e as unknown as FormEvent)
            }
          }}
          placeholder="输入你的问题..."
          rows={focused || draft ? 2 : 1}
          className="min-h-[52px] resize-none border-0 bg-transparent text-sm leading-relaxed text-[#0F172A] outline-none placeholder:text-[#94A3B8]"
        />
        <div className="flex h-8 items-center justify-between">
          <div className="flex items-center gap-[18px]">
            {[
              { icon: Image, label: '图片' },
              { icon: Code2, label: '代码' },
              { icon: Paperclip, label: '文件' },
              { icon: Wrench, label: '工具' },
            ].map(({ icon: Icon, label }) => (
              <button
                key={label}
                type="button"
                className="inline-flex items-center gap-1.5 text-[13px] text-[#64748B] transition-colors hover:text-[#2563EB]"
              >
                <Icon className="h-4 w-4" strokeWidth={1.8} />
                {label}
              </button>
            ))}
          </div>
          <div className="flex items-center gap-2.5">
            <button
              type="button"
              className="inline-flex h-7 items-center gap-1 rounded-lg border border-[#D8E4F5] px-2.5 text-xs text-[#475569]"
            >
              DeepSeek-R1
              <ChevronDown className="h-3.5 w-3.5" />
            </button>
            <button
              type="submit"
              disabled={!canSend}
              className={`flex h-[38px] w-[38px] items-center justify-center rounded-full text-white transition-all duration-200 ${
                canSend
                  ? 'bg-gradient-to-br from-[#3B82F6] to-[#2563EB] shadow-[0_8px_20px_rgba(37,99,235,0.28)] hover:shadow-[0_12px_28px_rgba(37,99,235,0.35)]'
                  : 'cursor-not-allowed bg-[#BFD7FF]'
              }`}
              aria-label="发送"
            >
              <Send className="h-4 w-4" />
            </button>
          </div>
        </div>
      </form>
    </div>
  )
}
