import { useEffect, useState } from 'react'
import {
  PRESET_CONFIG_KEYS,
  deleteSysConfig,
  listSysConfig,
  pingAiServer,
  saveSysConfig,
  type SysConfigItem,
} from '@/api/admin'
import styles from './SystemConfigPanel.module.css'

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
      const data = await listSysConfig()
      setConfigs(data)
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
    <div className={styles.wrap}>
      <div className={styles.toolbar}>
        <p className={styles.desc}>
          LQRAgent 服务连接参数（如 ai-server 入口地址、上传队列间隔）。<strong>大模型 API Key 请在上方「大模型对话 API」配置。</strong>
        </p>
        <button type="button" className={styles.secondaryBtn} onClick={() => void handlePing()} disabled={pinging}>
          {pinging ? '测试中...' : '测试 AI 连接'}
        </button>
      </div>

      {msg && <p className={styles.msg}>{msg}</p>}

      <div className={styles.presetRow}>
        {PRESET_CONFIG_KEYS.map((p) => (
          <button
            key={p.key}
            type="button"
            className={editKey === p.key ? styles.presetActive : styles.presetBtn}
            onClick={() => fillPreset(p.key)}
          >
            {p.label}
          </button>
        ))}
      </div>

      <form className={styles.form} onSubmit={(e) => void handleSave(e)}>
        <div className={styles.row}>
          <label>配置键</label>
          <input
            value={editKey}
            onChange={(e) => setEditKey(e.target.value)}
            placeholder="如 ai-server.base-url"
            required
          />
        </div>
        <div className={styles.row}>
          <label>配置值</label>
          <input
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            placeholder="配置值"
            required
          />
        </div>
        <div className={styles.row}>
          <label>备注</label>
          <input
            value={editRemark}
            onChange={(e) => setEditRemark(e.target.value)}
            placeholder="可选说明"
          />
        </div>
        <button type="submit" className={styles.primaryBtn} disabled={saving}>
          {saving ? '保存中...' : '保存配置'}
        </button>
      </form>

      <h3 className={styles.listTitle}>已保存配置</h3>
      {loading ? (
        <p className={styles.empty}>加载中...</p>
      ) : configs.length === 0 ? (
        <p className={styles.empty}>暂无配置，请使用上方表单添加</p>
      ) : (
        <table className={styles.table}>
          <thead>
            <tr>
              <th>配置键</th>
              <th>配置值</th>
              <th>备注</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {configs.map((c) => (
              <tr key={c.id}>
                <td className={styles.keyCell}>{c.configKey}</td>
                <td className={styles.valCell}>{c.configValue}</td>
                <td>{c.remark ?? '—'}</td>
                <td>{new Date(c.updatedAt).toLocaleString('zh-CN')}</td>
                <td>
                  <button type="button" className={styles.linkBtn} onClick={() => fillPreset(c.configKey)}>
                    编辑
                  </button>
                  <button type="button" className={styles.dangerBtn} onClick={() => void handleDelete(c.configKey)}>
                    删除
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
