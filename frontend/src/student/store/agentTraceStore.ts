import { create } from 'zustand'
import type { AgentId, AgentStepStatus } from '@/student/types/agent-events'

export interface AgentTraceStep {
  id: string
  agent: AgentId
  label: string
  status: AgentStepStatus
  detail?: string
  updatedAt: Date
}

interface AgentTraceState {
  steps: AgentTraceStep[]
  upsertStep: (step: Omit<AgentTraceStep, 'id' | 'updatedAt'> & { id?: string }) => void
  clearSteps: () => void
}

export const useAgentTraceStore = create<AgentTraceState>((set) => ({
  steps: [],

  upsertStep: (step) =>
    set((state) => {
      const id = step.id ?? `${step.agent}-${step.label}`
      const existing = state.steps.findIndex((s) => s.id === id)
      const entry: AgentTraceStep = {
        id,
        agent: step.agent,
        label: step.label,
        status: step.status,
        detail: step.detail,
        updatedAt: new Date(),
      }
      if (existing >= 0) {
        const steps = [...state.steps]
        steps[existing] = entry
        return { steps }
      }
      return { steps: [...state.steps, entry] }
    }),

  clearSteps: () => set({ steps: [] }),
}))
