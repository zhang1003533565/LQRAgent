import { useEffect, useState } from 'react'
import { listAdminUsers, type AdminUser } from '@/api/admin/admin'
import { Card, CardContent, CardHeader, CardTitle } from '@/admin/components/dev-console/ui'
import { panel } from './panelStyles'

export default function UserListPanel() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    void (async () => {
      try {
        setUsers(await listAdminUsers())
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  return (
    <Card>
      <CardHeader>
        <CardTitle>用户管理</CardTitle>
        <p className={panel.desc}>GET /api/admin/users</p>
      </CardHeader>
      <CardContent>
        <p className={`${panel.hint} mb-4`}>测试账号 admin / student1 默认密码均为 123456</p>
        {loading ? (
          <p className={panel.hint}>加载中...</p>
        ) : users.length === 0 ? (
          <p className={panel.hint}>暂无用户</p>
        ) : (
          <div className="overflow-x-auto rounded-md border border-console-border">
            <table className={panel.table}>
              <thead>
                <tr>
                  <th className={panel.th}>ID</th>
                  <th className={panel.th}>用户名</th>
                  <th className={panel.th}>显示名</th>
                  <th className={panel.th}>角色</th>
                  <th className={panel.th}>状态</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id}>
                    <td className={panel.td}>{u.id}</td>
                    <td className={panel.td}>{u.username}</td>
                    <td className={panel.td}>{u.displayName}</td>
                    <td className={panel.td}>{u.role}</td>
                    <td className={panel.td}>{u.enabled ? '启用' : '禁用'}</td>
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
