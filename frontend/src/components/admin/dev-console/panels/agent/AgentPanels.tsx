/**
 * 8 个智能体的控制面板 — 每个智能体自带模型配置
 *
 * 能力清单：静态数据
 * 运行统计：GET /api/admin/agent-stats
 * 配置读写：GET+PUT /api/admin/config
 * 模型配置：每个 Agent 独立 model / host / api-key（sys_config 存储）
 */
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getAgentStats, listSysConfig, saveSysConfig, testAgent, type AgentStatsResponse } from '@/api/admin/admin'

// ==================== 可选的模型列表 ====================

const LLM_MODELS = [
  { label: 'DeepSeek Chat（默认）', value: 'deepseek-chat' },
  { label: 'DeepSeek V3', value: 'deepseek-v3' },
  { label: 'GPT-4o', value: 'gpt-4o' },
  { label: 'GPT-4o-mini', value: 'gpt-4o-mini' },
  { label: 'Qwen Plus', value: 'qwen-plus' },
  { label: 'GLM-4', value: 'glm-4' },
]

// ==================== 静态 Agent 定义 ====================

interface AgentFeature { label: string; status: 'done' | 'wip' | 'todo'; note: string }

interface AgentConfigItem {
  configKey: string
  label: string
  type: 'toggle' | 'select' | 'model'
  options?: { label: string; value: string }[]
  defaultValue: string
  /** model 类型时显示为模型选择器，options 是可选模型列表 */
}

interface AgentDef {
  id: string
  name: string
  description: string
  agentLogName: string
  aiSource: string  // 说明该 Agent 使用的 AI 来源
  features: AgentFeature[]
  configItems: AgentConfigItem[]
}

const COMMON_MODELS: { label: string; value: string }[] = LLM_MODELS

