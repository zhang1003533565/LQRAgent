import type { AgentId } from '@/utils/types/agent-events'

export const AGENT_LABELS: Record<AgentId, string> = {
  orchestrator: '协调调度',
  profile_agent: '画像智能体',
  learning_path_agent: '路径规划',
  resource_agent: '资源生成',
  quality_agent: '质量评估',
  effect_agent: '效果评估',
  qa_agent: '智能答疑',
  content_analysis_agent: '内容分析',
  intelligent_qa: '智能答疑',
  learner_profile: '学习画像',
}
