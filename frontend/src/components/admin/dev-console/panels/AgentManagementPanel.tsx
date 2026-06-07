/**
 * 合并的 Agent 管理面板 — 左侧列表 + 右侧详情
 * 替代原来 9 个独立的 Agent 面板
 */
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getAgentStats, listSysConfig, saveSysConfig, testAgent, type AgentStatsResponse } from '@/api/admin/admin'
import http from '@/api/http'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

// ==================== Agent 定义 ====================

interface AgentFeature { label: string; status: 'done' | 'wip' | 'todo'; note: string }

interface AgentDef {
  id: string
  name: string
  logName: string
  category: string
  description: string
  aiSource: string
  features: AgentFeature[]
  testFields: { key: string; label: string; placeholder: string; type?: 'text' | 'textarea' | 'select'; options?: { label: string; value: string }[]; defaultValue?: string }[]
}

const AGENTS: AgentDef[] = [
  // 调度模块
  {
    id: 'orchestrator', name: '协调智能体', logName: 'orchestrator', category: '调度',
    description: '意图识别 → 路由 → 聚合',
    aiSource: 'LLM API — 意图分类',
    features: [
      { label: 'LLM 意图分类', status: 'done', note: '优先 LLM，降级关键词' },
      { label: '任务分发', status: 'done', note: 'Redis Streams 消息队列' },
    ],
    testFields: [{ key: 'message', label: '用户意图', placeholder: '如：我想学 Python 装饰器' }],
  },
  
  // 用户模块
  {
    id: 'profile', name: '画像智能体', logName: 'profile_agent', category: '用户',
    description: '分析用户学习画像',
    aiSource: 'LLM API — 画像分析',
    features: [
      { label: '学习画像分析', status: 'done', note: 'LLM 分析用户知识水平' },
    ],
    testFields: [{ key: 'userId', label: '用户ID', placeholder: '如：2' }],
  },
  
  // 学习模块
  {
    id: 'learningpath', name: '路径规划', logName: 'learning_path_agent', category: '学习',
    description: '知识图谱拓扑排序 + 动态生成',
    aiSource: 'LLM API — 路径规划',
    features: [
      { label: '知识图谱匹配', status: 'done', note: '31 知识点' },
      { label: '动态路径生成', status: 'done', note: '无匹配时 LLM 生成' },
    ],
    testFields: [{ key: 'goal', label: '学习目标', placeholder: '如：掌握 Python 装饰器' }],
  },
  {
    id: 'knowledgestate', name: '知识状态', logName: 'knowledge_state_agent', category: '学习',
    description: '追踪知识点掌握度',
    aiSource: '答题记录分析',
    features: [
      { label: '掌握度计算', status: 'done', note: '基于答题正确率' },
      { label: '薄弱点识别', status: 'done', note: '正确率<60%为薄弱' },
    ],
    testFields: [{ key: 'userId', label: '用户ID', placeholder: '如：2' }],
  },
  {
    id: 'spacedrepetition', name: '间隔复习', logName: 'spaced_repetition_agent', category: '学习',
    description: '基于遗忘曲线安排复习',
    aiSource: 'SM-2 算法',
    features: [
      { label: '复习调度', status: 'done', note: '间隔重复算法' },
    ],
    testFields: [{ key: 'userId', label: '用户ID', placeholder: '如：2' }],
  },
  {
    id: 'difficulty', name: '自适应难度', logName: 'difficulty_agent', category: '学习',
    description: '根据表现调整难度',
    aiSource: 'LLM API — 难度分析',
    features: [
      { label: '难度评估', status: 'done', note: '基于答题表现' },
    ],
    testFields: [{ key: 'userId', label: '用户ID', placeholder: '如：2' }],
  },
  {
    id: 'learningstyle', name: '学习风格', logName: 'learning_style_agent', category: '学习',
    description: '识别视觉/听觉/动手型',
    aiSource: '行为分析',
    features: [
      { label: '风格识别', status: 'done', note: '基于学习行为' },
    ],
    testFields: [{ key: 'userId', label: '用户ID', placeholder: '如：2' }],
  },
  {
    id: 'effect', name: '效果评估', logName: 'effect_agent', category: '学习',
    description: '薄弱点分析 → 路径调整',
    aiSource: 'LLM API（薄弱点分析）',
    features: [
      { label: '低分→插复习节点', status: 'done', note: '简单规则' },
      { label: 'LLM 薄弱点分析', status: 'done', note: 'analyzeWeakness()' },
    ],
    testFields: [{ key: 'userId', label: '用户ID', placeholder: '如：2' }],
  },
  
  // 内容模块
  {
    id: 'resource', name: '资源生成', logName: 'resource_agent', category: '内容',
    description: 'LLM 生成教学资源',
    aiSource: 'LLM API — 资源生成',
    features: [
      { label: '讲义文档', status: 'done', note: 'Markdown + 代码块' },
      { label: '练习题目', status: 'done', note: '选择+填空+编程' },
    ],
    testFields: [{ key: 'pathId', label: '路径ID', placeholder: '如：258' }],
  },
  {
    id: 'diagram', name: '图表生成', logName: 'diagram_agent', category: '内容',
    description: '生成 Mermaid 图表',
    aiSource: 'LLM API — 图表代码',
    features: [
      { label: '流程图', status: 'done', note: 'Mermaid 语法' },
      { label: '思维导图', status: 'done', note: '知识结构可视化' },
    ],
    testFields: [{ key: 'topic', label: '主题', placeholder: '如：Python 装饰器' }],
  },
  {
    id: 'summary', name: '总结生成', logName: 'summary_agent', category: '内容',
    description: '生成学习总结和复习材料',
    aiSource: 'LLM API — 总结生成',
    features: [
      { label: '知识点总结', status: 'done', note: '提炼核心要点' },
    ],
    testFields: [{ key: 'topic', label: '主题', placeholder: '如：Python 装饰器' }],
  },
  {
    id: 'content', name: '内容分析', logName: 'content_analysis_agent', category: '内容',
    description: '上传文档 → 提取知识点',
    aiSource: 'LLM API + ai-server Knowledge Base',
    features: [
      { label: '关键词匹配知识点', status: 'done', note: '结构化课程够用' },
      { label: 'LLM 提取', status: 'done', note: 'analyzeWithLlm()' },
    ],
    testFields: [{ key: 'message', label: '文档内容', type: 'textarea', placeholder: '粘贴文档内容' }],
  },
  {
    id: 'promptgen', name: '提示词生成', logName: 'prompt_generation', category: '内容',
    description: '根据用户意图，LLM 生成适合 AI 图片/视频的英文提示词，并判断媒体类型',
    aiSource: 'LLM API — 提示词优化',
    features: [
      { label: '意图→英文提示词', status: 'done', note: 'LLM 理解意图后生成详细 prompt' },
      { label: '自动判断媒体类型', status: 'done', note: '静态场景→图片，动态过程→视频' },
    ],
    testFields: [
      { key: 'intent', label: '用户意图', placeholder: '如：我想看 Python 装饰器的工作原理动画' },
      { key: 'mediaType', label: '媒体类型', placeholder: '选择媒体类型', type: 'select', defaultValue: 'auto',
        options: [
          { label: 'auto — 自动判断', value: 'auto' },
          { label: 'image — 图片', value: 'image' },
          { label: 'video — 视频', value: 'video' },
        ] },
    ],
  },
  {
    id: 'mediagen', name: '媒体生成', logName: 'media_generation', category: '内容',
    description: 'AI 图片生成 + Mermaid 图表 + AI 视频生成',
    aiSource: 'Agnes AI（免费）+ LLM API（Mermaid）',
    features: [
      { label: 'AI 图片生成', status: 'done', note: 'Agnes AI Image / SiliconFlow 可图' },
      { label: 'AI 视频生成', status: 'done', note: 'Agnes Video V2.0' },
      { label: 'Mermaid 图表', status: 'done', note: 'LLM 生成 + 前端渲染' },
      { label: '图片质量检查', status: 'wip', note: '待接入' },
    ],
    testFields: [
      { key: 'prompt', label: '提示词', placeholder: '直接输入英文提示词，如：A cute cat sitting on a desk' },
      { key: 'mediaType', label: '媒体类型', placeholder: '选择媒体类型', type: 'select', defaultValue: 'illustration',
        options: [
          { label: 'illustration — 图片', value: 'illustration' },
          { label: 'video — AI 视频', value: 'video' },
        ] },
    ],
  },
  
  // 质检模块
  {
    id: 'quality', name: '质量评估', logName: 'quality_agent', category: '质检',
    description: 'LLM 自检 + 敏感词',
    aiSource: 'LLM API（事实校验）+ 本地（敏感词检查）',
    features: [
      { label: '非空/格式检查', status: 'done', note: '基础校验' },
      { label: '敏感内容过滤', status: 'done', note: 'SensitiveFilter' },
    ],
    testFields: [{ key: 'content', label: '资源内容', type: 'textarea', placeholder: '粘贴需要质检的文本' }],
  },
  
  // 服务模块
  {
    id: 'qa', name: '答疑智能体', logName: 'intelligent_qa', category: '服务',
    description: '流式 RAG 问答',
    aiSource: 'ai-server WS（DeepTutor RAG）',
    features: [
      { label: '文本流式问答', status: 'done', note: 'WS → ai-server' },
      { label: '知识库检索', status: 'done', note: 'RAG 向量检索' },
    ],
    testFields: [{ key: 'message', label: '问题内容', placeholder: '如：Python 中 *args 和 **kwargs 区别？' }],
  },
  {
    id: 'recommendation', name: '个性化推荐', logName: 'recommendation_agent', category: '服务',
    description: '基于画像推荐资源',
    aiSource: 'LLM API — 推荐算法',
    features: [
      { label: '资源推荐', status: 'done', note: '基于学习画像' },
    ],
    testFields: [{ key: 'userId', label: '用户ID', placeholder: '如：2' }],
  },
  {
    id: 'assessment', name: '评估批改', logName: 'assessment_agent', category: '服务',
    description: '评估答案质量',
    aiSource: 'LLM API — 评估',
    features: [
      { label: '答案评分', status: 'done', note: '多维度评估' },
    ],
    testFields: [{ key: 'answer', label: '答案', type: 'textarea', placeholder: '粘贴待评估的答案' }],
  },
  {
    id: 'intervention', name: '学习干预', logName: 'intervention_agent', category: '服务',
    description: '发现问题主动干预',
    aiSource: 'LLM API — 干预策略',
    features: [
      { label: '问题检测', status: 'done', note: '识别学习困难' },
    ],
    testFields: [{ key: 'userId', label: '用户ID', placeholder: '如：2' }],
  },

]

