import http from '@/api/http'

export interface AgentTestResult {
  success: boolean
  route?: string
  response?: string
  agent?: string
  durationMs?: number
  content?: string
  error?: string
  executions?: unknown
}

export interface PathTestResult {
  success: boolean
  pathId?: number
  goal?: string
  nodes?: unknown[]
  planDescription?: string
  durationMs?: number
  error?: string
}

export interface ResourceTestResult {
  success: boolean
  resourceId?: number
  kpId?: string
  resourceType?: string
  title?: string
  content?: string
  existingCount?: number
  durationMs?: number
  error?: string
}

export interface QuizTestResult {
  success: boolean
  message?: string
  kpId?: string
  score?: number
  correct?: boolean
  error?: string
}

/**
 * 通用Agent测试（通过Orchestrator路由）
 */
export async function testAgent(message: string): Promise<AgentTestResult> {
  const res = await http.post<AgentTestResult>('/test/agent', { message })
  return res.data
}

/**
 * 直接测试QA Agent（ReAct模式）
 */
export async function testQaAgent(message: string): Promise<AgentTestResult> {
  const res = await http.post<AgentTestResult>('/test/qa-agent', { message })
  return res.data
}

/**
 * 测试学习路径生成
 */
export async function testPath(goal: string, currentKpId?: string): Promise<PathTestResult> {
  const res = await http.post<PathTestResult>('/test/path', {
    goal,
    currentKpId: currentKpId || '',
  })
  return res.data
}

/**
 * 测试资源生成
 */
export async function testResource(kpId: string, resourceType = 'LESSON'): Promise<ResourceTestResult> {
  const res = await http.post<ResourceTestResult>('/test/resource', {
    kpId,
    resourceType,
  })
  return res.data
}

/**
 * 模拟答题提交与效果评估
 */
export async function testQuizSubmit(
  kpId: string,
  score: number,
  correct: boolean,
): Promise<QuizTestResult> {
  const res = await http.post<QuizTestResult>('/test/quiz-submit', {
    kpId,
    score,
    correct,
  })
  return res.data
}
