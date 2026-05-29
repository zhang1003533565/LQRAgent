import { useEffect, useState } from 'react'
import { getStudyBehaviors, type StudyBehaviorItem } from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

const ACTION_COLORS: Record<string, 'success' | 'danger' | 'muted' | 'warning'> = {
  VIEW: 'muted',
  CHAT: 'success',
  QUIZ: 'warning',
  UPLOAD: 'danger',
}

export default function StudyBehaviorPanel() {
  const [items, setItems] = useState<StudyBehaviorItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    void (async () => {
      try {
        const data = await getStudyBehaviors(page, 20)
        setItems(data.items)
        setTotal(data.total)
      } finally {
        setLoading(false)
      }
    })()
  }, [page])

  return (
    <Card>
      <CardHeader>
        <CardTitle>学习行为记录</CardTitle>
        <p className={panel.desc}>GET /api/admin/study-behaviors · 共 {total} 条</p>
      </CardHeader>
      <CardContent>
        {loading ? (
          <p className={panel.hint}>加载中...</p>
        ) : items.length === 0 ? (
          <p className={panel.hint}>暂无学习行为记录</p>
        ) : (
          <>
            <div className="overflow-x-auto rounded-md border border-console-border">
              <table className={panel.table}>
                <thead>
                  <tr>
                    <th className={panel.th}>ID</th>
                    <th className={panel.th}>用户</th>
                    <th className={panel.th}>知识点</th>
                    <th className={panel.th}>行为</th>
                    <th className={panel.th}>时长</th>
                    <th className={panel.th}>附加</th>
                    <th className={panel.th}>时间</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((b) => (
                    <tr key={b.id}>
                      <td className={panel.td}>{b.id}</td>
                      <td className={panel.td}>{b.userId}</td>
                      <td className={panel.td}>{b.kpId || '-'}</td>
                      <td className={panel.td}>
                        <ConsoleBadge variant={ACTION_COLORS[b.action] || 'muted'}>
                          {b.action}
                        </ConsoleBadge>
                      </td>
                      <td className={panel.td}>{b.durationSec != null ? `${b.durationSec}s` : '-'}</td>
                      <td className={`${panel.td} max-w-[200px] truncate`} title={b.extra || ''}>
                        {b.extra || '-'}
                      </td>
                      <td className={panel.td}>{b.createdAt}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="mt-3 flex items-center justify-between">
              <span className={panel.hint}>
                第 {page} 页 / 共 {Math.ceil(total / 20)} 页
              </span>
              <div className="flex gap-2">
                <button
                  className={panel.secondaryBtn}
                  disabled={page <= 1}
                  onClick={() => setPage((p) => p - 1)}
                >
                  上一页
                </button>
                <button
                  className={panel.secondaryBtn}
                  disabled={page * 20 >= total}
                  onClick={() => setPage((p) => p + 1)}
                >
                  下一页
                </button>
              </div>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}
