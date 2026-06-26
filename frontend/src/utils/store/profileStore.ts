import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { ProfileSummary } from '@/utils/types/profile'

interface ProfileState {
  summary: ProfileSummary | null
  wsPayload: Record<string, unknown> | null
  revision: number
  lastPatchAt: string | null
  setSummary: (s: ProfileSummary) => void
  patchSummary: (patch: Partial<ProfileSummary>) => void
  applyWsPatch: (payload: Record<string, unknown>) => void
}

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

function parseWeakTopics(raw: unknown): string[] {
  if (Array.isArray(raw)) return raw.map(String).filter(Boolean)
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const parsed = JSON.parse(raw) as unknown
      if (Array.isArray(parsed)) return parsed.map(String).filter(Boolean)
    } catch {
      return [raw]
    }
  }
  return []
}

function knowledgeLevelToMastery(level: unknown): number | undefined {
  if (level === 'ADVANCED') return 85
  if (level === 'INTERMEDIATE') return 65
  if (level === 'BEGINNER') return 40
  return undefined
}

function mapWsPatchToSummary(payload: Record<string, unknown>): Partial<ProfileSummary> {
  const topicMastery = parseTopicMastery(payload.topicMastery)
  const masteredCount = Object.values(topicMastery).filter((v) => v === 'MASTERED').length
  const weakTopics = parseWeakTopics(payload.commonErrors)
  const masteryFromLevel = knowledgeLevelToMastery(payload.knowledgeLevel)

  return {
    displayName: typeof payload.learningGoal === 'string' ? payload.learningGoal.slice(0, 24) : undefined,
    masteryLevel: masteryFromLevel,
    completedKpCount: masteredCount > 0 ? masteredCount : undefined,
    weakTopics: weakTopics.length > 0 ? weakTopics : undefined,
  }
}

export const useProfileStore = create<ProfileState>()(
  persist(
    (set, get) => ({
      summary: null,
      wsPayload: null,
      revision: 0,
      lastPatchAt: null,
      setSummary: (summary) => set({ summary }),
      patchSummary: (patch) =>
        set((state) => ({
          summary: state.summary
            ? { ...state.summary, ...patch }
            : (patch as ProfileSummary),
        })),
      applyWsPatch: (payload) => {
        const mapped = mapWsPatchToSummary(payload)
        set((state) => ({
          wsPayload: payload,
          lastPatchAt: new Date().toISOString(),
          revision: state.revision + 1,
          summary: state.summary
            ? { ...state.summary, ...mapped }
            : ({ ...mapped } as ProfileSummary),
        }))
      },
    }),
    {
      name: 'lqragent-profile-storage',
      partialize: (state) => ({
        summary: state.summary,
        lastPatchAt: state.lastPatchAt,
      }),
    },
  ),
)

/** 供 WS dispatcher 使用，兼容旧 patchSummary 调用 */
export function patchProfileFromWs(payload: Record<string, unknown>) {
  useProfileStore.getState().applyWsPatch(payload)
}

export function getProfileRevision() {
  return useProfileStore.getState().revision
}
