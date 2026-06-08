import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

interface AgentPrompt {
  id: number
  agentId: string
  agentName: string
  promptContent: string
  defaultContent: string
  version: number
  updatedAt: string
}

export default function PromptPanel() {
  const [prompts, setPrompts] = useState<AgentPrompt[]>([])
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<string | null>(null)
  const [editContent, setEditContent] = useState('')
  const [saving, setSaving] = useState(false)
  const [msg, setMsg] = useState('')

  async function load() {
    setLoading(true)
    try {
      const res = await fetch('/api/admin/prompts')
      if (res.ok) {
        setPrompts(await res.json())
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  function startEdit(prompt: AgentPrompt) {
    setEditing(prompt.agentId)
    setEditContent(prompt.promptContent)
    setMsg('')
  }

  function cancelEdit() {
    setEditing(null)
    setEditContent('')
  }

  async function savePrompt(agentId: string) {
    setSaving(true)
    setMsg('')
    try {
      const res = await fetch(`/api/admin/prompts/${agentId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: editContent, updatedBy: 'admin' }),
      })
      if (res.ok) {
        setMsg(`✅ ${agentId} 提示词已更新`)
        setEditing(null)
        await load()
      } else {
        setMsg('❌ 保存失败')
      }
    } catch (e) {
      setMsg('❌ 保存失败: ' + (e as Error).message)
    } finally {
      setSaving(false)
    }
  }

  async function resetPrompt(agentId: string) {
    if (!confirm(`确定要重置 ${agentId} 的提示词为默认值吗？`)) return

    setMsg('')
    try {
      const res = await fetch(`/api/admin/prompts/${agentId}/reset`, {
        method: 'POST',
      })
      if (res.ok) {
        setMsg(`✅ ${agentId} 已重置为默认值`)
        await load()
      } else {
        setMsg('❌ 重置失败')
      }
    } catch (e) {
      setMsg('❌ 重置失败: ' + (e as Error).message)
    }
  }

  async function clearCache() {
    try {
      await fetch('/api/admin/prompts/clear-cache', { method: 'POST' })
      setMsg('✅ 缓存已清除')
    } catch {
      setMsg('❌ 清除缓存失败')
    }
  }

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span>Agent 提示词管理</span>
              <ConsoleBadge variant="default">{prompts.length} 个智能体</ConsoleBadge>
            </div>
            <div className="flex gap-2">
              <button
                onClick={clearCache}
                className={panel.secondaryBtn}
              >
                清除缓存
              </button>
              <button
                onClick={() => void load()}
                className={panel.secondaryBtn}
              >
                刷新
              </button>
            </div>
          </CardTitle>
          <p className={panel.desc}>实时管理每个 Agent 的系统提示词，修改立即生效</p>
        </CardHeader>
        <CardContent>
          {msg && (
            <div className={`mb-4 ${msg.startsWith('✅') ? panel.msgOk : panel.msgErr}`}>
              {msg}
            </div>
          )}

          {loading ? (
            <p className={panel.hint}>加载中...</p>
          ) : prompts.length === 0 ? (
            <p className={panel.hint}>暂无提示词数据</p>
          ) : (
            <div className="space-y-4">
              {prompts.map((prompt) => (
                <div
                  key={prompt.agentId}
                  className="rounded-lg border border-console-border bg-console-bg/50 p-4"
                >
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-console-text">{prompt.agentName}</span>
                      <span className="font-mono text-xs text-console-muted">{prompt.agentId}</span>
                      <ConsoleBadge variant="muted">v{prompt.version}</ConsoleBadge>
                    </div>
                    <div className="flex gap-2">
                      {editing === prompt.agentId ? (
                        <>
                          <button
                            onClick={() => void savePrompt(prompt.agentId)}
                            disabled={saving}
                            className={panel.primaryBtn}
                          >
                            {saving ? '保存中...' : '保存'}
                          </button>
                          <button
                            onClick={cancelEdit}
                            className={panel.secondaryBtn}
                          >
                            取消
                          </button>
                        </>
                      ) : (
                        <>
                          <button
                            onClick={() => startEdit(prompt)}
                            className={panel.linkBtn}
                          >
                            编辑
                          </button>
                          <button
                            onClick={() => void resetPrompt(prompt.agentId)}
                            className={panel.dangerBtn}
                          >
                            重置
                          </button>
                        </>
                      )}
                    </div>
                  </div>

                  {editing === prompt.agentId ? (
                    <textarea
                      value={editContent}
                      onChange={(e) => setEditContent(e.target.value)}
                      className="w-full h-64 p-3 font-mono text-sm border border-console-border bg-console-bg text-console-text rounded resize-y focus:outline-none focus:border-console-blue"
                      placeholder="输入提示词内容（Markdown 格式）"
                    />
                  ) : (
                    <pre className="p-3 rounded-md bg-console-bg/80 text-sm text-console-text overflow-auto max-h-48 whitespace-pre-wrap font-mono">
                      {prompt.promptContent}
                    </pre>
                  )}

                  {prompt.updatedAt && (
                    <div className="mt-2 text-xs text-console-muted">
                      最后更新: {new Date(prompt.updatedAt).toLocaleString()}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
