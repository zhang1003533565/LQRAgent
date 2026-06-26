import type { LearningPathNodeItem } from '@/mock/learningPath'
import CurrentTaskCard from './CurrentTaskCard'
import PathTodayGoalCard from './PathTodayGoalCard'
import AchievementCard from './AchievementCard'

type Props = {
  selectedNode: LearningPathNodeItem | null
  todayGoal: { label: string; current: number; total: number }
  onStartLearning: () => void
  onGenerateNotes: () => void
  onGenerateQuiz: () => void
}

export default function PathAuxPanel({
  selectedNode,
  todayGoal,
  onStartLearning,
  onGenerateNotes,
  onGenerateQuiz,
}: Props) {
  return (
    <aside className="hidden w-[300px] shrink-0 flex-col gap-4 overflow-y-auto pr-5 pt-6 xl:flex xl:w-[340px]">
      <CurrentTaskCard
        node={selectedNode}
        onStartLearning={onStartLearning}
        onGenerateNotes={onGenerateNotes}
        onGenerateQuiz={onGenerateQuiz}
      />
      <PathTodayGoalCard
        label={todayGoal.label}
        current={todayGoal.current}
        total={todayGoal.total}
      />
      <AchievementCard />
    </aside>
  )
}
