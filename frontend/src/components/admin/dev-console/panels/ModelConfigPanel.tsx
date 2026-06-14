import { useEffect, useState } from 'react'
import {
  getModelConfig,
  saveModelConfig,
  testLlmConfig,
  type ModelConfig,
} from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

// ============ 供应商 → 模型/地址 预设配置 ============

/** LLM 供应商预设 */
const LLM_PROVIDERS: Record<string, {
  label: string; host: string; models: { value: string; label: string }[];
  embeddingHost: string; embeddingModels: { value: string; label: string }[];
}> = {
  openai: {
    label: 'OpenAI 兼容',
    host: 'https://api.openai.com/v1',
    models: [
      { value: 'gpt-4o-mini', label: 'GPT-4o Mini' },
      { value: 'gpt-4o', label: 'GPT-4o' },
      { value: 'gpt-4-turbo', label: 'GPT-4 Turbo' },
    ],
    embeddingHost: 'https://api.openai.com/v1/embeddings',
    embeddingModels: [
      { value: 'text-embedding-3-large', label: 'text-embedding-3-large' },
      { value: 'text-embedding-3-small', label: 'text-embedding-3-small' },
      { value: 'text-embedding-ada-002', label: 'text-embedding-ada-002' },
    ],
  },
  deepseek: {
    label: 'DeepSeek',
    host: 'https://api.deepseek.com',
    models: [
      { value: 'deepseek-chat', label: 'DeepSeek-V3 (Chat)' },
      { value: 'deepseek-reasoner', label: 'DeepSeek-R1 (Reasoner)' },
    ],
    embeddingHost: 'https://api.deepseek.com',
    embeddingModels: [
      { value: 'deepseek-embedding', label: 'DeepSeek Embedding' },
    ],
  },
  dashscope: {
    label: '通义千问 (DashScope)',
    host: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    models: [
      { value: 'qwen-plus', label: 'Qwen-Plus' },
      { value: 'qwen-max', label: 'Qwen-Max' },
      { value: 'qwen-turbo', label: 'Qwen-Turbo' },
    ],
    embeddingHost: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    embeddingModels: [
      { value: 'text-embedding-v3', label: 'text-embedding-v3' },
      { value: 'text-embedding-v2', label: 'text-embedding-v2' },
    ],
  },
  siliconflow: {
    label: 'SiliconFlow',
    host: 'https://api.siliconflow.cn/v1',
    models: [
      { value: 'deepseek-ai/DeepSeek-V2.5', label: 'DeepSeek-V2.5' },
      { value: 'deepseek-ai/DeepSeek-V3', label: 'DeepSeek-V3' },
      { value: 'Qwen/Qwen2.5-72B-Instruct', label: 'Qwen2.5-72B' },
    ],
    embeddingHost: 'https://api.siliconflow.cn/v1/embeddings',
    embeddingModels: [
      { value: 'BAAI/bge-large-zh-v1.5', label: 'BAAI/bge-large-zh-v1.5' },
      { value: 'BAAI/bge-m3', label: 'BAAI/bge-m3' },
    ],
  },
  openrouter: {
    label: 'OpenRouter',
    host: 'https://openrouter.ai/api/v1',
    models: [
      { value: 'openai/gpt-4o-mini', label: 'GPT-4o Mini' },
      { value: 'anthropic/claude-3.5-sonnet', label: 'Claude 3.5 Sonnet' },
    ],
    embeddingHost: 'https://openrouter.ai/api/v1',
    embeddingModels: [
      { value: 'openai/text-embedding-3-large', label: 'text-embedding-3-large' },
    ],
  },
  ollama: {
    label: 'Ollama 本地',
    host: 'http://localhost:11434/v1',
    models: [
      { value: 'llama3.1', label: 'Llama 3.1' },
      { value: 'qwen2.5', label: 'Qwen 2.5' },
    ],
    embeddingHost: 'http://localhost:11434/v1',
    embeddingModels: [
      { value: 'nomic-embed-text', label: 'nomic-embed-text' },
    ],
  },
  azure_openai: {
    label: 'Azure OpenAI',
    host: 'https://<your-resource>.openai.azure.com',
    models: [
      { value: 'gpt-4o-mini', label: 'GPT-4o Mini' },
      { value: 'gpt-4o', label: 'GPT-4o' },
    ],
    embeddingHost: 'https://<your-resource>.openai.azure.com',
    embeddingModels: [
      { value: 'text-embedding-3-large', label: 'text-embedding-3-large' },
    ],
  },
  agnes: {
    label: 'Agnes AI',
    host: 'https://apihub.agnes-ai.com/v1',
    models: [
      { value: 'agnes-2.0-flash', label: 'Agnes 2.0 Flash' },
      { value: 'agnes-2.0-pro', label: 'Agnes 2.0 Pro' },
    ],
    embeddingHost: 'https://apihub.agnes-ai.com/v1',
    embeddingModels: [
      { value: 'text-embedding-3-large', label: 'text-embedding-3-large' },
    ],
  },
  xfyun: {
    label: '讯飞星火 MaaS',
    host: 'https://maas-api.cn-huabei-1.xf-yun.com/v2',
    models: [
      { value: 'xopzimageturbo', label: 'Z Image Turbo' },
      { value: 'xppaddleocrv16', label: 'PaddleOCR-VL 1.6' },
    ],
    embeddingHost: 'https://maas-api.cn-huabei-1.xf-yun.com/v2/embeddings',
    embeddingModels: [
      { value: 'xop3qwen8bembedding', label: 'Qwen3-Embedding-8B' },
    ],
  },
}

