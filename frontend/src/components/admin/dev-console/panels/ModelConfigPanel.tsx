import { useEffect, useState } from 'react'
import {
  getModelConfig,
  saveModelConfig,
  testLlmConfig,
  type ModelConfig,
} from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

/** 模型分类用途说明 */
const USAGE_SECTIONS: { id: string; title: string; usage: string; agents: string }[] = [
  {
    id: 'chat-gen',
    title: '对话 & 资源生成（大模型）',
    usage: '用于答疑对话、生成教案/题目/代码示例',
    agents: 'QaAgent（答疑）· ResourceFacadeAgent（资源生成）· Orchestrator（P5 意图识别）',
  },
  {
    id: 'embedding',
    title: '嵌入模型（RAG 知识库）',
    usage: '用于上传文档的向量化检索',
    agents: 'KnowledgeBase（知识库）· UploadQueue（上传处理）',
  },
  {
    id: 'image',
    title: '图片生成（P6 规划中）',
    usage: '用于生成知识点示意图',
    agents: 'MediaGenerationAgent（媒体生成）',
  },
]

/** 提供商预设 → 自动填充地址 */
const PROVIDER_PRESETS: Record<string, { llmHost: string; embeddingHost: string; placeholder: string }> = {
  openai: { llmHost: 'https://api.openai.com/v1', embeddingHost: 'https://api.openai.com/v1/embeddings', placeholder: 'gpt-4o-mini' },
  deepseek: { llmHost: 'https://api.deepseek.com', embeddingHost: 'https://api.deepseek.com', placeholder: 'deepseek-chat' },
  dashscope: { llmHost: 'https://dashscope.aliyuncs.com/compatible-mode/v1', embeddingHost: 'https://dashscope.aliyuncs.com/compatible-mode/v1', placeholder: 'qwen-turbo' },
  siliconflow: { llmHost: 'https://api.siliconflow.cn/v1', embeddingHost: 'https://api.siliconflow.cn/v1', placeholder: 'deepseek-ai/DeepSeek-V2.5' },
  openrouter: { llmHost: 'https://openrouter.ai/api/v1', embeddingHost: 'https://openrouter.ai/api/v1', placeholder: 'openai/gpt-4o-mini' },
  ollama: { llmHost: 'http://localhost:11434/v1', embeddingHost: 'http://localhost:11434/v1', placeholder: 'llama3.1' },
  azure_openai: { llmHost: 'https://<your-resource>.openai.azure.com', embeddingHost: 'https://<your-resource>.openai.azure.com', placeholder: 'gpt-4o-mini' },
}

const BINDING_OPTIONS = [
  { value: 'openai', label: 'OpenAI 兼容' },
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'dashscope', label: '通义千问 (DashScope)' },
  { value: 'siliconflow', label: 'SiliconFlow' },
  { value: 'openrouter', label: 'OpenRouter' },
  { value: 'ollama', label: 'Ollama 本地' },
  { value: 'azure_openai', label: 'Azure OpenAI' },
]

