import TodayGoalCard from './TodayGoalCard'
import QuickToolsCard from './QuickToolsCard'
import RecentLearningCard from './RecentLearningCard'

type Props = {
  onToolSelect: (prompt: string) => void
}

export default function ChatLearningAuxPanel({ onToolSelect }: Props) {
  return (
    <aside className="hidden h-full w-[280px] shrink-0 flex-col gap-4 overflow-hidden pr-5 pt-6 2xl:flex 2xl:w-[320px]">
      <TodayGoalCard />
      <QuickToolsCard onToolSelect={onToolSelect} />
      <RecentLearningCard />
    </aside>
  )
}
