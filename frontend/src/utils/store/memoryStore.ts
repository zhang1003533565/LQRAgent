import { create } from 'zustand'
import type { UserMemory, MemoryType } from '@/utils/types/memory'

interface MemoryState {
  memories: UserMemory[]
  loading: boolean
  error: string | null
  
  loadMemories: (userId: string, type?: MemoryType) => Promise<void>
  addMemory: (userId: string, memory: Partial<UserMemory>) => Promise<UserMemory | null>
  updateMemory: (memoryId: string, data: Partial<UserMemory>) => Promise<UserMemory | null>
  deleteMemory: (memoryId: string) => Promise<void>
  searchMemories: (userId: string, keyword: string) => Promise<UserMemory[]>
  clearError: () => void
}

const API_BASE = import.meta.env.VITE_API_URL || '/api'

export const useMemoryStore = create<MemoryState>((set, get) => ({
  memories: [],
  loading: false,
  error: null,

  loadMemories: async (userId: string, type?: MemoryType) => {
    set({ loading: true, error: null })
    try {
      const params = new URLSearchParams({ userId })
      if (type) params.append('type', type)
      
      const response = await fetch(`${API_BASE}/memory?${params}`)
      if (!response.ok) throw new Error('Failed to load memories')
      
      const memories = await response.json()
      set({ memories, loading: false })
    } catch (error) {
      set({ error: (error as Error).message, loading: false })
    }
  },

  addMemory: async (userId: string, memory: Partial<UserMemory>) => {
    try {
      const response = await fetch(`${API_BASE}/memory?userId=${userId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(memory),
      })
      
      if (!response.ok) throw new Error('Failed to add memory')
      
      const newMemory = await response.json()
      set((state) => ({ memories: [...state.memories, newMemory] }))
      return newMemory
    } catch (error) {
      set({ error: (error as Error).message })
      return null
    }
  },

  updateMemory: async (memoryId: string, data: Partial<UserMemory>) => {
    try {
      const response = await fetch(`${API_BASE}/memory/${memoryId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      })
      
      if (!response.ok) throw new Error('Failed to update memory')
      
      const updated = await response.json()
      set((state) => ({
        memories: state.memories.map((m) =>
          m.id === memoryId ? updated : m
        ),
      }))
      return updated
    } catch (error) {
      set({ error: (error as Error).message })
      return null
    }
  },

  deleteMemory: async (memoryId: string) => {
    try {
      const response = await fetch(`${API_BASE}/memory/${memoryId}`, {
        method: 'DELETE',
      })
      
      if (!response.ok) throw new Error('Failed to delete memory')
      
      set((state) => ({
        memories: state.memories.filter((m) => m.id !== memoryId),
      }))
    } catch (error) {
      set({ error: (error as Error).message })
    }
  },

  searchMemories: async (userId: string, keyword: string) => {
    set({ loading: true, error: null })
    try {
      const response = await fetch(
        `${API_BASE}/memory/search?userId=${userId}&keyword=${encodeURIComponent(keyword)}`
      )
      
      if (!response.ok) throw new Error('Failed to search memories')
      
      const memories = await response.json()
      set({ memories, loading: false })
      return memories
    } catch (error) {
      set({ error: (error as Error).message, loading: false })
      return []
    }
  },

  clearError: () => set({ error: null }),
}))
