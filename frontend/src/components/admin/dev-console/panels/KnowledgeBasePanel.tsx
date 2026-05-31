/**
 * 知识库管理面板 — 查看已上传文档、KB 状态
 */
import { useEffect, useState } from 'react'
import { listAdminUploadTasks } from '@/api/admin/admin'
import type { UploadTask } from '@/api/student/upload'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

export default function KnowledgeBasePanel() {
  const [tasks, setTasks] = useState<UploadTask[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    void (async () => {
      try {
        setTasks(await listAdminUploadTasks(100))
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  const completed = tasks.filter(t => t.status === 'COMPLETED')
  const pending = tasks.filter(t => t.status === 'PENDING' || t.status === 'PROCESSING')
  const failed = tasks.filter(t => t.status === 'FAILED')

  return (
    <div className="space-y-4">
      {/* 概览卡片 */}
      <div className="grid grid-cols-3 gap-3">
        {[
          { label: '已入库', value: completed.length, color: 'text-console-green' },
          { label: '处理中', value: pending.length, color: 'text-console-yellow' },
          { label: '失败', value: failed.length, color: 'text-console-red' },
        ].map(s => (
          <Card key={s.label}>
            <CardContent className="py-3 text-center">
              <p className={`text-2xl font-bold ${s.color}`}>{s.value}</p>
              <p className="text-xs text-console-muted">{s.label}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* 文档列表 */}
      <Card>
        <CardHeader>
          <CardTitle>知识库文档</CardTitle>
          <p className={panel.desc}>共 {tasks.length} 个文件，已向量化 {completed.length} 个</p>
        </CardHeader>
        <CardContent>
          {loading ? (
            <p className={panel.hint}>加载中...</p>
          ) : tasks.length === 0 ? (
            <p className={panel.hint}>暂无文档，请通过「上传队列」或管理员接口上传</p>
          ) : (
            <div className="overflow-x-auto rounded-md border border-console-border">
              <table className={panel.table}>
                <thead>
                  <tr>
                    <th className={panel.th}>文件名</th>
                    <th className={panel.th}>用户</th>
                    <th className={panel.th}>范围</th>
                    <th className={panel.th}>状态</th>
                    <th className={panel.th}>映射知识点</th>
                    <th className={panel.th}>上传时间</th>
                  </tr>
                </thead>
                <tbody>
                  {tasks.map(t => (
                    <tr key={t.id}>
                      <td className={panel.td}>{t.fileName}</td>
                      <td className={panel.td}>{t.userId}</td>
                      <td className={panel.td}>
                        <ConsoleBadge variant={t.kbScope === 'PUBLIC' ? 'success' : 'muted'}>
                          {t.kbScope === 'PUBLIC' ? '公共' : '私有'}
                        </ConsoleBadge>
                      </td>
                      <td className={panel.td}>
                        <ConsoleBadge variant={
                          t.status === 'COMPLETED' ? 'success' :
                          t.status === 'FAILED' ? 'danger' : 'muted'
                        }>
                          {t.status === 'COMPLETED' ? '已入库' :
                           t.status === 'FAILED' ? '失败' :
                           t.status === 'PROCESSING' ? '处理中' : '排队中'}
                        </ConsoleBadge>
                      </td>
                      <td className={`${panel.td} max-w-[200px] truncate`} title={t.mappedKpIds || ''}>
                        {t.mappedKpIds || '-'}
                      </td>
                      <td className={panel.td}>{t.createdAt}</td>
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
