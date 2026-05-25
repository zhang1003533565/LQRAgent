import { useEffect, useState } from 'react'
import { listAdminLearningPaths, type LearningPathItem } from '@/shared/api/admin'
import { Card, CardContent, CardHeader, CardTitle } from '@/admin/components/dev-console/ui'
import { panel } from './panelStyles'

export default function LearningPathPanel() {
  const [paths, setPaths] = useState<LearningPathItem[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    void (async () => {
      try {
        setPaths(await listAdminLearningPaths())
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  return (
    <Card>
      <CardHeader>
        <CardTitle>学习路径</CardTitle>
        <p className={panel.desc}>GET /api/admin/learning-paths — 已生成的个性化路径</p>
      </CardHeader>
      <CardContent>
        {loading ? (
          <p className={panel.hint}>加载中...</p>
        ) : paths.length === 0 ? (
          <p className={panel.hint}>暂无学习路径</p>
        ) : (
          <div className="overflow-x-auto rounded-md border border-console-border">
            <table className={panel.table}>
              <thead>
                <tr>
                  <th className={panel.th}>路径ID</th>
                  <th className={panel.th}>用户ID</th>
                  <th className={panel.th}>目标</th>
                  <th className={panel.th}>步骤数</th>
                  <th className={panel.th}>已完成</th>
                  <th className={panel.th}>创建时间</th>
                </tr>
              </thead>
              <tbody>
                {paths.map((p) => (
                  <tr key={p.id}>
                    <td className={panel.td}>{p.id}</td>
                    <td className={panel.td}>{p.userId}</td>
                    <td className={panel.td}>{p.goal}</td>
                    <td className={panel.td}>{p.stepCount}</td>
                    <td className={panel.td}>
                      <span className={p.completedCount > 0 ? panel.msgOk : panel.hint}>
                        {p.completedCount} / {p.stepCount}
                      </span>
                    </td>
                    <td className={panel.td}>{new Date(p.createdAt).toLocaleString('zh-CN')}</td>
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
