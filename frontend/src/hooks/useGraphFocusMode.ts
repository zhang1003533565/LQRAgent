import { useMemo } from 'react'
import type { GraphEdge, GraphNode, GraphViewMode } from '@/types/knowledgeGraph'
import { computeFocusGraphView } from '@/utils/knowledgeGraph/graphFocus'
import type { VisibleGraphResult } from '@/utils/knowledgeGraph/graphRelationshipZones'

export function useGraphFocusMode(
  nodes: GraphNode[],
  graphVisibility: VisibleGraphResult,
  options: {
    viewMode: GraphViewMode
    selectedId: string | null
    currentLearningId: string | null
  },
) {
  return useMemo(
    () =>
      computeFocusGraphView(nodes, graphVisibility, {
        viewMode: options.viewMode,
        anchorId: options.selectedId ?? options.currentLearningId,
        currentLearningId: options.currentLearningId,
      }),
    [nodes, graphVisibility, options.viewMode, options.selectedId, options.currentLearningId],
  )
}
