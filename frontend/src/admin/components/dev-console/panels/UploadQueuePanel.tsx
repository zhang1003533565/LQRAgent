import { useEffect, useState } from 'react'
import { listAdminUploadTasks, processOneUpload } from '@/api/admin/admin'
import type { UploadTask } from '@/student/api/upload'
import { Button, Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/admin/components/dev-console/ui'
import { panel } from './panelStyles'

const STATUS_LABEL: Record<string, string> = {
  PENDING: '排队中',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '失败',
}

const STATUS_VARIANT: Record<string, 'warning' | 'default' | 'success' | 'danger'> = {
  PENDING: 'warning',
  PROCESSING: 'default',
  COMPLETED: 'success',
  FAILED: 'danger',
}

export default function UploadQueuePanel() {
  const [tasks, setTasks] = useState<UploadTask[]>([])
  const [loading, setLoading] = useState(true)
  const [processMsg, setProcessMsg] = useState('')

  async function load() {
    setTasks(await listAdminUploadTasks(50))
  }

  useEffect(() => {
    void load().finally(() => setLoading(false))
    const timer = setInterval(() => void load(), 8000)
    return () => clearInterval(timer)
  }, [])

  async function handleProcessOne() {
    setProcessMsg('')
    try {
      const ok = await processOneUpload()
      setProcessMsg(ok ? '已触发处理一条待处理任务' : '当前无待处理任务')
      await load()
    } catch {
      setProcessMsg('操作失败')
    }
  }

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <div>
          <CardTitle>上传任务队列</CardTitle>
          <p className={panel.desc}>GET /api/admin/upload/tasks</p>
        </div>
        <Button variant="secondary" size="sm" onClick={() => void handleProcessOne()}>
          处理一条待处理
        </Button>
      </CardHeader>
      <CardContent>
        {processMsg && <p className={`${panel.msg} mb-4`}>{processMsg}</p>}
        {loading ? (
          <p className={panel.hint}>加载中...</p>
        ) : tasks.length === 0 ? (
          <p className={panel.hint}>暂无任务</p>
        ) : (
          <div className="overflow-x-auto rounded-md border border-console-border">
            <table className={panel.table}>
              <thead>
                <tr>
                  <th className={panel.th}>ID</th>
                  <th className={panel.th}>用户</th>
                  <th className={panel.th}>文件</th>
                  <th className={panel.th}>范围</th>
                  <th className={panel.th}>状态</th>
                  <th className={panel.th}>映射知识点</th>
                  <th className={panel.th}>创建</th>
                  <th className={panel.th}>完成</th>
                  <th className={panel.th}>错误</th>
                </tr>
              </thead>
              <tbody>
                {tasks.map((t) => (
                  <tr key={t.id}>
                    <td className={panel.td}>{t.id}</td>
                    <td className={panel.td}>{t.userId}</td>
                    <td className={`${panel.td} max-w-[200px] truncate`}>{t.fileName}</td>
                    <td className={panel.td}>{t.kbScope === 'PERSONAL' ? '个人' : '公共'}</td>
                    <td className={panel.td}>
                      <ConsoleBadge variant={STATUS_VARIANT[t.status] ?? 'muted'}>
                        {STATUS_LABEL[t.status] ?? t.status}
                      </ConsoleBadge>
                    </td>
                    <td className={`${panel.td} max-w-[120px] truncate`}>
                      {t.mappedKpIds || '—'}
                    </td>
                    <td className={`${panel.td} whitespace-nowrap text-xs`}>
                      {new Date(t.createdAt).toLocaleString('zh-CN')}
                    </td>
                    <td className={`${panel.td} whitespace-nowrap text-xs`}>
                      {t.finishedAt ? new Date(t.finishedAt).toLocaleString('zh-CN') : '—'}
                    </td>
                    <td className={`${panel.td} max-w-[160px] truncate text-console-red`}>
                      {t.errorMessage ?? '—'}
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