const AGENT_DEFS: Record<string, AgentDef> = {
  orchestrator: {
    id: 'orchestrator', name: '协调智能体 Orchestrator', agentLogName: 'orchestrator',
    description: '意图识别 → 路由 → Agent 调度',
    aiSource: '🔗 LLM API — 意图分类（91% 准确率）',
    features: [
      { label: 'LLM 意图分类', status: 'done', note: '支持 14 种意图' },
      { label: '上下文记忆', status: 'done', note: '多轮对话理解' },
      { label: 'Agent 调用链', status: 'done', note: '路径→推荐自动触发' },
    ],
    configItems: [
      { configKey: 'agent.orchestrator.llm_intent_enabled', label: '启用 LLM 意图分类', type: 'toggle', defaultValue: 'true' },
      { configKey: 'agent.orchestrator.model', label: '模型', type: 'model', options: COMMON_MODELS, defaultValue: 'deepseek-chat' },
    ],
  },
  qa: {
    id: 'qa', name: '答疑智能体 QaAgent', agentLogName: 'intelligent_qa',
    description: '流式 RAG 问答 + Mermaid 流程图',
    aiSource: '🔗 ai-server WS（DeepTutor 内部 RAG）',
    features: [
      { label: '文本流式问答', status: 'done', note: 'WS → ai-server' },
      { label: 'Mermaid 流程图', status: 'done', note: 'CDN mermaid 11 + StreamingMessage 渲染' },
      { label: '质量校验前置', status: 'todo', note: '答案经质检再返回' },
    ],
    configItems: [
      { configKey: 'agent.qa.mermaid_enabled', label: '启用 Mermaid 生成', type: 'toggle', defaultValue: 'true' },
    ],
  },
  learningpath: {
    id: 'learningpath', name: '路径规划 LearningPath', agentLogName: 'learning_path',
    description: 'BFS 图谱遍历 + LLM 个性化排序',
    aiSource: '🔗 LLM API — 个性化排序',
    features: [
      { label: 'BFS 路径骨架', status: 'done', note: '31 知识点，依赖拓扑排序' },
      { label: 'LLM 个性化排序', status: 'done', note: 'sortNodesWithLlm() 读画像+LLM排序' },
      { label: '资源类型关联', status: 'todo', note: '每步骤指定 lesson/quiz/code' },
    ],
    configItems: [
      { configKey: 'agent.learningpath.llm_enabled', label: '启用 LLM 个性化', type: 'toggle', defaultValue: 'false' },
      { configKey: 'agent.learningpath.model', label: '模型', type: 'model', options: COMMON_MODELS, defaultValue: 'gpt-4o-mini' },
      { configKey: 'agent.learningpath.max_steps', label: '最大路径步数', type: 'select', defaultValue: '5',
        options: [{ label: '3 步', value: '3' }, { label: '5 步', value: '5' }, { label: '7 步', value: '7' }] },
    ],
  },
  resourcefacade: {
    id: 'resourcefacade', name: '资源生成 ResourceFacade', agentLogName: 'resource_generation',
    description: 'LLM 生成 5 种教学资源，失败模板兜底',
    aiSource: '🔗 LLM API — 资源生成',
    features: [
      { label: '讲义文档', status: 'done', note: 'Markdown + 代码块' },
      { label: '练习题目', status: 'done', note: '选择+填空+编程' },
      { label: '代码案例', status: 'done', note: '完整可运行代码' },
      { label: '思维导图', status: 'wip', note: 'Markdown 列表已生成，待 Mermaid 语法输出' },
      { label: '拓展阅读', status: 'done', note: 'LLM 生成 + 模板兜底 + 持久化' },
    ],
    configItems: [
      { configKey: 'agent.resourcefacade.model', label: '模型', type: 'model', options: COMMON_MODELS, defaultValue: 'gpt-4o-mini' },
      { configKey: 'agent.resourcefacade.types', label: '生成类型', type: 'select', defaultValue: 'all',
        options: [
          { label: '全部（讲义+题目+代码+思维导图+拓展阅读）', value: 'all' },
          { label: '仅讲义+题目+代码', value: 'basic' },
          { label: '仅思维导图+拓展阅读', value: 'extended' },
        ] },
    ],
  },
  learnerprofile: {
    id: 'learnerprofile', name: '学生画像 LearnerProfile', agentLogName: 'learner_profile',
    description: 'LLM 从对话抽取 6 维度 + ai-server Memory',
    aiSource: '🔗 LLM API（抽取）+ 🔗 ai-server Memory（对话摘要）',
    features: [
      { label: '答题记录画像', status: 'done', note: '6 维度粗算' },
      { label: 'LLM 对话抽取', status: 'done', note: '规则+ProfileExtractor 双通道' },
      { label: 'ai-server Memory', status: 'wip', note: '对话摘要存储' },
    ],
    configItems: [
      { configKey: 'agent.learnerprofile.model', label: '模型', type: 'model', options: COMMON_MODELS, defaultValue: 'gpt-4o-mini' },
      { configKey: 'agent.learnerprofile.llm_extraction', label: '启用 LLM 抽取', type: 'toggle', defaultValue: 'false' },
      { configKey: 'agent.learnerprofile.extraction_freq', label: '抽取频次', type: 'select', defaultValue: '3',
        options: [{ label: '每 3 条对话', value: '3' }, { label: '每 5 条对话', value: '5' }, { label: '每 10 条对话', value: '10' }] },
    ],
  },
  qualityassessment: {
    id: 'qualityassessment', name: '质量评估 QualityAssessment', agentLogName: 'quality_assessment',
    description: 'LLM 自检 + 敏感词 + 学术规范 + 重试',
    aiSource: '🔗 LLM API（事实校验）+ 🏗️ 本地（敏感词+学术检查）',
    features: [
      { label: '非空/格式检查', status: 'done', note: '当前仅此' },
      { label: 'LLM 事实性校验', status: 'wip', note: 'assessFull() 已实现，依赖开关' },
      { label: '敏感内容过滤', status: 'done', note: 'SensitiveFilter 词库匹配' },
      { label: '学术规范性检查', status: 'done', note: 'AcademicChecker 正则检测' },
    ],
    configItems: [
      { configKey: 'agent.qualityassessment.model', label: '校验模型', type: 'model', options: COMMON_MODELS, defaultValue: 'gpt-4o-mini' },
      { configKey: 'agent.qualityassessment.llm_check', label: '启用 LLM 事实校验', type: 'toggle', defaultValue: 'false' },
    ],
  },
  contentanalyzer: {
    id: 'contentanalyzer', name: '内容分析 ContentAnalyzer', agentLogName: 'content_analyzer',
    description: '上传文档 → 提取知识点 → 关联图谱',
    aiSource: '🔗 ai-server Knowledge Base',
    features: [
      { label: '关键词匹配知识点', status: 'done', note: '结构化课程够用' },
      { label: '上传入知识库', status: 'done', note: 'ai-server RAG' },
      { label: 'LLM 提取（可选）', status: 'done', note: 'analyzeWithLlm() 优先LLM降级子串' },
    ],
    configItems: [
      { configKey: 'agent.contentanalyzer.extraction_method', label: '提取方式', type: 'select', defaultValue: 'keyword',
        options: [{ label: '关键词匹配', value: 'keyword' }, { label: 'LLM 提取', value: 'llm' }] },
    ],
  },
  effectassessment: {
    id: 'effectassessment', name: '效果评估 EffectAssessment', agentLogName: 'effect_assessment',
    description: '行为追踪 + LLM 薄弱点分析 → 路径调整',
    aiSource: '🔗 LLM API（薄弱点分析）',
    features: [
      { label: '低分→插复习节点', status: 'done', note: '简单规则' },
      { label: 'LLM 薄弱点分析', status: 'done', note: 'analyzeWeakness() 行为数据+LLM' },
      { label: '行为追踪', status: 'todo', note: '前端埋点：点击+停留时间' },
    ],
    configItems: [
      { configKey: 'agent.effectassessment.model', label: '分析模型', type: 'model', options: COMMON_MODELS, defaultValue: 'gpt-4o-mini' },
      { configKey: 'agent.effectassessment.llm_analysis', label: '启用 LLM 分析', type: 'toggle', defaultValue: 'false' },
    ],
  },
  mediagen: {
    id: 'mediagen', name: '媒体生成 MediaGeneration', agentLogName: 'media_generation',
    description: 'AI 图片生成 + Mermaid 图表',
    aiSource: '🔗 SiliconFlow 可图（免费）+ 🔗 LLM API（Mermaid）',
    features: [
      { label: 'AI 图片生成', status: 'done', note: 'SiliconFlow 可图 Kolors' },
      { label: 'Mermaid 图表', status: 'done', note: 'LLM 生成 + 前端渲染' },
      { label: '图片质量检查', status: 'wip', note: '待接入' },
    ],
    configItems: [
      { configKey: 'agent.mediagen.image_provider', label: '生图提供商', type: 'select', defaultValue: 'siliconflow',
        options: [
          { label: 'Mock（占位符）', value: 'mock' },
          { label: 'SiliconFlow 可图（免费）', value: 'siliconflow' },
          { label: 'DALL·E 3', value: 'dalle3' },
          { label: 'Stable Diffusion 3', value: 'sd3' },
        ] },
      { configKey: 'agent.mediagen.model', label: '生图模型', type: 'select', defaultValue: 'Kwai-Kolors/Kolors',
        options: [
          { label: 'Kolors（可图）', value: 'Kwai-Kolors/Kolors' },
          { label: 'Stable Diffusion XL', value: 'stabilityai/stable-diffusion-xl-base-1.0' },
        ] },
      { configKey: 'agent.mediagen.host', label: 'API 地址', type: 'select', defaultValue: 'https://api.siliconflow.cn/v1',
        options: [
          { label: 'SiliconFlow', value: 'https://api.siliconflow.cn/v1' },
          { label: 'OpenAI', value: 'https://api.openai.com/v1' },
        ] },
      { configKey: 'agent.mediagen.api_key', label: 'API Key', type: 'toggle', defaultValue: 'true' },
    ],
  },
}

