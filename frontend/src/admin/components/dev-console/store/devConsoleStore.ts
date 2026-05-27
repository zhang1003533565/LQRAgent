import { create } from 'zustand'
import type { DevConsoleNavId, DevLogEntry, LogLevel } from '@/admin/components/dev-console/types/dev-console'
import { MOCK_INITIAL_LOGS } from '@/admin/components/dev-console/mock/data'

interface DevConsoleState {
  activeNav: DevConsoleNavId
  logs: DevLogEntry[]
  logFilter: LogLevel | 'ALL'
  wsConnected: boolean
  setActiveNav: (id: DevConsoleNavId) => void
  appendLog: (entry: Omit<DevLogEntry, 'id'>) => void
  clearLogs: () => void
  setLogFilter: (level: LogLevel | 'ALL') => void
  setWsConnected: (v: boolean) => void
}

export const useDevConsoleStore = create<DevConsoleState>((set) => ({
  activeNav: 'dashboard',
  logs: MOCK_INITIAL_LOGS,
  logFilter: 'ALL',
  wsConnected: true,
  setActiveNav: (id) => set({ activeNav: id }),
  appendLog: (entry) =>
    set((s) => ({
      logs: [
        ...s.logs,
        {
          ...entry,
          id: crypto.randomUUID(),
        },
      ].slice(-200),
    })),
  clearLogs: () => set({ logs: [] }),
  setLogFilter: (logFilter) => set({ logFilter }),
  setWsConnected: (wsConnected) => set({ wsConnected }),
}))
