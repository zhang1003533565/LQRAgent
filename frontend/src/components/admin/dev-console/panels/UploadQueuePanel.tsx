/**
 * 上传队列面板 — 支持上传公共资料 + 查看任务列表
 */
import { useEffect, useRef, useState } from 'react'
import { listAdminUploadTasks, processOneUpload } from '@/api/admin/admin'
import http from '@/api/http'
import type { UploadTask } from '@/api/student/upload'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

const STATUS_LABEL: Record<string, string> = {
  PENDING: '排队中', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败',
}
const STATUS_VARIANT: Record<string, 'warning' | 'default' | 'success' | 'danger'> = {
  PENDING: 'warning', PROCESSING: 'default', COMPLETED: 'success', FAILED: 'danger',
}

export default function UploadQueuePanel() {
  const [tasks, setTasks] = useState<UploadTask[]>([])
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [msg, setMsg] = useState('')
  const [msgOk, setMsgOk] = useState(true)
  const fileRef = useRef<HTMLInputElement>(null)

  async function load() {
    try { setTasks(await listAdminUploadTasks(50)) } finally { setLoading(false) }
  }

  useEffect(() => {
    void load()
    const timer = setInterval(() => void load(), 8000)
    return () => clearInterval(timer)
  }, [])

  async function handleUploadPublic(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    setMsg('')
    try {
      const formData = new FormData()
      formData.append('file', file)
      await http.post('/admin/upload-public', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setMsg(`"${file.name}" 已上传，正在向量化...`)
      setMsgOk(true)
      await load()
    } catch (err: unknown) {
      const resp = (err as { response?: { data?: Record<string, unknown>; status?: number } })?.response
      const detail = (resp?.data?.detail ?? resp?.data?.error ?? resp?.data?.message) as string | undefined
      const status = resp?.status
      setMsg(detail ? `上传失败: ${detail}` : status ? `上传失败 (HTTP ${status})` : '上传失败，请检查后端是否启动')
      setMsgOk(false)
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  async function handleProcessOne() {
    try {
      const ok = await processOneUpload()
      setMsg(ok ? '已触发处理一条待处理任务' : '当前无待处理任务')
      setMsgOk(true)
      await load()
    } catch {
      setMsg('操作失败')
      setMsgOk(false)
    }
  }

  async function handleRetry(taskId: number) {
    try {
      await http.post(`/admin/upload/process/${taskId}`)
      setMsg('已重新触发处理')
      setMsgOk(true)
      await load()
    } catch {
      setMsg('重试失败')
      setMsgOk(false)
    }
  }

  async function handleDeleteTask(taskId: number) {
    if (!confirm('确定删除此任务？')) return
    try {
      await http.delete(`/admin/upload/tasks/${taskId}`)
      setMsg('已删除')
      setMsgOk(true)
      await load()
    } catch {
      setMsg('删除失败')
      setMsgOk(false)
    }
  }

  const completed = tasks.filter(t => t.status === 'COMPLETED').length
  const pending = tasks.filter(t => t.status === 'PENDING' || t.status === 'PROCESSING').length
  const failed = tasks.filter(t => t.status === 'FAILED').length

  return (
    <div className="space-y-4">
      {/* 统计 + 上传 */}
      <div className="grid grid-cols-4 gap-3">
        <Card>
          <CardContent className="py-3 text-center">
            <p className="text-2xl font-bold text-console-text">{tasks.length}</p>
            <p className="text-xs text-console-muted">总任务</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-3 text-center">
            <p className="text-2xl font-bold text-green-400">{completed}</p>
            <p className="text-xs text-console-muted">已完成</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-3 text-center">
            <p className="text-2xl font-bold text-yellow-400">{pending}</p>
            <p className="text-xs text-console-muted">处理中</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-3 text-center">
            <p className="text-2xl font-bold text-red-400">{failed}</p>
            <p className="text-xs text-console-muted">失败</p>
          </CardContent>
        </Card>
      </div>

      {/* 上传公共资料 */}
      <Card>
        <CardHeader className="flex-row items-center justify-between space-y-0">
          <div>
            <CardTitle>上传公共资料</CardTitle>
            <p className={panel.desc}>文件将存入公共知识库 kb-public，立即向量化</p>
          </div>
          <div className="flex gap-2">
            <input ref={fileRef} type="file" className="hidden" accept=".pdf,.md,.txt,.py,.doc,.docx,.pptx,.xlsx,.json,.csv"
              onChange={(e) => void handleUploadPublic(e)} />
            <button className={panel.primaryBtn} disabled={uploading}
              onClick={() => fileRef.current?.click()}>
              {uploading ? '上传中...' : '📤 上传文件'}
            </button>
            <button className={panel.secondaryBtn} onClick={() => void handleProcessOne()}>
              处理一条队列
            </button>
          </div>
        </CardHeader>
        {msg && <CardContent><p className={msgOk ? panel.msgOk : panel.msgErr}>{msg}</p></CardContent>}
      </Card>

      {/* 任务列表 */}
      <Card>
        <CardHeader>
          <CardTitle>任务列表</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <p className={panel.hint}>加载中...</p>
          ) : tasks.length === 0 ? (
            <p className={panel.hint}>暂无任务</p>
          ) : (
            <div className="overflow-x-auto rounded-md border border-console-border">
              <table className={panel.table}>
                <thead>
                  <tr>
                    <th className={panel.th}>文件</th>
                    <th className={panel.th}>范围</th>
                    <th className={panel.th}>状态</th>
                    <th className={panel.th}>知识点</th>
                    <th className={panel.th}>创建时间</th>
                    <th className={panel.th}>错误</th>
                    <th className={panel.th}>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {tasks.map(t => (
                    <tr key={t.id}>
                      <td className={`${panel.td} max-w-[200px] truncate font-medium`}>{t.fileName}</td>
                      <td className={panel.td}>
                        <ConsoleBadge variant={t.kbScope === 'PUBLIC' ? 'success' : 'muted'}>
                          {t.kbScope === 'PUBLIC' ? '公共' : '私有'}
                        </ConsoleBadge>
                      </td>
                      <td className={panel.td}>
                        <ConsoleBadge variant={STATUS_VARIANT[t.status] ?? 'muted'}>
                          {STATUS_LABEL[t.status] ?? t.status}
                        </ConsoleBadge>
                      </td>
                      <td className={`${panel.td} max-w-[150px] truncate`}>{t.mappedKpIds || '—'}</td>
                      <td className={`${panel.td} whitespace-nowrap text-xs`}>
                        {new Date(t.createdAt).toLocaleString('zh-CN')}
                      </td>
                      <td className={`${panel.td} max-w-[160px] truncate text-xs ${t.status === 'FAILED' ? 'text-console-red' : 'text-console-muted'}`}>
                        {t.status === 'FAILED' ? (t.errorMessage || '—') : '—'}
                      </td>
                      <td className={panel.td}>
                        {t.status === 'FAILED' && (
                          <div className="flex gap-1">
                            <button className="text-xs text-console-blue hover:underline" onClick={() => void handleRetry(t.id)}>重试</button>
                            <span className="text-console-border">|</span>
                            <button className="text-xs text-console-red hover:underline" onClick={() => void handleDeleteTask(t.id)}>删除</button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
