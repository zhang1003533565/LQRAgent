import { Search, ChevronRight, SlidersHorizontal } from 'lucide-react'
import type { GraphNode, GraphViewMode } from '@/types/knowledgeGraph'
import type { GraphStatus } from '@/utils/knowledgeGraph/graphStatus'
import type { EdgeFilter } from '@/types/knowledgeGraph'
import { STATUS_META } from '@/utils/knowledgeGraph/graphColors'
import styles from '@/pages/student/KnowledgeGraphPage.module.css'

interface KnowledgeGraphToolbarProps {
  search: string
  onSearchChange: (value: string) => void
  searchResults: GraphNode[]
  onSelectSearchResult: (node: GraphNode) => void
  statusFilter: GraphStatus | 'all'
  onStatusFilterChange: (value: GraphStatus | 'all') => void
  edgeFilter: EdgeFilter
  onEdgeFilterChange: (value: EdgeFilter) => void
  viewMode: GraphViewMode
  onViewModeChange: (mode: GraphViewMode) => void
  filtersExpanded: boolean
  onFiltersExpandedChange: (value: boolean) => void
  subjects: string[]
  subject: string
  onSubjectChange: (value: string) => void
  searchInputRef?: React.RefObject<HTMLInputElement | null>
}

const STATUS_OPTIONS: { value: GraphStatus | 'all'; label: string; color?: string }[] = [
  { value: 'all', label: '全部' },
  ...(Object.keys(STATUS_META) as GraphStatus[]).map((status) => ({
    value: status,
    label: STATUS_META[status].label,
    color: STATUS_META[status].color,
  })),
]

const VIEW_OPTIONS: { value: GraphViewMode; label: string }[] = [
  { value: 'path', label: '路径聚焦' },
  { value: 'module', label: '模块聚类' },
  { value: 'full', label: '全量图谱' },
]

const EDGE_OPTIONS: { value: EdgeFilter; label: string }[] = [
  { value: 'all', label: '全部关系' },
  { value: 'path_main', label: '主干' },
  { value: 'path_segment', label: '路径关联' },
  { value: 'prerequisite', label: '依赖' },
]

export default function KnowledgeGraphToolbar({
  search,
  onSearchChange,
  searchResults,
  onSelectSearchResult,
  statusFilter,
  onStatusFilterChange,
  edgeFilter,
  onEdgeFilterChange,
  viewMode,
  onViewModeChange,
  filtersExpanded,
  onFiltersExpandedChange,
  subjects,
  subject,
  onSubjectChange,
  searchInputRef,
}: KnowledgeGraphToolbarProps) {
  return (
    <div className={styles.canvasToolbar}>
      <div className={styles.toolbarTopRow}>
        <div className={styles.searchBox}>
          <Search size={15} />
          <input
            ref={searchInputRef}
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="搜索知识点..."
            aria-label="搜索知识点"
          />
          <kbd className={styles.searchKbd}>Ctrl K</kbd>
          {searchResults.length > 0 && (
            <div className={styles.searchResults}>
              {searchResults.map((node) => (
                <button key={node.id} type="button" onClick={() => onSelectSearchResult(node)}>
                  {node.name}
                  <ChevronRight size={14} />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className={styles.segmentGroup} role="group" aria-label="视图模式">
          {VIEW_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              className={viewMode === opt.value ? styles.segmentActive : styles.segmentBtn}
              onClick={() => onViewModeChange(opt.value)}
            >
              {opt.label}
            </button>
          ))}
        </div>

        <button
          type="button"
          className={filtersExpanded ? styles.chipActive : styles.chip}
          onClick={() => onFiltersExpandedChange(!filtersExpanded)}
        >
          <SlidersHorizontal size={13} />
          筛选
        </button>
      </div>

      <div className={styles.filterRow}>
        {STATUS_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            className={statusFilter === opt.value ? styles.chipActive : styles.chip}
            onClick={() => onStatusFilterChange(opt.value)}
          >
            {opt.color ? <span className={styles.chipDot} style={{ background: opt.color }} /> : null}
            {opt.label}
          </button>
        ))}
      </div>

      {filtersExpanded ? (
        <div className={styles.toolbarSegmentRow}>
          <div className={styles.segmentGroup} role="group" aria-label="关系筛选">
            {EDGE_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                className={edgeFilter === opt.value ? styles.segmentActive : styles.segmentBtn}
                onClick={() => onEdgeFilterChange(opt.value)}
              >
                {opt.label}
              </button>
            ))}
          </div>
          {subjects.length > 1 && (
            <select
              className={styles.edgeSelect}
              value={subject}
              onChange={(e) => onSubjectChange(e.target.value)}
              aria-label="选择课程"
            >
              <option value="">全部主题</option>
              {subjects.map((item) => (
                <option key={item} value={item}>{item}</option>
              ))}
            </select>
          )}
        </div>
      ) : null}
    </div>
  )
}
