import { useChatStore } from '@/utils/store/chatStore'
import { useChatAutoScroll } from '@/utils/hooks/useChatAutoScroll'
import type { ChatMessage } from '@/utils/types/chat'
import StreamingMessage from './StreamingMessage'

function isRenderableMessage(msg: ChatMessage): boolean {
  if (msg.role !== 'assistant') return true
  if (msg.streaming) return true
  if (msg.content?.trim()) return true
  if (msg.imageUrl || msg.videoUrl || msg.quizData || msg.cards?.length || msg.ragSources?.length) {
    return true
  }
  if (msg.contentType && msg.contentType !== 'text') return true
  return false
}

export default function ChatMessageList() {
  const messages = useChatStore((s) => s.messages)
  const loadingMessages = useChatStore((s) => s.loadingMessages)
  const bottomRef = useChatAutoScroll([messages], loadingMessages)

  return (
    <div className="flex flex-col gap-5">
      {messages.filter(isRenderableMessage).map((msg) => (
        <StreamingMessage key={msg.id} message={msg} />
      ))}
      <div ref={bottomRef} />
    </div>
  )
}
