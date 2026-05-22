import { useEffect, useState } from 'react'
import {
  getModelConfig,
  saveModelConfig,
  testLlmConfig,
  type ModelConfig,
} from '@/api/admin'
import styles from './ModelConfigPanel.module.css'

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
    return <p className={styles.hint}>加载模型配置...</p>
  }

  return (
    <div className={styles.wrap}>
      <p className={styles.desc}>
        此处配置<strong>实际调用的大模型与嵌入模型 API</strong>（Key、地址、模型名），保存后写入数据库并同步到
        <code>ai-server/.env</code>。与下方的「AI 服务地址」不同：后者只是 LQRAgent 连接 Python 服务的入口。
      </p>

      {msg && <p className={msgOk ? styles.msgOk : styles.msgErr}>{msg}</p>}

      <form className={styles.form} onSubmit={(e) => void handleSave(e)}>
        <h3 className={styles.blockTitle}>大模型对话 API</h3>
        <div className={styles.grid}>
          <label>
            提供商 (binding)
            <select
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
          <label>
            模型名称
            <input
              value={form.llmModel}
              onChange={(e) => setForm({ ...form, llmModel: e.target.value })}
              placeholder="gpt-4o-mini / deepseek-chat"
            />
          </label>
          <label>
            API Key
            <input
              type="password"
              value={form.llmApiKey}
              onChange={(e) => setForm({ ...form, llmApiKey: e.target.value })}
              placeholder="sk-..."
              autoComplete="off"
            />
          </label>
          <label>
            API 地址 (LLM_HOST)
            <input
              value={form.llmHost}
              onChange={(e) => setForm({ ...form, llmHost: e.target.value })}
              placeholder="https://api.openai.com/v1"
            />
          </label>
          <label>
            API 版本（可选，Azure 等）
            <input
              value={form.llmApiVersion}
              onChange={(e) => setForm({ ...form, llmApiVersion: e.target.value })}
              placeholder="留空即可"
            />
          </label>
        </div>

        <h3 className={styles.blockTitle}>嵌入模型 API（知识库 / RAG）</h3>
        <div className={styles.grid}>
          <label>
            提供商
            <input
              value={form.embeddingBinding}
              onChange={(e) => setForm({ ...form, embeddingBinding: e.target.value })}
            />
          </label>
          <label>
            模型名称
            <input
              value={form.embeddingModel}
              onChange={(e) => setForm({ ...form, embeddingModel: e.target.value })}
            />
          </label>
          <label>
            API Key
            <input
              type="password"
              value={form.embeddingApiKey}
              onChange={(e) => setForm({ ...form, embeddingApiKey: e.target.value })}
              placeholder="可与 LLM 相同"
              autoComplete="off"
            />
          </label>
          <label className={styles.span2}>
            API 地址（完整 URL）
            <input
              value={form.embeddingHost}
              onChange={(e) => setForm({ ...form, embeddingHost: e.target.value })}
              placeholder="https://api.openai.com/v1/embeddings"
            />
          </label>
        </div>

        <label className={styles.check}>
          <input
            type="checkbox"
            checked={form.syncToAiServer}
            onChange={(e) => setForm({ ...form, syncToAiServer: e.target.checked })}
          />
          同步到 ai-server/.env
        </label>

        <div className={styles.actions}>
          <button type="submit" className={styles.primaryBtn} disabled={saving}>
            {saving ? '保存中...' : '保存模型配置'}
          </button>
          <button
            type="button"
            className={styles.secondaryBtn}
            disabled={testing}
            onClick={() => void handleTestLlm()}
          >
            {testing ? '测试中...' : '测试大模型 API'}
          </button>
        </div>
      </form>
    </div>
  )
}
