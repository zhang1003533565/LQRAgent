import { useEffect, useState } from 'react'
import {
  getModelConfig,
  saveModelConfig,
  testLlmConfig,
  type ModelConfig,
} from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

/** 提供商预设 → 自动填充地址和推荐模型 */
const PROVIDER_PRESETS: Record<string, {
  llmHost: string; llmModel: string;
  embeddingHost: string; embeddingModel: string;
}> = {
  openai: {
    llmHost: 'https://api.openai.com/v1', llmModel: 'gpt-4o-mini',
    embeddingHost: 'https://api.openai.com/v1/embeddings', embeddingModel: 'text-embedding-3-large',
  },
  deepseek: {
    llmHost: 'https://api.deepseek.com', llmModel: 'deepseek-chat',
    embeddingHost: 'https://api.deepseek.com', embeddingModel: 'deepseek-embedding',
  },
  dashscope: {
    llmHost: 'https://dashscope.aliyuncs.com/compatible-mode/v1', llmModel: 'qwen-plus',
    embeddingHost: 'https://dashscope.aliyuncs.com/compatible-mode/v1', embeddingModel: 'text-embedding-v3',
  },
  siliconflow: {
    llmHost: 'https://api.siliconflow.cn/v1', llmModel: 'deepseek-ai/DeepSeek-V2.5',
    embeddingHost: 'https://api.siliconflow.cn/v1/embeddings', embeddingModel: 'BAAI/bge-large-zh-v1.5',
  },
  openrouter: {
    llmHost: 'https://openrouter.ai/api/v1', llmModel: 'openai/gpt-4o-mini',
    embeddingHost: 'https://openrouter.ai/api/v1', embeddingModel: 'openai/text-embedding-3-large',
  },
  ollama: {
    llmHost: 'http://localhost:11434/v1', llmModel: 'llama3.1',
    embeddingHost: 'http://localhost:11434/v1', embeddingModel: 'nomic-embed-text',
  },
  azure_openai: {
    llmHost: 'https://<your-resource>.openai.azure.com', llmModel: 'gpt-4o-mini',
    embeddingHost: 'https://<your-resource>.openai.azure.com', embeddingModel: 'text-embedding-3-large',
  },
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

function ConfiguredBadge({ configured }: { configured: boolean }) {
  if (!configured) return null
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-green-500/10 px-2 py-0.5 text-[11px] font-medium text-green-400">
      <span className="h-1.5 w-1.5 rounded-full bg-green-400" />
      已配置
    </span>
  )
}

