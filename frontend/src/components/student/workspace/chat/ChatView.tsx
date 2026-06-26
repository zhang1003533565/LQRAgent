import { useCallback, useEffect, useState } from 'react'
import { sendChatWs } from '@/utils/chat/chatWebSocket'
import { useChatStore } from '@/utils/store/chatStore'
import { useAuthStore } from '@/utils/store/authStore'
import { usePathStore } from '@/utils/store/pathStore'
import { chatApi } from '@/api/student/chat'
import { trackBehavior } from '@/utils/tracker'
import { submitChatMessage } from '@/utils/chat/sendChatMessage'
import ChatMessageList from './ChatMessageList'
import {
  ChatLearningAuxPanel,
  ChatLearningFooter,
  ChatLearningInput,
  ChatWelcomeCard,
} from '../chat-learning'

export default function ChatView() {
  const sessionId = useChatStore((s) => s.sessionId)
  const isConnected = useChatStore((s) => s.isConnected)
  const loadMessages = useChatStore((s) => s.loadMessages)
  const messages = useChatStore((s) => s.messages)
  const user = useAuthStore((s) => s.user)
  const userId = user?.userId != null ? String(user.userId) : null
  const selectedKpId = usePathStore((s) => s.selectedKpId)
  const autoLoaded = useChatStore((s) => s.autoLoaded)
  const setAutoLoaded = useChatStore((s) => s.setAutoLoaded)
  const [inputDraft, setInputDraft] = useState('')

  const userName = user?.username || '同学'
  const hasMessages = messages.length > 0

  useEffect(() => {
    if (autoLoaded || !userId) return
    if (messages.length > 0 || sessionId) {
      setAutoLoaded(true)
      return
    }
    const loadRecentSession = async () => {
      try {
        const sessions = await chatApi.getSessions(userId)
        if (sessions.length > 0) {
          await loadMessages(String(sessions[0].id))
        }
      } catch (err) {
        console.error('Failed to auto-load recent session:', err)
      } finally {
        setAutoLoaded(true)
      }
    }
    loadRecentSession()
  }, [userId, autoLoaded, loadMessages, messages.length, sessionId, setAutoLoaded])

  const trackSend = useCallback(
    (content: string) => {
      if (selectedKpId) {
        trackBehavior({ kpId: selectedKpId, action: 'chat_send', extra: content.slice(0, 100) })
      }
      sendChatWs(content)
    },
    [selectedKpId],
  )

  const handleQuickPrompt = useCallback((prompt: string) => {
    setInputDraft(prompt)
    const textarea = document.querySelector<HTMLTextAreaElement>(
      '[data-chat-learning-input]',
    )
    textarea?.focus()
  }, [])

  const handleQuickSend = useCallback((prompt: string) => {
    submitChatMessage(prompt, trackSend)
  }, [trackSend])

  return (
    <div className="flex h-full min-h-0 overflow-hidden bg-[#F6F9FE] font-sans">
      <div className="flex min-w-0 flex-1 flex-col gap-4 px-5 pb-4 pt-6">
        <div className="relative flex min-h-0 flex-1 flex-col gap-4">
          <div
            className={`absolute right-0 top-0 z-10 flex items-center gap-1.5 rounded-full border border-[#E6EEFA] bg-white/80 px-2.5 py-1 text-[11px] text-[#64748B] backdrop-blur-sm ${
              hasMessages ? '' : 'mt-0'
            }`}
          >
            <span
              className={`h-2 w-2 rounded-full ${
                isConnected ? 'bg-[#22C55E] shadow-[0_0_6px_rgba(34,197,94,0.4)]' : 'animate-pulse bg-[#F59E0B]'
              }`}
            />
            {isConnected ? '已连接' : '连接中'}
          </div>

          {!hasMessages ? (
            <ChatWelcomeCard userName={userName} onQuickAction={handleQuickSend} />
          ) : null}

          <div className="min-h-0 flex-1 overflow-y-auto px-1 py-2">
            <ChatMessageList />
          </div>
        </div>

        <ChatLearningInput
          draft={inputDraft}
          onDraftChange={setInputDraft}
          onSend={trackSend}
        />
        <ChatLearningFooter />
      </div>

      <ChatLearningAuxPanel onToolSelect={handleQuickPrompt} />
    </div>
  )
}