/** 图片生成供应商 → 模型列表 */
const IMAGE_PROVIDERS: Record<string, {
  label: string; host: string; models: { value: string; label: string }[];
}> = {
  mock: {
    label: 'Mock（占位符）', host: '',
    models: [{ value: 'mock', label: '占位符' }],
  },
  agnes: {
    label: 'Agnes AI（免费）', host: 'https://apihub.agnes-ai.com/v1',
    models: [
      { value: 'agnes-image-2.0-flash', label: 'Agnes Image 2.0 Flash' },
      { value: 'agnes-image-2.1-flash', label: 'Agnes Image 2.1 Flash' },
    ],
  },
  siliconflow: {
    label: 'SiliconFlow 可图（免费）', host: 'https://api.siliconflow.cn/v1',
    models: [
      { value: 'Kwai-Kolors/Kolors', label: 'Kolors（可图）' },
      { value: 'stabilityai/stable-diffusion-xl-base-1.0', label: 'Stable Diffusion XL' },
    ],
  },
  dalle3: {
    label: 'DALL·E 3', host: 'https://api.openai.com/v1',
    models: [{ value: 'dall-e-3', label: 'DALL·E 3' }],
  },
  sd3: {
    label: 'Stable Diffusion 3', host: 'https://api.stability.ai',
    models: [{ value: 'stabilityai/stable-diffusion-3', label: 'Stable Diffusion 3' }],
  },
}

/** 视频生成供应商 → 模型列表 */
const VIDEO_PROVIDERS: Record<string, {
  label: string; host: string; models: { value: string; label: string }[];
}> = {
  mock: {
    label: 'Mock（占位符）', host: '',
    models: [{ value: 'mock', label: '占位符' }],
  },
  agnes: {
    label: 'Agnes AI', host: 'https://apihub.agnes-ai.com/v1',
    models: [
      { value: 'agnes-video-v2.0', label: 'Agnes Video V2.0' },
    ],
  },
}

