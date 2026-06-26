import type { BackendKnowledgeMapItem } from '@/utils/learningProfile/profileMappers'

export type GraphStatus = 'mastered' | 'learning' | 'weak' | 'unlearned'

export type KpMasteryEntry = {
  mastery: number
  status?: string
}

export function buildMasteryMap(items: BackendKnowledgeMapItem[] = []): Map<string, KpMasteryEntry> {
  const map = new Map<string, KpMasteryEntry>()
  for (const item of items) {
    if (!item.kpId) continue
    map.set(item.kpId, { mastery: item.mastery ?? 0, status: item.status })
  }
  return map
}

export function resolveGraphNodeStatus(
  kpId: string,
  masteryByKp: Map<string, KpMasteryEntry>,
  pathActiveSet: Set<string>,
  pathCompletedSet: Set<string>,
): GraphStatus {
  const entry = masteryByKp.get(kpId)
  if (entry) {
    if (entry.status === 'MASTERED' || entry.mastery >= 80) return 'mastered'
    if (entry.mastery >= 35) return 'learning'
    if (entry.mastery > 0) return 'weak'
  }
  if (pathCompletedSet.has(kpId)) return 'mastered'
  if (pathActiveSet.has(kpId)) return 'learning'
  return 'unlearned'
}