export default function ModelConfigPanel() {
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [msg, setMsg] = useState('')
  const [msgOk, setMsgOk] = useState(true)

  const [form, setForm] = useState({
    llmBinding: 'openai',
    llmModel: 'gpt-4o-mini',
    llmApiKey: '',
    llmHost: 'https://api.openai.com/v1',
    llmApiVersion: '',
    embeddingBinding: 'openai',
    embeddingModel: 'text-embedding-3-large',
    embeddingApiKey: '',
    embeddingHost: 'https://api.openai.com/v1/embeddings',
    syncToAiServer: true,
  })

  async function load() {
    setLoading(true)
    try {
      const data: ModelConfig = await getModelConfig()
      setForm((f) => ({
        ...f,
        llmBinding: data.llmBinding,
        llmModel: data.llmModel,
        llmApiKey: data.llmApiKeySet ? '********' : '',
        llmHost: data.llmHost,
        llmApiVersion: data.llmApiVersion ?? '',
        embeddingBinding: data.embeddingBinding,
        embeddingModel: data.embeddingModel,
        embeddingApiKey: data.embeddingApiKeySet ? '********' : '',
        embeddingHost: data.embeddingHost,
      }))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  function showMessage(text: string, ok: boolean) {
    setMsg(text)
    setMsgOk(ok)
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = {
        ...form,
        llmApiKey: form.llmApiKey === '********' ? undefined : form.llmApiKey,
        embeddingApiKey: form.embeddingApiKey === '********' ? undefined : form.embeddingApiKey,
      }
      await saveModelConfig(payload)
      showMessage('已保存并同步到 ai-server/.env（重启 AI 服务后完全生效）', true)
      await load()
    } catch (err: unknown) {
      const m = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      showMessage(m ?? '保存失败', false)
    } finally {
      setSaving(false)
    }
  }

  async function handleTestLlm() {
    setTesting(true)
    try {
      const r = await testLlmConfig()
      showMessage(r.message, r.success)
    } catch {
      showMessage('测试请求失败', false)
    } finally {
      setTesting(false)
    }
  }

  /** 切换提供商时自动填充地址 */
  function handleLlmBindingChange(binding: string) {
    const preset = PROVIDER_PRESETS[binding]
    setForm((f) => ({
      ...f,
      llmBinding: binding,
      llmHost: preset?.llmHost ?? f.llmHost,
      llmModel: preset?.placeholder ?? f.llmModel,
    }))
  }

  function handleEmbeddingBindingChange(binding: string) {
    const preset = PROVIDER_PRESETS[binding]
    setForm((f) => ({
      ...f,
      embeddingBinding: binding,
      embeddingHost: preset?.embeddingHost ?? f.embeddingHost,
      embeddingModel: preset?.placeholder ?? f.embeddingModel,
    }))
  }

  if (loading) {
    return <p className={panel.hint}>加载模型配置...</p>
  }

  return (
    <div className="space-y-4">
      {/* 图例 */}
      <Card>
        <CardContent className="flex flex-wrap gap-6 py-4 text-xs text-console-muted">
          {USAGE_SECTIONS.filter((s) => s.id !== 'image').map((s) => (
            <div key={s.id} className="flex items-center gap-2">
              <span className="inline-block h-2 w-2 rounded-full"
                style={{ backgroundColor: s.id === 'chat-gen' ? '#22c55e' : '#3b82f6' }} />
              <span>{s.title.replace(/（.*$/, '')}</span>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>模型配置</CardTitle>
          <p className={panel.desc}>
            配置大模型与嵌入模型的 API Key、地址和模型名。保存后写入数据库并同步到 <code className="text-console-blue">ai-server/.env</code>。
          </p>
        </CardHeader>
        <CardContent>
          {msg && <p className={`mb-4 ${msgOk ? panel.msgOk : panel.msgErr}`}>{msg}</p>}

          <form className="space-y-8" onSubmit={(e) => void handleSave(e)}>
            {/* ===== 对话 & 生成 ===== */}
            <div className="rounded-lg border border-console-border p-4">
              <div className="mb-3 flex items-center gap-2">
                <span className="inline-block h-2.5 w-2.5 rounded-full bg-green-500" />
                <h3 className="text-sm font-medium text-console-text">对话 &amp; 资源生成（大模型）</h3>
              </div>
              <p className="mb-4 text-xs text-console-muted">
                用于答疑对话（QaAgent）、生成讲义/题目/代码（ResourceFacadeAgent）。
                {form.syncToAiServer && <span className="ml-2 text-console-green">· 将同步至 ai-server</span>}
              </p>
              <div className={`${panel.grid} mt-3`}>
                <label className={panel.label}>
                  提供商
                  <select
                    className={panel.select}
                    value={form.llmBinding}
                    onChange={(e) => handleLlmBindingChange(e.target.value)}
                  >
                    {BINDING_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </label>
                <label className={panel.label}>
                  模型名称
                  <input
                    className={panel.input}
                    value={form.llmModel}
                    onChange={(e) => setForm({ ...form, llmModel: e.target.value })}
                    placeholder={PROVIDER_PRESETS[form.llmBinding]?.placeholder ?? 'gpt-4o-mini'}
                  />
                </label>
                <label className={panel.label}>
                  API Key
                  <input
                    type="password"
                    className={panel.input}
                    value={form.llmApiKey}
                    onChange={(e) => setForm({ ...form, llmApiKey: e.target.value })}
                    autoComplete="off"
                    placeholder={form.llmApiKey === '********' ? '已配置，留空则不修改' : 'sk-...'}
                  />
                </label>
                <label className={panel.label}>
                  API 地址
                  <input
                    className={panel.input}
                    value={form.llmHost}
                    onChange={(e) => setForm({ ...form, llmHost: e.target.value })}
                    placeholder={PROVIDER_PRESETS[form.llmBinding]?.llmHost}
                  />
                </label>
                <label className={`${panel.label} sm:col-span-2`}>
                  API 版本（Azure 等场景需要）
                  <input
                    className={panel.input}
                    value={form.llmApiVersion}
                    onChange={(e) => setForm({ ...form, llmApiVersion: e.target.value })}
                    placeholder="2024-02-01"
                  />
                </label>
              </div>
            </div>

            {/* ===== 嵌入模型 ===== */}
            <div className="rounded-lg border border-console-border p-4">
              <div className="mb-3 flex items-center gap-2">
                <span className="inline-block h-2.5 w-2.5 rounded-full bg-blue-500" />
                <h3 className="text-sm font-medium text-console-text">嵌入模型（RAG 知识库）</h3>
              </div>
              <p className="mb-4 text-xs text-console-muted">
                用于上传文档的向量化检索。
                {form.syncToAiServer && <span className="ml-2 text-console-green">· 将同步至 ai-server</span>}
              </p>
              <div className={`${panel.grid} mt-3`}>
                <label className={panel.label}>
                  提供商
                  <select
                    className={panel.select}
                    value={form.embeddingBinding}
                    onChange={(e) => handleEmbeddingBindingChange(e.target.value)}
                  >
                    {BINDING_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </label>
                <label className={panel.label}>
                  模型名称
                  <input
                    className={panel.input}
                    value={form.embeddingModel}
                    onChange={(e) => setForm({ ...form, embeddingModel: e.target.value })}
                    placeholder={PROVIDER_PRESETS[form.embeddingBinding]?.placeholder ?? 'text-embedding-3-large'}
                  />
                </label>
                <label className={panel.label}>
                  API Key
                  <input
                    type="password"
                    className={panel.input}
                    value={form.embeddingApiKey}
                    onChange={(e) => setForm({ ...form, embeddingApiKey: e.target.value })}
                    autoComplete="off"
                    placeholder={form.embeddingApiKey === '********' ? '已配置，留空则不修改' : 'sk-...'}
                  />
                </label>
                <label className={panel.label}>
                  API 地址
                  <input
                    className={panel.input}
                    value={form.embeddingHost}
                    onChange={(e) => setForm({ ...form, embeddingHost: e.target.value })}
                    placeholder={PROVIDER_PRESETS[form.embeddingBinding]?.embeddingHost}
                  />
                </label>
              </div>
            </div>

            {/* ===== 图片生成（禁用占位） ===== */}
            <div className="rounded-lg border border-dashed border-console-border/50 bg-console-bg/30 p-4 opacity-50">
              <div className="mb-1 text-xs font-medium text-console-muted">
                🎨 图片生成 · P6 规划中
              </div>
              <p className="text-xs text-console-muted/60">
                用于生成知识点示意图。支持 Midjourney / DALL·E / Stable Diffusion 等。
                此区域将在 P6 开放配置。
              </p>
            </div>

            {/* ===== 操作栏 ===== */}
            <div className="flex flex-wrap items-center justify-between gap-4 border-t border-console-border pt-4">
              <label className="flex items-center gap-2 text-sm text-console-muted">
                <input
                  type="checkbox"
                  checked={form.syncToAiServer}
                  onChange={(e) => setForm({ ...form, syncToAiServer: e.target.checked })}
                  className="rounded border-console-border"
                />
                同步到 ai-server/.env
              </label>
              <div className="flex flex-wrap gap-2">
                <button type="submit" className={panel.primaryBtn} disabled={saving}>
                  {saving ? '保存中...' : '保存模型配置'}
                </button>
                <button
                  type="button"
                  className={panel.secondaryBtn}
                  disabled={testing}
                  onClick={() => void handleTestLlm()}
                >
                  {testing ? '测试中...' : '测试大模型 API'}
                </button>
              </div>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