/** OCR/视觉识别供应商 → 模型列表 */
const OCR_PROVIDERS: Record<string, {
  label: string; host: string; models: { value: string; label: string }[];
  hasSecretKey: boolean;
}> = {
  mock: {
    label: 'Mock（占位符）', host: '',
    models: [{ value: 'mock', label: '占位符' }],
    hasSecretKey: false,
  },
  xfyun: {
    label: '讯飞星火 MaaS', host: 'https://maas-api.cn-huabei-1.xf-yun.com/v2',
    models: [
      { value: 'xppaddleocrv16', label: 'PaddleOCR-VL 1.6' },
      { value: 'xopzimageturbo', label: 'Z Image Turbo' },
    ],
    hasSecretKey: false,
  },
  paddle: {
    label: 'PaddleOCR（本地）', host: '',
    models: [
      { value: 'chinese', label: '中文通用' },
      { value: 'english', label: '英文识别' },
    ],
    hasSecretKey: false,
  },
}

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
  const [videoConfigured, setVideoConfigured] = useState(false)
  const [ocrConfigured, setOcrConfigured] = useState(false)

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
    imageProvider: 'agnes',
    imageModel: 'agnes-image-2.1-flash',
    imageApiKey: '',
    imageHost: 'https://apihub.agnes-ai.com/v1',
    videoProvider: 'agnes',
    videoModel: 'agnes-video-v2.0',
    videoApiKey: '',
    videoHost: 'https://apihub.agnes-ai.com/v1',
    ocrProvider: 'xfyun',
    ocrModel: 'general',
    ocrApiKey: '',
    ocrSecretKey: '',
    ocrHost: 'https://maas.xfyun.cn/v1',
    syncToAiServer: true,
  })

  async function load() {
    setLoading(true)
    try {
      const data: ModelConfig = await getModelConfig()
      setLlmConfigured(data.llmApiKeySet)
      setEmbeddingConfigured(data.embeddingApiKeySet)
      setVideoConfigured(data.videoApiKeySet)
      setOcrConfigured(data.ocrApiKeySet)
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
        videoProvider: data.videoBinding,
        videoModel: data.videoModel,
        videoApiKey: data.videoApiKeySet ? '********' : '',
        videoHost: data.videoHost,
        imageProvider: data.imageBinding,
        imageModel: data.imageModel,
        imageApiKey: data.imageApiKeySet ? '********' : '',
        imageHost: data.imageHost,
        ocrProvider: data.ocrBinding,
        ocrModel: data.ocrModel,
        ocrApiKey: data.ocrApiKeySet ? '********' : '',
        ocrSecretKey: data.ocrSecretKeySet ? '********' : '',
        ocrHost: data.ocrHost,
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
        llmBinding: form.llmBinding,
        llmModel: form.llmModel,
        llmApiKey: form.llmApiKey === '********' ? undefined : form.llmApiKey,
        llmHost: form.llmHost,
        llmApiVersion: form.llmApiVersion,
        embeddingBinding: form.embeddingBinding,
        embeddingModel: form.embeddingModel,
        embeddingApiKey: form.embeddingApiKey === '********' ? undefined : form.embeddingApiKey,
        embeddingHost: form.embeddingHost,
        videoBinding: form.videoProvider,
        videoModel: form.videoModel,
        videoApiKey: form.videoApiKey === '********' ? undefined : form.videoApiKey,
        videoHost: form.videoHost,
        imageBinding: form.imageProvider,
        imageModel: form.imageModel,
        imageApiKey: form.imageApiKey === '********' ? undefined : form.imageApiKey,
        imageHost: form.imageHost,
        ocrBinding: form.ocrProvider,
        ocrModel: form.ocrModel,
        ocrApiKey: form.ocrApiKey === '********' ? undefined : form.ocrApiKey,
        ocrSecretKey: form.ocrSecretKey === '********' ? undefined : form.ocrSecretKey,
        ocrHost: form.ocrHost,
        syncToAiServer: form.syncToAiServer,
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
    const preset = LLM_PROVIDERS[binding]
    if (!preset) return
    setForm(f => ({
      ...f,
      llmBinding: binding,
      llmHost: preset.host,
      llmModel: preset.models[0]?.value || '',
    }))
  }

  function handleEmbeddingBindingChange(binding: string) {
    const preset = LLM_PROVIDERS[binding]
    if (!preset) return
    setForm(f => ({
      ...f,
      embeddingBinding: binding,
      embeddingHost: preset.embeddingHost,
      embeddingModel: preset.embeddingModels[0]?.value || '',
    }))
  }

  function handleImageProviderChange(provider: string) {
    const spec = IMAGE_PROVIDERS[provider]
    if (!spec) return
    setForm(f => ({
      ...f,
      imageProvider: provider,
      imageModel: spec.models[0]?.value || '',
      imageHost: spec.host,
    }))
  }

  function handleVideoProviderChange(provider: string) {
    const spec = VIDEO_PROVIDERS[provider]
    if (!spec) return
    setForm(f => ({
      ...f,
      videoProvider: provider,
      videoModel: spec.models[0]?.value || '',
      videoHost: spec.host,
    }))
  }

  function handleOcrProviderChange(provider: string) {
    const spec = OCR_PROVIDERS[provider]
    if (!spec) return
    setForm(f => ({
      ...f,
      ocrProvider: provider,
      ocrModel: spec.models[0]?.value || '',
      ocrHost: spec.host,
    }))
  }

  if (loading) return <p className={panel.hint}>加载模型配置...</p>

  return (
    <div className="space-y-4">
      {/* 状态概览 */}
      <div className="grid grid-cols-4 gap-3">
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
        <Card className={`border transition-colors ${videoConfigured ? 'border-orange-500/30 bg-orange-500/5' : ''}`}>
          <CardContent className="flex items-center gap-3 py-3">
            <span className={`h-3 w-3 rounded-full ${videoConfigured ? 'bg-orange-500' : 'bg-gray-400'}`} />
            <div>
              <p className="text-sm font-medium text-console-text">视频生成</p>
              <p className="text-xs text-console-muted">
                {videoConfigured ? `${form.videoProvider} · ${form.videoModel}` : '未配置'}
              </p>
            </div>
            <ConfiguredBadge configured={videoConfigured} />
          </CardContent>
        </Card>
        <Card className={`border transition-colors ${ocrConfigured ? 'border-cyan-500/30 bg-cyan-500/5' : ''}`}>
          <CardContent className="flex items-center gap-3 py-3">
            <span className={`h-3 w-3 rounded-full ${ocrConfigured ? 'bg-cyan-500' : 'bg-gray-400'}`} />
            <div>
              <p className="text-sm font-medium text-console-text">视觉识别 (OCR)</p>
              <p className="text-xs text-console-muted">
                {ocrConfigured ? `${form.ocrProvider} · ${form.ocrModel}` : '未配置'}
              </p>
            </div>
            <ConfiguredBadge configured={ocrConfigured} />
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
                    {Object.entries(LLM_PROVIDERS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                  </select>
                </label>
                <label className={panel.label}>
                  模型名称
                  <select className={panel.select} value={form.llmModel}
                    onChange={(e) => setForm({ ...form, llmModel: e.target.value })}>
                    {(LLM_PROVIDERS[form.llmBinding]?.models || []).map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
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
                    {Object.entries(LLM_PROVIDERS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                  </select>
                </label>
                <label className={panel.label}>
                  模型名称
                  <select className={panel.select} value={form.embeddingModel}
                    onChange={(e) => setForm({ ...form, embeddingModel: e.target.value })}>
                    {(LLM_PROVIDERS[form.embeddingBinding]?.embeddingModels || []).map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
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
                    onChange={(e) => handleImageProviderChange(e.target.value)}>
                    {Object.entries(IMAGE_PROVIDERS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                  </select>
                </label>
                <label className={panel.label}>
                  生图模型
                  <select className={panel.select} value={form.imageModel}
                    onChange={(e) => setForm({ ...form, imageModel: e.target.value })}>
                    {(IMAGE_PROVIDERS[form.imageProvider]?.models || []).map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </label>
                <label className={panel.label}>
                  API Key
                  <input type="password" className={panel.input} value={form.imageApiKey}
                    onChange={(e) => setForm({ ...form, imageApiKey: e.target.value })}
                    autoComplete="off"
                    placeholder={form.imageApiKey === '********' ? '已配置，留空不修改' : 'sk-...'} />
                </label>
                <label className={panel.label}>
                  API 地址
                  <input className={panel.input} value={form.imageHost}
                    onChange={(e) => setForm({ ...form, imageHost: e.target.value })} />
                </label>
              </div>
            </fieldset>

            {/* ===== 视频生成 ===== */}
            <fieldset className="rounded-lg border border-console-border p-4">
              <legend className="flex items-center gap-2 px-2 text-sm font-medium text-console-text">
                <span className="h-2.5 w-2.5 rounded-full bg-orange-500" />
                视频生成（AI 视频）
              </legend>
              <p className="mb-3 text-xs text-console-muted">
                用于生成教学视频和动画演示。
              </p>
              <div className={`${panel.grid} mt-3`}>
                <label className={panel.label}>
                  视频提供商
                  <select className={panel.select} value={form.videoProvider}
                    onChange={(e) => handleVideoProviderChange(e.target.value)}>
                    {Object.entries(VIDEO_PROVIDERS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                  </select>
                </label>
                <label className={panel.label}>
                  视频模型
                  <select className={panel.select} value={form.videoModel}
                    onChange={(e) => setForm({ ...form, videoModel: e.target.value })}>
                    {(VIDEO_PROVIDERS[form.videoProvider]?.models || []).map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </label>
                <label className={panel.label}>
                  API Key
                  <input type="password" className={panel.input} value={form.videoApiKey}
                    onChange={(e) => setForm({ ...form, videoApiKey: e.target.value })}
                    autoComplete="off"
                    placeholder={form.videoApiKey === '********' ? '已配置，留空不修改' : 'sk-...'} />
                </label>
                <label className={panel.label}>
                  API 地址
                  <input className={panel.input} value={form.videoHost}
                    onChange={(e) => setForm({ ...form, videoHost: e.target.value })} />
                </label>
              </div>
            </fieldset>

            {/* ===== 视觉识别 (OCR) ===== */}
            <fieldset className="rounded-lg border border-console-border p-4">
              <legend className="flex items-center gap-2 px-2 text-sm font-medium text-console-text">
                <span className="h-2.5 w-2.5 rounded-full bg-cyan-500" />
                视觉识别（OCR）
                <ConfiguredBadge configured={ocrConfigured} />
              </legend>
              <p className="mb-3 text-xs text-console-muted">
                用于图片文字识别，支持图片向量化。推荐使用讯飞星火 MaaS，需配置 API Key 和 Secret Key。
              </p>
              <div className={`${panel.grid} mt-3`}>
                <label className={panel.label}>
                  OCR 提供商
                  <select className={panel.select} value={form.ocrProvider}
                    onChange={(e) => handleOcrProviderChange(e.target.value)}>
                    {Object.entries(OCR_PROVIDERS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                  </select>
                </label>
                <label className={panel.label}>
                  OCR 模型
                  <select className={panel.select} value={form.ocrModel}
                    onChange={(e) => setForm({ ...form, ocrModel: e.target.value })}>
                    {(OCR_PROVIDERS[form.ocrProvider]?.models || []).map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
                  </select>
                </label>
                <label className={panel.label}>
                  API Key
                  <input type="password" className={panel.input} value={form.ocrApiKey}
                    onChange={(e) => setForm({ ...form, ocrApiKey: e.target.value })}
                    autoComplete="off"
                    placeholder={form.ocrApiKey === '********' ? '已配置，留空不修改' : 'API Key'} />
                </label>
                {(OCR_PROVIDERS[form.ocrProvider]?.hasSecretKey) && (
                  <label className={panel.label}>
                    Secret Key
                    <input type="password" className={panel.input} value={form.ocrSecretKey}
                      onChange={(e) => setForm({ ...form, ocrSecretKey: e.target.value })}
                      autoComplete="off"
                      placeholder={form.ocrSecretKey === '********' ? '已配置，留空不修改' : 'Secret Key'} />
                  </label>
                )}
                <label className={panel.label}>
                  API 地址
                  <input className={panel.input} value={form.ocrHost}
                    onChange={(e) => setForm({ ...form, ocrHost: e.target.value })} />
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