// ==================== Hooks ====================

function useAgentRuntime() {
  return useQuery({ queryKey: ['admin', 'agent-stats'], queryFn: getAgentStats, refetchInterval: 15_000 })
}
function useAgentConfigs() {
  return useQuery({ queryKey: ['admin', 'sys-config'], queryFn: listSysConfig, refetchInterval: 30_000 })
}
function useSaveConfig() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) => saveSysConfig(key, value),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'sys-config'] }),
  })
}

// ==================== 每个 Agent 的测试表单字段定义 ====================

interface TestFieldDef {
  key: string
  label: string
  placeholder: string
  type?: 'text' | 'textarea' | 'select'
  options?: { label: string; value: string }[]
  defaultValue?: string
  /** 额外说明 */
  hint?: string
}

const AGENT_TEST_FIELDS: Record<string, TestFieldDef[]> = {
  orchestrator: [
    { key: 'message', label: '用户意图', placeholder: '如：我想学 Python 装饰器', hint: 'Orchestrator 会做意图分类并路由到对应 Agent' },
  ],
  qa_agent: [
    { key: 'message', label: '问题内容', placeholder: '如：Python 中 *args 和 **kwargs 有什么区别？', hint: '触发 RAG 问答，返回流式文本回答' },
  ],
  learningpath: [
    { key: 'goal', label: '学习目标', placeholder: '如：掌握 Python 装饰器', hint: 'BFS 遍历知识图谱 + LLM 排序生成路径' },
    { key: 'currentKpId', label: '当前知识点（可选）', placeholder: '如：python_decorator', hint: '从哪个知识点开始规划，留空从头开始' },
  ],
  resourcefacade: [
    { key: 'kpId', label: '知识点 ID', placeholder: '如：python_decorator', hint: '要生成资源的知识点' },
    { key: 'resourceType', label: '资源类型', type: 'select', defaultValue: 'LESSON',
      options: [
        { label: 'LESSON — 讲义文档', value: 'LESSON' },
        { label: 'QUIZ — 练习题目', value: 'QUIZ' },
        { label: 'CODE_CASE — 代码案例', value: 'CODE_CASE' },
        { label: 'ILLUSTRATION — 思维导图', value: 'ILLUSTRATION' },
        { label: 'EXTENSION_READING — 拓展阅读', value: 'EXTENSION_READING' },
      ] },
  ],
  learnerprofile: [
    { key: 'message', label: '对话内容（用于 LLM 抽取画像）', placeholder: '如：我觉得函数式编程太难了，更喜欢面向对象的方式', hint: '从对话中提取 6 维度画像' },
  ],
  qualityassessment: [
    { key: 'content', label: '待校验内容', type: 'textarea',
      placeholder: '粘贴需要质检的文本，如讲义或题目内容',
      hint: '检查格式、敏感词、学术规范、事实性' },
  ],
  contentanalyzer: [
    { key: 'message', label: '文档内容 / 关键词', type: 'textarea',
      placeholder: '粘贴文档内容或输入关键词，如：装饰器 闭包 Python',
      hint: '提取知识点并关联知识图谱' },
  ],
  effectassessment: [
    { key: 'kpId', label: '知识点 ID', placeholder: '如：python_decorator' },
    { key: 'score', label: '答题分数', placeholder: '如：60', type: 'select', defaultValue: '40',
      options: [
        { label: '40 分（不及格，触发薄弱分析）', value: '40' },
        { label: '60 分（及格）', value: '60' },
        { label: '80 分（良好）', value: '80' },
        { label: '100 分（满分）', value: '100' },
      ] },
    { key: 'correct', label: '正确题数', placeholder: '如：2', defaultValue: '2' },
    { key: 'total', label: '总题数', placeholder: '如：5', defaultValue: '5' },
  ],
  mediagen: [
    { key: 'kpId', label: '知识点 ID', placeholder: '如：python_decorator' },
    { key: 'mediaType', label: '媒体类型', type: 'select', defaultValue: 'illustration',
      options: [
        { label: 'illustration — 示意图', value: 'illustration' },
        { label: 'mindmap — 思维导图', value: 'mindmap' },
      ] },
  ],
}

