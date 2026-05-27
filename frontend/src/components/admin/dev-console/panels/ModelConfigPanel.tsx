import { useEffect, useState } from 'react'
import {
  getModelConfig,
  saveModelConfig,
  testLlmConfig,
  type ModelConfig,
} from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

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

  if (loading) {
    return <p className={panel.hint}>加载模型配置...</p>
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>模型配置</CardTitle>
        <p className={panel.desc}>
          大模型与嵌入 API（Key、地址、模型名），保存后写入数据库并同步 <code className="text-console-blue">ai-server/.env</code>
        </p>
      </CardHeader>
      <CardContent className="space-y-4">
        {msg && <p className={msgOk ? panel.msgOk : panel.msgErr}>{msg}</p>}

        <form className="space-y-6" onSubmit={(e) => void handleSave(e)}>
          <div>
            <h3 className={panel.subTitle}>大模型对话 API</h3>
            <div className={`${panel.grid} mt-3`}>
              <label className={panel.label}>
                提供商
                <select
                  className={panel.select}
                  value={form.llmBinding}
                  onChange={(e) => setForm({ ...form, llmBinding: e.target.value })}
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
                />
              </label>
              <label className={panel.label}>
                API 地址
                <input
                  className={panel.input}
                  value={form.llmHost}
                  onChange={(e) => setForm({ ...form, llmHost: e.target.value })}
                />
              </label>
              <label className={`${panel.label} sm:col-span-2`}>
                API 版本（可选）
                <input
                  className={panel.input}
                  value={form.llmApiVersion}
                  onChange={(e) => setForm({ ...form, llmApiVersion: e.target.value })}
                />
              </label>
            </div>
          </div>

          <div>
            <h3 className={panel.subTitle}>嵌入模型 API（RAG）</h3>
            <div className={`${panel.grid} mt-3`}>
              <label className={panel.label}>
                提供商
                <input
                  className={panel.input}
                  value={form.embeddingBinding}
                  onChange={(e) => setForm({ ...form, embeddingBinding: e.target.value })}
                />
              </label>
              <label className={panel.label}>
                模型名称
                <input
                  className={panel.input}
                  value={form.embeddingModel}
                  onChange={(e) => setForm({ ...form, embeddingModel: e.target.value })}
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
                />
              </label>
              <label className={`${panel.label} sm:col-span-2`}>
                API 地址
                <input
                  className={panel.input}
                  value={form.embeddingHost}
                  onChange={(e) => setForm({ ...form, embeddingHost: e.target.value })}
                />
              </label>
            </div>
          </div>

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
        </form>
      </CardContent>
    </Card>
  )
}
