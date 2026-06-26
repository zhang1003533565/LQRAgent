import { useMemo } from 'react'
import { usePathStore } from '@/utils/store/pathStore'
import { getGreeting } from '@/utils/chat/getGreeting'
import { buildWelcomeQuickActions } from '@/services/chatSidebarService'
import QuickActionCard from './QuickActionCard'

type Props = {
  userName: string
  onQuickAction: (prompt: string) => void
}

export default function ChatWelcomeCard({ userName, onQuickAction }: Props) {
  const pathNodes = usePathStore((s) => s.nodes)
  const selectedKpId = usePathStore((s) => s.selectedKpId)
  const goal = usePathStore((s) => s.goal)

  const actions = useMemo(
    () => buildWelcomeQuickActions(),
    [pathNodes, selectedKpId, goal],
  )

  return (
    <section className="shrink-0 rounded-[18px] border border-[#E6EEFA] bg-white p-6 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <h1 className="text-2xl font-bold tracking-tight text-[#0F172A]">{getGreeting(userName)}</h1>
      <p className="mt-1.5 text-sm text-[#64748B]">
        有什么学习问题？我可以帮你解答，陪你一起进步～
      </p>
      <div className="mt-4 flex gap-3 overflow-x-auto pb-1 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        {actions.map((action) => (
          <QuickActionCard key={action.id} action={action} onSelect={onQuickAction} />
        ))}
      </div>
    </section>
  )
}
