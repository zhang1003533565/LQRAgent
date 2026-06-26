import TodayGoalCard from './TodayGoalCard'
import QuickToolsCard from './QuickToolsCard'
import RecentLearningCard from './RecentLearningCard'
import { useChatSidebar } from '@/utils/hooks/useChatSidebar'

type Props = {
  onToolSelect: (prompt: string) => void
}

export default function ChatLearningAuxPanel({ onToolSelect }: Props) {
  const { data, loading } = useChatSidebar()

  return (
    <aside className="hidden h-full w-[280px] shrink-0 flex-col gap-4 overflow-hidden pr-5 pt-6 2xl:flex 2xl:w-[320px]">
      <TodayGoalCard data={data.todayGoal} loading={loading} />
      <QuickToolsCard tools={data.quickTools} loading={loading} onToolSelect={onToolSelect} />
      <RecentLearningCard items={data.recentLearning} loading={loading} />
    </aside>
  )
}
