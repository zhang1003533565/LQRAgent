import { create } from 'zustand'
import type { ArtifactKind } from '@/utils/types/artifact'
import type { LearningResource } from '@/utils/types/media-resource'
import type { MultiCardBlock } from '@/utils/types/multi-card'

interface ArtifactState {
  lastKind: ArtifactKind | null
  activeKpId: string | null
  resources: LearningResource[]
  multiCardBlocks: MultiCardBlock[]
  resourceRefreshToken: number
  setActiveKpId: (kpId: string | null) => void
  setResources: (items: LearningResource[]) => void
  addResource: (item: LearningResource) => void
  markResourceReady: (kpId: string) => void
  setMultiCardBlocks: (blocks: MultiCardBlock[]) => void
  setLastKind: (kind: ArtifactKind | null) => void
  reset: () => void
}

export const useArtifactStore = create<ArtifactState>((set) => ({
  lastKind: null,
  activeKpId: null,
  resources: [],
  multiCardBlocks: [],
  resourceRefreshToken: 0,

  setActiveKpId: (kpId) => set({ activeKpId: kpId }),
  setResources: (items) => set({ resources: items }),
  addResource: (item) =>
    set((state) => ({
      resources: [...state.resources.filter(
        (r) => !(r.kpId === item.kpId && r.resourceType === item.resourceType && r.title === item.title),
      ), item],
      resourceRefreshToken: state.resourceRefreshToken + 1,
    })),
  markResourceReady: (kpId) =>
    set((state) => ({
      activeKpId: kpId,
      resourceRefreshToken: state.resourceRefreshToken + 1,
    })),
  setMultiCardBlocks: (blocks) => set({ multiCardBlocks: blocks }),
  setLastKind: (kind) => set({ lastKind: kind }),
  reset: () =>
    set({
      lastKind: null,
      activeKpId: null,
      resources: [],
      multiCardBlocks: [],
      resourceRefreshToken: 0,
    }),
}))
