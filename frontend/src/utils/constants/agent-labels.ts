import type { AgentId } from '@/utils/types/agent-events'

/** Pipeline 步骤 ID → 中文展示名 */
export const STEP_LABELS: Record<string, string> = {
  profile: '获取画像',
  path_gen: '生成路径',
  resources: '生成资源',
  effect: '效果评估',
  quality: '质量检查',
  resource: '资源生成',
  diagram: '图表生成',
  media_gen: '媒体生成',
  assessment: '评估批改',
  summary: '总结生成',
  recommendation: '推荐',
  intervention: '学习干预',
}

export const AGENT_LABELS: Record<AgentId, string> = {
  // 调度
  orchestrator: '协调调度',
  
  // 用户理解层
  profile_agent: '画像智能体',
  learner_profile: '学习画像',
  
  // 学习科学层
  learning_path_agent: '路径规划',
  knowledge_state_agent: '知识状态',
  spaced_repetition_agent: '间隔复习',
  difficulty_agent: '自适应难度',
  learning_style_agent: '学习风格',
  
  // 内容生成层
  resource_agent: '资源生成',
  lesson_agent: '讲义生成',
  quiz_agent: '练习题生成',
  code_agent: '代码生成',
  diagram_agent: '图表生成',
  summary_agent: '总结生成',
  video_agent: '视频生成',
  
  // 质量保障层
  quality_agent: '质量评估',
  content_quality_agent: '内容质量',
  pedagogy_quality_agent: '教学质量',
  fact_check_agent: '事实检查',
  
  // 智能服务层
  qa_agent: '智能答疑',
  intelligent_qa: '智能答疑',
  recommendation_agent: '个性化推荐',
  assessment_agent: '评估批改',
  intervention_agent: '学习干预',
  motivation_agent: '激励系统',
  
  // 内容分析
  content_analysis_agent: '内容分析',
  content_analyzer: '内容分析',
}
