import { useEffect, useState } from 'react'
import { getQuizRecords, type QuizRecordItem } from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/components/admin/dev-console/ui'
import { panel } from './panelStyles'

export default function QuizRecordPanel() {
  const [items, setItems] = useState<QuizRecordItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    void (async () => {
      try {
        const data = await getQuizRecords(page, 20)
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
        <CardTitle>答题记录</CardTitle>
        <p className={panel.desc}>GET /api/admin/quiz-records · 共 {total} 条</p>
      </CardHeader>
      <CardContent>
        {loading ? (
          <p className={panel.hint}>加载中...</p>
        ) : items.length === 0 ? (
          <p className={panel.hint}>暂无答题记录</p>
        ) : (
          <>
            <div className="overflow-x-auto rounded-md border border-console-border">
              <table className={panel.table}>
                <thead>
                  <tr>
                    <th className={panel.th}>ID</th>
                    <th className={panel.th}>用户</th>
                    <th className={panel.th}>知识点</th>
                    <th className={panel.th}>得分</th>
                    <th className={panel.th}>结果</th>
                    <th className={panel.th}>答案</th>
                    <th className={panel.th}>时间</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((r) => (
                    <tr key={r.id}>
                      <td className={panel.td}>{r.id}</td>
                      <td className={panel.td}>{r.userId}</td>
                      <td className={panel.td}>{r.kpId}</td>
                      <td className={panel.td}>{r.score}</td>
                      <td className={panel.td}>
                        <ConsoleBadge variant={r.isCorrect ? 'success' : 'danger'}>
                          {r.isCorrect ? '正确' : '错误'}
                        </ConsoleBadge>
                      </td>
                      <td className={`${panel.td} max-w-[200px] truncate`} title={r.answer}>
                        {r.answer || '-'}
                      </td>
                      <td className={panel.td}>{r.createdAt}</td>
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
