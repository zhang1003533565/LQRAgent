import { create } from 'zustand'
import type { ArtifactKind } from '@/types/artifact'
import type { LearningResource } from '@/types/media-resource'
import type { MultiCardBlock } from '@/types/multi-card'

interface ArtifactState {
  lastKind: ArtifactKind | null
  activeKpId: string | null
  resources: LearningResource[]
  multiCardBlocks: MultiCardBlock[]
  setActiveKpId: (kpId: string | null) => void
  setResources: (items: LearningResource[]) => void
  addResource: (item: LearningResource) => void
  setMultiCardBlocks: (blocks: MultiCardBlock[]) => void
  setLastKind: (kind: ArtifactKind | null) => void
  reset: () => void
}

export const useArtifactStore = create<ArtifactState>((set) => ({
  lastKind: null,
  activeKpId: null,
  resources: [],
  multiCardBlocks: [],

  setActiveKpId: (kpId) => set({ activeKpId: kpId }),
  setResources: (items) => set({ resources: items }),
  addResource: (item) =>
    set((state) => ({ resources: [...state.resources, item] })),
  setMultiCardBlocks: (blocks) => set({ multiCardBlocks: blocks }),
  setLastKind: (kind) => set({ lastKind: kind }),
  reset: () =>
    set({
      lastKind: null,
      activeKpId: null,
      resources: [],
      multiCardBlocks: [],
    }),
}))
