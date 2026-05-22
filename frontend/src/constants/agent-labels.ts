import type { AgentId } from '@/types/agent-events'

export const AGENT_LABELS: Record<AgentId, string> = {
  orchestrator: '协调调度',
  content_analyzer: '内容分析',
  path_planner: '路径规划',
  resource_facade: '资源生成',
  quality_assessment: '质量评估',
  learner_profile: '学习画像',
  qa_agent: '智能答疑',
  effect_assessment: '效果评估',
}
