import { useEffect, useState } from 'react'
import {
  PRESET_CONFIG_KEYS,
  deleteSysConfig,
  listSysConfig,
  pingAiServer,
  saveSysConfig,
  type SysConfigItem,
} from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle } from '@/admin/components/dev-console/ui'
import { panel } from './panelStyles'

export default function SystemConfigPanel() {
  const [configs, setConfigs] = useState<SysConfigItem[]>([])
  const [loading, setLoading] = useState(true)
  const [msg, setMsg] = useState('')
  const [pinging, setPinging] = useState(false)
  const [editKey, setEditKey] = useState('ai-server.base-url')
  const [editValue, setEditValue] = useState('')
  const [editRemark, setEditRemark] = useState('')
  const [saving, setSaving] = useState(false)

  async function load() {
    setLoading(true)
    try {
      setConfigs(await listSysConfig())
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  function fillPreset(key: string) {
    const preset = PRESET_CONFIG_KEYS.find((p) => p.key === key)
    setEditKey(key)
    const existing = configs.find((c) => c.configKey === key)
    setEditValue(existing?.configValue ?? '')
    setEditRemark(existing?.remark ?? preset?.label ?? '')
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault()
    setMsg('')
    setSaving(true)
    try {
      await saveSysConfig(editKey.trim(), editValue.trim(), editRemark.trim() || undefined)
      setMsg('保存成功')
      await load()
    } catch (err: unknown) {
      const m = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setMsg(m ?? '保存失败')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(key: string) {
    if (!window.confirm(`删除配置「${key}」？将回退到 application.properties 默认值`)) return
    setMsg('')
    try {
      await deleteSysConfig(key)
      setMsg('已删除')
      await load()
    } catch {
      setMsg('删除失败')
    }
  }

  async function handlePing() {
    setPinging(true)
    setMsg('')
    try {
      const r = await pingAiServer()
      setMsg(r.reachable ? `AI 服务可达：${r.baseUrl}` : `无法连接：${r.baseUrl}`)
    } catch {
      setMsg('连接测试失败')
    } finally {
      setPinging(false)
    }
  }

  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-start justify-between gap-3 space-y-0">
        <div>
          <CardTitle>系统配置</CardTitle>
          <p className={panel.desc}>LQRAgent 服务连接参数（ai-server 地址、上传队列等）</p>
        </div>
        <button type="button" className={panel.secondaryBtn} onClick={() => void handlePing()} disabled={pinging}>
          {pinging ? '测试中...' : '测试 AI 连接'}
        </button>
      </CardHeader>
      <CardContent className="space-y-4">
        {msg && <p className={panel.msg}>{msg}</p>}

        <div className="flex flex-wrap gap-2">
          {PRESET_CONFIG_KEYS.map((p) => (
            <button
              key={p.key}
              type="button"
              className={editKey === p.key ? panel.presetActive : panel.presetBtn}
              onClick={() => fillPreset(p.key)}
            >
              {p.label}
            </button>
          ))}
        </div>

        <form className="space-y-3 rounded-md border border-console-border bg-console-bg/40 p-4" onSubmit={(e) => void handleSave(e)}>
          <label className={panel.label}>
            配置键
            <input className={panel.input} value={editKey} onChange={(e) => setEditKey(e.target.value)} required />
          </label>
          <label className={panel.label}>
            配置值
            <input className={panel.input} value={editValue} onChange={(e) => setEditValue(e.target.value)} required />
          </label>
          <label className={panel.label}>
            备注
            <input className={panel.input} value={editRemark} onChange={(e) => setEditRemark(e.target.value)} />
          </label>
          <button type="submit" className={panel.primaryBtn} disabled={saving}>
            {saving ? '保存中...' : '保存配置'}
          </button>
        </form>

        <h3 className={panel.subTitle}>已保存配置</h3>
        {loading ? (
          <p className={panel.hint}>加载中...</p>
        ) : configs.length === 0 ? (
          <p className={panel.hint}>暂无配置</p>
        ) : (
          <div className="overflow-x-auto rounded-md border border-console-border">
            <table className={panel.table}>
              <thead>
                <tr>
                  <th className={panel.th}>配置键</th>
                  <th className={panel.th}>配置值</th>
                  <th className={panel.th}>备注</th>
                  <th className={panel.th}>更新</th>
                  <th className={panel.th}>操作</th>
                </tr>
              </thead>
              <tbody>
                {configs.map((c) => (
                  <tr key={c.id}>
                    <td className={`${panel.td} font-mono text-xs`}>{c.configKey}</td>
                    <td className={`${panel.td} max-w-xs truncate`}>{c.configValue}</td>
                    <td className={panel.td}>{c.remark ?? '—'}</td>
                    <td className={`${panel.td} text-xs whitespace-nowrap`}>
                      {new Date(c.updatedAt).toLocaleString('zh-CN')}
                    </td>
                    <td className={panel.td}>
                      <button type="button" className={panel.linkBtn} onClick={() => fillPreset(c.configKey)}>
                        编辑
                      </button>
                      {' · '}
                      <button type="button" className={panel.dangerBtn} onClick={() => void handleDelete(c.configKey)}>
                        删除
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
