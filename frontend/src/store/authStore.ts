import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface AuthUser {
  userId: number
  username: string
  role: 'student' | 'teacher' | 'admin'
  token: string
  redirectPath: string
}

interface AuthState {
  user: AuthUser | null
  setUser: (user: AuthUser) => void
  logout: () => void
  isAuthenticated: () => boolean
}

/**
 * 全局鉴权状态，持久化到 localStorage。
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,

      setUser: (user) => set({ user }),

      logout: () => {
        set({ user: null })
      },

      isAuthenticated: () => get().user !== null,
    }),
    {
      name: 'lqragent-auth',
    },
  ),
)
