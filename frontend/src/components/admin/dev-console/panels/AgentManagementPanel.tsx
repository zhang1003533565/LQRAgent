/**
 * 合并的 Agent 管理面板 — 左侧列表 + 右侧详情
 * 替代原来 9 个独立的 Agent 面板
 */
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getAgentStats, listSysConfig, saveSysConfig, testAgent, type AgentStatsResponse } from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

// ==================== Agent 定义 ====================

interface AgentFeature { label: string; status: 'done' | 'wip' | 'todo'; note: string }

interface AgentDef {
  id: string
  name: string
  logName: string
  description: string
  aiSource: string
  features: AgentFeature[]
  testFields: { key: string; label: string; placeholder: string; type?: 'text' | 'textarea' | 'select'; options?: { label: string; value: string }[]; defaultValue?: string }[]
}

const AGENTS: AgentDef[] = [
  {
    id: 'orchestrator', name: '协调智能体', logName: 'orchestrator',
    description: '意图识别 → 路由 → 聚合',
    aiSource: 'LLM API — 意图分类',
    features: [
      { label: 'LLM 意图分类', status: 'done', note: '优先 LLM，降级关键词' },
      { label: '任务分发', status: 'done', note: 'Redis Streams 消息队列' },
      { label: 'RequestContext', status: 'done', note: 'ThreadLocal userId + requestId' },
    ],
    testFields: [{ key: 'message', label: '用户意图', placeholder: '如：我想学 Python 装饰器' }],
  },
  {
    id: 'profile', name: '画像智能体', logName: 'profile_agent',
    description: '分析用户学习画像，提取知识水平和学习偏好',
    aiSource: 'LLM API — 画像分析',
    features: [
      { label: '学习画像分析', status: 'done', note: 'LLM 分析用户知识水平' },
      { label: '对话抽取', status: 'done', note: 'ProfileExtractor 双通道' },
      { label: 'Redis 通信', status: 'done', note: 'Stream 消息队列' },
    ],
    testFields: [{ key: 'userId', label: '用户ID', placeholder: '如：2' }],
  },
  {
    id: 'learningpath', name: '路径规划', logName: 'learning_path_agent',
    description: '知识图谱拓扑排序 + LLM 个性化排序 + 动态生成',
    aiSource: 'LLM API — 路径规划',
    features: [
      { label: '知识图谱匹配', status: 'done', note: '31 知识点拓扑排序' },
      { label: 'LLM 个性化排序', status: 'done', note: 'sortNodesWithLlm()' },
      { label: '动态路径生成', status: 'done', note: '无匹配时 LLM 生成' },
    ],
    testFields: [
      { key: 'goal', label: '学习目标', placeholder: '如：掌握 Python 装饰器' },
      { key: 'userId', label: '用户ID', placeholder: '如：2' },
    ],
  },
  {
    id: 'resource', name: '资源生成', logName: 'resource_agent',
    description: 'LLM 生成教学资源（讲义、练习题、代码）',
    aiSource: 'LLM API — 资源生成',
    features: [
      { label: '讲义文档', status: 'done', note: 'Markdown + 代码块' },
      { label: '练习题目', status: 'done', note: '选择+填空+编程' },
      { label: '代码案例', status: 'done', note: '完整可运行代码' },
    ],
    testFields: [
      { key: 'pathId', label: '路径ID', placeholder: '如：258' },
    ],
  },
  {
    id: 'quality', name: '质量评估', logName: 'quality_agent',
    description: 'LLM 自检 + 敏感词 + 学术规范',
    aiSource: 'LLM API（事实校验）+ 本地（敏感词+学术检查）',
    features: [
      { label: '非空/格式检查', status: 'done', note: '基础校验' },
      { label: 'LLM 事实性校验', status: 'wip', note: 'assessFull() 依赖开关' },
      { label: '敏感内容过滤', status: 'done', note: 'SensitiveFilter 词库匹配' },
    ],
    testFields: [{ key: 'resources', label: '资源内容', type: 'textarea', placeholder: '粘贴需要质检的文本' }],
  },
  {
    id: 'effect', name: '效果评估', logName: 'effect_agent',
    description: '行为追踪 + LLM 薄弱点分析 → 路径调整',
    aiSource: 'LLM API（薄弱点分析）',
    features: [
      { label: '低分→插复习节点', status: 'done', note: '简单规则' },
      { label: 'LLM 薄弱点分析', status: 'done', note: 'analyzeWeakness() 行为+LLM' },
      { label: '行为追踪', status: 'todo', note: '前端埋点待做' },
    ],
    testFields: [
      { key: 'userId', label: '用户ID', placeholder: '如：2' },
      { key: 'kpId', label: '知识点ID', placeholder: '如：kp_decorator' },
    ],
  },
  {
    id: 'qa', name: '答疑智能体', logName: 'intelligent_qa',
    description: '流式 RAG 问答 + Mermaid 流程图',
    aiSource: 'ai-server WS（DeepTutor RAG）',
    features: [
      { label: '文本流式问答', status: 'done', note: 'WS → ai-server' },
      { label: 'Mermaid 流程图', status: 'done', note: 'CDN mermaid 11 + 渲染' },
      { label: '知识库检索', status: 'done', note: 'RAG 向量检索' },
    ],
    testFields: [{ key: 'message', label: '问题内容', placeholder: '如：Python 中 *args 和 **kwargs 区别？' }],
  },
  {
    id: 'content', name: '内容分析', logName: 'content_analysis_agent',
    description: '上传文档 → 提取知识点 → 关联图谱',
    aiSource: 'LLM API + ai-server Knowledge Base',
    features: [
      { label: '关键词匹配知识点', status: 'done', note: '结构化课程够用' },
      { label: '上传入知识库', status: 'done', note: 'ai-server RAG' },
      { label: 'LLM 提取', status: 'done', note: 'analyzeWithLlm() 优先LLM' },
    ],
    testFields: [{ key: 'message', label: '文档内容', type: 'textarea', placeholder: '粘贴文档内容或关键词' }],
  },
]

const LLM_MODELS = [
  { label: 'gpt-4o-mini（默认）', value: 'gpt-4o-mini' },
  { label: 'gpt-4o', value: 'gpt-4o' },
  { label: 'DeepSeek Chat', value: 'deepseek-chat' },
  { label: 'Qwen Plus', value: 'qwen-plus' },
  { label: 'GLM-4', value: 'glm-4' },
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
      const res = await testAgent(agent.logName, payload)
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
          <pre className="mt-3 max-h-48 overflow-auto rounded border border-console-border bg-console-card p-3 text-xs text-console-text whitespace-pre-wrap">
            {testResult}
          </pre>
        )}
      </div>
    </div>
  )
}

export default function AgentManagementPanel() {
  const [selectedId, setSelectedId] = useState(AGENTS[0].id)
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
        <CardContent className="space-y-1 p-2">
          {AGENTS.map(a => {
            const s = stats?.stats?.find(x => x.agent === a.logName)
            const total = s?.total ?? 0
            return (
              <button
                key={a.id}
                onClick={() => setSelectedId(a.id)}
                className={`w-full text-left rounded-md px-3 py-2 text-sm transition-colors ${
                  selectedId === a.id
                    ? 'bg-console-blue/10 text-console-blue'
                    : 'text-console-text hover:bg-console-border/30'
                }`}
              >
                <div className="font-medium">{a.name}</div>
                <div className="text-[11px] text-console-muted">
                  {total > 0 ? `${total} 次调用` : '未调用'}
                </div>
              </button>
            )
          })}
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
