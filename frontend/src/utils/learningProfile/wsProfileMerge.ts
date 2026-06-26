import type { LearningProfile, MasteryStatus } from '@/utils/types/learningProfile'

function parseTopicMastery(raw: unknown): Record<string, string> {
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw) as Record<string, string>
    } catch {
      return {}
    }
  }
  if (raw && typeof raw === 'object') {
    return raw as Record<string, string>
  }
  return {}
}

function masteryPercentFromStatus(status: string): number {
  if (status === 'MASTERED') return 100
  if (status === 'PENDING') return 20
  return 0
}

function mapMasteryStatus(rate: number, status?: string): MasteryStatus {
  if (status === 'MASTERED' || rate >= 80) return 'mastered'
  if (rate >= 60) return 'good'
  if (rate >= 35) return 'normal'
  return 'weak'
}

export function mergeProfileFromWsPatch(
  profile: LearningProfile,
  payload: Record<string, unknown>,
): LearningProfile | null {
  const topicMastery = parseTopicMastery(payload.topicMastery)
  if (Object.keys(topicMastery).length === 0) return null

  const existingIds = new Set(profile.knowledgeMastery.map((item) => item.knowledgePointId))
  const updatedKnowledgeMastery = profile.knowledgeMastery.map((item) => {
    const topicStatus = topicMastery[item.knowledgePointId]
    if (!topicStatus) return item
    const masteryRate = masteryPercentFromStatus(topicStatus)
    return {
      ...item,
      masteryRate,
      status: mapMasteryStatus(masteryRate, topicStatus),
    }
  })

  for (const [kpId, topicStatus] of Object.entries(topicMastery)) {
    if (existingIds.has(kpId)) continue
    const masteryRate = masteryPercentFromStatus(topicStatus)
    updatedKnowledgeMastery.push({
      knowledgePointId: kpId,
      name: kpId,
      masteryRate,
      status: mapMasteryStatus(masteryRate, topicStatus),
      relatedLearningPathNodeId: kpId,
    })
  }

  const masteredCount = Object.values(topicMastery).filter((v) => v === 'MASTERED').length
  const avgMastery =
    updatedKnowledgeMastery.length > 0
      ? Math.round(
          updatedKnowledgeMastery.reduce((sum, item) => sum + item.masteryRate, 0) /
            updatedKnowledgeMastery.length,
        )
      : profile.overview.overallMasteryRate

  return {
    ...profile,
    knowledgeMastery: updatedKnowledgeMastery,
    overview: {
      ...profile.overview,
      overallMasteryRate: avgMastery,
      completedLearningPathNodes:
        masteredCount > 0 ? masteredCount : profile.overview.completedLearningPathNodes,
      updatedAt: new Date().toISOString(),
    },
  }
}
