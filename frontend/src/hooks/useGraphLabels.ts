import { useMemo } from 'react'
import type { LayoutNode } from '@/types/knowledgeGraph'
import { resolveGraphNodeLabels } from '@/utils/knowledgeGraph/graphLabels'

export function useGraphLabels(
  layoutNodes: LayoutNode[],
  zoom: number,
  selectedNodeId: string | null,
  hoveredNodeId: string | null,
  currentLearningId: string | null,
  visibleIds: Set<string>,
  visibleIdKey: string,
) {
  return useMemo(
    () => resolveGraphNodeLabels(
      layoutNodes,
      zoom,
      selectedNodeId,
      hoveredNodeId,
      currentLearningId,
      visibleIds,
    ),
    [layoutNodes, zoom, selectedNodeId, hoveredNodeId, currentLearningId, visibleIds, visibleIdKey],
  )
}
