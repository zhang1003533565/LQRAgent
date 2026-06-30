import { useCallback, useEffect, useMemo, useState } from 'react'
import { getKnowledgeGraph, type KnowledgeGraphData } from '@/api/student/knowledge'
import { fetchProfileDetailRaw } from '@/api/student/profile'
import { usePathStore } from '@/utils/store/pathStore'
import { useProfileStore } from '@/utils/store/profileStore'
import { buildMasteryMap } from '@/utils/knowledgeGraph/graphStatus'
import { normalizeGraphData } from '@/utils/knowledgeGraph/normalizeGraphData'
import { resolveCurrentLearningKpId } from '@/utils/knowledgeGraph/graphFocus'
import { parseApiError } from '@/utils/api/parseApiError'
import type { GraphEdge, GraphNode, GraphStats } from '@/types/knowledgeGraph'

export function useKnowledgeGraphData(subject: string) {
  const [raw, setRaw] = useState<KnowledgeGraphData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [masteryByKp, setMasteryByKp] = useState(buildMasteryMap([]))
  const profileRevision = useProfileStore((s) => s.revision)

  const pathNodes = usePathStore((s) => s.nodes)
  const pathSet = useMemo(() => new Set(pathNodes.map((n) => n.kpId)), [pathNodes])
  const pathCompletedSet = useMemo(
    () => new Set(pathNodes.filter((n) => n.completed).map((n) => n.kpId)),
    [pathNodes],
  )
  const pathIndexMap = useMemo(() => {
    const map = new Map<string, number>()
    pathNodes.forEach((node, index) => map.set(node.kpId, index))
    return map
  }, [pathNodes])

  const currentLearningId = useMemo(
    () => resolveCurrentLearningKpId(pathNodes, []),
    [pathNodes],
  )

  const loadGraph = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const graph = await getKnowledgeGraph(subject || undefined)
      setRaw(graph)
    } catch (e) {
      setRaw(null)
      setError(parseApiError(e, '加载知识图谱失败'))
    } finally {
      setLoading(false)
    }
  }, [subject])

  useEffect(() => {
    void loadGraph()
  }, [loadGraph])

  useEffect(() => {
    void fetchProfileDetailRaw()
      .then((detail) => setMasteryByKp(buildMasteryMap(detail.knowledgeMap ?? [])))
      .catch(() => setMasteryByKp(buildMasteryMap([])))
  }, [profileRevision])

  const normalized = useMemo(() => {
    if (!raw) return null
    const base = normalizeGraphData(raw, {
      masteryByKp,
      pathSet,
      pathCompletedSet,
      pathIndexMap,
      currentLearningId: null,
    })
    const learningId = resolveCurrentLearningKpId(pathNodes, base.nodes)
    if (!learningId) return base
    return normalizeGraphData(raw, {
      masteryByKp,
      pathSet,
      pathCompletedSet,
      pathIndexMap,
      currentLearningId: learningId,
    })
  }, [raw, masteryByKp, pathSet, pathCompletedSet, pathIndexMap, pathNodes])

  const currentLearningIdResolved = useMemo(
    () => (normalized ? resolveCurrentLearningKpId(pathNodes, normalized.nodes) : currentLearningId),
    [normalized, pathNodes, currentLearningId],
  )

  return {
    raw,
    nodes: normalized?.nodes ?? ([] as GraphNode[]),
    edges: normalized?.edges ?? ([] as GraphEdge[]),
    stats: normalized?.stats ?? null,
    loading,
    error,
    refetch: loadGraph,
    pathSet,
    pathIndexMap,
    pathNodes,
    currentLearningId: currentLearningIdResolved,
  }
}

export type KnowledgeGraphDataState = {
  nodes: GraphNode[]
  edges: GraphEdge[]
  stats: GraphStats | null
}
