import { create } from 'zustand'
import type { LearningPathDto, PathNode } from '@/utils/types/learning-path'

interface PathState {
  goal: string
  planDescription: string
  nodes: PathNode[]
  selectedKpId: string | null
  loading: boolean
  setLoading: (v: boolean) => void
  setPath: (data: LearningPathDto) => void
  selectNode: (kpId: string | null) => void
  clear: () => void
}

export const usePathStore = create<PathState>((set) => ({
  goal: '',
  planDescription: '',
  nodes: [],
  selectedKpId: null,
  loading: false,

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
    }),
}))