export default function ModelConfigPanel() {
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [msg, setMsg] = useState('')
  const [msgOk, setMsgOk] = useState(true)
  const [llmConfigured, setLlmConfigured] = useState(false)
  const [embeddingConfigured, setEmbeddingConfigured] = useState(false)

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
    imageProvider: 'siliconflow',
    imageModel: 'Kwai-Kolors/Kolors',
    imageApiKey: '',
    imageHost: 'https://api.siliconflow.cn/v1',
    syncToAiServer: true,
  })

  async function load() {
    setLoading(true)
    try {
      const data: ModelConfig = await getModelConfig()
      setLlmConfigured(data.llmApiKeySet)
      setEmbeddingConfigured(data.embeddingApiKeySet)
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

  useEffect(() => { void load() }, [])

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
      showMessage('配置已保存并同步到 ai-server', true)
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

  function handleLlmBindingChange(binding: string) {
    const preset = PROVIDER_PRESETS[binding]
    if (!preset) return
    setForm(f => ({ ...f, llmBinding: binding, llmHost: preset.llmHost, llmModel: preset.llmModel }))
  }

  function handleEmbeddingBindingChange(binding: string) {
    const preset = PROVIDER_PRESETS[binding]
    if (!preset) return
    setForm(f => ({ ...f, embeddingBinding: binding, embeddingHost: preset.embeddingHost, embeddingModel: preset.embeddingModel }))
  }

  if (loading) return <p className={panel.hint}>加载模型配置...</p>

  return (
    <div className="space-y-4">
      {/* 状态概览 */}
      <div className="grid grid-cols-3 gap-3">
        <Card className={`border transition-colors ${llmConfigured ? 'border-green-500/30 bg-green-500/5' : ''}`}>
          <CardContent className="flex items-center gap-3 py-3">
            <span className={`h-3 w-3 rounded-full ${llmConfigured ? 'bg-green-500' : 'bg-gray-400'}`} />
            <div>
              <p className="text-sm font-medium text-console-text">对话 & 资源生成</p>
              <p className="text-xs text-console-muted">
                {llmConfigured ? `${form.llmBinding} · ${form.llmModel}` : '未配置'}
              </p>
            </div>
            <ConfiguredBadge configured={llmConfigured} />
          </CardContent>
        </Card>
        <Card className={`border transition-colors ${embeddingConfigured ? 'border-blue-500/30 bg-blue-500/5' : ''}`}>
          <CardContent className="flex items-center gap-3 py-3">
            <span className={`h-3 w-3 rounded-full ${embeddingConfigured ? 'bg-blue-500' : 'bg-gray-400'}`} />
            <div>
              <p className="text-sm font-medium text-console-text">嵌入模型 (RAG)</p>
              <p className="text-xs text-console-muted">
                {embeddingConfigured ? `${form.embeddingBinding} · ${form.embeddingModel}` : '未配置'}
              </p>
            </div>
            <ConfiguredBadge configured={embeddingConfigured} />
          </CardContent>
        </Card>
        <Card className={`border transition-colors ${form.imageProvider !== 'mock' ? 'border-purple-500/30 bg-purple-500/5' : ''}`}>
          <CardContent className="flex items-center gap-3 py-3">
            <span className={`h-3 w-3 rounded-full ${form.imageProvider !== 'mock' ? 'bg-purple-500' : 'bg-gray-400'}`} />
            <div>
              <p className="text-sm font-medium text-console-text">图片生成</p>
              <p className="text-xs text-console-muted">
                {form.imageProvider !== 'mock' ? `${form.imageProvider} · ${form.imageModel}` : '未配置'}
              </p>
            </div>
            <ConfiguredBadge configured={form.imageProvider !== 'mock'} />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>模型配置</CardTitle>
          <p className={panel.desc}>
            配置大模型与嵌入模型。保存后同步到 <code className="text-console-blue">ai-server/.env</code>。
          </p>
        </CardHeader>
        <CardContent>
          {msg && <p className={`mb-4 ${msgOk ? panel.msgOk : panel.msgErr}`}>{msg}</p>}

          <form className="space-y-6" onSubmit={(e) => void handleSave(e)}>
            {/* ===== 对话 & 生成 ===== */}
            <fieldset className="rounded-lg border border-console-border p-4">
              <legend className="flex items-center gap-2 px-2 text-sm font-medium text-console-text">
                <span className="h-2.5 w-2.5 rounded-full bg-green-500" />
                对话 & 资源生成（大模型）
                <ConfiguredBadge configured={llmConfigured} />
              </legend>
              <p className="mb-3 text-xs text-console-muted">
                用于答疑对话、生成讲义/题目/代码。切换提供商时自动填充推荐模型。
              </p>
              <div className={`${panel.grid} mt-3`}>
                <label className={panel.label}>
                  提供商
                  <select className={panel.select} value={form.llmBinding}
                    onChange={(e) => handleLlmBindingChange(e.target.value)}>
                    {BINDING_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </select>
                </label>
                <label className={panel.label}>
                  模型名称
                  <input className={panel.input} value={form.llmModel}
                    onChange={(e) => setForm({ ...form, llmModel: e.target.value })} />
                </label>
                <label className={panel.label}>
                  API Key
                  <input type="password" className={panel.input} value={form.llmApiKey}
                    onChange={(e) => setForm({ ...form, llmApiKey: e.target.value })}
                    autoComplete="off"
                    placeholder={llmConfigured ? '已配置，留空不修改' : 'sk-...'} />
                </label>
                <label className={panel.label}>
                  API 地址
                  <input className={panel.input} value={form.llmHost}
                    onChange={(e) => setForm({ ...form, llmHost: e.target.value })} />
                </label>
                {form.llmBinding === 'azure_openai' && (
                  <label className={`${panel.label} sm:col-span-2`}>
                    API 版本
                    <input className={panel.input} value={form.llmApiVersion}
                      onChange={(e) => setForm({ ...form, llmApiVersion: e.target.value })}
                      placeholder="2024-02-01" />
                  </label>
                )}
              </div>
            </fieldset>

            {/* ===== 嵌入模型 ===== */}
            <fieldset className="rounded-lg border border-console-border p-4">
              <legend className="flex items-center gap-2 px-2 text-sm font-medium text-console-text">
                <span className="h-2.5 w-2.5 rounded-full bg-blue-500" />
                嵌入模型（RAG 知识库）
                <ConfiguredBadge configured={embeddingConfigured} />
              </legend>
              <p className="mb-3 text-xs text-console-muted">
                用于文档向量化和语义检索。切换提供商时自动填充推荐的 embedding 模型。
              </p>
              <div className={`${panel.grid} mt-3`}>
                <label className={panel.label}>
                  提供商
                  <select className={panel.select} value={form.embeddingBinding}
                    onChange={(e) => handleEmbeddingBindingChange(e.target.value)}>
                    {BINDING_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                  </select>
                </label>
                <label className={panel.label}>
                  模型名称
                  <input className={panel.input} value={form.embeddingModel}
                    onChange={(e) => setForm({ ...form, embeddingModel: e.target.value })} />
                </label>
                <label className={panel.label}>
                  API Key
                  <input type="password" className={panel.input} value={form.embeddingApiKey}
                    onChange={(e) => setForm({ ...form, embeddingApiKey: e.target.value })}
                    autoComplete="off"
                    placeholder={embeddingConfigured ? '已配置，留空不修改' : 'sk-...'} />
                </label>
                <label className={panel.label}>
                  API 地址
                  <input className={panel.input} value={form.embeddingHost}
                    onChange={(e) => setForm({ ...form, embeddingHost: e.target.value })} />
                </label>
              </div>
            </fieldset>

            {/* ===== 图片生成 ===== */}
            <fieldset className="rounded-lg border border-console-border p-4">
              <legend className="flex items-center gap-2 px-2 text-sm font-medium text-console-text">
                <span className="h-2.5 w-2.5 rounded-full bg-purple-500" />
                图片生成（AI 生图）
              </legend>
              <p className="mb-3 text-xs text-console-muted">
                用于生成知识点示意图。推荐使用 SiliconFlow 可图（免费）。
              </p>
              <div className={`${panel.grid} mt-3`}>
                <label className={panel.label}>
                  生图提供商
                  <select className={panel.select} value={form.imageProvider}
                    onChange={(e) => setForm({ ...form, imageProvider: e.target.value })}>
                    <option value="mock">Mock（占位符）</option>
                    <option value="siliconflow">SiliconFlow 可图（免费）</option>
                    <option value="dalle3">DALL·E 3</option>
                    <option value="sd3">Stable Diffusion 3</option>
                  </select>
                </label>
                <label className={panel.label}>
                  生图模型
                  <select className={panel.select} value={form.imageModel}
                    onChange={(e) => setForm({ ...form, imageModel: e.target.value })}>
                    <option value="Kwai-Kolors/Kolors">Kolors（可图）</option>
                    <option value="stabilityai/stable-diffusion-xl-base-1.0">Stable Diffusion XL</option>
                  </select>
                </label>
                <label className={panel.label}>
                  API Key
                  <input type="password" className={panel.input} value={form.imageApiKey}
                    onChange={(e) => setForm({ ...form, imageApiKey: e.target.value })}
                    autoComplete="off"
                    placeholder="sk-..." />
                </label>
                <label className={panel.label}>
                  API 地址
                  <input className={panel.input} value={form.imageHost}
                    onChange={(e) => setForm({ ...form, imageHost: e.target.value })} />
                </label>
              </div>
            </fieldset>

            {/* ===== 操作栏 ===== */}
            <div className="flex flex-wrap items-center justify-between gap-4 border-t border-console-border pt-4">
              <label className="flex items-center gap-2 text-sm text-console-muted">
                <input type="checkbox" checked={form.syncToAiServer}
                  onChange={(e) => setForm({ ...form, syncToAiServer: e.target.checked })}
                  className="rounded border-console-border" />
                同步到 ai-server
              </label>
              <div className="flex gap-2">
                <button type="submit" className={panel.primaryBtn} disabled={saving}>
                  {saving ? '保存中...' : '保存配置'}
                </button>
                <button type="button" className={panel.secondaryBtn} disabled={testing}
                  onClick={() => void handleTestLlm()}>
                  {testing ? '测试中...' : '测试连通性'}
                </button>
              </div>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