// ==================== 渲染组件 ====================

function StatusBadge({ status }: { status: 'done' | 'wip' | 'todo' }) {
  const m: Record<string, { label: string; cls: string }> = {
    done: { label: '✅ 已完成', cls: 'text-green-400' },
    wip: { label: '🛠️ 进行中', cls: 'text-yellow-400' },
    todo: { label: '❌ 未开始', cls: 'text-red-400' },
  }
  return <span className={`text-xs ${(m[status] ?? m.todo).cls}`}>{(m[status] ?? m.todo).label}</span>
}

function AgentDetailView({
  def, stats, configMap, onSave, saving,
}: {
  def: AgentDef
  stats: AgentStatsResponse | undefined
  configMap: Map<string, string>
  onSave: (key: string, value: string) => void
  saving: boolean
}) {
  const testFields = AGENT_TEST_FIELDS[def.agentLogName] ?? [{ key: 'message', label: '消息', placeholder: '输入测试消息' }]
  const [testValues, setTestValues] = useState<Record<string, string>>(() => {
    const init: Record<string, string> = {}
    testFields.forEach((f) => { init[f.key] = f.defaultValue ?? '' })
    return init
  })
  const [testResult, setTestResult] = useState<string | null>(null)
  const [testLoading, setTestLoading] = useState(false)

  const myStats = stats?.stats?.find((s) => s.agent === def.agentLogName)
  const totalCalls = myStats?.total ?? 0
  const successRate = totalCalls > 0 ? Math.round((myStats.success / totalCalls) * 100) : 0
  const status = totalCalls > 0 ? 'online' : 'idle'
  const statusDot = status === 'online' ? 'bg-green-500' : 'bg-yellow-500'

  const handleTest = async () => {
    const payload: Record<string, unknown> = {}
    Object.entries(testValues).forEach(([k, v]) => {
      if (v.trim() !== '') payload[k] = v.trim()
    })
    if (Object.keys(payload).length === 0) return
    setTestLoading(true)
    setTestResult(null)
    try {
      const res = await testAgent(def.agentLogName, payload)
      setTestResult(JSON.stringify(res, null, 2))
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e)
      setTestResult(`请求失败: ${msg}`)
    } finally {
      setTestLoading(false)
    }
  }

  return (
    <div className="space-y-4" data-agent={def.id}>
      {/* 状态卡片 */}
      <div className="rounded-lg border border-console-border bg-console-card p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className={`inline-block h-2.5 w-2.5 rounded-full ${statusDot}`} />
            <div>
              <h3 className="text-sm font-medium text-console-text">{def.name}</h3>
              <p className="text-xs text-console-muted">{def.description}</p>
              <p className="mt-1 text-xs text-console-muted/60">{def.aiSource}</p>
            </div>
          </div>
          <span className="rounded bg-console-border/30 px-2 py-0.5 text-xs text-console-muted">
            {status === 'online' ? '运行中' : '待调用'}
          </span>
        </div>
        <div className="mt-3 flex gap-4 text-xs text-console-muted">
          <span>调用: {totalCalls}</span>
          <span>成功: {myStats?.success ?? 0}</span>
          <span>失败: {myStats?.failed ?? 0}</span>
          <span>成功率: {successRate}%</span>
        </div>
      </div>

      {/* 能力清单 */}
      <div className="rounded-lg border border-console-border p-4">
        <h4 className="mb-3 text-xs font-medium uppercase tracking-wider text-console-muted">能力清单</h4>
        <div className="space-y-2">
          {def.features.map((f) => (
            <div key={f.label} className="flex items-start justify-between border-b border-console-border/30 pb-1.5 last:border-0">
              <div>
                <span className="text-sm text-console-text">{f.label}</span>
                <p className="text-xs text-console-muted">{f.note}</p>
              </div>
              <StatusBadge status={f.status} />
            </div>
          ))}
        </div>
      </div>

      {/* 配置 + 模型 */}
      <div className="rounded-lg border border-console-border p-4">
        <h4 className="mb-3 text-xs font-medium uppercase tracking-wider text-console-muted">配置 & 模型</h4>
        <div className="space-y-3">
          {def.configItems.map((cfg) => {
            const currentVal = configMap.get(cfg.configKey) ?? cfg.defaultValue
            return (
              <div key={cfg.configKey} className="flex items-center justify-between text-sm text-console-text">
                <span>{cfg.label}</span>

                {cfg.type === 'toggle' ? (
                  <button
                    type="button"
                    disabled={saving}
                    onClick={() => onSave(cfg.configKey, currentVal === 'true' ? 'false' : 'true')}
                    className={`relative h-5 w-9 rounded-full transition-colors ${
                      currentVal === 'true' ? 'bg-green-500' : 'bg-console-border'
                    }`}
                  >
                    <span className={`absolute left-0.5 top-0.5 h-4 w-4 rounded-full bg-white transition-transform ${
                      currentVal === 'true' ? 'translate-x-4' : 'translate-x-0'
                    }`} />
                  </button>
                ) : cfg.type === 'model' ? (
                  <div className="flex items-center gap-2">
                    <select
                      value={currentVal}
                      disabled={saving}
                      onChange={(e) => onSave(cfg.configKey, e.target.value)}
                      className="rounded border border-console-border bg-console-card px-2 py-1 text-xs text-console-text"
                    >
                      {cfg.options?.map((opt) => (
                        <option key={opt.value} value={opt.value}>{opt.label}</option>
                      ))}
                    </select>
                  </div>
                ) : (
                  <select
                    value={currentVal}
                    disabled={saving}
                    onChange={(e) => onSave(cfg.configKey, e.target.value)}
                    className="rounded border border-console-border bg-console-card px-2 py-1 text-xs text-console-text"
                  >
                    {cfg.options?.map((opt) => (
                      <option key={opt.value} value={opt.value}>{opt.label}</option>
                    ))}
                  </select>
                )}
              </div>
            )
          })}
        </div>
      </div>

      {/* 发送测试 — 每个 Agent 专属表单 */}
      <div className="rounded-lg border border-console-border p-4">
        <h4 className="mb-1 text-xs font-medium uppercase tracking-wider text-console-muted">发送测试</h4>
        <div className="mb-3 space-y-3">
          {testFields.map((field) => (
            <div key={field.key}>
              <label className="mb-1 block text-xs text-console-muted">{field.label}</label>
              {field.type === 'textarea' ? (
                <textarea
                  value={testValues[field.key] ?? ''}
                  onChange={(e) => setTestValues((prev) => ({ ...prev, [field.key]: e.target.value }))}
                  placeholder={field.placeholder}
                  disabled={testLoading}
                  rows={4}
                  className="w-full rounded border border-console-border bg-console-card px-3 py-1.5 text-sm text-console-text placeholder:text-console-muted/40 resize-y"
                />
              ) : field.type === 'select' ? (
                <select
                  value={testValues[field.key] ?? field.defaultValue ?? ''}
                  disabled={testLoading}
                  onChange={(e) => setTestValues((prev) => ({ ...prev, [field.key]: e.target.value }))}
                  className="w-full rounded border border-console-border bg-console-card px-3 py-1.5 text-sm text-console-text"
                >
                  {field.options?.map((opt) => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              ) : (
                <input
                  type="text"
                  value={testValues[field.key] ?? ''}
                  onChange={(e) => setTestValues((prev) => ({ ...prev, [field.key]: e.target.value }))}
                  onKeyDown={(e) => e.key === 'Enter' && handleTest()}
                  placeholder={field.placeholder}
                  disabled={testLoading}
                  className="w-full rounded border border-console-border bg-console-card px-3 py-1.5 text-sm text-console-text placeholder:text-console-muted/40"
                />
              )}
              {field.hint && <p className="mt-0.5 text-[10px] text-console-muted/50">{field.hint}</p>}
            </div>
          ))}
        </div>
        <button
          onClick={handleTest}
          disabled={testLoading}
          className="w-full rounded bg-blue-600 px-4 py-1.5 text-sm text-white hover:bg-blue-500 disabled:opacity-50"
        >
          {testLoading ? '执行中...' : '发送测试'}
        </button>
        {testResult && (
          <pre className="mt-3 max-h-60 overflow-auto rounded border border-console-border bg-console-card p-3 text-xs text-console-text whitespace-pre-wrap">
            {testResult}
          </pre>
        )}
      </div>
    </div>
  )
}

// ==================== 每个 Agent 一个 export ====================

function AgentView({ agentId }: { agentId: string }) {
  const { data: stats } = useAgentRuntime()
  const { data: configs = [] } = useAgentConfigs()
  const saveMutation = useSaveConfig()
  const def = AGENT_DEFS[agentId]
  const configMap = new Map<string, string>()
  configs.forEach((c) => configMap.set(c.configKey, c.configValue))

  if (!def) return <div className="text-console-muted">未知智能体: {agentId}</div>
  return (
    <AgentDetailView def={def} stats={stats} configMap={configMap}
      onSave={(k, v) => saveMutation.mutate({ key: k, value: v })}
      saving={saveMutation.isPending} />
  )
}

export function AgentOrchestratorPanel() { return <AgentView agentId="orchestrator" /> }
export function AgentQaPanel() { return <AgentView agentId="qa" /> }
export function AgentLearningPathPanel() { return <AgentView agentId="learningpath" /> }
export function AgentResourceFacadePanel() { return <AgentView agentId="resourcefacade" /> }
export function AgentLearnerProfilePanel() { return <AgentView agentId="learnerprofile" /> }
export function AgentQualityAssessmentPanel() { return <AgentView agentId="qualityassessment" /> }
export function AgentContentAnalyzerPanel() { return <AgentView agentId="contentanalyzer" /> }
export function AgentEffectAssessmentPanel() { return <AgentView agentId="effectassessment" /> }
export function AgentMediaGenPanel() { return <AgentView agentId="mediagen" /> }
