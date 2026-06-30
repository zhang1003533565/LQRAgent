import { useEffect, useMemo, useState } from 'react'
import type { EdgeFilter, GraphViewMode } from '@/types/knowledgeGraph'
import type { GraphStatus } from '@/utils/knowledgeGraph/graphStatus'
import type { GraphEdge, GraphNode } from '@/types/knowledgeGraph'
import { edgeMatchesFilter } from '@/utils/knowledgeGraph/graphRelations'
import { getVisibleGraphElements } from '@/utils/knowledgeGraph/graphRelationshipZones'

export function useKnowledgeGraphFilters(
  nodes: GraphNode[],
  edges: GraphEdge[],
  pathSet: Set<string>,
  options: {
    selectedNodeId?: string | null
    currentLearningId?: string | null
  } = {},
) {
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<GraphStatus | 'all'>('all')
  const [edgeFilter, setEdgeFilter] = useState<EdgeFilter>('all')
  const [viewMode, setViewMode] = useState<GraphViewMode>('path')
  const [filtersExpanded, setFiltersExpanded] = useState(false)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), 220)
    return () => window.clearTimeout(timer)
  }, [search])

  const searchHighlightIds = useMemo(() => {
    const keyword = debouncedSearch.toLowerCase()
    if (!keyword) return new Set<string>()
    return new Set(
      nodes
        .filter((n) => n.name.toLowerCase().includes(keyword) || n.id.toLowerCase().includes(keyword))
        .map((n) => n.id),
    )
  }, [nodes, debouncedSearch])

  const statusFilteredIds = useMemo(() => {
    const keyword = debouncedSearch.toLowerCase()
    const ids = new Set<string>()
    for (const node of nodes) {
      const matchSearch = !keyword || node.name.toLowerCase().includes(keyword) || node.id.toLowerCase().includes(keyword)
      const matchStatus = statusFilter === 'all' || node.status === statusFilter
      if (matchSearch && matchStatus) ids.add(node.id)
    }
    return ids
  }, [nodes, debouncedSearch, statusFilter])

  const graphVisibility = useMemo(
    () => getVisibleGraphElements(nodes, edges, {
      viewMode,
      selectedNodeId: options.selectedNodeId ?? null,
      currentLearningId: options.currentLearningId ?? null,
      pathSet,
    }),
    [nodes, edges, viewMode, pathSet, options.selectedNodeId, options.currentLearningId],
  )

  const visibleIds = useMemo(() => {
    const ids = new Set<string>()
    for (const id of graphVisibility.visibleIds) {
      if (statusFilteredIds.has(id)) ids.add(id)
    }
    return ids
  }, [graphVisibility.visibleIds, statusFilteredIds])

  const visibleEdges = useMemo(() => {
    return edges.filter((edge) => {
      if (!visibleIds.has(edge.source) || !visibleIds.has(edge.target)) return false
      return edgeMatchesFilter(edge, edgeFilter, options.selectedNodeId ?? null)
    })
  }, [edges, visibleIds, edgeFilter, options.selectedNodeId])

  const searchResults = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    if (!keyword) return []
    return nodes
      .filter((n) => n.name.toLowerCase().includes(keyword) || n.id.toLowerCase().includes(keyword))
      .slice(0, 8)
  }, [nodes, search])

  return {
    search,
    setSearch,
    debouncedSearch,
    statusFilter,
    setStatusFilter,
    edgeFilter,
    setEdgeFilter,
    viewMode,
    setViewMode,
    filtersExpanded,
    setFiltersExpanded,
    visibleIds,
    visibleEdges,
    searchHighlightIds,
    searchResults,
    graphVisibility,
  }
}