const LLM_MODELS = [
  { label: 'gpt-4o-mini（默认）', value: 'gpt-4o-mini' },
  { label: 'gpt-4o', value: 'gpt-4o' },
  { label: 'DeepSeek Chat', value: 'deepseek-chat' },
  { label: 'Qwen Plus', value: 'qwen-plus' },
  { label: 'Agnes 2.0 Flash', value: 'agnes-2.0-flash' },
  { label: 'Agnes 1.5 Flash', value: 'agnes-1.5-flash' },
]

// ==================== 组件 ====================

function StatusBadge({ status }: { status: 'done' | 'wip' | 'todo' }) {
  const m = { done: { label: '✅ 已完成', cls: 'text-green-400' }, wip: { label: '🛠️ 进行中', cls: 'text-yellow-400' }, todo: { label: '❌ 未开始', cls: 'text-red-400' } }
  return <span className={`text-xs ${(m[status] ?? m.todo).cls}`}>{(m[status] ?? m.todo).label}</span>
}

function AgentDetail({ agent, stats, configMap, onSave, saving }: {
  agent: AgentDef; stats: AgentStatsResponse | undefined; configMap: Map<string, string>
  onSave: (key: string, value: string) => void; saving: boolean
}) {
  const [testValues, setTestValues] = useState<Record<string, string>>(() => {
    const init: Record<string, string> = {}
    agent.testFields.forEach(f => { init[f.key] = f.defaultValue ?? '' })
    return init
  })
  const [testResult, setTestResult] = useState<string | null>(null)
  const [testLoading, setTestLoading] = useState(false)

  const myStats = stats?.stats?.find(s => s.agent === agent.logName)
  const total = myStats?.total ?? 0
  const successRate = total > 0 ? Math.round((myStats!.success / total) * 100) : 0
  const modelKey = `agent.${agent.id}.model`
  const currentModel = configMap.get(modelKey) ?? 'gpt-4o-mini'

  const handleTest = async () => {
    const payload: Record<string, unknown> = {}
    Object.entries(testValues).forEach(([k, v]) => { if (v.trim()) payload[k] = v.trim() })
    if (!Object.keys(payload).length) return
    setTestLoading(true)
    setTestResult(null)
    try {
      let res: unknown
      if (agent.id === 'promptgen') {
        // 提示词生成智能体：调用 LLM 生成 prompt + 判断媒体类型
        const intent = (payload.intent as string) || ''
        const mediaType = (payload.mediaType as string) || 'auto'
        const r = await http.post<{ data: { prompt: string; mediaType: string; reason: string; success: boolean } }>(
          '/media/generate-prompt',
          { intent, mediaType },
        )
        res = r.data.data
      } else if (agent.id === 'mediagen') {
        // 媒体生成智能体直接调用 /api/media/test-* 接口
        const mediaType = (payload.mediaType as string) || 'illustration'
        const prompt = (payload.prompt as string) || ''
        if (!prompt) {
          setTestResult('请输入提示词')
          setTestLoading(false)
          return
        }
        if (mediaType === 'video') {
          const r = await http.post<{ data: { success: boolean; videoUrl: string; prompt: string } }>(
            '/media/test-video',
            { prompt },
          )
          res = r.data.data
        } else {
          const r = await http.post<{ data: { success: boolean; imageUrl: string; prompt: string } }>(
            '/media/test-image',
            { prompt },
          )
          res = r.data.data
        }
      } else {
        res = await testAgent(agent.logName, payload)
      }
      setTestResult(JSON.stringify(res, null, 2))
    } catch (e: unknown) {
      setTestResult(`请求失败: ${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setTestLoading(false)
    }
  }

  return (
    <div className="space-y-4">
      {/* 状态 */}
      <div className="rounded-lg border border-console-border bg-console-card p-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-sm font-medium text-console-text">{agent.name}</h3>
            <p className="text-xs text-console-muted">{agent.description}</p>
            <p className="mt-1 text-xs text-console-muted/60">{agent.aiSource}</p>
          </div>
          <span className="rounded bg-console-border/30 px-2 py-0.5 text-xs text-console-muted">
            {total > 0 ? '已调用' : '待调用'}
          </span>
        </div>
        <div className="mt-3 flex gap-4 text-xs text-console-muted">
          <span>调用: {total}</span>
          <span>成功: {myStats?.success ?? 0}</span>
          <span>失败: {myStats?.failed ?? 0}</span>
          <span>成功率: {successRate}%</span>
          {myStats?.avgDurationMs ? <span>均耗时: {myStats.avgDurationMs}ms</span> : null}
        </div>
      </div>

      {/* 能力清单 */}
      <div className="rounded-lg border border-console-border p-4">
        <h4 className="mb-3 text-xs font-medium uppercase tracking-wider text-console-muted">能力清单</h4>
        <div className="space-y-2">
          {agent.features.map(f => (
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

      {/* 模型选择 */}
      <div className="rounded-lg border border-console-border p-4">
        <h4 className="mb-3 text-xs font-medium uppercase tracking-wider text-console-muted">模型配置</h4>
        <div className="flex items-center justify-between text-sm text-console-text">
          <span>模型</span>
          <select
            value={currentModel}
            disabled={saving}
            onChange={e => onSave(modelKey, e.target.value)}
            className="rounded border border-console-border bg-console-card px-2 py-1 text-xs text-console-text"
          >
            {LLM_MODELS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        </div>
      </div>

      {/* 测试 */}
      <div className="rounded-lg border border-console-border p-4">
        <h4 className="mb-2 text-xs font-medium uppercase tracking-wider text-console-muted">发送测试</h4>
        <div className="mb-3 space-y-2">
          {agent.testFields.map(field => (
            <div key={field.key}>
              <label className="mb-1 block text-xs text-console-muted">{field.label}</label>
              {field.type === 'textarea' ? (
                <textarea
                  value={testValues[field.key] ?? ''}
                  onChange={e => setTestValues(p => ({ ...p, [field.key]: e.target.value }))}
                  placeholder={field.placeholder}
                  disabled={testLoading}
                  rows={3}
                  className="w-full rounded border border-console-border bg-console-card px-3 py-1.5 text-sm text-console-text placeholder:text-console-muted/40 resize-y"
                />
              ) : field.type === 'select' ? (
                <select
                  value={testValues[field.key] ?? field.defaultValue ?? ''}
                  disabled={testLoading}
                  onChange={e => setTestValues(p => ({ ...p, [field.key]: e.target.value }))}
                  className="w-full rounded border border-console-border bg-console-card px-3 py-1.5 text-sm text-console-text"
                >
                  {field.options?.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
              ) : (
                <input
                  type="text"
                  value={testValues[field.key] ?? ''}
                  onChange={e => setTestValues(p => ({ ...p, [field.key]: e.target.value }))}
                  onKeyDown={e => e.key === 'Enter' && handleTest()}
                  placeholder={field.placeholder}
                  disabled={testLoading}
                  className="w-full rounded border border-console-border bg-console-card px-3 py-1.5 text-sm text-console-text placeholder:text-console-muted/40"
                />
              )}
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
          <>
            {/* 图片预览 */}
            {(agent.id === 'mediagen' || agent.id === 'promptgen') && (() => {
              try {
                const parsed = JSON.parse(testResult)
                // promptgen 结果展示
                if (agent.id === 'promptgen' && parsed?.prompt) {
                  return (
                    <div className="mt-3 space-y-2">
                      <div className="rounded border border-console-border bg-console-card p-3">
                        <div className="mb-1 text-[11px] text-console-muted">生成的提示词</div>
                        <div className="text-sm text-console-text font-mono">{parsed.prompt}</div>
                      </div>
                      <div className="flex gap-2 text-xs">
                        <span className="rounded bg-blue-600/20 px-2 py-0.5 text-blue-400">{parsed.mediaType}</span>
                        <span className="text-console-muted">{parsed.reason}</span>
                      </div>
                    </div>
                  )
                }
                // mediagen 结果展示 - 图片
                const imageUrl = parsed?.imageUrl ?? parsed?.data?.imageUrl
                const videoUrl = parsed?.videoUrl ?? parsed?.data?.videoUrl
                if (imageUrl && typeof imageUrl === 'string') {
                  return (
                    <div className="mt-3">
                      <img
                        src={imageUrl}
                        alt={parsed?.prompt ?? '生成图片'}
                        onError={e => { (e.target as HTMLImageElement).style.display = 'none' }}
                        className="max-h-64 rounded border border-console-border object-contain"
                      />
                    </div>
                  )
                }
                if (videoUrl && typeof videoUrl === 'string') {
                  return (
                    <div className="mt-3">
                      <video
                        src={videoUrl}
                        controls
                        className="max-h-64 rounded border border-console-border"
                      />
                    </div>
                  )
                }
              } catch { /* not JSON, skip preview */ }
              return null
            })()}
            <pre className="mt-3 max-h-48 overflow-auto rounded border border-console-border bg-console-card p-3 text-xs text-console-text whitespace-pre-wrap">
              {testResult}
            </pre>
          </>
        )}
      </div>
    </div>
  )
}

export default function AgentManagementPanel() {
  const [selectedId, setSelectedId] = useState(AGENTS[0].id)
  const [expandedCats, setExpandedCats] = useState<Set<string>>(new Set(['调度', '用户', '学习', '内容', '质检', '服务']))
  const toggleCat = (cat: string) => {
    setExpandedCats(prev => {
      const next = new Set(prev)
      if (next.has(cat)) next.delete(cat)
      else next.add(cat)
      return next
    })
  }
  const { data: stats } = useQuery({ queryKey: ['admin', 'agent-stats'], queryFn: getAgentStats, refetchInterval: 15_000 })
  const { data: configs = [] } = useQuery({ queryKey: ['admin', 'sys-config'], queryFn: listSysConfig, refetchInterval: 30_000 })
  const qc = useQueryClient()
  const saveMutation = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) => saveSysConfig(key, value),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'sys-config'] }),
  })

  const configMap = new Map<string, string>()
  configs.forEach(c => configMap.set(c.configKey, c.configValue))

  const selected = AGENTS.find(a => a.id === selectedId) ?? AGENTS[0]

  return (
    <div className="flex gap-4 h-[calc(100vh-8rem)]">
      {/* 左侧列表 */}
      <Card className="w-56 shrink-0 overflow-y-auto">
        <CardHeader className="pb-2">
          <CardTitle className="text-xs">Agent 列表</CardTitle>
        </CardHeader>
        <CardContent className="space-y-0 p-2">
          {(() => {
            const categories = ['调度', '用户', '学习', '内容', '质检', '服务']
            return categories.map(cat => {
              const agentsInCat = AGENTS.filter(a => a.category === cat)
              if (agentsInCat.length === 0) return null
              const isExpanded = expandedCats.has(cat)
              return (
                <div key={cat} className="mb-1">
                  <button
                    onClick={() => toggleCat(cat)}
                    className="w-full flex items-center justify-between px-2 py-1.5 text-[11px] font-bold text-console-muted hover:text-console-text hover:bg-console-border/20 rounded-md transition-colors"
                  >
                    <span className="uppercase tracking-wider">{cat}模块</span>
                    <svg
                      className={`w-3 h-3 transition-transform ${isExpanded ? 'rotate-90' : ''}`}
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                    </svg>
                  </button>
                  {isExpanded && (
                    <div className="ml-1 space-y-0.5">
                      {agentsInCat.map(a => {
                        const s = stats?.stats?.find(x => x.agent === a.logName)
                        const total = s?.total ?? 0
                        return (
                          <button
                            key={a.id}
                            onClick={() => setSelectedId(a.id)}
                            className={`w-full text-left rounded-md px-3 py-1.5 text-sm transition-colors ${
                              selectedId === a.id
                                ? 'bg-console-blue/10 text-console-blue'
                                : 'text-console-text hover:bg-console-border/30'
                            }`}
                          >
                            <div className="font-medium text-[13px]">{a.name}</div>
                            <div className="text-[10px] text-console-muted">
                              {total > 0 ? `${total} 次调用` : '未调用'}
                            </div>
                          </button>
                        )
                      })}
                    </div>
                  )}
                </div>
              )
            })
          })()}
        </CardContent>
      </Card>

      {/* 右侧详情 */}
      <div className="flex-1 overflow-y-auto">
        <AgentDetail
          agent={selected}
          stats={stats}
          configMap={configMap}
          onSave={(k, v) => saveMutation.mutate({ key: k, value: v })}
          saving={saveMutation.isPending}
        />
      </div>
    </div>
  )
}
