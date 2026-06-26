import MyResourceLibraryCard from './MyResourceLibraryCard'
import ResourceCoverageCard from './ResourceCoverageCard'
import WeeklyResourcePlanCard from './WeeklyResourcePlanCard'
import type {
  CoverageLegend,
  MyLibraryItem,
  WeeklyPlanItem,
} from '@/utils/types/learningResources'

type Props = {
  libraryItems: MyLibraryItem[]
  coverage: { percent: number; legend: CoverageLegend[] }
  weeklyPlan: WeeklyPlanItem[]
  onViewCoverage?: () => void
  onViewPlan?: () => void
  onLibraryItemClick?: (id: string) => void
}

export default function ResourcesAuxPanel({
  libraryItems,
  coverage,
  weeklyPlan,
  onViewCoverage,
  onViewPlan,
  onLibraryItemClick,
}: Props) {
  return (
    <aside className="hidden w-[280px] shrink-0 flex-col gap-4 overflow-y-auto pr-5 pt-6 xl:flex xl:w-[320px]">
      <MyResourceLibraryCard items={libraryItems} onItemClick={onLibraryItemClick} />
      <ResourceCoverageCard
        percent={coverage.percent}
        legend={coverage.legend}
        onViewCoverage={onViewCoverage}
      />
      <WeeklyResourcePlanCard items={weeklyPlan} onViewPlan={onViewPlan} />
    </aside>
  )
}
