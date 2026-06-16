import http from '@/api/http'

export interface PipelineStepResult {
  stepId: string
  agentId: string
  success: boolean
  durationMs: number
  summary: string
}

export interface PipelineTaskDto {
  taskId: string
  pipelineId: string
  pipelineName: string
  goal: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  stepCount: number
  completedSteps: number
  currentStep: string
  stepResults: PipelineStepResult[]
  errorMessage: string
  createdAt: string
  completedAt: string | null
}

export const pipelineTaskApi = {
  /** 查询单个任务状态 */
  getTask(taskId: string) {
    return http.get<{ code: number; data: PipelineTaskDto }>(`/pipeline/tasks/${taskId}`).then((r) => r.data.data)
  },

  /** 查询当前用户最近一次任务 */
  getLatest() {
    return http.get<{ code: number; data: PipelineTaskDto }>('/pipeline/tasks/latest').then((r) => r.data.data)
  },

  /** 查询当前用户所有任务 */
  list() {
    return http.get<{ code: number; data: PipelineTaskDto[] }>('/pipeline/tasks').then((r) => r.data.data)
  },
}
