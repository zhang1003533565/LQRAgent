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

function normalizeAdminRedirect(user: AuthUser): AuthUser {
  if (
    user.role === 'admin' &&
    (user.redirectPath === '/admin' || user.redirectPath === '/admin/')
  ) {
    return { ...user, redirectPath: '/admin/console' }
  }
  return user
}

/**
 * 全局鉴权状态，持久化到 localStorage。
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,

      setUser: (user) => set({ user: normalizeAdminRedirect(user) }),

      logout: () => {
        set({ user: null })
      },

      isAuthenticated: () => get().user !== null,
    }),
    {
      name: 'lqragent-auth',
      onRehydrateStorage: () => (state) => {
        if (state?.user) {
          const normalized = normalizeAdminRedirect(state.user)
          if (normalized !== state.user) {
            state.setUser(normalized)
          }
        }
      },
    },
  ),
)
