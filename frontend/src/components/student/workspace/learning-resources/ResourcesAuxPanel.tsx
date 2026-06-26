import {
  MOCK_COVERAGE,
  MOCK_MY_LIBRARY,
  MOCK_WEEKLY_PLAN,
} from '@/mock/learningResources'
import MyResourceLibraryCard from './MyResourceLibraryCard'
import ResourceCoverageCard from './ResourceCoverageCard'
import WeeklyResourcePlanCard from './WeeklyResourcePlanCard'

type Props = {
  onViewCoverage?: () => void
  onViewPlan?: () => void
  onLibraryItemClick?: (id: string) => void
}

export default function ResourcesAuxPanel({
  onViewCoverage,
  onViewPlan,
  onLibraryItemClick,
}: Props) {
  return (
    <aside className="hidden w-[280px] shrink-0 flex-col gap-4 overflow-y-auto pr-5 pt-6 xl:flex xl:w-[320px]">
      <MyResourceLibraryCard items={MOCK_MY_LIBRARY} onItemClick={onLibraryItemClick} />
      <ResourceCoverageCard
        percent={MOCK_COVERAGE.percent}
        legend={MOCK_COVERAGE.legend}
        onViewCoverage={onViewCoverage}
      />
      <WeeklyResourcePlanCard items={MOCK_WEEKLY_PLAN} onViewPlan={onViewPlan} />
    </aside>
  )
}
