import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { LearningPathDto, PathNode } from '@/utils/types/learning-path'
import { getCurrentPath } from '@/api/student/learningPath'

interface PathState {
  goal: string
  planDescription: string
  nodes: PathNode[]
  selectedKpId: string | null
  loading: boolean
  hasUpdates: boolean
  setLoading: (v: boolean) => void
  setPath: (data: LearningPathDto) => void
  selectNode: (kpId: string | null) => void
  clear: () => void
  refresh: () => Promise<void>
  markUpdated: () => void
  clearUpdates: () => void
}

export const usePathStore = create<PathState>()(
  persist(
    (set, get) => ({
      goal: '',
      planDescription: '',
      nodes: [],
      selectedKpId: null,
      loading: false,
      hasUpdates: false,

      setLoading: (loading) => set({ loading }),
      setPath: (data) =>
        set({
          goal: data.goal,
          planDescription: data.planDescription,
          nodes: data.nodes,
          selectedKpId: data.nodes[0]?.kpId ?? null,
        }),
      selectNode: (kpId) => set({ selectedKpId: kpId }),
      clear: () =>
        set({
          goal: '',
          planDescription: '',
          nodes: [],
          selectedKpId: null,
          loading: false,
          hasUpdates: false,
        }),
      refresh: async () => {
        try {
          const data = await getCurrentPath()
          if (data?.nodes && data.nodes.length > 0) {
            const prev = get().nodes
            const prevIds = new Set(prev.map((n) => n.kpId))
            const newIds = new Set(data.nodes.map((n) => n.kpId))
            const changed = prev.length !== data.nodes.length ||
              [...newIds].some((id) => !prevIds.has(id))

            set({
              goal: data.goal,
              planDescription: data.planDescription,
              nodes: data.nodes,
              hasUpdates: changed,
            })
          }
        } catch {
          // silent
        }
      },
      markUpdated: () => set({ hasUpdates: true }),
      clearUpdates: () => set({ hasUpdates: false }),
    }),
    {
      name: 'lqragent-path-storage',
      partialize: (state) => ({
        goal: state.goal,
        planDescription: state.planDescription,
        nodes: state.nodes,
        selectedKpId: state.selectedKpId,
      }),
    },
  ),
)
