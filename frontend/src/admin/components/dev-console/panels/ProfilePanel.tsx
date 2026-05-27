import { useEffect, useState } from 'react'
import { listAdminProfiles, type AdminProfile } from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle } from '@/admin/components/dev-console/ui'
import { panel } from './panelStyles'

export default function ProfilePanel() {
  const [profiles, setProfiles] = useState<AdminProfile[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    void (async () => {
      try {
        setProfiles(await listAdminProfiles())
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  return (
    <Card>
      <CardHeader>
        <CardTitle>学习画像</CardTitle>
        <p className={panel.desc}>GET /api/admin/profiles — 6 维度画像</p>
      </CardHeader>
      <CardContent>
        {loading ? (
          <p className={panel.hint}>加载中...</p>
        ) : profiles.length === 0 ? (
          <p className={panel.hint}>暂无画像数据</p>
        ) : (
          <div className="overflow-x-auto rounded-md border border-console-border">
            <table className={panel.table}>
              <thead>
                <tr>
                  <th className={panel.th}>用户ID</th>
                  <th className={panel.th}>知识水平</th>
                  <th className={panel.th}>学习目标</th>
                  <th className={panel.th}>认知风格</th>
                  <th className={panel.th}>学习节奏</th>
                  <th className={panel.th}>兴趣方向</th>
                  <th className={panel.th}>偏好资源</th>
                </tr>
              </thead>
              <tbody>
                {profiles.map((p) => (
                  <tr key={p.id}>
                    <td className={panel.td}>{p.userId}</td>
                    <td className={panel.td}>{p.knowledgeLevel}</td>
                    <td className={panel.td}>{p.learningGoal ?? '-'}</td>
                    <td className={panel.td}>{p.cognitiveStyle ?? '-'}</td>
                    <td className={panel.td}>{p.learningPace}</td>
                    <td className={panel.td}>{p.interestDirection ?? '-'}</td>
                    <td className={panel.td}>{p.preferredResourceType ?? '-'}</td>
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
