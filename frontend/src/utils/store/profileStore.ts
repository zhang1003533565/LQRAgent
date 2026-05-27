import { create } from 'zustand'
import type { ProfileSummary } from '@/utils/types/profile'

interface ProfileState {
  summary: ProfileSummary | null
  setSummary: (s: ProfileSummary) => void
  patchSummary: (patch: Partial<ProfileSummary>) => void
}

export const useProfileStore = create<ProfileState>((set) => ({
  summary: null,
  setSummary: (summary) => set({ summary }),
  patchSummary: (patch) =>
    set((state) => ({
      summary: state.summary
        ? { ...state.summary, ...patch }
        : (patch as ProfileSummary),
    })),
}))
